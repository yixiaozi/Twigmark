package org.docear.plugin.mcp.client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import javax.swing.Timer;

import org.docear.plugin.mcp.DocearMcpConfig;
import org.freeplane.core.util.Compat;
import org.freeplane.core.util.LogUtils;

public final class CursorAiClientSync {

	private static final String SYNC_STATE_FILE = "cursor-plugin-sync.properties";
	private static final String KEY_SYNCED_VERSION = "syncedVersion";
	private static final String KEY_SYNCED_AT = "syncedAt";

	private static CursorAiClientSync instance;

	private CursorAiClientSync() {
		scheduleSync(true);
	}

	public static synchronized void install() {
		if (instance == null) {
			instance = new CursorAiClientSync();
			LogUtils.info("Cursor AI client auto-sync installed.");
		}
	}

	public static void requestSync() {
		if (instance != null) {
			instance.scheduleSync(false);
		}
	}

	private void scheduleSync(final boolean startupDelay) {
		if (!DocearMcpConfig.isCursorPluginSyncEnabled()) {
			return;
		}
		final int delay = startupDelay ? DocearMcpConfig.getCursorPluginSyncDelayMs() : 500;
		final Timer timer = new Timer(delay, new java.awt.event.ActionListener() {
			public void actionPerformed(final java.awt.event.ActionEvent e) {
				runSyncInBackground();
			}
		});
		timer.setRepeats(false);
		timer.start();
	}

	private void runSyncInBackground() {
		final Thread worker = new Thread(new Runnable() {
			public void run() {
				try {
					syncIfNeeded();
				}
				catch (Exception e) {
					LogUtils.warn("Cursor plugin sync failed: " + e.getMessage());
				}
			}
		}, "CursorAiClientSync");
		worker.setDaemon(true);
		worker.start();
	}

	private void syncIfNeeded() throws IOException {
		final File sourceDir = CursorPluginBundleLocator.getBundledCursorPluginDir();
		if (sourceDir == null || !sourceDir.isDirectory()) {
			LogUtils.info("Cursor plugin sync skipped: bundled cursor-plugin directory not found.");
			return;
		}

		final String bundledVersion = readBundledVersion(sourceDir);
		final File targetDir = getTargetDir();
		final String lastSynced = loadLastSyncedVersion();

		if (bundledVersion.equals(lastSynced) && isTargetComplete(targetDir)) {
			LogUtils.info("Cursor plugin sync skipped: already at version " + bundledVersion);
			return;
		}

		copyDirectory(sourceDir, targetDir, bundledVersion);
		saveSyncedVersion(bundledVersion);
		LogUtils.info("Cursor plugin synced to " + targetDir.getAbsolutePath() + " (version " + bundledVersion + "). Reload Cursor Window to apply.");
	}

	private static File getTargetDir() {
		final String home = System.getProperty("user.home", "");
		return new File(home, ".cursor/plugins/local/docear");
	}

	private static String readBundledVersion(final File sourceDir) throws IOException {
		final File versionFile = new File(sourceDir, "VERSION");
		if (!versionFile.isFile()) {
			return "unknown";
		}
		final InputStream in = new BufferedInputStream(new FileInputStream(versionFile));
		try {
			final StringBuilder builder = new StringBuilder();
			int ch;
			while ((ch = in.read()) >= 0) {
				if (ch != '\r' && ch != '\n') {
					builder.append((char) ch);
				}
			}
			final String version = builder.toString().trim();
			return version.length() > 0 ? version : "unknown";
		}
		finally {
			in.close();
		}
	}

	private static boolean isTargetComplete(final File targetDir) {
		return new File(targetDir, "mcp.json").isFile()
		    && new File(targetDir, ".cursor-plugin/plugin.json").isFile()
		    && new File(targetDir, "rules/docear-mcp-context.mdc").isFile()
		    && new File(targetDir, "skills/docear-mcp-context/SKILL.md").isFile();
	}

	private static void copyDirectory(final File sourceDir, final File targetDir, final String bundledVersion)
	    throws IOException {
		if (!targetDir.exists() && !targetDir.mkdirs()) {
			throw new IOException("Cannot create target directory: " + targetDir.getAbsolutePath());
		}
		copyChildren(sourceDir, targetDir, bundledVersion);
	}

	private static void copyChildren(final File sourceDir, final File targetDir, final String bundledVersion)
	    throws IOException {
		final File[] children = sourceDir.listFiles();
		if (children == null) {
			return;
		}
		for (int i = 0; i < children.length; i++) {
			final File child = children[i];
			if (shouldSkip(child.getName())) {
				continue;
			}
			final File dest = new File(targetDir, child.getName());
			if (child.isDirectory()) {
				if (!dest.exists() && !dest.mkdirs()) {
					throw new IOException("Cannot create directory: " + dest.getAbsolutePath());
				}
				copyChildren(child, dest, bundledVersion);
			}
			else {
				copyFile(child, dest);
			}
		}
	}

	private static boolean shouldSkip(final String name) {
		return "README.md".equalsIgnoreCase(name) || "install.ps1".equalsIgnoreCase(name);
	}

	private static void copyFile(final File source, final File dest) throws IOException {
		final InputStream in = new BufferedInputStream(new FileInputStream(source));
		try {
			final OutputStream out = new BufferedOutputStream(new FileOutputStream(dest));
			try {
				final byte[] buffer = new byte[8192];
				int read;
				while ((read = in.read(buffer)) >= 0) {
					out.write(buffer, 0, read);
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

	private static File getSyncStateFile() {
		return new File(Compat.getApplicationUserDirectory(), SYNC_STATE_FILE);
	}

	private static String loadLastSyncedVersion() {
		final File stateFile = getSyncStateFile();
		if (!stateFile.isFile()) {
			return "";
		}
		final Properties properties = new Properties();
		InputStream in = null;
		try {
			in = new BufferedInputStream(new FileInputStream(stateFile));
			properties.load(in);
			return properties.getProperty(KEY_SYNCED_VERSION, "").trim();
		}
		catch (IOException e) {
			return "";
		}
		finally {
			if (in != null) {
				try {
					in.close();
				}
				catch (IOException e) {
					// ignore
				}
			}
		}
	}

	private static void saveSyncedVersion(final String version) throws IOException {
		final Properties properties = new Properties();
		properties.setProperty(KEY_SYNCED_VERSION, version);
		properties.setProperty(KEY_SYNCED_AT, String.valueOf(System.currentTimeMillis()));
		final File stateFile = getSyncStateFile();
		final File parent = stateFile.getParentFile();
		if (parent != null && !parent.exists()) {
			parent.mkdirs();
		}
		OutputStream out = null;
		try {
			out = new BufferedOutputStream(new FileOutputStream(stateFile));
			properties.store(out, "Docear Cursor plugin sync state");
		}
		finally {
			if (out != null) {
				out.close();
			}
		}
	}
}
