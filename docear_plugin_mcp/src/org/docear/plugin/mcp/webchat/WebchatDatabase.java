package org.docear.plugin.mcp.webchat;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.docear.plugin.mcp.audit.McpAuditMachineId;
import org.freeplane.core.util.LogUtils;

/**
 * One SQLite file: {@code webchat-&lt;mac&gt;.db}. Writes go to the local file only.
 */
public final class WebchatDatabase {

	private static final String JDBC_PREFIX = "jdbc:sqlite:";
	private static final int SCHEMA_VERSION = 1;
	private static volatile WebchatDatabase LOCAL;

	private final File dbFile;
	private final boolean readOnlyOpen;

	public static synchronized WebchatDatabase local() {
		if (LOCAL == null) {
			LOCAL = new WebchatDatabase(org.docear.plugin.mcp.DocearMcpConfig.getWebchatDbFile(), false);
		}
		return LOCAL;
	}

	public static synchronized void resetLocalForTests(final File file) {
		LOCAL = file == null ? null : new WebchatDatabase(file, false);
	}

	WebchatDatabase(final File dbFile, final boolean readOnlyOpen) {
		this.dbFile = dbFile;
		this.readOnlyOpen = readOnlyOpen;
		if (!readOnlyOpen) {
			final File parent = dbFile.getParentFile();
			if (parent != null && !parent.exists()) {
				parent.mkdirs();
			}
			ensureSchema();
		}
		else if (dbFile.isFile()) {
			ensureSchema();
		}
	}

	public File getDbFile() {
		return dbFile;
	}

	public boolean isLocalMachineFile() {
		try {
			return dbFile.getCanonicalPath()
					.equals(org.docear.plugin.mcp.DocearMcpConfig.getWebchatDbFile().getCanonicalPath());
		}
		catch (Exception e) {
			return dbFile.getAbsolutePath()
					.equals(org.docear.plugin.mcp.DocearMcpConfig.getWebchatDbFile().getAbsolutePath());
		}
	}

	// ---------- users ----------

	public boolean hasAnyUser() throws SQLException {
		Connection c = null;
		Statement st = null;
		ResultSet rs = null;
		try {
			c = openConnection();
			st = c.createStatement();
			rs = st.executeQuery("SELECT 1 FROM users LIMIT 1");
			return rs.next();
		}
		finally {
			closeQuietly(rs);
			closeQuietly(st);
			closeQuietly(c);
		}
	}

	public Map<String, Object> findUser(final String username) throws SQLException {
		Connection c = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			c = openConnection();
			ps = c.prepareStatement(
					"SELECT username, password_hash, salt, created_at, machine_id FROM users WHERE username = ?");
			ps.setString(1, username);
			rs = ps.executeQuery();
			if (!rs.next()) {
				return null;
			}
			final Map<String, Object> row = new LinkedHashMap<String, Object>();
			row.put("username", rs.getString("username"));
			row.put("passwordHash", rs.getString("password_hash"));
			row.put("salt", rs.getString("salt"));
			row.put("createdAt", Long.valueOf(rs.getLong("created_at")));
			row.put("machineId", nullToEmpty(rs.getString("machine_id")));
			return row;
		}
		finally {
			closeQuietly(rs);
			closeQuietly(ps);
			closeQuietly(c);
		}
	}

	public void insertUser(final String username, final String passwordHash, final String salt) throws SQLException {
		Connection c = null;
		PreparedStatement ps = null;
		try {
			c = openConnection();
			ps = c.prepareStatement(
					"INSERT INTO users(username, password_hash, salt, created_at, machine_id) VALUES(?,?,?,?,?)");
			ps.setString(1, username);
			ps.setString(2, passwordHash);
			ps.setString(3, salt);
			ps.setLong(4, System.currentTimeMillis());
			ps.setString(5, McpAuditMachineId.getMachineId());
			ps.executeUpdate();
		}
		finally {
			closeQuietly(ps);
			closeQuietly(c);
		}
	}

	// ---------- sessions (local only) ----------

	public void insertSession(final String token, final String username, final long expiresAt) throws SQLException {
		Connection c = null;
		PreparedStatement ps = null;
		try {
			c = openConnection();
			ps = c.prepareStatement(
					"INSERT INTO sessions(token, username, created_at, expires_at, machine_id) VALUES(?,?,?,?,?)");
			ps.setString(1, token);
			ps.setString(2, username);
			ps.setLong(3, System.currentTimeMillis());
			ps.setLong(4, expiresAt);
			ps.setString(5, McpAuditMachineId.getMachineId());
			ps.executeUpdate();
		}
		finally {
			closeQuietly(ps);
			closeQuietly(c);
		}
	}

	public String findUsernameByToken(final String token) throws SQLException {
		Connection c = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			c = openConnection();
			ps = c.prepareStatement("SELECT username, expires_at FROM sessions WHERE token = ?");
			ps.setString(1, token);
			rs = ps.executeQuery();
			if (!rs.next()) {
				return null;
			}
			final long expires = rs.getLong("expires_at");
			if (expires > 0 && expires < System.currentTimeMillis()) {
				return null;
			}
			return rs.getString("username");
		}
		finally {
			closeQuietly(rs);
			closeQuietly(ps);
			closeQuietly(c);
		}
	}

	public void deleteSession(final String token) throws SQLException {
		Connection c = null;
		PreparedStatement ps = null;
		try {
			c = openConnection();
			ps = c.prepareStatement("DELETE FROM sessions WHERE token = ?");
			ps.setString(1, token);
			ps.executeUpdate();
		}
		finally {
			closeQuietly(ps);
			closeQuietly(c);
		}
	}

	// ---------- llm profiles ----------

	public void upsertProfile(final Map<String, Object> profile) throws SQLException {
		Connection c = null;
		PreparedStatement ps = null;
		try {
			c = openConnection();
			if (((Boolean) profile.get("isDefault")).booleanValue()) {
				final PreparedStatement clear = c.prepareStatement(
						"UPDATE llm_profiles SET is_default = 0 WHERE username = ?");
				try {
					clear.setString(1, (String) profile.get("username"));
					clear.executeUpdate();
				}
				finally {
					closeQuietly(clear);
				}
			}
			ps = c.prepareStatement("INSERT OR REPLACE INTO llm_profiles"
					+ "(id, username, name, base_url, api_key, model, is_default, updated_at, machine_id)"
					+ " VALUES(?,?,?,?,?,?,?,?,?)");
			ps.setString(1, (String) profile.get("id"));
			ps.setString(2, (String) profile.get("username"));
			ps.setString(3, (String) profile.get("name"));
			ps.setString(4, (String) profile.get("baseUrl"));
			ps.setString(5, (String) profile.get("apiKey"));
			ps.setString(6, (String) profile.get("model"));
			ps.setInt(7, ((Boolean) profile.get("isDefault")).booleanValue() ? 1 : 0);
			ps.setLong(8, System.currentTimeMillis());
			ps.setString(9, McpAuditMachineId.getMachineId());
			ps.executeUpdate();
		}
		finally {
			closeQuietly(ps);
			closeQuietly(c);
		}
	}

	public void deleteProfile(final String username, final String profileId) throws SQLException {
		Connection c = null;
		PreparedStatement ps = null;
		try {
			c = openConnection();
			ps = c.prepareStatement("DELETE FROM llm_profiles WHERE id = ? AND username = ?");
			ps.setString(1, profileId);
			ps.setString(2, username);
			ps.executeUpdate();
		}
		finally {
			closeQuietly(ps);
			closeQuietly(c);
		}
	}

	public List<Map<String, Object>> listProfiles(final String username, final boolean includeSecrets)
			throws SQLException {
		Connection c = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			c = openConnection();
			ps = c.prepareStatement(
					"SELECT id, username, name, base_url, api_key, model, is_default, updated_at, machine_id"
							+ " FROM llm_profiles WHERE username = ? ORDER BY is_default DESC, updated_at DESC");
			ps.setString(1, username);
			rs = ps.executeQuery();
			final List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
			while (rs.next()) {
				items.add(readProfile(rs, includeSecrets));
			}
			return items;
		}
		finally {
			closeQuietly(rs);
			closeQuietly(ps);
			closeQuietly(c);
		}
	}

	public Map<String, Object> getProfile(final String username, final String profileId, final boolean includeSecrets)
			throws SQLException {
		Connection c = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			c = openConnection();
			ps = c.prepareStatement(
					"SELECT id, username, name, base_url, api_key, model, is_default, updated_at, machine_id"
							+ " FROM llm_profiles WHERE username = ? AND id = ?");
			ps.setString(1, username);
			ps.setString(2, profileId);
			rs = ps.executeQuery();
			if (!rs.next()) {
				return null;
			}
			return readProfile(rs, includeSecrets);
		}
		finally {
			closeQuietly(rs);
			closeQuietly(ps);
			closeQuietly(c);
		}
	}

	private Map<String, Object> readProfile(final ResultSet rs, final boolean includeSecrets) throws SQLException {
		final Map<String, Object> row = new LinkedHashMap<String, Object>();
		row.put("id", rs.getString("id"));
		row.put("username", rs.getString("username"));
		row.put("name", rs.getString("name"));
		row.put("baseUrl", rs.getString("base_url"));
		final String key = nullToEmpty(rs.getString("api_key"));
		if (includeSecrets) {
			row.put("apiKey", key);
		}
		else {
			row.put("apiKeyConfigured", Boolean.valueOf(key.length() > 0));
			row.put("apiKeyPreview", key.length() > 4 ? ("****" + key.substring(key.length() - 4)) : "");
		}
		row.put("model", rs.getString("model"));
		row.put("isDefault", Boolean.valueOf(rs.getInt("is_default") == 1));
		row.put("updatedAt", Long.valueOf(rs.getLong("updated_at")));
		row.put("machineId", nullToEmpty(rs.getString("machine_id")));
		return row;
	}

	// ---------- conversations / messages ----------

	public void upsertConversation(final String id, final String username, final String title, final long createdAt,
			final long updatedAt) throws SQLException {
		Connection c = null;
		PreparedStatement ps = null;
		try {
			c = openConnection();
			ps = c.prepareStatement("INSERT OR REPLACE INTO conversations"
					+ "(id, username, title, created_at, updated_at, machine_id, machine_name) VALUES(?,?,?,?,?,?,?)");
			ps.setString(1, id);
			ps.setString(2, username);
			ps.setString(3, title == null ? "" : title);
			ps.setLong(4, createdAt);
			ps.setLong(5, updatedAt);
			ps.setString(6, McpAuditMachineId.getMachineId());
			ps.setString(7, McpAuditMachineId.getMachineName());
			ps.executeUpdate();
		}
		finally {
			closeQuietly(ps);
			closeQuietly(c);
		}
	}

	public void touchConversation(final String id, final String title, final long updatedAt) throws SQLException {
		Connection c = null;
		PreparedStatement ps = null;
		try {
			c = openConnection();
			ps = c.prepareStatement("UPDATE conversations SET title = ?, updated_at = ? WHERE id = ?");
			ps.setString(1, title == null ? "" : title);
			ps.setLong(2, updatedAt);
			ps.setString(3, id);
			ps.executeUpdate();
		}
		finally {
			closeQuietly(ps);
			closeQuietly(c);
		}
	}

	public Map<String, Object> getConversation(final String id) throws SQLException {
		Connection c = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			c = openConnection();
			ps = c.prepareStatement(
					"SELECT id, username, title, created_at, updated_at, machine_id, machine_name FROM conversations WHERE id = ?");
			ps.setString(1, id);
			rs = ps.executeQuery();
			if (!rs.next()) {
				return null;
			}
			return readConversation(rs);
		}
		finally {
			closeQuietly(rs);
			closeQuietly(ps);
			closeQuietly(c);
		}
	}

	public List<Map<String, Object>> listConversations(final String username, final int limit) throws SQLException {
		Connection c = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			c = openConnection();
			ps = c.prepareStatement(
					"SELECT id, username, title, created_at, updated_at, machine_id, machine_name FROM conversations"
							+ " WHERE username = ? ORDER BY updated_at DESC LIMIT ?");
			ps.setString(1, username);
			ps.setInt(2, limit < 1 ? 100 : limit);
			rs = ps.executeQuery();
			final List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
			while (rs.next()) {
				items.add(readConversation(rs));
			}
			return items;
		}
		finally {
			closeQuietly(rs);
			closeQuietly(ps);
			closeQuietly(c);
		}
	}

	public void insertMessage(final String id, final String conversationId, final String role, final String content,
			final String toolTraceJson, final String model) throws SQLException {
		Connection c = null;
		PreparedStatement ps = null;
		try {
			c = openConnection();
			ps = c.prepareStatement("INSERT OR REPLACE INTO messages"
					+ "(id, conversation_id, role, content, tool_trace_json, model, created_at, machine_id)"
					+ " VALUES(?,?,?,?,?,?,?,?)");
			ps.setString(1, id);
			ps.setString(2, conversationId);
			ps.setString(3, role);
			ps.setString(4, content == null ? "" : content);
			ps.setString(5, toolTraceJson == null ? "" : toolTraceJson);
			ps.setString(6, model == null ? "" : model);
			ps.setLong(7, System.currentTimeMillis());
			ps.setString(8, McpAuditMachineId.getMachineId());
			ps.executeUpdate();
		}
		finally {
			closeQuietly(ps);
			closeQuietly(c);
		}
	}

	public List<Map<String, Object>> listMessages(final String conversationId, final int limit) throws SQLException {
		Connection c = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			c = openConnection();
			ps = c.prepareStatement(
					"SELECT id, conversation_id, role, content, tool_trace_json, model, created_at, machine_id"
							+ " FROM messages WHERE conversation_id = ? ORDER BY created_at ASC LIMIT ?");
			ps.setString(1, conversationId);
			ps.setInt(2, limit < 1 ? 500 : limit);
			rs = ps.executeQuery();
			final List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
			while (rs.next()) {
				final Map<String, Object> row = new LinkedHashMap<String, Object>();
				row.put("id", rs.getString("id"));
				row.put("conversationId", rs.getString("conversation_id"));
				row.put("role", rs.getString("role"));
				row.put("content", nullToEmpty(rs.getString("content")));
				row.put("toolTraceJson", nullToEmpty(rs.getString("tool_trace_json")));
				row.put("model", nullToEmpty(rs.getString("model")));
				row.put("createdAt", Long.valueOf(rs.getLong("created_at")));
				row.put("machineId", nullToEmpty(rs.getString("machine_id")));
				items.add(row);
			}
			return items;
		}
		finally {
			closeQuietly(rs);
			closeQuietly(ps);
			closeQuietly(c);
		}
	}

	private Map<String, Object> readConversation(final ResultSet rs) throws SQLException {
		final Map<String, Object> row = new LinkedHashMap<String, Object>();
		row.put("id", rs.getString("id"));
		row.put("username", rs.getString("username"));
		row.put("title", nullToEmpty(rs.getString("title")));
		row.put("createdAt", Long.valueOf(rs.getLong("created_at")));
		row.put("updatedAt", Long.valueOf(rs.getLong("updated_at")));
		row.put("machineId", nullToEmpty(rs.getString("machine_id")));
		row.put("machineName", nullToEmpty(rs.getString("machine_name")));
		return row;
	}

	private void ensureSchema() {
		Connection c = null;
		Statement st = null;
		try {
			c = openConnection();
			st = c.createStatement();
			st.execute("PRAGMA journal_mode=WAL");
			st.execute("PRAGMA synchronous=NORMAL");
			st.execute("CREATE TABLE IF NOT EXISTS webchat_meta (key TEXT PRIMARY KEY, value TEXT)");
			st.execute("CREATE TABLE IF NOT EXISTS users ("
					+ "username TEXT PRIMARY KEY,"
					+ "password_hash TEXT NOT NULL,"
					+ "salt TEXT NOT NULL,"
					+ "created_at INTEGER NOT NULL,"
					+ "machine_id TEXT NOT NULL DEFAULT ''"
					+ ")");
			st.execute("CREATE TABLE IF NOT EXISTS sessions ("
					+ "token TEXT PRIMARY KEY,"
					+ "username TEXT NOT NULL,"
					+ "created_at INTEGER NOT NULL,"
					+ "expires_at INTEGER NOT NULL,"
					+ "machine_id TEXT NOT NULL DEFAULT ''"
					+ ")");
			st.execute("CREATE TABLE IF NOT EXISTS llm_profiles ("
					+ "id TEXT PRIMARY KEY,"
					+ "username TEXT NOT NULL,"
					+ "name TEXT NOT NULL,"
					+ "base_url TEXT NOT NULL,"
					+ "api_key TEXT NOT NULL DEFAULT '',"
					+ "model TEXT NOT NULL,"
					+ "is_default INTEGER NOT NULL DEFAULT 0,"
					+ "updated_at INTEGER NOT NULL,"
					+ "machine_id TEXT NOT NULL DEFAULT ''"
					+ ")");
			st.execute("CREATE TABLE IF NOT EXISTS conversations ("
					+ "id TEXT PRIMARY KEY,"
					+ "username TEXT NOT NULL,"
					+ "title TEXT NOT NULL DEFAULT '',"
					+ "created_at INTEGER NOT NULL,"
					+ "updated_at INTEGER NOT NULL,"
					+ "machine_id TEXT NOT NULL DEFAULT '',"
					+ "machine_name TEXT NOT NULL DEFAULT ''"
					+ ")");
			st.execute("CREATE TABLE IF NOT EXISTS messages ("
					+ "id TEXT PRIMARY KEY,"
					+ "conversation_id TEXT NOT NULL,"
					+ "role TEXT NOT NULL,"
					+ "content TEXT NOT NULL DEFAULT '',"
					+ "tool_trace_json TEXT NOT NULL DEFAULT '',"
					+ "model TEXT NOT NULL DEFAULT '',"
					+ "created_at INTEGER NOT NULL,"
					+ "machine_id TEXT NOT NULL DEFAULT ''"
					+ ")");
			st.execute("CREATE INDEX IF NOT EXISTS idx_webchat_conv_user ON conversations(username, updated_at)");
			st.execute("CREATE INDEX IF NOT EXISTS idx_webchat_msg_conv ON messages(conversation_id, created_at)");
			st.execute("CREATE INDEX IF NOT EXISTS idx_webchat_profile_user ON llm_profiles(username, is_default)");
			st.execute("INSERT OR REPLACE INTO webchat_meta(key, value) VALUES('schema_version', '"
					+ SCHEMA_VERSION + "')");
		}
		catch (Exception e) {
			LogUtils.warn("Webchat DB schema failed for " + dbFile + ": " + e.getMessage(), e);
		}
		finally {
			closeQuietly(st);
			closeQuietly(c);
		}
	}

	private Connection openConnection() throws SQLException {
		final ClassLoader loader = WebchatDatabase.class.getClassLoader();
		try {
			Class.forName("org.sqlite.JDBC", true, loader);
		}
		catch (ClassNotFoundException e) {
			throw new SQLException("SQLite JDBC driver not found", e);
		}
		return DriverManager.getConnection(JDBC_PREFIX + dbFile.getAbsolutePath());
	}

	private static String nullToEmpty(final String value) {
		return value == null ? "" : value;
	}

	private static void closeQuietly(final ResultSet rs) {
		if (rs != null) {
			try {
				rs.close();
			}
			catch (Exception ignored) {
			}
		}
	}

	private static void closeQuietly(final Statement st) {
		if (st != null) {
			try {
				st.close();
			}
			catch (Exception ignored) {
			}
		}
	}

	private static void closeQuietly(final Connection c) {
		if (c != null) {
			try {
				c.close();
			}
			catch (Exception ignored) {
			}
		}
	}
}
