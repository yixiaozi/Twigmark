package org.docear.plugin.mermaid;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.freeplane.core.util.LogUtils;

/**
 * Renders Mermaid via {@code npx @mermaid-js/mermaid-cli} when JavaFX WebView is unavailable
 * (common on Temurin/Zulu JREs without JavaFX).
 */
final class MermaidCliRenderer {

	private static final String DEFAULT_PACKAGE = "@mermaid-js/mermaid-cli@11.4.2";
	private static volatile Boolean available;
	private static volatile String lastError = "";
	private static volatile String npxPath;

	private MermaidCliRenderer() {
	}

	static synchronized boolean ensureAvailable() {
		if (available != null) {
			return available.booleanValue();
		}
		npxPath = findNpx();
		if (npxPath == null) {
			lastError = "npx not found on PATH";
			available = Boolean.FALSE;
			return false;
		}
		available = Boolean.TRUE;
		LogUtils.info("Mermaid: CLI fallback via " + npxPath);
		return true;
	}

	static String getLastError() {
		return lastError == null ? "" : lastError;
	}

	static BufferedImage render(final String source) throws Exception {
		if (!ensureAvailable()) {
			throw new IllegalStateException("Mermaid CLI unavailable: " + lastError);
		}
		final File work = File.createTempFile("docear-mermaid-", "");
		if (!work.delete() || !work.mkdir()) {
			throw new IllegalStateException("cannot create temp dir");
		}
		final File input = new File(work, "diagram.mmd");
		final File output = new File(work, "diagram.png");
		final File puppeteerCfg = new File(work, "puppeteer.json");
		try {
			final OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(input), StandardCharsets.UTF_8);
			try {
				w.write(source);
			}
			finally {
				w.close();
			}
			final String chrome = findChromeExecutable();
			writePuppeteerConfig(puppeteerCfg, chrome);

			final List<String> cmd = new ArrayList<String>();
			if (isMac()) {
				cmd.add("arch");
				cmd.add("-arm64");
			}
			cmd.add(npxPath);
			cmd.add("-y");
			cmd.add(DEFAULT_PACKAGE);
			cmd.add("-i");
			cmd.add(input.getAbsolutePath());
			cmd.add("-o");
			cmd.add(output.getAbsolutePath());
			cmd.add("-b");
			cmd.add("transparent");
			cmd.add("-p");
			cmd.add(puppeteerCfg.getAbsolutePath());

			final ProcessBuilder pb = new ProcessBuilder(cmd);
			pb.redirectErrorStream(true);
			pb.directory(work);
			if (chrome != null) {
				pb.environment().put("PUPPETEER_EXECUTABLE_PATH", chrome);
			}
			// Prefer arm64 node if present under PATH
			final String path = pb.environment().get("PATH");
			if (path != null && path.indexOf("/usr/local/bin") < 0) {
				pb.environment().put("PATH", "/usr/local/bin:/opt/homebrew/bin:" + path);
			}
			final Process p = pb.start();
			final ByteArrayOutputStream bos = new ByteArrayOutputStream();
			final InputStream in = p.getInputStream();
			final byte[] buf = new byte[4096];
			int n;
			while ((n = in.read(buf)) >= 0) {
				bos.write(buf, 0, n);
			}
			final boolean finished = p.waitFor(180, TimeUnit.SECONDS);
			if (!finished) {
				p.destroyForcibly();
				throw new IllegalStateException("mermaid-cli timed out");
			}
			final int code = p.exitValue();
			if (code != 0 || !output.isFile()) {
				final String out = new String(bos.toByteArray(), StandardCharsets.UTF_8);
				// Network fallback when local Chrome/puppeteer fails
				try {
					return MermaidInkRenderer.render(source);
				}
				catch (Throwable inkErr) {
					throw new IllegalStateException("mermaid-cli exit " + code + ": " + truncate(out, 400)
							+ " | ink: " + inkErr.getMessage());
				}
			}
			final BufferedImage img = ImageIO.read(output);
			if (img == null) {
				throw new IllegalStateException("mermaid-cli produced unreadable PNG");
			}
			return img;
		}
		finally {
			silentDelete(input);
			silentDelete(output);
			silentDelete(puppeteerCfg);
			silentDelete(work);
		}
	}

	private static void writePuppeteerConfig(final File file, final String chrome) throws Exception {
		final StringBuilder sb = new StringBuilder();
		sb.append("{\n");
		if (chrome != null) {
			sb.append("  \"executablePath\": ").append(jsonString(chrome)).append(",\n");
		}
		sb.append("  \"args\": [\"--no-sandbox\", \"--disable-gpu\", \"--headless=new\", \"--disable-dev-shm-usage\"]\n");
		sb.append("}\n");
		final OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
		try {
			w.write(sb.toString());
		}
		finally {
			w.close();
		}
	}

	private static String jsonString(final String s) {
		return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	private static String findNpx() {
		final String pathEnv = System.getenv("PATH");
		final String[] extras = new String[] {
				"/usr/local/bin",
				"/opt/homebrew/bin",
				"/usr/bin",
				System.getProperty("user.home", "") + "/.nvm/current/bin"
		};
		final List<String> dirs = new ArrayList<String>();
		if (pathEnv != null) {
			for (final String part : pathEnv.split(File.pathSeparator)) {
				if (part != null && part.length() > 0) {
					dirs.add(part);
				}
			}
		}
		for (int i = 0; i < extras.length; i++) {
			dirs.add(extras[i]);
		}
		final String exe = isWindows() ? "npx.cmd" : "npx";
		for (final String dir : dirs) {
			final File f = new File(dir, exe);
			if (f.isFile() && f.canExecute()) {
				return f.getAbsolutePath();
			}
		}
		return null;
	}

	private static String findChromeExecutable() {
		final String env = System.getenv("PUPPETEER_EXECUTABLE_PATH");
		if (env != null && new File(env).isFile()) {
			return env;
		}
		final String[] candidates;
		if (isWindows()) {
			candidates = new String[] {
					"C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
					"C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe"
			};
		}
		else if (System.getProperty("os.name", "").toLowerCase().indexOf("mac") >= 0) {
			candidates = new String[] {
					"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
					"/Applications/Chromium.app/Contents/MacOS/Chromium",
					"/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge"
			};
		}
		else {
			candidates = new String[] {
					"/usr/bin/google-chrome",
					"/usr/bin/chromium",
					"/usr/bin/chromium-browser"
			};
		}
		for (int i = 0; i < candidates.length; i++) {
			final File f = new File(candidates[i]);
			if (f.isFile()) {
				return f.getAbsolutePath();
			}
		}
		return null;
	}

	private static boolean isWindows() {
		final String os = System.getProperty("os.name", "").toLowerCase();
		return os.indexOf("win") >= 0;
	}

	private static boolean isMac() {
		final String os = System.getProperty("os.name", "").toLowerCase();
		return os.indexOf("mac") >= 0;
	}

	private static void silentDelete(final File f) {
		if (f != null && f.exists()) {
			f.delete();
		}
	}

	private static String truncate(final String s, final int max) {
		if (s == null) {
			return "";
		}
		final String one = s.replace('\n', ' ').trim();
		return one.length() <= max ? one : one.substring(0, max) + "…";
	}
}
