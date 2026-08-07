package org.docear.plugin.mcp.audit;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.util.UUID;

import org.docear.plugin.mcp.DocearMcpConfig;
import org.freeplane.core.util.LogUtils;

/**
 * Stable per-install machine identity for multi-PC audit merge.
 * Stored next to audit.db as {@code audit_machine.id}; created once and reused.
 */
public final class McpAuditMachineId {

	private static final Charset UTF8 = Charset.forName("UTF-8");
	private static final Object LOCK = new Object();
	private static volatile String CACHED_ID;
	private static volatile String CACHED_NAME;

	private McpAuditMachineId() {
	}

	public static String getMachineId() {
		ensureLoaded();
		return CACHED_ID;
	}

	public static String getMachineName() {
		ensureLoaded();
		return CACHED_NAME;
	}

	/** Test helper: force id/name without touching disk. */
	static void setForTests(final String machineId, final String machineName) {
		synchronized (LOCK) {
			CACHED_ID = machineId;
			CACHED_NAME = machineName != null ? machineName : machineId;
		}
	}

	static void resetForTests() {
		synchronized (LOCK) {
			CACHED_ID = null;
			CACHED_NAME = null;
		}
	}

	private static void ensureLoaded() {
		if (CACHED_ID != null && CACHED_ID.length() > 0) {
			return;
		}
		synchronized (LOCK) {
			if (CACHED_ID != null && CACHED_ID.length() > 0) {
				return;
			}
			final File file = machineIdFile();
			String id = readFirstLine(file);
			String name = resolveHostName();
			if (id == null || id.length() == 0) {
				id = "m-" + sanitize(name) + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
				writeFile(file, id + "\n" + name + "\n");
				LogUtils.info("Docear MCP audit machine id created: " + id + " (" + name + ")");
			}
			else {
				final String second = readSecondLine(file);
				if (second != null && second.length() > 0) {
					name = second;
				}
			}
			CACHED_ID = id;
			CACHED_NAME = name;
		}
	}

	private static File machineIdFile() {
		return new File(DocearMcpConfig.getAuditDataDir(), "audit_machine.id");
	}

	private static String resolveHostName() {
		try {
			final String host = InetAddress.getLocalHost().getHostName();
			if (host != null && host.trim().length() > 0) {
				return host.trim();
			}
		}
		catch (Exception ignored) {
		}
		final String env = System.getenv("COMPUTERNAME");
		if (env != null && env.trim().length() > 0) {
			return env.trim();
		}
		final String user = System.getProperty("user.name", "pc");
		return user + "-host";
	}

	private static String sanitize(final String raw) {
		if (raw == null || raw.length() == 0) {
			return "pc";
		}
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < raw.length() && sb.length() < 24; i++) {
			final char c = raw.charAt(i);
			if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_') {
				sb.append(Character.toLowerCase(c));
			}
			else if (c == '.' || c == ' ') {
				sb.append('-');
			}
		}
		return sb.length() > 0 ? sb.toString() : "pc";
	}

	private static String readFirstLine(final File file) {
		if (file == null || !file.isFile()) {
			return null;
		}
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), UTF8));
			final String line = reader.readLine();
			return line != null ? line.trim() : null;
		}
		catch (Exception e) {
			LogUtils.warn("Read audit machine id failed: " + e.getMessage(), e);
			return null;
		}
		finally {
			if (reader != null) {
				try {
					reader.close();
				}
				catch (Exception ignored) {
				}
			}
		}
	}

	private static String readSecondLine(final File file) {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), UTF8));
			reader.readLine();
			final String line = reader.readLine();
			return line != null ? line.trim() : null;
		}
		catch (Exception e) {
			return null;
		}
		finally {
			if (reader != null) {
				try {
					reader.close();
				}
				catch (Exception ignored) {
				}
			}
		}
	}

	private static void writeFile(final File file, final String content) {
		try {
			if (!file.getParentFile().exists()) {
				file.getParentFile().mkdirs();
			}
			final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), UTF8));
			try {
				writer.write(content);
			}
			finally {
				writer.close();
			}
		}
		catch (Exception e) {
			LogUtils.warn("Write audit machine id failed: " + e.getMessage(), e);
		}
	}
}
