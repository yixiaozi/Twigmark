package org.docear.plugin.mermaid;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.freeplane.core.util.LogUtils;

/** Renders Excalidraw JSON to PNG via headless Chrome + {@code @excalidraw/utils}. */
final class ExcalidrawCliRenderer {

	private static volatile Boolean available;
	private static volatile String lastError = "";
	private static volatile String nodePath;
	private static volatile File shellDir;

	private ExcalidrawCliRenderer() {
	}

	static synchronized boolean ensureAvailable() {
		if (available != null) {
			return available.booleanValue();
		}
		nodePath = findNode();
		if (nodePath == null) {
			lastError = "node not found on PATH";
			available = Boolean.FALSE;
			return false;
		}
		try {
			prepareShellResources();
			available = Boolean.TRUE;
			LogUtils.info("Excalidraw: CLI via " + nodePath);
			return true;
		}
		catch (Throwable t) {
			lastError = t.getMessage() != null ? t.getMessage() : "shell init failed";
			available = Boolean.FALSE;
			LogUtils.warn("Excalidraw: unavailable — " + lastError, t);
			return false;
		}
	}

	static String getLastError() {
		return lastError == null ? "" : lastError;
	}

	static BufferedImage render(final String source) throws Exception {
		return render(source, RichPreviewScale.DEFAULT);
	}

	static BufferedImage render(final String source, final float zoom) throws Exception {
		if (!ensureAvailable()) {
			throw new IllegalStateException("Excalidraw unavailable: " + lastError);
		}
		final String json = ExcalidrawNormalize.normalize(source);
		final File work = File.createTempFile("docear-excalidraw-", "");
		if (!work.delete() || !work.mkdir()) {
			throw new IllegalStateException("cannot create temp dir");
		}
		final File input = new File(work, "diagram.json");
		final File output = new File(work, "diagram.png");
		try {
			final OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(input), StandardCharsets.UTF_8);
			try {
				w.write(json);
			}
			finally {
				w.close();
			}
			final List<String> cmd = buildCommand(input, output);
			final ProcessBuilder pb = new ProcessBuilder(cmd);
			pb.redirectErrorStream(true);
			pb.directory(shellDir);
			pb.environment().put("TWIGMARK_PREVIEW_ZOOM", RichPreviewScale.format(RichPreviewScale.clamp(zoom)));
			final String chrome = findChromeExecutable();
			if (chrome != null) {
				pb.environment().put("PUPPETEER_EXECUTABLE_PATH", chrome);
			}
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
			final boolean finished = p.waitFor(120, TimeUnit.SECONDS);
			if (!finished) {
				p.destroyForcibly();
				throw new IllegalStateException("excalidraw export timed out");
			}
			if (p.exitValue() != 0 || !output.isFile()) {
				final String out = new String(bos.toByteArray(), StandardCharsets.UTF_8);
				throw new IllegalStateException("excalidraw export exit " + p.exitValue() + ": " + truncate(out, 400));
			}
			final BufferedImage img = ImageIO.read(output);
			if (img == null) {
				throw new IllegalStateException("excalidraw export produced unreadable PNG");
			}
			return img;
		}
		finally {
			silentDelete(work);
		}
	}

	private static List<String> buildCommand(final File input, final File output) {
		final List<String> cmd = new ArrayList<String>();
		if (isMac()) {
			cmd.add("arch");
			cmd.add("-arm64");
		}
		cmd.add(nodePath);
		final File script = new File(shellDir, "excalidraw-export.cjs");
		cmd.add(script.getAbsolutePath());
		cmd.add(new File(shellDir, "excalidraw-shell.html").getAbsolutePath());
		cmd.add(input.getAbsolutePath());
		cmd.add(output.getAbsolutePath());
		return cmd;
	}

	private static void prepareShellResources() throws Exception {
		final File dir = new File(RichCache.cacheDir(), "excalidraw-shell");
		dir.mkdirs();
		copyResource("excalidraw-shell.html", new File(dir, "excalidraw-shell.html"));
		copyResource("excalidraw-export.cjs", new File(dir, "excalidraw-export.cjs"));
		copyResource("excalidraw-package.json", new File(dir, "package.json"));
		ensureNpmDependencies(dir);
		shellDir = dir;
	}

	private static void ensureNpmDependencies(final File dir) throws Exception {
		final File utils = new File(dir, "node_modules/@excalidraw/utils/dist/prod/index.js");
		if (utils.isFile()) {
			return;
		}
		final String npm = findNpm();
		if (npm == null) {
			throw new IllegalStateException("npm not found (needed once for Excalidraw export deps)");
		}
		LogUtils.info("Excalidraw: installing npm dependencies (first run)…");
		final List<String> cmd = new ArrayList<String>();
		if (isMac()) {
			cmd.add("arch");
			cmd.add("-arm64");
		}
		cmd.add(npm);
		cmd.add("install");
		cmd.add("--omit=dev");
		cmd.add("--no-audit");
		cmd.add("--no-fund");
		final ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.redirectErrorStream(true);
		pb.directory(dir);
		final String path = pb.environment().get("PATH");
		if (path != null && path.indexOf("/opt/homebrew/bin") < 0) {
			pb.environment().put("PATH", "/opt/homebrew/bin:/usr/local/bin:" + path);
		}
		final Process p = pb.start();
		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		final InputStream in = p.getInputStream();
		final byte[] buf = new byte[4096];
		int n;
		while ((n = in.read(buf)) >= 0) {
			bos.write(buf, 0, n);
		}
		final boolean finished = p.waitFor(300, TimeUnit.SECONDS);
		if (!finished) {
			p.destroyForcibly();
			throw new IllegalStateException("npm install timed out");
		}
		if (p.exitValue() != 0 || !utils.isFile()) {
			final String out = new String(bos.toByteArray(), StandardCharsets.UTF_8);
			throw new IllegalStateException("npm install failed: " + truncate(out, 400));
		}
	}

	private static void copyResource(final String name, final File target) throws Exception {
		if (target.isFile() && target.length() > 0) {
			return;
		}
		final InputStream in = ExcalidrawCliRenderer.class.getClassLoader().getResourceAsStream(name);
		if (in == null) {
			throw new IllegalStateException("Missing resource: " + name);
		}
		try {
			final OutputStream out = new FileOutputStream(target);
			try {
				final byte[] buf = new byte[8192];
				int n;
				while ((n = in.read(buf)) >= 0) {
					out.write(buf, 0, n);
				}
			}
			finally {
				out.close();
			}
		}
		finally {
			in.close();
		}
	}

	private static String findNode() {
		final String[] extras = new String[] { "/opt/homebrew/bin", "/usr/local/bin", "/usr/bin" };
		for (int i = 0; i < extras.length; i++) {
			final File f = new File(extras[i], "node");
			if (f.isFile() && f.canExecute()) {
				return f.getAbsolutePath();
			}
		}
		final String pathEnv = System.getenv("PATH");
		if (pathEnv == null) {
			return null;
		}
		for (final String part : pathEnv.split(File.pathSeparator)) {
			final File f = new File(part, "node");
			if (f.isFile() && f.canExecute()) {
				return f.getAbsolutePath();
			}
		}
		return null;
	}

	private static String findNpm() {
		final String[] extras = new String[] { "/opt/homebrew/bin", "/usr/local/bin", "/usr/bin" };
		for (int i = 0; i < extras.length; i++) {
			final File f = new File(extras[i], "npm");
			if (f.isFile() && f.canExecute()) {
				return f.getAbsolutePath();
			}
		}
		final String pathEnv = System.getenv("PATH");
		if (pathEnv == null) {
			return null;
		}
		for (final String part : pathEnv.split(File.pathSeparator)) {
			final File f = new File(part, "npm");
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
		final String[] candidates = isMac()
				? new String[] {
						"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
						"/Applications/Chromium.app/Contents/MacOS/Chromium"
				}
				: new String[] { "/usr/bin/google-chrome", "/usr/bin/chromium" };
		for (int i = 0; i < candidates.length; i++) {
			final File f = new File(candidates[i]);
			if (f.isFile()) {
				return f.getAbsolutePath();
			}
		}
		return null;
	}

	private static boolean isMac() {
		return System.getProperty("os.name", "").toLowerCase().indexOf("mac") >= 0;
	}

	private static void silentDelete(final File f) {
		if (f != null && f.exists()) {
			if (f.isDirectory()) {
				final File[] children = f.listFiles();
				if (children != null) {
					for (int i = 0; i < children.length; i++) {
						children[i].delete();
					}
				}
			}
			f.delete();
		}
	}

	private static String truncate(final String s, final int max) {
		if (s == null) {
			return "";
		}
		final String one = s.replace('\n', ' ').trim();
		return one.length() <= max ? one : one.substring(0, max - 1) + "…";
	}
}
