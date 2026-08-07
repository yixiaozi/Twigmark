(function () {
  var TOKEN_KEY = "twigmark.web.session";
  var PROFILE_KEY = "twigmark.web.profileId";

  var authView = document.getElementById("auth-view");
  var appView = document.getElementById("app-view");
  var authUser = document.getElementById("auth-user");
  var authPass = document.getElementById("auth-pass");
  var authError = document.getElementById("auth-error");
  var authHint = document.getElementById("auth-hint");
  var dbMeta = document.getElementById("db-meta");
  var who = document.getElementById("who");
  var statusPill = document.getElementById("status-pill");
  var mapList = document.getElementById("map-list");
  var mapCount = document.getElementById("map-count");
  var mapFilter = document.getElementById("map-filter");
  var libraryEmpty = document.getElementById("library-empty");
  var mapViewer = document.getElementById("map-viewer");
  var viewerTitle = document.getElementById("viewer-title");
  var viewerPath = document.getElementById("viewer-path");
  var outlineList = document.getElementById("outline-list");
  var browseCrumb = document.getElementById("browse-crumb");
  var nodeDetailBody = document.getElementById("node-detail-body");
  var detailTitle = document.getElementById("detail-title");
  var detailMeta = document.getElementById("detail-meta");
  var detailNote = document.getElementById("detail-note");
  var detailChildren = document.getElementById("detail-children");
  var nodeFilter = document.getElementById("node-filter");
  var askPanel = document.getElementById("ask-panel");
  var askChat = document.getElementById("ask-chat");
  var askInput = document.getElementById("ask-input");
  var askProfile = document.getElementById("ask-profile");
  var chatEl = document.getElementById("chat");
  var inputEl = document.getElementById("input");
  var sendBtn = document.getElementById("btn-send");
  var convList = document.getElementById("conv-list");
  var convTitleInput = document.getElementById("conv-title-input");
  var profileSelect = document.getElementById("profile-select");
  var profileList = document.getElementById("profile-list");
  var llmNote = document.getElementById("llm-note");
  var toastEl = document.getElementById("toast");

  var token = localStorage.getItem(TOKEN_KEY) || "";
  var username = "";
  var conversationId = "";
  var askConversationId = "";
  var selectedProfileId = localStorage.getItem(PROFILE_KEY) || "";
  var profiles = [];
  var maps = [];
  var currentMap = null;
  var currentTree = null;
  var expanded = {};
  var folderOpen = {};
  var busy = false;
  var askBusy = false;
  var filterTimer = null;
  var nodeFilterTimer = null;
  var mapsWarmPoll = null;
  var editingProfileId = "";
  var creatingProfile = false;

  var nodeById = {};
  var parentById = {};
  var flatRows = [];
  var focusIdx = 0;
  var selectedNodeId = "";

  function api(path, opts) {
    opts = opts || {};
    var headers = opts.headers || {};
    headers.Accept = "application/json";
    if (opts.body && !headers["Content-Type"]) headers["Content-Type"] = "application/json";
    if (token) headers.Authorization = "Bearer " + token;
    return fetch("../api" + path, {
      method: opts.method || "GET",
      headers: headers,
      body: opts.body || undefined,
      cache: "no-store",
    }).then(function (r) {
      return r.json().then(function (body) {
        return { ok: r.ok, status: r.status, body: body };
      });
    });
  }

  function toast(msg) {
    if (!toastEl) return;
    toastEl.textContent = msg;
    toastEl.classList.remove("hidden");
    clearTimeout(toast._t);
    toast._t = setTimeout(function () {
      toastEl.classList.add("hidden");
    }, 3200);
  }

  function setStatus(text, kind) {
    if (!statusPill) return;
    statusPill.textContent = text;
    statusPill.className = "pill" + (kind ? " " + kind : "");
  }

  function showAuth(msg) {
    appView.classList.add("hidden");
    authView.classList.remove("hidden");
    authError.textContent = msg || "";
  }

  function showApp() {
    authView.classList.add("hidden");
    appView.classList.remove("hidden");
  }

  function switchView(name) {
    ["library", "chat", "settings"].forEach(function (v) {
      var el = document.getElementById("view-" + v);
      if (el) el.classList.toggle("hidden", v !== name);
    });
    document.querySelectorAll(".nav-btn").forEach(function (btn) {
      btn.classList.toggle("active", btn.getAttribute("data-view") === name);
    });
    if (name === "chat") {
      loadConversations();
      loadProfilesInto(profileSelect);
      scrollToBottom(chatEl);
    }
    if (name === "settings") {
      loadProfiles().then(function () {
        renderProfileList();
        if (creatingProfile) fillLlmForm(null, true);
        else fillLlmForm(selectedProfile() || currentProfile(), false);
      });
    }
  }

  function escapeHtml(text) {
    return String(text || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function renderMarkdown(text) {
    var raw = String(text || "");
    var blocks = [];
    raw = raw.replace(/```([\s\S]*?)```/g, function (_, code) {
      var i = blocks.length;
      blocks.push("<pre><code>" + escapeHtml(code.replace(/^\n/, "")) + "</code></pre>");
      return "%%BLOCK" + i + "%%";
    });
    var html = escapeHtml(raw)
      .replace(/`([^`]+)`/g, "<code>$1</code>")
      .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
      .replace(/\n/g, "<br>");
    return html.replace(/%%BLOCK(\d+)%%/g, function (_, idx) {
      return blocks[Number(idx)] || "";
    });
  }

  function formatTime(ts) {
    if (!ts) return "";
    try {
      return new Date(Number(ts)).toLocaleString();
    } catch (e) {
      return "";
    }
  }

  function formatSize(n) {
    n = Number(n) || 0;
    if (n < 1024) return n + " B";
    if (n < 1024 * 1024) return (n / 1024).toFixed(1) + " KB";
    return (n / (1024 * 1024)).toFixed(1) + " MB";
  }

  function scrollToBottom(container) {
    if (!container) return;
    requestAnimationFrame(function () {
      container.scrollTop = container.scrollHeight;
    });
  }

  function parseTools(tools) {
    if (!tools) return [];
    if (Array.isArray(tools)) return tools;
    if (typeof tools === "string") {
      try {
        var parsed = JSON.parse(tools);
        return Array.isArray(parsed) ? parsed : [];
      } catch (e) {
        return [];
      }
    }
    return [];
  }

  function addMessage(container, role, text, meta, tools, messageId) {
    var row = document.createElement("div");
    row.className = "msg " + role;
    if (messageId) row.setAttribute("data-id", messageId);

    var bubble = document.createElement("div");
    bubble.className = "bubble";

    if (role === "system") {
      bubble.textContent = text || "";
    } else {
      var roleEl = document.createElement("span");
      roleEl.className = "role";
      roleEl.textContent = role === "user" ? "你" : "助手";
      bubble.appendChild(roleEl);
      var body = document.createElement("div");
      body.className = "body";
      body.innerHTML = renderMarkdown(text || "");
      bubble.appendChild(body);
    }

    var toolList = parseTools(tools);
    if (toolList.length) {
      var toolRow = document.createElement("div");
      toolRow.className = "tools";
      toolList.forEach(function (t) {
        var s = document.createElement("span");
        s.className = t.ok ? "ok" : "bad";
        s.textContent = (t.ok ? "✓ " : "✗ ") + (t.name || "tool");
        toolRow.appendChild(s);
      });
      bubble.appendChild(toolRow);
    }

    if ((meta || messageId) && role !== "system") {
      var m = document.createElement("div");
      m.className = "meta";
      if (meta) {
        var metaSpan = document.createElement("span");
        metaSpan.textContent = meta;
        m.appendChild(metaSpan);
      }
      if (role === "assistant" && messageId) {
        var shareBtn = document.createElement("button");
        shareBtn.type = "button";
        shareBtn.className = "share-btn";
        shareBtn.textContent = "公开此回答";
        shareBtn.addEventListener("click", function () {
          shareMessage(messageId);
        });
        m.appendChild(shareBtn);
      }
      bubble.appendChild(m);
    }

    row.appendChild(bubble);
    container.appendChild(row);
    scrollToBottom(container);
  }

  function shareMessage(messageId) {
    if (!messageId) return;
    api("/messages/share", {
      method: "POST",
      body: JSON.stringify({ messageId: messageId, includeTitle: true, expireDays: 30 }),
    }).then(function (res) {
      if (!res.ok) {
        toast((res.body && res.body.error) || "公开失败");
        return;
      }
      var path = (res.body && res.body.path) || "";
      var url = location.origin + path;
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(url).then(
          function () {
            toast("已公开并复制链接（30 天有效，可撤销）");
          },
          function () {
            toast("公开链接：" + url);
          }
        );
      } else {
        window.prompt("公开链接（仅含本条回答）", url);
      }
    });
  }

  function refreshStatus() {
    return api("/status").then(function (res) {
      if (!res.ok) return;
      var s = res.body;
      var regBtn = document.getElementById("btn-register");
      if (s.registrationOpen) {
        authHint.textContent = "首次使用请注册唯一账号。导图库与对话共用此登录。";
        if (regBtn) regBtn.classList.remove("hidden");
      } else {
        authHint.textContent = "请登录。推荐配置 OpenRouter 后即可对导图提问。";
        if (regBtn) regBtn.classList.add("hidden");
      }
      dbMeta.textContent = (s.webchatDbCount || 1) + " 库 · " + (s.machineName || s.machineId || "");
      if (token) setStatus("就绪", "ok");
    });
  }

  function afterLogin(body) {
    token = body.token || "";
    username = body.username || "";
    localStorage.setItem(TOKEN_KEY, token);
    who.textContent = username;
    showApp();
    switchView("library");
    return Promise.all([loadProfiles(), loadMaps(), loadConversations()]);
  }

  function doAuth(register) {
    authError.textContent = "";
    api(register ? "/register" : "/login", {
      method: "POST",
      body: JSON.stringify({ username: authUser.value.trim(), password: authPass.value }),
    }).then(function (res) {
      if (!res.ok) {
        authError.textContent = (res.body && res.body.error) || "失败";
        return;
      }
      afterLogin(res.body);
    });
  }

  function loadProfiles() {
    return api("/llm-profiles").then(function (res) {
      if (!res.ok) return;
      profiles = res.body.profiles || [];
      if (!selectedProfileId) {
        var def = profiles.filter(function (p) {
          return p.isDefault;
        })[0];
        selectedProfileId = (def && def.id) || (profiles[0] && profiles[0].id) || "";
      }
      loadProfilesInto(profileSelect);
      loadProfilesInto(askProfile);
      renderProfileList();
      if (!profiles.length) setStatus("需要配置 LLM", "warn");
      else setStatus("就绪", "ok");
    });
  }

  function loadProfilesInto(select) {
    if (!select) return;
    var prev = select.value || selectedProfileId;
    select.innerHTML = "";
    if (!profiles.length) {
      var opt = document.createElement("option");
      opt.value = "";
      opt.textContent = "未配置大模型";
      select.appendChild(opt);
      return;
    }
    profiles.forEach(function (p) {
      var opt = document.createElement("option");
      opt.value = p.id;
      opt.textContent = (p.isDefault ? "★ " : "") + p.name + " · " + p.model;
      select.appendChild(opt);
    });
    if (prev && profiles.some(function (p) { return p.id === prev; })) select.value = prev;
    else if (selectedProfileId) select.value = selectedProfileId;
  }

  function currentProfile() {
    var id = selectedProfileId || profileSelect.value || askProfile.value;
    for (var i = 0; i < profiles.length; i++) if (profiles[i].id === id) return profiles[i];
    return profiles[0] || null;
  }

  function selectedProfile() {
    for (var i = 0; i < profiles.length; i++) if (profiles[i].id === editingProfileId) return profiles[i];
    return null;
  }

  function renderProfileList() {
    if (!profileList) return;
    profileList.innerHTML = "";
    if (!profiles.length) {
      profileList.innerHTML = '<p class="muted tiny">还没有模型配置，点击「新建」。</p>';
      return;
    }
    profiles.forEach(function (p) {
      var btn = document.createElement("button");
      btn.type = "button";
      btn.className =
        "profile-item" +
        ((creatingProfile ? false : editingProfileId ? editingProfileId === p.id : selectedProfileId === p.id)
          ? " active"
          : "");
      btn.innerHTML = '<span class="t"></span><span class="s"></span>';
      btn.querySelector(".t").textContent = p.name || "未命名";
      btn.querySelector(".s").textContent = (p.model || "") + " · " + (p.baseUrl || "");
      if (p.isDefault) {
        var badge = document.createElement("span");
        badge.className = "badge";
        badge.textContent = "默认";
        btn.appendChild(badge);
      }
      if (selectedProfileId === p.id) {
        var use = document.createElement("span");
        use.className = "badge";
        use.textContent = "当前选用";
        btn.appendChild(use);
      }
      btn.addEventListener("click", function () {
        creatingProfile = false;
        editingProfileId = p.id;
        fillLlmForm(p, false);
        renderProfileList();
      });
      profileList.appendChild(btn);
    });
  }

  function detectProvider(base) {
    var u = (base || "").toLowerCase();
    if (u.indexOf("openrouter.ai") >= 0) return "openrouter";
    if (u.indexOf("api.openai.com") >= 0) return "openai";
    return "custom";
  }

  function applyProviderPreset(provider) {
    var base = document.getElementById("llm-base");
    var model = document.getElementById("llm-model");
    var name = document.getElementById("llm-name");
    if (provider === "openrouter") {
      base.value = "https://openrouter.ai/api/v1";
      if (!model.value || model.value.indexOf("/") < 0) model.value = "openai/gpt-4o-mini";
      if (!name.value || name.value === "Default" || name.value === "OpenAI") name.value = "OpenRouter";
    } else if (provider === "openai") {
      base.value = "https://api.openai.com/v1";
      if (!model.value || model.value.indexOf("/") >= 0) model.value = "gpt-4o-mini";
      if (!name.value || name.value === "OpenRouter") name.value = "OpenAI";
    }
  }

  function fillLlmForm(p, asNew) {
    creatingProfile = !!asNew;
    editingProfileId = asNew ? "" : (p && p.id) || "";
    document.getElementById("llm-editor-title").textContent = asNew ? "新建配置" : "编辑配置";
    document.getElementById("llm-name").value = asNew ? "OpenRouter" : (p && p.name) || "OpenRouter";
    document.getElementById("llm-base").value =
      asNew || !p ? "https://openrouter.ai/api/v1" : p.baseUrl || "https://openrouter.ai/api/v1";
    document.getElementById("llm-key").value = "";
    document.getElementById("llm-model").value =
      asNew || !p ? "openai/gpt-4o-mini" : p.model || "openai/gpt-4o-mini";
    document.getElementById("llm-default").checked = asNew ? true : !p || !!p.isDefault;
    document.getElementById("llm-provider").value = detectProvider(document.getElementById("llm-base").value);
    renderProfileList();
  }

  function loadMaps() {
    var q = (mapFilter.value || "").trim();
    if (!maps.length) mapCount.textContent = "加载中…";
    var url = "/maps?limit=2000" + (q ? "&q=" + encodeURIComponent(q) : "");
    return api(url).then(function (res) {
      if (!res.ok) {
        mapCount.textContent = (res.body && res.body.error) || "加载失败";
        if (res.status === 401) {
          token = "";
          localStorage.removeItem(TOKEN_KEY);
          showAuth("请重新登录");
        }
        return;
      }
      maps = res.body.maps || [];
      if (res.body.warming && !maps.length) {
        mapCount.textContent = "索引构建中…";
        mapList.innerHTML = '<p class="muted tiny">正在扫描导图库，请稍候…</p>';
        if (mapsWarmPoll) clearTimeout(mapsWarmPoll);
        mapsWarmPoll = setTimeout(loadMaps, 1500);
        return;
      }
      if (mapsWarmPoll) {
        clearTimeout(mapsWarmPoll);
        mapsWarmPoll = null;
      }
      var groups = groupMaps(maps);
      mapCount.textContent =
        maps.length + " 张 · " + groups.length + " 组" + (res.body.warming ? " · 刷新中" : "");
      renderMapList();
      if (res.body.warming) mapsWarmPoll = setTimeout(loadMaps, 2500);
    });
  }

  function folderKey(m) {
    var rel = m.relativePath || m.path || "";
    rel = rel.replace(/\\/g, "/");
    var parts = rel.split("/");
    if (parts.length <= 1) return "（根目录）";
    return parts[0] || "（根目录）";
  }

  function groupMaps(list) {
    var order = [];
    var by = {};
    list.forEach(function (m) {
      var key = folderKey(m);
      if (!by[key]) {
        by[key] = [];
        order.push(key);
      }
      by[key].push(m);
    });
    return order.map(function (k) {
      return { name: k, items: by[k] };
    });
  }

  function renderMapList() {
    mapList.innerHTML = "";
    if (!maps.length) {
      mapList.innerHTML = '<p class="muted tiny">没有匹配的导图</p>';
      return;
    }
    var q = (mapFilter.value || "").trim();
    var groups = groupMaps(maps);
    groups.forEach(function (g, idx) {
      var open = folderOpen[g.name];
      if (q) open = true;
      else if (open === undefined) {
        open = g.items.length <= 12 || idx === 0;
        folderOpen[g.name] = open;
      }
      var wrap = document.createElement("div");
      wrap.className = "folder-group" + (open ? " open" : "");
      var head = document.createElement("button");
      head.type = "button";
      head.className = "folder-head";
      head.innerHTML = '<span class="chev"></span><span class="fname"></span><span class="fcnt"></span>';
      head.querySelector(".chev").textContent = open ? "▾" : "▸";
      head.querySelector(".fname").textContent = g.name;
      head.querySelector(".fcnt").textContent = String(g.items.length);
      head.addEventListener("click", function () {
        folderOpen[g.name] = !folderOpen[g.name];
        renderMapList();
      });
      var body = document.createElement("div");
      body.className = "folder-body";
      g.items.forEach(function (m) {
        var btn = document.createElement("button");
        btn.type = "button";
        btn.className = "map-item" + (currentMap && currentMap.path === m.path ? " active" : "");
        btn.innerHTML = '<span class="t"></span><span class="s"></span>';
        btn.querySelector(".t").textContent = m.title || m.name || "(未命名)";
        var sub = m.relativePath || m.path || "";
        if (sub.indexOf(g.name + "/") === 0) sub = sub.substring(g.name.length + 1);
        btn.querySelector(".s").textContent =
          sub + " · " + formatSize(m.size) + " · " + (m.modifiedAt || "").replace(/:\d{2}$/, "");
        btn.addEventListener("click", function () {
          openMap(m);
        });
        body.appendChild(btn);
      });
      wrap.appendChild(head);
      wrap.appendChild(body);
      mapList.appendChild(wrap);
    });
  }

  function nodeMatches(node, needle) {
    if (!needle) return true;
    var text = String(node.text || "").toLowerCase();
    var note = String(node.notePlain || node.note || "").toLowerCase();
    if (text.indexOf(needle) >= 0 || note.indexOf(needle) >= 0) return true;
    var kids = node.children || [];
    for (var i = 0; i < kids.length; i++) if (nodeMatches(kids[i], needle)) return true;
    return false;
  }

  function buildNodeIndex(root) {
    nodeById = {};
    parentById = {};
    function walk(node, parentId) {
      if (!node) return;
      var id = node.id || "root";
      nodeById[id] = node;
      if (parentId) parentById[id] = parentId;
      (node.children || []).forEach(function (c) {
        if (c) walk(c, id);
      });
    }
    walk(root, "");
  }

  function isExpanded(id) {
    return !!expanded[id];
  }

  function toggleExpand(id) {
    if (!nodeById[id] || !(nodeById[id].children || []).length) return;
    expanded[id] = !expanded[id];
    renderBrowse();
  }

  function expandAncestors(id) {
    var cur = id;
    while (cur && parentById[cur]) {
      expanded[parentById[cur]] = true;
      cur = parentById[cur];
    }
  }

  function buildFlatRows() {
    flatRows = [];
    if (!currentTree) return;
    var needle = (nodeFilter.value || "").trim().toLowerCase();

    function pushRow(node, depth) {
      var id = node.id || "root";
      var kids = node.children || [];
      var hasKids = kids.length > 0;
      var open = isExpanded(id) || (!!needle && hasKids);
      flatRows.push({
        id: id,
        node: node,
        depth: depth,
        hasKids: hasKids,
        open: open,
        hit: !!(needle && String(node.text || "").toLowerCase().indexOf(needle) >= 0),
      });
      if (hasKids && open) {
        kids.forEach(function (c) {
          if (!c) return;
          if (needle && !nodeMatches(c, needle) && !hasVisibleDescendant(c, needle)) return;
          pushRow(c, depth + 1);
        });
      }
    }

    function hasVisibleDescendant(node, needle) {
      if (!node) return false;
      if (String(node.text || "").toLowerCase().indexOf(needle) >= 0) return true;
      var kids = node.children || [];
      for (var i = 0; i < kids.length; i++) {
        if (hasVisibleDescendant(kids[i], needle)) return true;
      }
      return false;
    }

    if (needle && !nodeMatches(currentTree, needle) && !hasVisibleDescendant(currentTree, needle)) {
      return;
    }
    pushRow(currentTree, 0);
  }

  function renderBrowse() {
    buildFlatRows();
    outlineList.innerHTML = "";
    if (!flatRows.length) {
      outlineList.innerHTML = '<p class="muted tiny outline-empty">没有可显示的节点</p>';
      return;
    }
    if (focusIdx >= flatRows.length) focusIdx = flatRows.length - 1;
    if (focusIdx < 0) focusIdx = 0;

    flatRows.forEach(function (row, idx) {
      var btn = document.createElement("button");
      btn.type = "button";
      btn.className =
        "outline-row" +
        (idx === focusIdx ? " focused" : "") +
        (row.id === selectedNodeId ? " selected" : "") +
        (row.hit ? " hit" : "") +
        (row.depth === 0 ? " root" : "");
      btn.setAttribute("role", "treeitem");
      btn.setAttribute("data-id", row.id);
      btn.style.paddingLeft = 0.65 + row.depth * 1.15 + "rem";
      btn.innerHTML =
        '<span class="o-chev"></span><span class="o-text"></span><span class="o-meta"></span>';
      btn.querySelector(".o-chev").textContent = row.hasKids ? (row.open ? "▾" : "▸") : "·";
      btn.querySelector(".o-text").textContent = row.node.text || "(空节点)";
      var meta = row.hasKids ? row.node.children.length + " 项" : "";
      if (row.node.notePlain || row.node.note) meta = (meta ? meta + " · " : "") + "有备注";
      btn.querySelector(".o-meta").textContent = meta;
      btn.addEventListener("click", function () {
        focusIdx = idx;
        selectNode(row.id, true);
        renderBrowse();
      });
      btn.addEventListener("dblclick", function (e) {
        e.preventDefault();
        if (row.hasKids) toggleExpand(row.id);
      });
      outlineList.appendChild(btn);
    });

    scrollFocusIntoView();
    renderDetail(selectedNodeId || flatRows[focusIdx].id);
    renderCrumb(selectedNodeId || flatRows[focusIdx].id);
  }

  function scrollFocusIntoView() {
    var el = outlineList.querySelector(".outline-row.focused");
    if (el && el.scrollIntoView) {
      el.scrollIntoView({ block: "nearest", behavior: "smooth" });
    }
  }

  function selectNode(id, fromClick) {
    selectedNodeId = id;
    var idx = -1;
    for (var i = 0; i < flatRows.length; i++) {
      if (flatRows[i].id === id) {
        idx = i;
        break;
      }
    }
    if (idx >= 0) focusIdx = idx;
    renderDetail(id);
    renderCrumb(id);
    if (!fromClick && outlineList) outlineList.focus();
  }

  function renderCrumb(id) {
    if (!browseCrumb) return;
    browseCrumb.innerHTML = "";
    var chain = [];
    var cur = id;
    while (cur && nodeById[cur]) {
      chain.unshift({ id: cur, text: nodeById[cur].text || "(空)" });
      cur = parentById[cur] || "";
    }
    chain.forEach(function (c, i) {
      if (i > 0) {
        var sep = document.createElement("span");
        sep.className = "crumb-sep";
        sep.textContent = "›";
        browseCrumb.appendChild(sep);
      }
      var btn = document.createElement("button");
      btn.type = "button";
      btn.className = "crumb-btn";
      btn.textContent = c.text.length > 24 ? c.text.slice(0, 24) + "…" : c.text;
      btn.addEventListener("click", function () {
        expandAncestors(c.id);
        selectNode(c.id);
        renderBrowse();
      });
      browseCrumb.appendChild(btn);
    });
  }

  function renderDetail(id) {
    var node = nodeById[id];
    var emptyEl = document.querySelector(".detail-empty");
    if (!node) {
      if (nodeDetailBody) nodeDetailBody.classList.add("hidden");
      if (emptyEl) emptyEl.classList.remove("hidden");
      return;
    }
    if (emptyEl) emptyEl.classList.add("hidden");
    if (nodeDetailBody) nodeDetailBody.classList.remove("hidden");
    detailTitle.textContent = node.text || "(空节点)";
    var kids = node.children || [];
    var bits = [];
    bits.push("层级 " + (chainDepth(id) + 1));
    if (kids.length) bits.push(kids.length + " 个子节点");
    detailMeta.textContent = bits.join(" · ");
    var note = node.notePlain || node.note;
    if (note) {
      detailNote.classList.remove("hidden");
      detailNote.textContent = String(note);
    } else {
      detailNote.classList.add("hidden");
      detailNote.textContent = "";
    }
    detailChildren.innerHTML = "";
    if (kids.length) {
      var label = document.createElement("p");
      label.className = "detail-sub muted tiny";
      label.textContent = "子节点";
      detailChildren.appendChild(label);
      kids.slice(0, 12).forEach(function (c) {
        var chip = document.createElement("button");
        chip.type = "button";
        chip.className = "detail-chip";
        chip.textContent = c.text || "(空)";
        chip.addEventListener("click", function () {
          if (c.id) {
            expandAncestors(c.id);
            expanded[parentById[c.id] || id] = true;
            selectNode(c.id);
            renderBrowse();
          }
        });
        detailChildren.appendChild(chip);
      });
      if (kids.length > 12) {
        var more = document.createElement("span");
        more.className = "muted tiny";
        more.textContent = "还有 " + (kids.length - 12) + " 项…";
        detailChildren.appendChild(more);
      }
    }
  }

  function chainDepth(id) {
    var d = 0;
    var cur = id;
    while (parentById[cur]) {
      d++;
      cur = parentById[cur];
    }
    return d;
  }

  function moveFocus(delta) {
    if (!flatRows.length) return;
    focusIdx = Math.max(0, Math.min(flatRows.length - 1, focusIdx + delta));
    selectNode(flatRows[focusIdx].id);
    renderBrowse();
  }

  function browseKeyAction(key, shift) {
    if (!flatRows.length || !mapViewer || mapViewer.classList.contains("hidden")) return false;
    if (isTypingTarget(document.activeElement) && key !== "/") return false;

    var row = flatRows[focusIdx];
    if (!row) return false;

    if (key === "ArrowDown" || key === "j") {
      moveFocus(1);
      return true;
    }
    if (key === "ArrowUp" || key === "k") {
      moveFocus(-1);
      return true;
    }
    if (key === "ArrowRight" || key === "l") {
      if (row.hasKids && !row.open) {
        expanded[row.id] = true;
        renderBrowse();
      } else if (row.hasKids && row.open && focusIdx + 1 < flatRows.length) {
        moveFocus(1);
      }
      return true;
    }
    if (key === "ArrowLeft" || key === "h") {
      if (row.hasKids && row.open) {
        expanded[row.id] = false;
        renderBrowse();
      } else if (parentById[row.id]) {
        selectNode(parentById[row.id]);
        renderBrowse();
      }
      return true;
    }
    if (key === "Enter") {
      if (row.hasKids) {
        expanded[row.id] = !row.open;
        renderBrowse();
      }
      return true;
    }
    if (key === " ") {
      if (row.hasKids) {
        expanded[row.id] = !row.open;
        renderBrowse();
      }
      return true;
    }
    if (key === "Home") {
      focusIdx = 0;
      selectNode(flatRows[0].id);
      renderBrowse();
      return true;
    }
    if (key === "End") {
      focusIdx = flatRows.length - 1;
      selectNode(flatRows[focusIdx].id);
      renderBrowse();
      return true;
    }
    if (key === "/" && !shift) {
      nodeFilter.focus();
      nodeFilter.select();
      return true;
    }
    if (key === "?" || (key === "/" && shift)) {
      openAskPanel();
      return true;
    }
    if (key === "a" && !shift) {
      openAskPanel();
      return true;
    }
    return false;
  }

  function isTypingTarget(el) {
    if (!el) return false;
    var tag = (el.tagName || "").toLowerCase();
    return tag === "input" || tag === "textarea" || tag === "select" || el.isContentEditable;
  }

  function openMap(m) {
    currentMap = m;
    askConversationId = "";
    expanded = {};
    focusIdx = 0;
    selectedNodeId = "";
    libraryEmpty.classList.add("hidden");
    mapViewer.classList.remove("hidden");
    viewerTitle.textContent = m.title || m.name || "导图";
    viewerPath.textContent = m.relativePath || m.path || "";
    outlineList.innerHTML = '<p class="muted tiny outline-empty">加载结构…</p>';
    askChat.innerHTML = "";
    addMessage(askChat, "system", "可针对「" + (m.title || m.name) + "」提问。模型会优先阅读这张图。");
    renderMapList();
    api("/maps/json?path=" + encodeURIComponent(m.path) + "&maxDepth=18").then(function (res) {
      if (!res.ok) {
        outlineList.innerHTML = "";
        addMessage(askChat, "system", (res.body && res.body.error) || "无法加载导图");
        return;
      }
      currentTree = res.body.root || null;
      buildNodeIndex(currentTree);
      if (currentTree && currentTree.id) expanded[currentTree.id] = true;
      (currentTree && currentTree.children ? currentTree.children : []).forEach(function (c) {
        if (c && c.id) expanded[c.id] = true;
      });
      focusIdx = 0;
      selectedNodeId = currentTree && currentTree.id ? currentTree.id : "";
      renderBrowse();
      if (outlineList) outlineList.focus();
    });
  }

  function setExpandAll(open) {
    function walk(node) {
      if (!node) return;
      if (node.id) expanded[node.id] = open;
      (node.children || []).forEach(walk);
    }
    walk(currentTree);
    if (currentTree && currentTree.id) expanded[currentTree.id] = true;
    renderBrowse();
  }

  function bindBrowseKeys() {
    document.addEventListener("keydown", function (e) {
      if (browseKeyAction(e.key, e.shiftKey)) {
        e.preventDefault();
      }
    });
    if (outlineList) {
      outlineList.addEventListener("keydown", function (e) {
        if (browseKeyAction(e.key, e.shiftKey)) {
          e.preventDefault();
        }
      });
    }
    nodeFilter.addEventListener("keydown", function (e) {
      if (e.key === "Escape") {
        nodeFilter.value = "";
        renderBrowse();
        outlineList.focus();
        e.preventDefault();
      }
      if (e.key === "ArrowDown") {
        outlineList.focus();
        e.preventDefault();
      }
    });
  }

  function openAskPanel() {
    askPanel.classList.add("open");
    askInput.focus();
  }

  function closeAskPanel() {
    askPanel.classList.remove("open");
  }

  function sendAsk() {
    if (askBusy || !currentMap) return;
    var text = (askInput.value || "").trim();
    if (!text) return;
    askBusy = true;
    document.getElementById("btn-ask-send").disabled = true;
    addMessage(askChat, "user", text);
    askInput.value = "";
    openAskPanel();
    api("/chat", {
      method: "POST",
      body: JSON.stringify({
        message: text,
        conversationId: askConversationId || "",
        profileId: askProfile.value || selectedProfileId || "",
        mapFile: currentMap.path,
      }),
    })
      .then(function (res) {
        if (!res.ok) {
          addMessage(askChat, "assistant", "错误：" + ((res.body && res.body.error) || res.status));
          return;
        }
        askConversationId = res.body.conversationId || askConversationId;
        addMessage(
          askChat,
          "assistant",
          res.body.reply || "",
          res.body.model || "",
          res.body.toolTrace || [],
          res.body.assistantMessageId || ""
        );
        loadConversations();
      })
      .catch(function (e) {
        addMessage(askChat, "assistant", "网络错误：" + (e && e.message ? e.message : e));
      })
      .then(function () {
        askBusy = false;
        document.getElementById("btn-ask-send").disabled = false;
        askInput.focus();
      });
  }

  function loadConversations() {
    return api("/conversations").then(function (res) {
      if (!res.ok) return;
      var items = res.body.conversations || [];
      convList.innerHTML = "";
      items.forEach(function (c) {
        var btn = document.createElement("button");
        btn.type = "button";
        btn.className = "conv-item" + (c.id === conversationId ? " active" : "");
        var source = c.source === "desktop" ? "desktop" : "web";
        btn.innerHTML =
          '<span class="t"></span><span class="s"><span class="tag ' +
          source +
          '"></span><span class="when"></span></span>';
        btn.querySelector(".t").textContent = c.title || "(未命名)";
        btn.querySelector(".tag").textContent = source === "desktop" ? "桌面" : "网页";
        btn.querySelector(".when").textContent = formatTime(c.updatedAt);
        btn.addEventListener("click", function () {
          openConversation(c.id, c.title);
        });
        convList.appendChild(btn);
      });
    });
  }

  function openConversation(id, title) {
    conversationId = id;
    convTitleInput.value = title || "对话";
    chatEl.innerHTML = "";
    api("/conversations/" + encodeURIComponent(id)).then(function (res) {
      if (!res.ok) {
        addMessage(chatEl, "system", (res.body && res.body.error) || "无法打开对话");
        return;
      }
      var conv = res.body.conversation || {};
      convTitleInput.value = conv.title || title || "对话";
      var msgs = res.body.messages || [];
      if (!msgs.length) addMessage(chatEl, "system", "空对话，开始提问吧。");
      msgs.forEach(function (m) {
        var role = m.role === "user" ? "user" : m.role === "assistant" ? "assistant" : "system";
        addMessage(chatEl, role, m.content, m.model || "", m.toolTraceJson || m.toolTrace || [], m.id || "");
      });
      scrollToBottom(chatEl);
      loadConversations();
    });
  }

  function renameConversation() {
    if (!conversationId) {
      toast("请先选择或创建对话");
      return;
    }
    var title = (convTitleInput.value || "").trim();
    api("/conversations/rename", {
      method: "POST",
      body: JSON.stringify({ id: conversationId, title: title }),
    }).then(function (res) {
      if (!res.ok) {
        toast((res.body && res.body.error) || "重命名失败");
        return;
      }
      convTitleInput.value = res.body.title || title;
      toast("标题已更新");
      loadConversations();
    });
  }

  function newConversation() {
    api("/conversations", { method: "POST", body: JSON.stringify({ title: "" }) }).then(function (res) {
      if (!res.ok) return;
      conversationId = res.body.id;
      chatEl.innerHTML = "";
      convTitleInput.value = "新对话";
      addMessage(chatEl, "system", "新对话已创建。");
      scrollToBottom(chatEl);
      loadConversations();
    });
  }

  function sendChat() {
    if (busy) return;
    var text = (inputEl.value || "").trim();
    if (!text) return;
    busy = true;
    sendBtn.disabled = true;
    addMessage(chatEl, "user", text);
    inputEl.value = "";
    setStatus("生成中…", "warn");
    api("/chat", {
      method: "POST",
      body: JSON.stringify({
        message: text,
        conversationId: conversationId || "",
        profileId: profileSelect.value || selectedProfileId || "",
      }),
    })
      .then(function (res) {
        if (!res.ok) {
          addMessage(chatEl, "assistant", "错误：" + ((res.body && res.body.error) || res.status));
          setStatus("出错", "err");
          if (res.status === 401) {
            token = "";
            localStorage.removeItem(TOKEN_KEY);
            showAuth("请重新登录");
          }
          return;
        }
        conversationId = res.body.conversationId || conversationId;
        addMessage(
          chatEl,
          "assistant",
          res.body.reply || "",
          res.body.model || "",
          res.body.toolTrace || [],
          res.body.assistantMessageId || ""
        );
        setStatus("就绪", "ok");
        loadConversations();
      })
      .catch(function (e) {
        addMessage(chatEl, "assistant", "网络错误：" + (e && e.message ? e.message : e));
        setStatus("网络错误", "err");
      })
      .then(function () {
        busy = false;
        sendBtn.disabled = false;
        inputEl.focus();
      });
  }

  // events
  document.querySelectorAll(".nav-btn").forEach(function (btn) {
    btn.addEventListener("click", function () {
      switchView(btn.getAttribute("data-view"));
    });
  });
  document.getElementById("auth-form").addEventListener("submit", function (e) {
    e.preventDefault();
    doAuth(false);
  });
  document.getElementById("btn-register").addEventListener("click", function () {
    doAuth(true);
  });
  document.getElementById("btn-logout").addEventListener("click", function () {
    api("/logout", { method: "POST" }).finally(function () {
      token = "";
      localStorage.removeItem(TOKEN_KEY);
      showAuth("");
    });
  });
  document.getElementById("btn-refresh-maps").addEventListener("click", loadMaps);
  mapFilter.addEventListener("input", function () {
    clearTimeout(filterTimer);
    filterTimer = setTimeout(loadMaps, 220);
  });
  nodeFilter.addEventListener("input", function () {
    clearTimeout(nodeFilterTimer);
    nodeFilterTimer = setTimeout(function () {
      if (nodeFilter.value.trim()) {
        function expandForFilter(node) {
          if (!node) return false;
          var id = node.id || "";
          var selfHit =
            String(node.text || "").toLowerCase().indexOf(nodeFilter.value.trim().toLowerCase()) >= 0;
          var childHit = false;
          (node.children || []).forEach(function (c) {
            if (expandForFilter(c)) childHit = true;
          });
          if (childHit && id) expanded[id] = true;
          return selfHit || childHit;
        }
        expandForFilter(currentTree);
      }
      renderBrowse();
    }, 120);
  });
  document.getElementById("btn-expand-all").addEventListener("click", function () {
    setExpandAll(true);
  });
  document.getElementById("btn-collapse-all").addEventListener("click", function () {
    setExpandAll(false);
  });
  document.getElementById("btn-ask-map").addEventListener("click", openAskPanel);
  document.getElementById("btn-close-ask").addEventListener("click", closeAskPanel);
  document.getElementById("btn-ask-send").addEventListener("click", sendAsk);
  askInput.addEventListener("keydown", function (e) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      sendAsk();
    }
  });
  document.getElementById("btn-new").addEventListener("click", newConversation);
  document.getElementById("btn-rename").addEventListener("click", renameConversation);
  convTitleInput.addEventListener("keydown", function (e) {
    if (e.key === "Enter") {
      e.preventDefault();
      renameConversation();
    }
  });
  sendBtn.addEventListener("click", sendChat);
  inputEl.addEventListener("keydown", function (e) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      sendChat();
    }
  });
  profileSelect.addEventListener("change", function () {
    selectedProfileId = profileSelect.value || "";
    localStorage.setItem(PROFILE_KEY, selectedProfileId);
  });
  askProfile.addEventListener("change", function () {
    selectedProfileId = askProfile.value || selectedProfileId;
    localStorage.setItem(PROFILE_KEY, selectedProfileId);
    if (profileSelect) profileSelect.value = selectedProfileId;
  });
  document.getElementById("llm-provider").addEventListener("change", function () {
    applyProviderPreset(document.getElementById("llm-provider").value);
  });
  document.getElementById("btn-llm-new").addEventListener("click", function () {
    fillLlmForm(null, true);
    llmNote.textContent = "填写后保存即可新建配置";
  });
  document.getElementById("btn-llm-use").addEventListener("click", function () {
    var id = creatingProfile ? "" : editingProfileId || (currentProfile() && currentProfile().id) || "";
    if (!id) {
      toast("请先保存配置");
      return;
    }
    selectedProfileId = id;
    localStorage.setItem(PROFILE_KEY, id);
    loadProfilesInto(profileSelect);
    loadProfilesInto(askProfile);
    renderProfileList();
    toast("已设为当前选用模型");
  });
  document.getElementById("btn-llm-save").addEventListener("click", function () {
    var id = creatingProfile ? "" : editingProfileId || "";
    api("/llm-profiles", {
      method: "POST",
      body: JSON.stringify({
        id: id,
        name: document.getElementById("llm-name").value.trim(),
        baseUrl: document.getElementById("llm-base").value.trim(),
        apiKey: document.getElementById("llm-key").value,
        model: document.getElementById("llm-model").value.trim(),
        isDefault: document.getElementById("llm-default").checked,
      }),
    }).then(function (res) {
      llmNote.textContent = res.ok ? "已保存（桌面与网页共用）" : (res.body && res.body.error) || "失败";
      if (res.ok) {
        creatingProfile = false;
        if (res.body && res.body.id) {
          editingProfileId = res.body.id;
          if (!selectedProfileId) selectedProfileId = res.body.id;
        }
        loadProfiles();
      }
    });
  });
  document.getElementById("btn-llm-del").addEventListener("click", function () {
    var id = editingProfileId || (currentProfile() && currentProfile().id);
    if (!id) return;
    if (!window.confirm("确定删除该模型配置？")) return;
    api("/llm-profiles/delete", { method: "POST", body: JSON.stringify({ id: id }) }).then(function (res) {
      llmNote.textContent = res.ok ? "已删除" : (res.body && res.body.error) || "失败";
      if (res.ok) {
        if (selectedProfileId === id) selectedProfileId = "";
        editingProfileId = "";
        loadProfiles();
      }
    });
  });

  bindBrowseKeys();

  refreshStatus().then(function () {
    if (!token) {
      showAuth("");
      return;
    }
    api("/me").then(function (res) {
      if (!res.ok) {
        token = "";
        localStorage.removeItem(TOKEN_KEY);
        showAuth("");
        return;
      }
      username = res.body.username;
      who.textContent = username;
      showApp();
      switchView("library");
      loadProfiles();
      loadMaps();
      loadConversations();
    });
  });
})();
