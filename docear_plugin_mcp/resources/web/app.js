(function () {
  var KEY_STORAGE = "twigmark.mcp.apiKey";
  var chatEl = document.getElementById("chat");
  var inputEl = document.getElementById("input");
  var sendBtn = document.getElementById("btn-send");
  var clearBtn = document.getElementById("btn-clear");
  var settingsBtn = document.getElementById("btn-settings");
  var settingsPanel = document.getElementById("settings");
  var keyInput = document.getElementById("mcp-key");
  var saveKeyBtn = document.getElementById("btn-save-key");
  var settingsNote = document.getElementById("settings-note");
  var statusPill = document.getElementById("status-pill");

  var history = [];
  var authRequired = false;
  var busy = false;

  function getKey() {
    try {
      return localStorage.getItem(KEY_STORAGE) || "";
    } catch (e) {
      return "";
    }
  }

  function setKey(value) {
    try {
      if (value) localStorage.setItem(KEY_STORAGE, value);
      else localStorage.removeItem(KEY_STORAGE);
    } catch (e) {}
  }

  function addMessage(role, text, meta) {
    var div = document.createElement("div");
    div.className = "msg " + role;
    div.textContent = text || "";
    if (meta) {
      var m = document.createElement("span");
      m.className = "meta";
      m.textContent = meta;
      div.appendChild(m);
    }
    chatEl.appendChild(div);
    chatEl.scrollTop = chatEl.scrollHeight;
  }

  function setStatus(text, kind) {
    statusPill.textContent = text;
    statusPill.className = "pill" + (kind ? " " + kind : "");
  }

  function authHeaders() {
    var headers = { "Content-Type": "application/json", Accept: "application/json" };
    var key = getKey();
    if (key) headers.Authorization = "Bearer " + key;
    return headers;
  }

  function refreshStatus() {
    return fetch("../api/status", { cache: "no-store" })
      .then(function (r) {
        return r.json();
      })
      .then(function (s) {
        authRequired = !!s.authRequired;
        var parts = [];
        if (s.llmConfigured) parts.push("LLM ready");
        else parts.push("LLM missing");
        if (authRequired) parts.push("auth on");
        else parts.push("local");
        setStatus(parts.join(" · "), s.llmConfigured ? "ok" : "warn");
        if (authRequired && !getKey()) {
          settingsPanel.classList.remove("hidden");
          settingsNote.textContent = "需要填写 MCP API Key";
        }
        if (!s.llmConfigured) {
          addMessage(
            "system",
            "服务器尚未配置大模型 Key。请在 Twigmark「产品设置 → MCP → Web」填写 OpenAI 兼容的 API Key / Base URL / 模型。"
          );
        }
      })
      .catch(function () {
        setStatus("offline", "err");
      });
  }

  function send() {
    if (busy) return;
    var text = (inputEl.value || "").trim();
    if (!text) return;
    if (authRequired && !getKey()) {
      settingsPanel.classList.remove("hidden");
      settingsNote.textContent = "请先保存 MCP API Key";
      return;
    }
    busy = true;
    sendBtn.disabled = true;
    addMessage("user", text);
    inputEl.value = "";
    fetch("../api/chat", {
      method: "POST",
      headers: authHeaders(),
      body: JSON.stringify({ message: text, history: history }),
    })
      .then(function (r) {
        return r.json().then(function (body) {
          return { ok: r.ok, status: r.status, body: body };
        });
      })
      .then(function (res) {
        if (!res.ok) {
          var err = (res.body && res.body.error) || "HTTP " + res.status;
          addMessage("assistant", "错误：" + err);
          if (res.status === 401) {
            settingsPanel.classList.remove("hidden");
            settingsNote.textContent = "API Key 无效或未填写";
          }
          return;
        }
        var reply = res.body.reply || "";
        var trace = res.body.toolTrace || [];
        var meta = "";
        if (trace.length) {
          meta =
            "tools: " +
            trace
              .map(function (t) {
                return (t.ok ? "✓" : "✗") + " " + t.name;
              })
              .join(", ");
        }
        if (res.body.model) meta = (meta ? meta + " · " : "") + res.body.model;
        addMessage("assistant", reply, meta);
        history.push({ role: "user", content: text });
        history.push({ role: "assistant", content: reply });
        if (history.length > 24) history = history.slice(history.length - 24);
      })
      .catch(function (e) {
        addMessage("assistant", "网络错误：" + (e && e.message ? e.message : e));
      })
      .then(function () {
        busy = false;
        sendBtn.disabled = false;
        inputEl.focus();
      });
  }

  settingsBtn.addEventListener("click", function () {
    settingsPanel.classList.toggle("hidden");
    keyInput.value = getKey();
  });
  saveKeyBtn.addEventListener("click", function () {
    setKey((keyInput.value || "").trim());
    settingsNote.textContent = getKey() ? "已保存到本浏览器" : "已清除";
  });
  clearBtn.addEventListener("click", function () {
    history = [];
    chatEl.innerHTML = "";
    addMessage("system", "对话已清空。");
  });
  sendBtn.addEventListener("click", send);
  inputEl.addEventListener("keydown", function (e) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      send();
    }
  });

  keyInput.value = getKey();
  addMessage("system", "Twigmark Web：通过本机 MCP 工具读写导图。先看清当前选中，再让我操作。");
  refreshStatus();
})();
