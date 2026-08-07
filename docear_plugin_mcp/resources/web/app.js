(function () {
  var TOKEN_KEY = "twigmark.web.session";
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
  var treeEl = document.getElementById("tree");
  var nodeFilter = document.getElementById("node-filter");
  var askPanel = document.getElementById("ask-panel");
  var askChat = document.getElementById("ask-chat");
  var askInput = document.getElementById("ask-input");
  var askProfile = document.getElementById("ask-profile");
  var chatEl = document.getElementById("chat");
  var inputEl = document.getElementById("input");
  var sendBtn = document.getElementById("btn-send");
  var convList = document.getElementById("conv-list");
  var convTitle = document.getElementById("conv-title");
  var profileSelect = document.getElementById("profile-select");
  var llmNote = document.getElementById("llm-note");

  var token = localStorage.getItem(TOKEN_KEY) || "";
  var username = "";
  var conversationId = "";
  var askConversationId = "";
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
    }
    if (name === "settings") {
      loadProfiles().then(function () {
        fillLlmForm(currentProfile(), false);
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

  function addMessage(container, role, text, meta, tools) {
    var div = document.createElement("div");
    div.className = "msg " + role;
    if (role === "system") {
      div.textContent = text || "";
    } else {
      var body = document.createElement("div");
      body.className = "body";
      body.innerHTML = renderMarkdown(text || "");
      div.appendChild(body);
    }
    if (tools && tools.length) {
      var row = document.createElement("div");
      row.className = "tools";
      tools.forEach(function (t) {
        var s = document.createElement("span");
        s.className = t.ok ? "ok" : "bad";
        s.textContent = (t.ok ? "✓ " : "✗ ") + (t.name || "tool");
        row.appendChild(s);
      });
      div.appendChild(row);
    }
    if (meta) {
      var m = document.createElement("span");
      m.className = "meta";
      m.textContent = meta;
      div.appendChild(m);
    }
    container.appendChild(div);
    container.scrollTop = container.scrollHeight;
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
      loadProfilesInto(profileSelect);
      loadProfilesInto(askProfile);
      if (!profiles.length) setStatus("需要配置 LLM", "warn");
      else setStatus("就绪", "ok");
    });
  }

  function loadProfilesInto(select) {
    if (!select) return;
    var prev = select.value;
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
      if (p.isDefault) select.value = p.id;
    });
    if (prev) select.value = prev;
  }

  function currentProfile() {
    var id = profileSelect.value || askProfile.value;
    for (var i = 0; i < profiles.length; i++) if (profiles[i].id === id) return profiles[i];
    return profiles[0] || null;
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
    document.getElementById("llm-name").value = asNew ? "OpenRouter" : (p && p.name) || "OpenRouter";
    document.getElementById("llm-base").value =
      asNew || !p ? "https://openrouter.ai/api/v1" : p.baseUrl || "https://openrouter.ai/api/v1";
    document.getElementById("llm-key").value = "";
    document.getElementById("llm-model").value =
      asNew || !p ? "openai/gpt-4o-mini" : p.model || "openai/gpt-4o-mini";
    document.getElementById("llm-default").checked = asNew ? true : !p || !!p.isDefault;
    document.getElementById("llm-provider").value = detectProvider(document.getElementById("llm-base").value);
    if (asNew) profileSelect.value = "";
  }

  var mapsWarmPoll = null;

  function loadMaps() {
    var q = (mapFilter.value || "").trim();
    if (!maps.length) {
      mapCount.textContent = "加载中…";
    }
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
      if (res.body.warming) {
        mapsWarmPoll = setTimeout(loadMaps, 2500);
      }
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
      if (q) {
        open = true;
      } else if (open === undefined) {
        // Auto-open small groups / first group
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
        // show path under folder without repeating folder prefix
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

  function openMap(m) {
    currentMap = m;
    askConversationId = "";
    expanded = {};
    libraryEmpty.classList.add("hidden");
    mapViewer.classList.remove("hidden");
    viewerTitle.textContent = m.title || m.name || "导图";
    viewerPath.textContent = m.relativePath || m.path || "";
    treeEl.innerHTML = '<p class="muted tiny">加载结构…</p>';
    askChat.innerHTML = "";
    addMessage(askChat, "system", "可针对「" + (m.title || m.name) + "」提问。模型会优先阅读这张图。");
    renderMapList();
    api("/maps/json?path=" + encodeURIComponent(m.path) + "&maxDepth=18").then(function (res) {
      if (!res.ok) {
        treeEl.innerHTML = "";
        addMessage(askChat, "system", (res.body && res.body.error) || "无法加载导图");
        return;
      }
      currentTree = res.body.root || null;
      if (currentTree && currentTree.id) expanded[currentTree.id] = true;
      renderTree();
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

  function renderTree() {
    treeEl.innerHTML = "";
    if (!currentTree) {
      treeEl.innerHTML = '<p class="muted tiny">空导图</p>';
      return;
    }
    var needle = (nodeFilter.value || "").trim().toLowerCase();
    var ul = document.createElement("ul");
    ul.className = "tree-node";
    ul.appendChild(renderNode(currentTree, 0, needle));
    treeEl.appendChild(ul);
  }

  function renderNode(node, depth, needle) {
    var li = document.createElement("li");
    li.className = "tree-node";
    var kids = node.children || [];
    var hasKids = kids.length > 0;
    var id = node.id || ("n-" + depth + "-" + Math.random());
    var open = !!expanded[id] || (!!needle && hasKids);
    if (needle && !nodeMatches(node, needle)) {
      li.style.display = "none";
      return li;
    }
    var row = document.createElement("div");
    row.className = "tree-row" + (needle && String(node.text || "").toLowerCase().indexOf(needle) >= 0 ? " hit" : "");
    var toggle = document.createElement("button");
    toggle.type = "button";
    toggle.className = "tree-toggle" + (hasKids ? "" : " leaf");
    toggle.textContent = hasKids ? (open ? "▾" : "▸") : "·";
    toggle.addEventListener("click", function () {
      if (!hasKids) return;
      expanded[id] = !expanded[id];
      renderTree();
    });
    var text = document.createElement("div");
    text.className = "tree-text";
    text.textContent = node.text || "(空节点)";
    if (node.icons && node.icons.length) {
      var meta = document.createElement("span");
      meta.className = "tree-meta";
      node.icons.slice(0, 4).forEach(function (ic) {
        var s = document.createElement("span");
        s.textContent = typeof ic === "string" ? ic : ic.name || "icon";
        meta.appendChild(s);
      });
      text.appendChild(meta);
    }
    if (node.link) {
      var linkMeta = document.createElement("span");
      linkMeta.className = "tree-meta";
      var ls = document.createElement("span");
      ls.textContent = "link";
      linkMeta.appendChild(ls);
      text.appendChild(linkMeta);
    }
    row.appendChild(toggle);
    row.appendChild(text);
    li.appendChild(row);
    var note = node.notePlain || node.note;
    if (note && (!needle || String(note).toLowerCase().indexOf(needle) >= 0 || open)) {
      var noteEl = document.createElement("div");
      noteEl.className = "tree-note";
      noteEl.textContent = String(note).slice(0, 400);
      li.appendChild(noteEl);
    }
    if (hasKids && open) {
      var cul = document.createElement("ul");
      cul.className = "tree-children";
      kids.forEach(function (child) {
        cul.appendChild(renderNode(child, depth + 1, needle));
      });
      li.appendChild(cul);
    }
    return li;
  }

  function setExpandAll(open) {
    function walk(node) {
      if (!node) return;
      if (node.id) expanded[node.id] = open;
      (node.children || []).forEach(walk);
    }
    walk(currentTree);
    if (currentTree && currentTree.id) expanded[currentTree.id] = true;
    renderTree();
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
        profileId: askProfile.value || "",
        mapFile: currentMap.path,
      }),
    })
      .then(function (res) {
        if (!res.ok) {
          addMessage(askChat, "assistant", "错误：" + ((res.body && res.body.error) || res.status));
          return;
        }
        askConversationId = res.body.conversationId || askConversationId;
        addMessage(askChat, "assistant", res.body.reply || "", res.body.model || "", res.body.toolTrace || []);
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
    convTitle.textContent = title || "对话";
    chatEl.innerHTML = "";
    api("/conversations/" + encodeURIComponent(id)).then(function (res) {
      if (!res.ok) {
        addMessage(chatEl, "system", (res.body && res.body.error) || "无法打开对话");
        return;
      }
      var conv = res.body.conversation || {};
      convTitle.textContent = conv.title || title || "对话";
      var msgs = res.body.messages || [];
      if (!msgs.length) addMessage(chatEl, "system", "空对话，开始提问吧。");
      msgs.forEach(function (m) {
        addMessage(
          chatEl,
          m.role === "user" ? "user" : m.role === "assistant" ? "assistant" : "system",
          m.content,
          m.model || ""
        );
      });
      loadConversations();
    });
  }

  function newConversation() {
    api("/conversations", { method: "POST", body: JSON.stringify({ title: "" }) }).then(function (res) {
      if (!res.ok) return;
      conversationId = res.body.id;
      chatEl.innerHTML = "";
      convTitle.textContent = "新对话";
      addMessage(chatEl, "system", "新对话已创建。");
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
        profileId: profileSelect.value || "",
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
        addMessage(chatEl, "assistant", res.body.reply || "", res.body.model || "", res.body.toolTrace || []);
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
    nodeFilterTimer = setTimeout(renderTree, 120);
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
  sendBtn.addEventListener("click", sendChat);
  inputEl.addEventListener("keydown", function (e) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      sendChat();
    }
  });
  document.getElementById("llm-provider").addEventListener("change", function () {
    applyProviderPreset(document.getElementById("llm-provider").value);
  });
  document.getElementById("btn-llm-new").addEventListener("click", function () {
    fillLlmForm(null, true);
    llmNote.textContent = "填写后保存即可新建配置";
  });
  document.getElementById("btn-llm-save").addEventListener("click", function () {
    var cur = currentProfile();
    var creatingNew = !profileSelect.value;
    api("/llm-profiles", {
      method: "POST",
      body: JSON.stringify({
        id: creatingNew ? "" : cur ? cur.id : "",
        name: document.getElementById("llm-name").value.trim(),
        baseUrl: document.getElementById("llm-base").value.trim(),
        apiKey: document.getElementById("llm-key").value,
        model: document.getElementById("llm-model").value.trim(),
        isDefault: document.getElementById("llm-default").checked,
      }),
    }).then(function (res) {
      llmNote.textContent = res.ok ? "已保存（桌面与网页共用）" : (res.body && res.body.error) || "失败";
      if (res.ok) loadProfiles();
    });
  });
  document.getElementById("btn-llm-del").addEventListener("click", function () {
    var cur = currentProfile();
    if (!cur) return;
    api("/llm-profiles/delete", { method: "POST", body: JSON.stringify({ id: cur.id }) }).then(function (res) {
      llmNote.textContent = res.ok ? "已删除" : (res.body && res.body.error) || "失败";
      if (res.ok) loadProfiles();
    });
  });

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
