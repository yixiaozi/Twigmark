package org.docear.plugin.mcp;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.docear.plugin.mcp.server.McpRole;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.Compat;

public final class DocearMcpConfig {
	private static final String PREFIX = "mcp.";
	private static final String DEFAULT_HOST = "127.0.0.1";
	private static final int DEFAULT_PORT = 7720;
	/** Test-only override for audit data directory (null = normal). */
	private static volatile File AUDIT_DATA_DIR_FOR_TESTS;

	private DocearMcpConfig() {
	}

	/** Package/test helper: force audit data dir without ResourceController. */
	public static void setAuditDataDirForTests(final File dir) {
		AUDIT_DATA_DIR_FOR_TESTS = dir;
	}

	public static boolean isEnabled() {
		return getBoolean("enabled", true);
	}

	public static String getHost() {
		return getString("host", DEFAULT_HOST);
	}

	public static int getPort() {
		return getInt("port", DEFAULT_PORT);
	}

	public static boolean isReadOnly() {
		return getBoolean("readonly", false);
	}

	public static boolean isAuthEnabled() {
		return getBoolean("auth.enabled", false);
	}

	public static String getApiKey() {
		return getString("auth.apiKey", "");
	}

	/** Role for the legacy single API key: read | write | owner (default owner). */
	public static McpRole getAuthRole() {
		return McpRole.parse(getString("auth.role", "owner"));
	}

	/**
	 * Extra named keys. Default {@code {configDir}/mcp-access.json}.
	 * Override with {@code mcp.auth.keysFile}.
	 */
	public static File getMcpAccessFile() {
		final String configured = getString("auth.keysFile", "");
		if (configured.length() > 0) {
			return new File(configured);
		}
		return new File(getAuditDataDir(), "mcp-access.json");
	}

	/** True when bind address is not loopback (0.0.0.0, ::, LAN IP, hostname). */
	public static boolean isPublicBind() {
		final String host = getHost();
		if (host == null || host.length() == 0) {
			return false;
		}
		final String h = host.trim().toLowerCase();
		if ("127.0.0.1".equals(h) || "localhost".equals(h) || "::1".equals(h)) {
			return false;
		}
		return true;
	}

	public static boolean isWebEnabled() {
		return getBoolean("web.enabled", true);
	}

	public static String getWebLlmBaseUrl() {
		final String url = getString("web.llm.baseUrl", "https://openrouter.ai/api/v1");
		if (url.endsWith("/")) {
			return url.substring(0, url.length() - 1);
		}
		return url;
	}

	public static String getWebLlmApiKey() {
		return getString("web.llm.apiKey", "");
	}

	public static String getWebLlmModel() {
		return getString("web.llm.model", "openai/gpt-4o-mini");
	}

	public static int getWebLlmMaxToolRounds() {
		final int rounds = getInt("web.llm.maxToolRounds", 8);
		if (rounds < 1) {
			return 1;
		}
		if (rounds > 24) {
			return 24;
		}
		return rounds;
	}

	public static int getWebchatSessionTtlHours() {
		final int hours = getInt("webchat.sessionTtlHours", 720);
		return hours < 1 ? 1 : hours;
	}

	/**
	 * When true, web chat only exposes read/search tools (no writes) unless
	 * {@code mcp.readonly=false} and {@code mcp.web.readOnlyTools=false}.
	 * Defaults to true on public bind or global readonly.
	 */
	public static boolean isWebReadOnlyTools() {
		if (isReadOnly()) {
			return true;
		}
		if (isPublicBind()) {
			return getBoolean("web.readOnlyTools", true);
		}
		return getBoolean("web.readOnlyTools", false);
	}

	/** Required on public bind for the first account registration. */
	public static String getWebRegistrationToken() {
		return getString("web.registrationToken", "");
	}

	/** Comma-separated host allowlist for user LLM profiles (SSRF guard). */
	public static String[] getWebLlmAllowedHosts() {
		final String configured = getString("web.llm.allowedHosts",
				"openrouter.ai,api.openai.com,api.openai.com.cn,api.deepseek.com,api.moonshot.cn,generativelanguage.googleapis.com,api.anthropic.com,api.groq.com,api.together.xyz,api.perplexity.ai,api.x.ai");
		final String[] parts = configured.split(",");
		final java.util.List list = new java.util.ArrayList();
		for (int i = 0; i < parts.length; i++) {
			final String host = parts[i] == null ? "" : parts[i].trim().toLowerCase();
			if (host.length() > 0) {
				list.add(host);
			}
		}
		return (String[]) list.toArray(new String[list.size()]);
	}

	/** Public HTTPS origin for OAuth metadata (empty = infer from Forwarded headers). */
	public static String getPublicBaseUrl() {
		String url = getString("publicBaseUrl", "");
		if (url == null) {
			url = "";
		}
		url = url.trim();
		while (url.endsWith("/")) {
			url = url.substring(0, url.length() - 1);
		}
		return url;
	}

	/** Role granted to OAuth access tokens (Grok etc.). Default read. */
	public static McpRole getOauthRole() {
		return McpRole.parse(getString("oauth.role", "read"));
	}

	public static int getOauthAccessTtlSeconds() {
		final int sec = getInt("oauth.accessTtlSeconds", 86400);
		return sec < 300 ? 300 : sec;
	}

	/** Extra redirect hosts besides grok.com / x.ai / x.com. */
	public static String[] getOauthRedirectHosts() {
		final String configured = getString("oauth.redirectHosts", "");
		if (configured == null || configured.trim().length() == 0) {
			return new String[0];
		}
		final String[] parts = configured.split(",");
		final java.util.List list = new java.util.ArrayList();
		for (int i = 0; i < parts.length; i++) {
			final String host = parts[i] == null ? "" : parts[i].trim().toLowerCase();
			if (host.length() > 0) {
				list.add(host);
			}
		}
		return (String[]) list.toArray(new String[list.size()]);
	}

	public static boolean isWebLlmConfigured() {
		final String key = getWebLlmApiKey();
		return key != null && key.trim().length() > 0;
	}

	/** Same data dir as audit by default ({workingDirectory}/data). Override: {@code mcp.webchat.dataDir}. */
	public static File getWebchatDataDir() {
		final String configured = getString("webchat.dataDir", "");
		if (configured.length() > 0) {
			return new File(configured);
		}
		return getAuditDataDir();
	}

	/**
	 * Local writable webchat DB: {@code webchat-&lt;mac&gt;.db}.
	 * Peer machines sync their own files into the same folder; readers merge all.
	 */
	public static File getWebchatDbFile() {
		final String configured = getString("webchat.dbPath", "");
		if (configured.length() > 0) {
			return new File(configured);
		}
		final File dir = getWebchatDataDir();
		if (!dir.exists()) {
			dir.mkdirs();
		}
		final String macHex = org.docear.plugin.mcp.audit.McpAuditMachineId.getMacHex();
		return new File(dir, "webchat-" + macHex + ".db");
	}

	/** Local + synced peer DBs: {@code webchat-*.db}. */
	public static File[] listWebchatDbFiles() {
		final File dir = getWebchatDataDir();
		final java.util.List list = new java.util.ArrayList();
		final File local = getWebchatDbFile();
		if (local != null) {
			list.add(local);
		}
		final File[] children = dir.listFiles();
		if (children != null) {
			for (int i = 0; i < children.length; i++) {
				final File f = children[i];
				if (f == null || !f.isFile()) {
					continue;
				}
				final String name = f.getName().toLowerCase();
				if (!name.startsWith("webchat-") || !name.endsWith(".db")) {
					continue;
				}
				if (name.endsWith("-wal") || name.endsWith("-shm")) {
					continue;
				}
				if (!containsFile(list, f)) {
					list.add(f);
				}
			}
		}
		return (File[]) list.toArray(new File[list.size()]);
	}

	public static boolean isAuditEnabled() {
		return getBoolean("audit.enabled", true);
	}

	public static int getAuditMaxEntries() {
		return getInt("audit.maxEntries", 1000);
	}

	public static File getAuditDataDir() {
		if (AUDIT_DATA_DIR_FOR_TESTS != null) {
			return AUDIT_DATA_DIR_FOR_TESTS;
		}
		final String configured = getString("audit.dataDir", "");
		if (configured.length() > 0) {
			return new File(configured);
		}
		// Profile is already {workingDirectory}/data — per-machine audit-*.db lives there.
		return new File(Compat.getApplicationUserDirectory());
	}

	/**
	 * Local writable audit DB for this PC: {@code audit-&lt;mac&gt;.db}.
	 * Legacy plain {@code audit.db} is renamed once when the MAC file is missing.
	 */
	public static File getAuditDbFile() {
		if (AUDIT_DATA_DIR_FOR_TESTS == null) {
			final String configured = getString("audit.dbPath", "");
			if (configured.length() > 0) {
				return new File(configured);
			}
		}
		final File dir = getAuditDataDir();
		if (!dir.exists()) {
			dir.mkdirs();
		}
		final String macHex = org.docear.plugin.mcp.audit.McpAuditMachineId.getMacHex();
		final File local = new File(dir, "audit-" + macHex + ".db");
		migrateLegacyAuditDb(dir, local);
		// Stale synced id file is unused (MAC is the identity).
		final File staleId = new File(dir, "audit_machine.id");
		if (staleId.isFile()) {
			staleId.delete();
		}
		return local;
	}

	/**
	 * Per-PC overflow spill when the SQLite writer queue is full:
	 * {@code audit_overflow-&lt;mac&gt;.jsonl}. Legacy shared file is renamed once.
	 */
	public static File getAuditOverflowFile() {
		final File dir = getAuditDataDir();
		if (!dir.exists()) {
			dir.mkdirs();
		}
		final String macHex = org.docear.plugin.mcp.audit.McpAuditMachineId.getMacHex();
		final File local = new File(dir, "audit_overflow-" + macHex + ".jsonl");
		if (!local.exists()) {
			final File legacy = new File(dir, "audit_overflow.jsonl");
			if (legacy.isFile()) {
				legacy.renameTo(local);
			}
		}
		return local;
	}

	/**
	 * All audit SQLite files in the data dir (this PC + synced peers):
	 * {@code audit-*.db} and legacy {@code audit.db}.
	 */
	public static File[] listAuditDbFiles() {
		final File dir = getAuditDataDir();
		final java.util.List list = new java.util.ArrayList();
		final File local = getAuditDbFile();
		if (local != null) {
			list.add(local);
		}
		final File[] children = dir.listFiles();
		if (children != null) {
			for (int i = 0; i < children.length; i++) {
				final File f = children[i];
				if (f == null || !f.isFile()) {
					continue;
				}
				final String name = f.getName().toLowerCase();
				if (!name.endsWith(".db")) {
					continue;
				}
				if (!"audit.db".equals(name) && !name.startsWith("audit-")) {
					continue;
				}
				if (name.endsWith("-wal") || name.endsWith("-shm")) {
					continue;
				}
				if (!containsFile(list, f)) {
					list.add(f);
				}
			}
		}
		return (File[]) list.toArray(new File[list.size()]);
	}

	private static void migrateLegacyAuditDb(final File dir, final File local) {
		if (local.exists()) {
			return;
		}
		final File legacy = new File(dir, "audit.db");
		if (!legacy.isFile()) {
			return;
		}
		if (legacy.renameTo(local)) {
			renameSibling(legacy, local, "-wal");
			renameSibling(legacy, local, "-shm");
		}
	}

	private static void renameSibling(final File fromBase, final File toBase, final String suffix) {
		final File from = new File(fromBase.getAbsolutePath() + suffix);
		if (from.isFile()) {
			from.renameTo(new File(toBase.getAbsolutePath() + suffix));
		}
	}

	private static boolean containsFile(final java.util.List list, final File file) {
		try {
			final String path = file.getCanonicalPath();
			for (int i = 0; i < list.size(); i++) {
				final File other = (File) list.get(i);
				if (other != null && path.equals(other.getCanonicalPath())) {
					return true;
				}
			}
		}
		catch (Exception e) {
			for (int i = 0; i < list.size(); i++) {
				final File other = (File) list.get(i);
				if (other != null && file.getAbsolutePath().equals(other.getAbsolutePath())) {
					return true;
				}
			}
		}
		return false;
	}

	public static int getAuditQueueSize() {
		return getInt("audit.queueSize", 5000);
	}

	public static int getAuditBatchSize() {
		final int batch = getInt("audit.batchSize", 200);
		if (batch < 10) {
			return 10;
		}
		if (batch > 500) {
			return 500;
		}
		return batch;
	}

	public static int getAuditMaxResponseBytes() {
		return getInt("audit.maxResponseBytes", 524288);
	}

	public static boolean isCursorPluginSyncEnabled() {
		return getBoolean("cursorPlugin.sync.enabled", true);
	}

	public static int getCursorPluginSyncDelayMs() {
		return getInt("cursorPlugin.sync.delayMs", 12000);
	}

	public static void setEnabled(final boolean enabled) {
		setProperty("enabled", enabled ? "true" : "false");
	}

	public static void setHost(final String host) {
		setProperty("host", host == null || host.trim().length() == 0 ? DEFAULT_HOST : host.trim());
	}

	public static void setPort(final int port) {
		final int safe = port < 1 || port > 65535 ? DEFAULT_PORT : port;
		setProperty("port", String.valueOf(safe));
	}

	public static void setReadOnly(final boolean readOnly) {
		setProperty("readonly", readOnly ? "true" : "false");
	}

	public static void setAuthEnabled(final boolean enabled) {
		setProperty("auth.enabled", enabled ? "true" : "false");
	}

	public static void setApiKey(final String apiKey) {
		setProperty("auth.apiKey", apiKey == null ? "" : apiKey.trim());
	}

	public static void setAuthRole(final String role) {
		setProperty("auth.role", role == null || role.trim().length() == 0 ? "owner" : role.trim().toLowerCase());
	}

	public static void setWebEnabled(final boolean enabled) {
		setProperty("web.enabled", enabled ? "true" : "false");
	}

	public static void setWebLlmBaseUrl(final String baseUrl) {
		setProperty("web.llm.baseUrl",
				baseUrl == null || baseUrl.trim().length() == 0 ? "https://openrouter.ai/api/v1" : baseUrl.trim());
	}

	public static void setWebLlmApiKey(final String apiKey) {
		setProperty("web.llm.apiKey", apiKey == null ? "" : apiKey.trim());
	}

	public static void setWebLlmModel(final String model) {
		setProperty("web.llm.model",
				model == null || model.trim().length() == 0 ? "openai/gpt-4o-mini" : model.trim());
	}

	public static void setCursorPluginSyncEnabled(final boolean enabled) {
		setProperty("cursorPlugin.sync.enabled", enabled ? "true" : "false");
	}

	public static void setAuditEnabled(final boolean enabled) {
		setProperty("audit.enabled", enabled ? "true" : "false");
	}

	private static void setProperty(final String key, final String value) {
		try {
			ResourceController.getResourceController().setProperty(PREFIX + key, value == null ? "" : value);
		}
		catch (Exception e) {
			// ignore when controller unavailable
		}
	}

	private static String getString(final String key, final String defaultValue) {
		// JVM -Dmcp.* overrides (used by headless VPS start scripts).
		try {
			final String sys = System.getProperty(PREFIX + key);
			if (sys != null && sys.trim().length() > 0) {
				return sys.trim();
			}
		}
		catch (Throwable ignored) {
		}
		try {
			final String value = ResourceController.getResourceController().getProperty(PREFIX + key, null);
			if (value != null && value.trim().length() > 0) {
				return value.trim();
			}
		}
		catch (Throwable ignored) {
		}
		return loadDefaults().getProperty(key, defaultValue);
	}

	private static boolean getBoolean(final String key, final boolean defaultValue) {
		final String value = getString(key, defaultValue ? "true" : "false");
		return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
	}

	private static int getInt(final String key, final int defaultValue) {
		try {
			return Integer.parseInt(getString(key, String.valueOf(defaultValue)));
		}
		catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	private static Properties loadDefaults() {
		final Properties properties = new Properties();
		InputStream stream = DocearMcpConfig.class.getClassLoader().getResourceAsStream("mcp.properties");
		if (stream != null) {
			try {
				properties.load(stream);
			}
			catch (IOException e) {
				// ignore
			}
			finally {
				try {
					stream.close();
				}
				catch (IOException e) {
					// ignore
				}
			}
		}
		return properties;
	}
}
