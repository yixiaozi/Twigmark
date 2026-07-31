package org.freeplane.view.swing.features.clipboardhistory;

import java.io.File;
import java.security.MessageDigest;
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

import org.freeplane.core.util.LogUtils;

/**
 * SQLite store for clipboard texts. Same content hashes to one row; re-copy only
 * bumps {@code last_ts} / {@code hit_count}.
 */
final class ClipboardHistoryDatabase {
	private static final String JDBC_PREFIX = "jdbc:sqlite:";
	private static volatile ClipboardHistoryDatabase INSTANCE;

	private final File dbFile;

	static synchronized ClipboardHistoryDatabase getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new ClipboardHistoryDatabase(ClipboardHistoryConfig.getDbFile());
		}
		return INSTANCE;
	}

	ClipboardHistoryDatabase(final File dbFile) {
		this.dbFile = dbFile;
		ensureParent();
		ensureSchema();
	}

	File getDbFile() {
		return dbFile;
	}

	long getDbFileBytes() {
		return dbFile.exists() ? dbFile.length() : 0L;
	}

	/**
	 * Insert or bump hit for truncated text. Returns true when a new row was created.
	 */
	boolean recordText(final String rawText) throws SQLException {
		final String content = normalizeAndTruncate(rawText);
		if (content.length() == 0) {
			return false;
		}
		final String hash = sha1Hex(content);
		final long now = System.currentTimeMillis();
		Connection connection = null;
		PreparedStatement select = null;
		PreparedStatement update = null;
		PreparedStatement insert = null;
		ResultSet rs = null;
		try {
			connection = openConnection();
			connection.setAutoCommit(false);
			select = connection.prepareStatement("SELECT id FROM clipboard_entry WHERE content_hash = ?");
			select.setString(1, hash);
			rs = select.executeQuery();
			boolean created;
			if (rs.next()) {
				update = connection.prepareStatement(
						"UPDATE clipboard_entry SET last_ts = ?, hit_count = hit_count + 1 WHERE content_hash = ?");
				update.setLong(1, now);
				update.setString(2, hash);
				update.executeUpdate();
				created = false;
			}
			else {
				insert = connection.prepareStatement(
						"INSERT INTO clipboard_entry (content_hash, content, char_len, first_ts, last_ts, hit_count)"
								+ " VALUES (?,?,?,?,?,1)");
				insert.setString(1, hash);
				insert.setString(2, content);
				insert.setInt(3, content.length());
				insert.setLong(4, now);
				insert.setLong(5, now);
				insert.executeUpdate();
				created = true;
			}
			pruneIfNeeded(connection);
			connection.commit();
			return created;
		}
		catch (SQLException e) {
			if (connection != null) {
				try {
					connection.rollback();
				}
				catch (SQLException ignore) {
				}
			}
			throw e;
		}
		finally {
			closeQuietly(rs);
			closeQuietly(select);
			closeQuietly(update);
			closeQuietly(insert);
			closeQuietly(connection);
		}
	}

	List listRecent(final String query, final int limit) throws SQLException {
		final StringBuilder sql = new StringBuilder(
				"SELECT id, content_hash, content, char_len, first_ts, last_ts, hit_count FROM clipboard_entry");
		final boolean hasQuery = query != null && query.trim().length() > 0;
		if (hasQuery) {
			sql.append(" WHERE content LIKE ?");
		}
		sql.append(" ORDER BY last_ts DESC LIMIT ?");
		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		try {
			connection = openConnection();
			statement = connection.prepareStatement(sql.toString());
			int index = 1;
			if (hasQuery) {
				statement.setString(index++, "%" + query.trim() + "%");
			}
			statement.setInt(index, limit > 0 ? limit : 200);
			rs = statement.executeQuery();
			final List rows = new ArrayList();
			while (rs.next()) {
				rows.add(readRow(rs));
			}
			return rows;
		}
		finally {
			closeQuietly(rs);
			closeQuietly(statement);
			closeQuietly(connection);
		}
	}

	ClipboardHistoryEntry getById(final long id) throws SQLException {
		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		try {
			connection = openConnection();
			statement = connection.prepareStatement(
					"SELECT id, content_hash, content, char_len, first_ts, last_ts, hit_count FROM clipboard_entry WHERE id = ?");
			statement.setLong(1, id);
			rs = statement.executeQuery();
			return rs.next() ? readRow(rs) : null;
		}
		finally {
			closeQuietly(rs);
			closeQuietly(statement);
			closeQuietly(connection);
		}
	}

	boolean deleteById(final long id) throws SQLException {
		Connection connection = null;
		PreparedStatement statement = null;
		try {
			connection = openConnection();
			statement = connection.prepareStatement("DELETE FROM clipboard_entry WHERE id = ?");
			statement.setLong(1, id);
			return statement.executeUpdate() > 0;
		}
		finally {
			closeQuietly(statement);
			closeQuietly(connection);
		}
	}

	void clearAll() throws SQLException {
		Connection connection = null;
		Statement statement = null;
		try {
			connection = openConnection();
			statement = connection.createStatement();
			statement.executeUpdate("DELETE FROM clipboard_entry");
			statement.executeUpdate("VACUUM");
		}
		finally {
			closeQuietly(statement);
			closeQuietly(connection);
		}
	}

	int countEntries() throws SQLException {
		return queryInt("SELECT COUNT(*) FROM clipboard_entry");
	}

	long sumHits() throws SQLException {
		return queryLong("SELECT COALESCE(SUM(hit_count), 0) FROM clipboard_entry");
	}

	/** dayStartMs -> hit sum for last N days (by last_ts). */
	Map statsHitsByDay(final int days) throws SQLException {
		final long since = System.currentTimeMillis() - days * 86400000L;
		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		try {
			connection = openConnection();
			statement = connection.prepareStatement(
					"SELECT (last_ts / 86400000) * 86400000 AS day_ts, SUM(hit_count) AS hits"
							+ " FROM clipboard_entry WHERE last_ts >= ? GROUP BY day_ts ORDER BY day_ts DESC");
			statement.setLong(1, since);
			rs = statement.executeQuery();
			final Map map = new LinkedHashMap();
			while (rs.next()) {
				map.put(Long.valueOf(rs.getLong("day_ts")), Long.valueOf(rs.getLong("hits")));
			}
			return map;
		}
		finally {
			closeQuietly(rs);
			closeQuietly(statement);
			closeQuietly(connection);
		}
	}

	List topByHits(final int limit) throws SQLException {
		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		try {
			connection = openConnection();
			statement = connection.prepareStatement(
					"SELECT id, content_hash, content, char_len, first_ts, last_ts, hit_count FROM clipboard_entry"
							+ " ORDER BY hit_count DESC, last_ts DESC LIMIT ?");
			statement.setInt(1, limit > 0 ? limit : 20);
			rs = statement.executeQuery();
			final List rows = new ArrayList();
			while (rs.next()) {
				rows.add(readRow(rs));
			}
			return rows;
		}
		finally {
			closeQuietly(rs);
			closeQuietly(statement);
			closeQuietly(connection);
		}
	}

	private void pruneIfNeeded(final Connection connection) throws SQLException {
		final int max = ClipboardHistoryConfig.getMaxRows();
		if (max <= 0) {
			return;
		}
		Statement countStatement = null;
		PreparedStatement delete = null;
		ResultSet rs = null;
		try {
			countStatement = connection.createStatement();
			rs = countStatement.executeQuery("SELECT COUNT(*) FROM clipboard_entry");
			final int count = rs.next() ? rs.getInt(1) : 0;
			if (count <= max) {
				return;
			}
			final int remove = count - max;
			delete = connection.prepareStatement(
					"DELETE FROM clipboard_entry WHERE id IN (SELECT id FROM clipboard_entry ORDER BY last_ts ASC LIMIT ?)");
			delete.setInt(1, remove);
			delete.executeUpdate();
		}
		finally {
			closeQuietly(rs);
			closeQuietly(countStatement);
			closeQuietly(delete);
		}
	}

	private void ensureParent() {
		final File parent = dbFile.getParentFile();
		if (parent != null && !parent.exists()) {
			parent.mkdirs();
		}
	}

	private void ensureSchema() {
		Connection connection = null;
		Statement statement = null;
		try {
			connection = openConnection();
			statement = connection.createStatement();
			statement.execute("PRAGMA journal_mode=WAL");
			statement.execute("PRAGMA synchronous=NORMAL");
			statement.execute("CREATE TABLE IF NOT EXISTS clipboard_entry ("
					+ "id INTEGER PRIMARY KEY AUTOINCREMENT,"
					+ "content_hash TEXT NOT NULL UNIQUE,"
					+ "content TEXT NOT NULL,"
					+ "char_len INTEGER NOT NULL DEFAULT 0,"
					+ "first_ts INTEGER NOT NULL,"
					+ "last_ts INTEGER NOT NULL,"
					+ "hit_count INTEGER NOT NULL DEFAULT 1)");
			statement.execute("CREATE INDEX IF NOT EXISTS idx_clip_last_ts ON clipboard_entry(last_ts DESC)");
			statement.execute("CREATE INDEX IF NOT EXISTS idx_clip_hit ON clipboard_entry(hit_count DESC)");
			LogUtils.info("Clipboard history database ready: " + dbFile.getAbsolutePath());
		}
		catch (SQLException e) {
			LogUtils.warn("Clipboard history database init failed: " + e.getMessage(), e);
		}
		finally {
			closeQuietly(statement);
			closeQuietly(connection);
		}
	}

	private Connection openConnection() throws SQLException {
		final ClassLoader loader = ClipboardHistoryDatabase.class.getClassLoader();
		try {
			Class.forName("org.sqlite.JDBC", true, loader);
		}
		catch (ClassNotFoundException e) {
			throw new SQLException("SQLite JDBC driver not found", e);
		}
		return DriverManager.getConnection(JDBC_PREFIX + dbFile.getAbsolutePath());
	}

	private int queryInt(final String sql) throws SQLException {
		Connection connection = null;
		Statement statement = null;
		ResultSet rs = null;
		try {
			connection = openConnection();
			statement = connection.createStatement();
			rs = statement.executeQuery(sql);
			return rs.next() ? rs.getInt(1) : 0;
		}
		finally {
			closeQuietly(rs);
			closeQuietly(statement);
			closeQuietly(connection);
		}
	}

	private long queryLong(final String sql) throws SQLException {
		Connection connection = null;
		Statement statement = null;
		ResultSet rs = null;
		try {
			connection = openConnection();
			statement = connection.createStatement();
			rs = statement.executeQuery(sql);
			return rs.next() ? rs.getLong(1) : 0L;
		}
		finally {
			closeQuietly(rs);
			closeQuietly(statement);
			closeQuietly(connection);
		}
	}

	private static ClipboardHistoryEntry readRow(final ResultSet rs) throws SQLException {
		final ClipboardHistoryEntry entry = new ClipboardHistoryEntry();
		entry.id = rs.getLong("id");
		entry.contentHash = nullToEmpty(rs.getString("content_hash"));
		entry.content = nullToEmpty(rs.getString("content"));
		entry.charLen = rs.getInt("char_len");
		entry.firstTs = rs.getLong("first_ts");
		entry.lastTs = rs.getLong("last_ts");
		entry.hitCount = rs.getInt("hit_count");
		return entry;
	}

	static String normalizeAndTruncate(final String rawText) {
		if (rawText == null) {
			return "";
		}
		String text = rawText.replace("\r\n", "\n").replace('\r', '\n');
		// Drop surrounding whitespace-only noise but keep inner formatting.
		text = trimEdges(text);
		final int max = ClipboardHistoryConfig.getMaxTextLength();
		if (text.length() > max) {
			text = text.substring(0, max);
		}
		return text;
	}

	private static String trimEdges(final String text) {
		int start = 0;
		int end = text.length();
		while (start < end && Character.isWhitespace(text.charAt(start))) {
			start++;
		}
		while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
			end--;
		}
		return start == 0 && end == text.length() ? text : text.substring(start, end);
	}

	static String sha1Hex(final String text) {
		try {
			final MessageDigest digest = MessageDigest.getInstance("SHA-1");
			final byte[] bytes = digest.digest(text.getBytes("UTF-8"));
			final StringBuffer sb = new StringBuffer(bytes.length * 2);
			for (int i = 0; i < bytes.length; i++) {
				final String hex = Integer.toHexString(bytes[i] & 0xff);
				if (hex.length() == 1) {
					sb.append('0');
				}
				sb.append(hex);
			}
			return sb.toString();
		}
		catch (Exception e) {
			return Integer.toHexString(text.hashCode());
		}
	}

	private static String nullToEmpty(final String value) {
		return value == null ? "" : value;
	}

	private static void closeQuietly(final ResultSet rs) {
		if (rs != null) {
			try {
				rs.close();
			}
			catch (Exception e) {
			}
		}
	}

	private static void closeQuietly(final Statement statement) {
		if (statement != null) {
			try {
				statement.close();
			}
			catch (Exception e) {
			}
		}
	}

	private static void closeQuietly(final Connection connection) {
		if (connection != null) {
			try {
				connection.close();
			}
			catch (Exception e) {
			}
		}
	}
}
