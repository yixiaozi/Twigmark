#!/usr/bin/env node
"use strict";

const fs = require("fs");
const http = require("http");
const path = require("path");
const url = require("url");

const MIME = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".woff2": "font/woff2"
};

function safePath(rootDir, requestPath) {
  const decoded = decodeURIComponent(requestPath.split("?")[0]);
  const rel = decoded.replace(/^\/+/, "");
  const resolved = path.resolve(rootDir, rel);
  if (!resolved.startsWith(rootDir)) {
    return null;
  }
  return resolved;
}

function startServer(rootDir) {
  return new Promise(function(resolve, reject) {
    const server = http.createServer(function(req, res) {
      let pathname = url.parse(req.url).pathname || "/";
      if (pathname === "/") {
        pathname = "/excalidraw-shell.html";
      }
      const filePath = safePath(rootDir, pathname);
      if (!filePath || !fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
        res.writeHead(404);
        res.end("not found");
        return;
      }
      const ext = path.extname(filePath).toLowerCase();
      res.writeHead(200, { "Content-Type": MIME[ext] || "application/octet-stream" });
      fs.createReadStream(filePath).pipe(res);
    });
    server.on("error", reject);
    server.listen(0, "127.0.0.1", function() {
      resolve(server);
    });
  });
}

async function main() {
  const shellHtml = process.argv[2];
  const inputJson = process.argv[3];
  const outputPng = process.argv[4];
  if (!shellHtml || !inputJson || !outputPng) {
    console.error("usage: node excalidraw-export.cjs <shell.html> <input.json> <output.png>");
    process.exit(2);
  }
  const rootDir = path.dirname(path.resolve(shellHtml));
  const json = fs.readFileSync(inputJson, "utf8");
  const puppeteer = require("puppeteer-core");
  const chromePath = process.env.PUPPETEER_EXECUTABLE_PATH
    || "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
  const server = await startServer(rootDir);
  const port = server.address().port;
  const browser = await puppeteer.launch({
    executablePath: chromePath,
    headless: "new",
    args: ["--no-sandbox", "--disable-gpu", "--disable-dev-shm-usage"]
  });
  try {
    const page = await browser.newPage();
    page.on("pageerror", function(err) {
      console.error("pageerror:", err && err.message ? err.message : String(err));
    });
    page.on("console", function(msg) {
      if (msg.type() === "error") {
        console.error("console:", msg.text());
      }
    });
    await page.goto("http://127.0.0.1:" + port + "/excalidraw-shell.html", {
      waitUntil: "networkidle0",
      timeout: 120000
    });
    await page.waitForFunction("window.excalidrawShellReady === true && typeof window.exportExcalidraw === 'function'", {
      timeout: 120000
    });
    const dataUrl = await page.evaluate(async function(source) {
      return await window.exportExcalidraw(source);
    }, json);
    if (!dataUrl || dataUrl.indexOf("base64,") < 0) {
      throw new Error("export returned no PNG data");
    }
    const b64 = dataUrl.split("base64,")[1];
    fs.writeFileSync(outputPng, Buffer.from(b64, "base64"));
  } finally {
    await browser.close();
    server.close();
  }
}

main().catch(function(err) {
  console.error(err && err.stack ? err.stack : String(err));
  process.exit(1);
});
