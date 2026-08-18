package org.docear.plugin.mcp.server;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.docear.plugin.mcp.DocearMcpConfig;
import org.docear.plugin.mcp.json.JsonParser;
import org.docear.plugin.mcp.json.JsonValue;
import org.freeplane.core.util.LogUtils;

/**
 * Resolves API keys to principals. Sources, in order:
 * <ol>
 * <li>{@code mcp-access.json} in the config/data directory (multiple named keys)</li>
 * <li>legacy {@code mcp.auth.apiKey} + {@code mcp.auth.role} (default owner)</li>
 * </ol>
 */
public final class McpAccessStore {
	private static final Charset UTF8 = Charset.forName("UTF-8");
	private static final McpAccessStore INSTANCE = new McpAccessStore();

	private final Object lock = new Object();
	private File file;
	private long loadedMtime = -1L;
	private List entries = new ArrayList();
	private byte[] legacyHash;
	private McpRole legacyRole = McpRole.OWNER;
	private boolean testMode;

	public static McpAccessStore get() {
		return INSTANCE;
	}

	/** Test helper: empty store with one legacy key. */
	public static McpAccessStore forTests(final File keysFile, final String legacyKey, final McpRole legacyRole) {
		final McpAccessStore store = new McpAccessStore();
		store.file = keysFile;
		store.legacyHash = sha256(legacyKey);
		store.legacyRole = legacyRole == null ? McpRole.OWNER : legacyRole;
		store.testMode = true;
		store.reloadLocked();
		return store;
	}

	public McpPrincipal resolve(final String apiKey) {
		if (apiKey == null) {
			return null;
		}
		final String key = apiKey.trim();
		if (key.length() == 0) {
			return null;
		}
		final byte[] provided = sha256(key);
		synchronized (lock) {
			maybeReloadLocked();
			for (int i = 0; i < entries.size(); i++) {
				final KeyEntry entry = (KeyEntry) entries.get(i);
				if (!entry.enabled) {
					continue;
				}
				if (constantEquals(provided, entry.keyHash)) {
					return new McpPrincipal(entry.id, entry.name, entry.role, McpPrincipal.SOURCE_KEY);
				}
			}
			if (legacyHash != null && constantEquals(provided, legacyHash)) {
				return new McpPrincipal("legacy", "mcp.auth.apiKey", legacyRole, McpPrincipal.SOURCE_KEY);
			}
		}
		return null;
	}

	private void maybeReloadLocked() {
		if (testMode) {
			return;
		}
		final File keysFile = file != null ? file : DocearMcpConfig.getMcpAccessFile();
		final String legacyKey = DocearMcpConfig.getApiKey();
		legacyHash = sha256(legacyKey);
		legacyRole = DocearMcpConfig.getAuthRole();
		if (keysFile == null) {
			return;
		}
		final long mtime = keysFile.isFile() ? keysFile.lastModified() : 0L;
		if (file == keysFile && mtime == loadedMtime) {
			return;
		}
		file = keysFile;
		loadedMtime = mtime;
		reloadLocked();
	}

	private void reloadLocked() {
		entries = new ArrayList();
		if (file == null || !file.isFile()) {
			return;
		}
		try {
			final String json = readFile(file);
			if (json.trim().length() == 0) {
				return;
			}
			final JsonValue root = JsonParser.parse(json);
			final Map map = root.asMap();
			if (!map.containsKey("keys")) {
				return;
			}
			final List keys = ((JsonValue) map.get("keys")).asList();
			for (int i = 0; i < keys.size(); i++) {
				final KeyEntry entry = parseEntry((JsonValue) keys.get(i), i);
				if (entry != null) {
					entries.add(entry);
				}
			}
			LogUtils.info("MCP access: loaded " + entries.size() + " key(s) from " + file.getAbsolutePath());
		}
		catch (Exception e) {
			LogUtils.warn("MCP access: failed to read " + file.getAbsolutePath() + ": " + e.getMessage());
		}
	}

	public boolean hasAnyEnabledKey() {
		synchronized (lock) {
			maybeReloadLocked();
			for (int i = 0; i < entries.size(); i++) {
				if (((KeyEntry) entries.get(i)).enabled) {
					return true;
				}
			}
			return legacyHash != null;
		}
	}

	private static KeyEntry parseEntry(final JsonValue value, final int index) {
		if (value == null || value.isNull()) {
			return null;
		}
		final Map map = value.asMap();
		final String secret = firstString(map, new String[] { "secret", "key", "apiKey" });
		final String hashHex = firstString(map, new String[] { "keyHash", "hash" });
		byte[] hash = null;
		if (hashHex.length() > 0) {
			hash = fromHex(stripShaPrefix(hashHex));
		}
		else if (secret.length() > 0) {
			hash = sha256(secret);
		}
		if (hash == null) {
			return null;
		}
		String id = firstString(map, new String[] { "id" });
		if (id.length() == 0) {
			id = "key-" + (index + 1);
		}
		String name = firstString(map, new String[] { "name", "label" });
		if (name.length() == 0) {
			name = id;
		}
		final McpRole role = McpRole.parse(firstString(map, new String[] { "role" }));
		boolean enabled = true;
		if (map.containsKey("enabled") && map.get("enabled") != null) {
			enabled = ((JsonValue) map.get("enabled")).asBoolean();
		}
		return new KeyEntry(id, name, role, hash, enabled);
	}

	private static String firstString(final Map map, final String[] keys) {
		for (int i = 0; i < keys.length; i++) {
			if (!map.containsKey(keys[i]) || map.get(keys[i]) == null) {
				continue;
			}
			final String value = ((JsonValue) map.get(keys[i])).asString();
			if (value != null && value.trim().length() > 0) {
				return value.trim();
			}
		}
		return "";
	}

	private static String readFile(final File file) throws Exception {
		final FileInputStream in = new FileInputStream(file);
		try {
			final byte[] buf = new byte[(int) Math.min(file.length(), 1024 * 1024)];
			int n = 0;
			int r;
			while (n < buf.length && (r = in.read(buf, n, buf.length - n)) >= 0) {
				n += r;
			}
			return new String(buf, 0, n, UTF8.name());
		}
		finally {
			in.close();
		}
	}

	static byte[] sha256(final String value) {
		if (value == null || value.length() == 0) {
			return null;
		}
		try {
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return digest.digest(value.getBytes(UTF8.name()));
		}
		catch (Exception e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}

	static boolean constantEquals(final byte[] a, final byte[] b) {
		if (a == null || b == null) {
			return false;
		}
		return MessageDigest.isEqual(a, b);
	}

	private static String stripShaPrefix(final String hex) {
		final String value = hex.trim();
		if (value.length() > 7 && value.toLowerCase().startsWith("sha256:")) {
			return value.substring(7).trim();
		}
		return value;
	}

	private static byte[] fromHex(final String hex) {
		final String clean = hex.replace(" ", "");
		if (clean.length() == 0 || (clean.length() % 2) != 0) {
			return null;
		}
		final byte[] out = new byte[clean.length() / 2];
		for (int i = 0; i < out.length; i++) {
			final int hi = Character.digit(clean.charAt(i * 2), 16);
			final int lo = Character.digit(clean.charAt(i * 2 + 1), 16);
			if (hi < 0 || lo < 0) {
				return null;
			}
			out[i] = (byte) ((hi << 4) + lo);
		}
		return out;
	}

	private static final class KeyEntry {
		final String id;
		final String name;
		final McpRole role;
		final byte[] keyHash;
		final boolean enabled;

		KeyEntry(final String id, final String name, final McpRole role, final byte[] keyHash, final boolean enabled) {
			this.id = id;
			this.name = name;
			this.role = role;
			this.keyHash = keyHash;
			this.enabled = enabled;
		}
	}
}
