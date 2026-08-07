(function () {
  const versionEl = document.getElementById("version-label");
  const statusEl = document.getElementById("status-label");
  const publishedEl = document.getElementById("published-label");
  const downloadBtn = document.getElementById("download-btn");
  const navReleases = document.getElementById("nav-releases");
  const notesLink = document.getElementById("notes-link");

  function applyRelease(data) {
    if (!data || typeof data !== "object") return;

    if (data.version && versionEl) {
      versionEl.textContent = data.version;
    }
    if (data.status && statusEl) {
      statusEl.textContent = data.status;
    }
    if (data.publishedAt && publishedEl) {
      publishedEl.textContent = "发布于 " + data.publishedAt;
    }
    if (data.downloadUrl && downloadBtn) {
      downloadBtn.href = data.downloadUrl;
    }
    if (data.htmlUrl && navReleases) {
      navReleases.href = data.htmlUrl;
    }
    if (data.notesUrl && notesLink) {
      notesLink.href = data.notesUrl;
    }

    document.title = "Twigmark " + (data.version || "") + " — 本地优先思维导图工作台";
  }

  function normalizeGithubRelease(release) {
    if (!release || !release.tag_name) return null;
    const tag = String(release.tag_name);
    const version = tag.replace(/^v/i, "");
    const asset =
      (release.assets || []).find(function (a) {
        return /twigmark|docear|windows|\.zip|\.exe/i.test(a.name || "");
      }) || (release.assets || [])[0];

    return {
      name: "Twigmark",
      version: version,
      status: /pre|rc|beta|alpha/i.test(tag + " " + (release.name || ""))
        ? "preview"
        : "stable",
      tag: tag,
      publishedAt: (release.published_at || "").slice(0, 10),
      htmlUrl: release.html_url,
      downloadUrl: asset
        ? asset.browser_download_url
        : release.html_url || "https://github.com/yixiaozi/Twigmark/releases/latest",
      notesUrl: "https://github.com/yixiaozi/Twigmark/blob/master/RELEASE_NOTES.md",
      repoUrl: "https://github.com/yixiaozi/Twigmark",
    };
  }

  fetch("./data/release.json", { cache: "no-store" })
    .then(function (r) {
      if (!r.ok) throw new Error("release.json missing");
      return r.json();
    })
    .then(applyRelease)
    .catch(function () {
      /* keep HTML defaults */
    })
    .finally(function () {
      fetch("https://api.github.com/repos/yixiaozi/Twigmark/releases/latest", {
        headers: { Accept: "application/vnd.github+json" },
      })
        .then(function (r) {
          if (!r.ok) throw new Error("api unavailable");
          return r.json();
        })
        .then(function (release) {
          const normalized = normalizeGithubRelease(release);
          if (normalized) applyRelease(normalized);
        })
        .catch(function () {
          /* keep release.json / defaults */
        });
    });
})();
