(function () {
  var TOKEN_KEY = "twigmark.web.session";
  var authView = document.getElementById("auth-view");
  var appView = document.getElementById("app-view");
  var authUser = document.getElementById("auth-user");
  var authPass = document.getElementById("auth-pass");
  var authError = document.getElementById("auth-error");
  var authHint = document.getElementById("auth-hint");
  var chatEl = document.getElementById("chat");
  var emptyState = document.getElementById("empty-state");
  var inputEl = document.getElementById("input");
  var sendBtn = document.getElementById("btn-send");
  var convList = document.getElementById("conv-list");
  var dbMeta = document.getElementById("db-meta");
  var who = document.getElementById("who");
  var convTitle = document.getElementById("conv-title");
  var statusPill = document.getElementById("status-pill");
  var profileSelect = document.getElementById("profile-select");
  var llmPanel = document.getElementById("llm-panel");
  var llmNote = document.getElementById("llm-note");

  var token = localStorage.getItem(TOKEN_KEY) || "";
  var username = "";
  var conversationId = "";
  var profiles = [];
  var busy = false;

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
    html = html.replace(/%%BLOCK(\d+)%%/g, function (_, idx) {
      return blocks[Number(idx)] || "";
    });
    return html;
  }

  function hideEmpty() {
    if (emptyState) emptyState.classList.add("hidden");
  }

  function showEmpty() {
    chatEl.innerHTML = "";
    if (emptyState) {
      chatEl.appendChild(emptyState);
      emptyState.classList.remove("hidden");
    }
    convTitle.textContent = "选择或创建对话";
  }

  function addMessage(role, text, meta, tools) {
    hideEmpty();
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
    chatEl.appendChild(div);
    chatEl.scrollTop = chatEl.scrollHeight;
  }

  function formatTime(ts) {
    if (!ts) return "";
    try {
      return new Date(Number(ts)).toLocaleString();
    } catch (e) {
      return "";
    }
  }

  function refreshStatus() {
    return api("/status").then(function (res) {
      if (!res.ok) return;
      var s = res.body;
      var regBtn = document.getElementById("btn-register");
      if (s.registrationOpen) {
        authHint.textContent =
          "首次使用请注册唯一账号。大模型配置写入 webchat 数据库，与桌面侧栏共用。";
        if (regBtn) regBtn.classList.remove("hidden");
      } else {
        authHint.textContent = "请登录。本产品仅允许一个账号；推荐 OpenRouter。";
        if (regBtn) regBtn.classList.add("hidden");
      }
      dbMeta.textContent =
        (s.webchatDbCount || 1) + " 库 · " + (s.machineName || s.machineId || "");
      if (token) {
        setStatus((s.webchatDbCount || 1) + " db", "ok");
      }
    });
  }

  function afterLogin(body) {
    token = body.token || "";
    username = body.username || "";
    localStorage.setItem(TOKEN_KEY, token);
    who.textContent = username;
    showApp();
    return Promise.all([loadProfiles(), loadConversations()]).then(function () {
      showEmpty();
      addMessage("system", "已登录。桌面与网页的大模型配置、对话历史均在 webchat 数据库中。");
    });
  }

  function doAuth(register) {
    authError.textContent = "";
    var path = register ? "/register" : "/login";
    api(path, {
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
      profileSelect.innerHTML = "";
      if (!profiles.length) {
        var opt = document.createElement("option");
        opt.value = "";
        opt.textContent = "未配置大模型";
        profileSelect.appendChild(opt);
        setStatus("需要配置 LLM", "warn");
        return;
      }
      profiles.forEach(function (p) {
        var opt = document.createElement("option");
        opt.value = p.id;
        opt.textContent = (p.isDefault ? "★ " : "") + p.name + " · " + p.model;
        profileSelect.appendChild(opt);
        if (p.isDefault) profileSelect.value = p.id;
      });
      fillLlmForm(currentProfile());
      setStatus("就绪", "ok");
    });
  }

  function currentProfile() {
    var id = profileSelect.value;
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
    document.getElementById("llm-provider").value = detectProvider(
      document.getElementById("llm-base").value
    );
    if (asNew) profileSelect.value = "";
  }

  function loadConversations() {
    return api("/conversations").then(function (res) {
      if (!res.ok) return;
      var items = res.body.conversations || [];
      dbMeta.textContent = (res.body.dbCount || 1) + " 库 · " + items.length + " 对话";
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
        btn.querySelector(".when").textContent =
          (c.machineName || c.machineId || "") + " · " + formatTime(c.updatedAt);
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
        addMessage("system", (res.body && res.body.error) || "无法打开对话");
        return;
      }
      var conv = res.body.conversation || {};
      convTitle.textContent = conv.title || title || "对话";
      var msgs = res.body.messages || [];
      if (!msgs.length) addMessage("system", "空对话，开始提问吧。");
      msgs.forEach(function (m) {
        var meta = "";
        if (m.model) meta = m.model;
        if (m.machineId) meta = (meta ? meta + " · " : "") + m.machineId;
        addMessage(m.role === "user" ? "user" : m.role === "assistant" ? "assistant" : "system", m.content, meta);
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
      addMessage("system", "新对话已创建。");
      loadConversations();
    });
  }

  function send() {
    if (busy) return;
    var text = (inputEl.value || "").trim();
    if (!text) return;
    busy = true;
    sendBtn.disabled = true;
    addMessage("user", text);
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
          addMessage("assistant", "错误：" + ((res.body && res.body.error) || res.status));
          setStatus("出错", "err");
          if (res.status === 401) {
            token = "";
            localStorage.removeItem(TOKEN_KEY);
            showAuth("请重新登录");
          }
          return;
        }
        conversationId = res.body.conversationId || conversationId;
        var trace = res.body.toolTrace || [];
        var meta = res.body.model || "";
        addMessage("assistant", res.body.reply || "", meta, trace);
        setStatus("就绪", "ok");
        loadConversations();
      })
      .catch(function (e) {
        addMessage("assistant", "网络错误：" + (e && e.message ? e.message : e));
        setStatus("网络错误", "err");
      })
      .then(function () {
        busy = false;
        sendBtn.disabled = false;
        inputEl.focus();
      });
  }

  document.getElementById("btn-login").addEventListener("click", function () {
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
  document.getElementById("btn-new").addEventListener("click", newConversation);
  document.getElementById("btn-llm").addEventListener("click", function () {
    llmPanel.classList.toggle("hidden");
    fillLlmForm(currentProfile(), false);
  });
  profileSelect.addEventListener("change", function () {
    fillLlmForm(currentProfile(), false);
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
      llmNote.textContent = res.ok ? "已保存（桌面侧栏可直接选用）" : (res.body && res.body.error) || "失败";
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
  sendBtn.addEventListener("click", send);
  inputEl.addEventListener("keydown", function (e) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      send();
    }
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
      loadProfiles();
      loadConversations();
      showEmpty();
      addMessage("system", "欢迎回来。可从左侧打开网页或桌面侧栏同步来的对话。");
    });
  });
})();
