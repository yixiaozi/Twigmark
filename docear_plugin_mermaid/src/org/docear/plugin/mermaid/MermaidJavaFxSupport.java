package org.docear.plugin.mermaid;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.freeplane.core.util.LogUtils;

/**
 * Locates / injects a Java 8 JavaFX runtime so Mermaid can render via WebView
 * when the active JVM is Adoptium/Temurin without JavaFX.
 */
final class MermaidJavaFxSupport {
	private static volatile Boolean available;
	private static volatile String lastError = "";

	private MermaidJavaFxSupport() {
	}

	static synchronized boolean ensureAvailable() {
		if (available != null) {
			return available.booleanValue();
		}
		if (canLoadJavaFxClasses()) {
			available = Boolean.TRUE;
			return true;
		}
		final File jre = discoverJavaFxJre();
		if (jre != null) {
			try {
				injectJavaFx(jre);
				if (canLoadJavaFxClasses()) {
					available = Boolean.TRUE;
					LogUtils.info("Mermaid: loaded JavaFX from " + jre.getAbsolutePath());
					return true;
				}
				lastError = "found JavaFX JRE at " + jre.getAbsolutePath()
				        + " but classes still unavailable after inject";
			}
			catch (Throwable t) {
				lastError = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
				LogUtils.warn("Mermaid: failed to inject JavaFX from " + jre, t);
			}
		}
		else {
			lastError = "no bundled/Liberica JavaFX JRE found next to the app";
		}
		available = Boolean.FALSE;
		return false;
	}

	static String getLastError() {
		return lastError == null ? "" : lastError;
	}

	private static boolean canLoadJavaFxClasses() {
		try {
			Class.forName("javafx.embed.swing.JFXPanel");
			Class.forName("javafx.scene.web.WebView");
			Class.forName("javafx.application.Platform");
			return true;
		}
		catch (Throwable t) {
			return false;
		}
	}

	private static File discoverJavaFxJre() {
		for (final File candidate : collectCandidateJreRoots()) {
			if (isJavaFxJre(candidate)) {
				return candidate;
			}
		}
		return null;
	}

	private static List<File> collectCandidateJreRoots() {
		final Set<File> roots = new LinkedHashSet<File>();
		addIfPresent(roots, System.getProperty("org.docear.javafx.home"));
		addIfPresent(roots, System.getenv("DOCEAR_JAVAFX_HOME"));
		final String javaHome = System.getProperty("java.home");
		addIfPresent(roots, javaHome);
		if (javaHome != null) {
			addIfPresent(roots, new File(javaHome).getParent());
		}
		final String resourceDir = System.getProperty("org.freeplane.globalresourcedir");
		if (resourceDir != null) {
			final File resources = new File(resourceDir);
			final File appRoot = resources.getParentFile();
			if (appRoot != null) {
				addIfPresent(roots, new File(appRoot, "jre"));
				addIfPresent(roots, appRoot);
			}
		}
		addIfPresent(roots, new File("jre"));
		addIfPresent(roots, new File(System.getProperty("user.dir", "."), "jre"));
		final String[] libericaGlobs = new String[] {
				"C:\\Program Files\\BellSoft\\LibericaJDK-8-Full",
				"C:\\Program Files\\BellSoft\\LibericaJDK-8",
				System.getProperty("user.home") + "/Library/Java/JavaVirtualMachines",
				"/Library/Java/JavaVirtualMachines"
		};
		for (int i = 0; i < libericaGlobs.length; i++) {
			final File base = new File(libericaGlobs[i]);
			if (!base.exists()) {
				continue;
			}
			addIfPresent(roots, base);
			addIfPresent(roots, new File(base, "jre"));
			final File[] children = base.listFiles();
			if (children != null) {
				for (int j = 0; j < children.length && j < 16; j++) {
					if (children[j].isDirectory()) {
						addIfPresent(roots, children[j]);
						addIfPresent(roots, new File(children[j], "Contents/Home"));
						addIfPresent(roots, new File(children[j], "jre"));
					}
				}
			}
		}
		return new ArrayList<File>(roots);
	}

	private static void addIfPresent(final Set<File> roots, final String path) {
		if (path == null || path.trim().length() == 0) {
			return;
		}
		addIfPresent(roots, new File(path.trim()));
	}

	private static void addIfPresent(final Set<File> roots, final File file) {
		if (file != null && file.isDirectory()) {
			roots.add(file.getAbsoluteFile());
		}
	}

	static boolean isJavaFxJre(final File jreRoot) {
		return jreRoot != null && jfxrtJar(jreRoot).isFile();
	}

	private static File jfxrtJar(final File jreRoot) {
		final File direct = new File(jreRoot, "lib/ext/jfxrt.jar");
		if (direct.isFile()) {
			return direct;
		}
		final File nested = new File(jreRoot, "jre/lib/ext/jfxrt.jar");
		if (nested.isFile()) {
			return nested;
		}
		// OpenJFX modular layout (rare for Docear 8)
		return new File(jreRoot, "lib/javafx.web.jar");
	}

	private static File jreBin(final File jreRoot) {
		final File bin = new File(jreRoot, "bin");
		if (bin.isDirectory()) {
			return bin;
		}
		final File nested = new File(jreRoot, "jre/bin");
		return nested.isDirectory() ? nested : bin;
	}

	private static void injectJavaFx(final File jreRoot) throws Exception {
		final File jar = jfxrtJar(jreRoot);
		if (!jar.isFile()) {
			throw new IllegalStateException("jfxrt.jar missing under " + jreRoot);
		}
		prependLibraryPath(jreBin(jreRoot));
		addJarToSystemClassLoader(jar);
	}

	private static void addJarToSystemClassLoader(final File jar) throws Exception {
		final ClassLoader system = ClassLoader.getSystemClassLoader();
		if (system instanceof URLClassLoader) {
			final Method addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
			addURL.setAccessible(true);
			addURL.invoke(system, jar.toURI().toURL());
			return;
		}
		final URLClassLoader child = new URLClassLoader(new URL[] { jar.toURI().toURL() }, system);
		Thread.currentThread().setContextClassLoader(child);
		Class.forName("javafx.embed.swing.JFXPanel", true, child);
	}

	private static void prependLibraryPath(final File binDir) {
		if (binDir == null || !binDir.isDirectory()) {
			return;
		}
		try {
			final String current = System.getProperty("java.library.path", "");
			final String prefix = binDir.getAbsolutePath();
			if (current.indexOf(prefix) >= 0) {
				return;
			}
			System.setProperty("java.library.path", prefix + File.pathSeparator + current);
			final Field sysPaths = ClassLoader.class.getDeclaredField("sys_paths");
			sysPaths.setAccessible(true);
			sysPaths.set(null, null);
		}
		catch (Throwable t) {
			LogUtils.warn("Mermaid: could not adjust java.library.path for " + binDir, t);
		}
	}
}
