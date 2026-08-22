package org.docear.plugin.mermaid;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.freeplane.core.util.LogUtils;

/** Renders PlantUML via {@code plantuml} CLI or bundled/downloaded plantuml.jar. */
final class PlantUmlCliRenderer {

	private static final String PLANTUML_JAR_URL =
			"https://github.com/plantuml/plantuml/releases/download/v1.2024.7/plantuml-1.2024.7.jar";

	private static volatile Boolean available;
	private static volatile String lastError = "";
	private static volatile String plantumlCommand;
	private static volatile String plantumlJar;

	private PlantUmlCliRenderer() {
	}

	static synchronized boolean ensureAvailable() {
		if (available != null) {
			return available.booleanValue();
		}
		plantumlCommand = findExecutable("plantuml");
		if (plantumlCommand == null) {
			plantumlJar = ensurePlantumlJar();
		}
		if (plantumlCommand != null || plantumlJar != null) {
			available = Boolean.TRUE;
			LogUtils.info("PlantUML: CLI via "
					+ (plantumlCommand != null ? plantumlCommand : plantumlJar));
			return true;
		}
		lastError = "PlantUML unavailable (network required for first download)";
		available = Boolean.FALSE;
		return false;
	}

	static String getLastError() {
		return lastError == null ? "" : lastError;
	}

	static BufferedImage render(final String source) throws Exception {
		return render(source, RichPreviewScale.DEFAULT);
	}

	static BufferedImage render(final String source, final float zoom) throws Exception {
		if (!ensureAvailable()) {
			throw new IllegalStateException("PlantUML unavailable: " + lastError);
		}
		final File work = File.createTempFile("docear-plantuml-", "");
		if (!work.delete() || !work.mkdir()) {
			throw new IllegalStateException("cannot create temp dir");
		}
		final File input = new File(work, "diagram.puml");
		try {
			final OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(input), StandardCharsets.UTF_8);
			try {
				w.write(source);
			}
			finally {
				w.close();
			}
			final List<String> cmd = buildCommand(input, zoom);
			final ProcessBuilder pb = new ProcessBuilder(cmd);
			pb.redirectErrorStream(true);
			pb.directory(work);
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
				throw new IllegalStateException("plantuml timed out");
			}
			final File png = new File(work, "diagram.png");
			if (p.exitValue() != 0 || !png.isFile()) {
				final String out = new String(bos.toByteArray(), StandardCharsets.UTF_8);
				throw new IllegalStateException("plantuml exit " + p.exitValue() + ": " + truncate(out, 400));
			}
			final BufferedImage img = ImageIO.read(png);
			if (img == null) {
				throw new IllegalStateException("plantuml produced unreadable PNG");
			}
			return img;
		}
		finally {
			silentDelete(work);
		}
	}

	private static List<String> buildCommand(final File input, final float zoom) {
		final List<String> cmd = new ArrayList<String>();
		if (plantumlCommand != null) {
			if (isMac()) {
				cmd.add("arch");
				cmd.add("-arm64");
			}
			cmd.add(plantumlCommand);
			cmd.add("-charset");
			cmd.add("UTF-8");
			if (RichPreviewScale.clamp(zoom) > 1.01f) {
				cmd.add("-scale");
				cmd.add(String.valueOf(Math.max(1, Math.round(RichPreviewScale.clamp(zoom)))));
			}
			cmd.add("-tpng");
			cmd.add(input.getAbsolutePath());
		}
		else {
			cmd.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
			cmd.add("-jar");
			cmd.add(plantumlJar);
			cmd.add("-charset");
			cmd.add("UTF-8");
			if (RichPreviewScale.clamp(zoom) > 1.01f) {
				cmd.add("-scale");
				cmd.add(String.valueOf(Math.max(1, Math.round(RichPreviewScale.clamp(zoom)))));
			}
			cmd.add("-tpng");
			cmd.add(input.getAbsolutePath());
		}
		return cmd;
	}

	private static String ensurePlantumlJar() {
		final File cached = new File(RichCache.cacheDir(), "plantuml.jar");
		if (cached.isFile() && cached.length() > 1024L) {
			return cached.getAbsolutePath();
		}
		try {
			copyResourceToFile("/plantuml.jar", cached);
			if (cached.isFile() && cached.length() > 1024L) {
				return cached.getAbsolutePath();
			}
		}
		catch (Throwable t) {
			LogUtils.warn("PlantUML: bundled jar missing", t);
		}
		try {
			downloadJar(PLANTUML_JAR_URL, cached);
			if (cached.isFile() && cached.length() > 1024L) {
				LogUtils.info("PlantUML: downloaded jar to " + cached.getAbsolutePath());
				return cached.getAbsolutePath();
			}
		}
		catch (Throwable t) {
			lastError = "PlantUML download failed: " + t.getMessage();
			LogUtils.warn(lastError, t);
		}
		return null;
	}

	private static void copyResourceToFile(final String resource, final File target) throws Exception {
		final InputStream in = PlantUmlCliRenderer.class.getResourceAsStream(resource);
		if (in == null) {
			return;
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

	private static void downloadJar(final String url, final File target) throws Exception {
		final HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
		conn.setConnectTimeout(20000);
		conn.setReadTimeout(120000);
		conn.setRequestProperty("User-Agent", "Docear-PlantUML/1.0");
		try {
			final InputStream in = conn.getInputStream();
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
		finally {
			conn.disconnect();
		}
	}

	private static String findExecutable(final String name) {
		final String pathEnv = System.getenv("PATH");
		if (pathEnv == null) {
			return null;
		}
		final String[] dirs = pathEnv.split(File.pathSeparator);
		final String[] extras = new String[] { "/opt/homebrew/bin", "/usr/local/bin", "/usr/bin" };
		for (int e = 0; e < extras.length; e++) {
			final File f = new File(extras[e], name);
			if (f.isFile() && f.canExecute()) {
				return f.getAbsolutePath();
			}
		}
		for (int i = 0; i < dirs.length; i++) {
			final File f = new File(dirs[i], name);
			if (f.isFile() && f.canExecute()) {
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
