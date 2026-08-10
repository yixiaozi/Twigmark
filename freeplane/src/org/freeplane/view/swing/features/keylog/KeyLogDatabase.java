package org.freeplane.view.swing.features.keylog;

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

import org.freeplane.core.util.LogUtils;

/**
 * SQLite store for keystroke sessions. Connections are short-lived (open → write → close)
 * so Dropbox / other tools are not blocked by a long-held lock.
 */
final class KeyLogDatabase {
	private static final String JDBC_PREFIX = "jdbc:sqlite:";
	private static volatile KeyLogDatabase INSTANCE;

	private final File dbFile;
	private final boolean localMachine;

	static synchronized KeyLogDatabase getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new KeyLogDatabase(KeyLogConfig.getDbFile(), true);
		}
		return INSTANCE;
	}

	static synchronized void resetForTests(final File dbFile) {
		INSTANCE = new KeyLogDatabase(dbFile, true);
	}

	static KeyLogDatabase open(final File dbFile, final boolean localMachine) {
		if (dbFile == null) {
			return null;
		}
		if (localMachine) {
			return getInstance();
		}
		if (!dbFile.isFile()) {
			return null;
		}
		return new KeyLogDatabase(dbFile, false);
	}

	KeyLogDatabase(final File dbFile, final boolean localMachine) {
		this.dbFile = dbFile;
		this.localMachine = localMachine;
		if (localMachine) {
			ensureParent();
			ensureSchema();
		}
	}

	File getDbFile() {
		return dbFile;
	}

	long getDbFileBytes() {
		return dbFile.exists() ? dbFile.length() : 0L;
	}

	boolean isLocalMachine() {
		return localMachine;
	}

	int ensureKeyId(final String name) throws SQLException {
		if (name == null || name.length() == 0) {
			return ensureKeyId("Unknown");
		}
		Connection connection = null;
		PreparedStatement select = null;
		PreparedStatement insert = null;
		ResultSet rs = null;
		try {
			connection = openConnection();
			select = connection.prepareStatement("SELECT id FROM key_dict WHERE name = ?");
			select.setString(1, name);
			rs = select.executeQuery();
			if (rs.next()) {
				return rs.getInt(1);
			}
			closeQuietly(rs);
			rs = null;
			insert = connection.prepareStatement("INSERT INTO key_dict(name) VALUES (?)",
			        Statement.RETURN_GENERATED_KEYS);
			insert.setString(1, name);
			insert.executeUpdate();
			rs = insert.getGeneratedKeys();
			if (rs.next()) {
				return rs.getInt(1);
			}
			return lookupKeyId(connection, name);
		}
		finally {
			closeQuietly(rs);
			closeQuietly(select);
			closeQuietly(insert);
			closeQuietly(connection);
		}
	}

	Map loadDict() throws SQLException {
		final Map map = new LinkedHashMap();
		Connection connection = null;
		Statement statement = null;
		ResultSet rs = null;
		try {
			connection = openConnection();
			statement = connection.createStatement();
			rs = statement.executeQuery("SELECT id, name FROM key_dict");
			while (rs.next()) {
				map.put(Integer.valueOf(rs.getInt(1)), rs.getString(2));
			}
			return map;
		}
		finally {
			closeQuietly(rs);
			closeQuietly(statement);
			closeQuietly(connection);
		}
	}

	/**
	 * Insert a finished session chunk and bump hour stats. Short transaction.
	 * @return session id
	 */
	long insertSession(final long startTs, final long endTs, final int keyCount, final boolean approx,
	        final String source, final byte[] blob, final int[] hourBuckets, final long[] hourKeys)
	        throws SQLException {
		Connection connection = null;
		PreparedStatement insertSession = null;
		PreparedStatement insertChunk = null;
		PreparedStatement upsertHour = null;
		ResultSet keys = null;
		try {
			connection = openConnection();
			connection.setAutoCommit(false);
			insertSession = connection.prepareStatement(
			        "INSERT INTO key_session(start_ts, end_ts, key_count, approx, source) VALUES (?,?,?,?,?)",
			        Statement.RETURN_GENERATED_KEYS);
			insertSession.setLong(1, startTs);
			insertSession.setLong(2, endTs);
			insertSession.setInt(3, keyCount);
			insertSession.setInt(4, approx ? 1 : 0);
			insertSession.setString(5, source == null ? "live" : source);
			insertSession.executeUpdate();
			keys = insertSession.getGeneratedKeys();
			final long sessionId = keys.next() ? keys.getLong(1) : 0L;
			insertChunk = connection.prepareStatement("INSERT INTO key_chunk(session_id, blob) VALUES (?,?)");
			insertChunk.setLong(1, sessionId);
			insertChunk.setBytes(2, blob);
			insertChunk.executeUpdate();
			if (hourBuckets != null && hourKeys != null) {
				upsertHour = connection.prepareStatement(
				        "INSERT OR IGNORE INTO key_hour_stat(hour_ts, key_count) VALUES (?, 0)");
				PreparedStatement addHour = null;
				try {
					addHour = connection.prepareStatement(
					        "UPDATE key_hour_stat SET key_count = key_count + ? WHERE hour_ts = ?");
					for (int i = 0; i < hourBuckets.length; i++) {
						if (hourBuckets[i] <= 0) {
							continue;
						}
						upsertHour.setLong(1, hourKeys[i]);
						upsertHour.executeUpdate();
						addHour.setInt(1, hourBuckets[i]);
						addHour.setLong(2, hourKeys[i]);
						addHour.executeUpdate();
					}
				}
				finally {
					closeQuietly(addHour);
				}
			}
			connection.commit();
			return sessionId;
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
			closeQuietly(keys);
			closeQuietly(insertSession);
			closeQuietly(insertChunk);
			closeQuietly(upsertHour);
			closeQuietly(connection);
		}
	}

	/** hour_ts -> count for [fromTs, toTs). */
	Map sumByHour(final long fromTs, final long toTs) throws SQLException {
		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		try {
			connection = openConnection();
			statement = connection.prepareStatement(
			        "SELECT hour_ts, key_count FROM key_hour_stat WHERE hour_ts >= ? AND hour_ts < ? ORDER BY hour_ts");
			statement.setLong(1, floorHour(fromTs));
			statement.setLong(2, toTs);
			rs = statement.executeQuery();
			final Map map = new LinkedHashMap();
			while (rs.next()) {
				map.put(Long.valueOf(rs.getLong(1)), Long.valueOf(rs.getLong(2)));
			}
			return map;
		}
		catch (SQLException e) {
			final String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
			if (msg.indexOf("no such table") >= 0) {
				return new LinkedHashMap();
			}
			throw e;
		}
		finally {
			closeQuietly(rs);
			closeQuietly(statement);
			closeQuietly(connection);
		}
	}

	long sumKeys(final long fromTs, final long toTs) throws SQLException {
		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		try {
			connection = openConnection();
			statement = connection.prepareStatement(
			        "SELECT COALESCE(SUM(key_count),0) FROM key_hour_stat WHERE hour_ts >= ? AND hour_ts < ?");
			statement.setLong(1, floorHour(fromTs));
			statement.setLong(2, toTs);
			rs = statement.executeQuery();
			return rs.next() ? rs.getLong(1) : 0L;
		}
		catch (SQLException e) {
			final String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
			if (msg.indexOf("no such table") >= 0) {
				return 0L;
			}
			throw e;
		}
		finally {
			closeQuietly(rs);
			closeQuietly(statement);
			closeQuietly(connection);
		}
	}

	List listSessions(final long fromTs, final long toTs, final int limit) throws SQLException {
		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		try {
			connection = openConnection();
			statement = connection.prepareStatement(
			        "SELECT id, start_ts, end_ts, key_count, approx, source FROM key_session "
			                + "WHERE end_ts >= ? AND start_ts < ? ORDER BY start_ts DESC LIMIT ?");
			statement.setLong(1, fromTs);
			statement.setLong(2, toTs);
			statement.setInt(3, limit > 0 ? limit : 500);
			rs = statement.executeQuery();
			final List rows = new ArrayList();
			while (rs.next()) {
				final KeyLogSession s = new KeyLogSession();
				s.id = rs.getLong(1);
				s.startTs = rs.getLong(2);
				s.endTs = rs.getLong(3);
				s.keyCount = rs.getInt(4);
				s.approx = rs.getInt(5) != 0;
				s.source = rs.getString(6);
				s.sourceDbPath = dbFile.getAbsolutePath();
				rows.add(s);
			}
			return rows;
		}
		finally {
			closeQuietly(rs);
			closeQuietly(statement);
			closeQuietly(connection);
		}
	}

	byte[] loadChunk(final long sessionId) throws SQLException {
		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		try {
			connection = openConnection();
			statement = connection.prepareStatement("SELECT blob FROM key_chunk WHERE session_id = ?");
			statement.setLong(1, sessionId);
			rs = statement.executeQuery();
			return rs.next() ? rs.getBytes(1) : null;
		}
		finally {
			closeQuietly(rs);
			closeQuietly(statement);
			closeQuietly(connection);
		}
	}

	void checkpoint() {
		Connection connection = null;
		Statement statement = null;
		try {
			connection = openConnection();
			statement = connection.createStatement();
			statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
		}
		catch (Exception e) {
		}
		finally {
			closeQuietly(statement);
			closeQuietly(connection);
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
			statement.execute("CREATE TABLE IF NOT EXISTS key_dict ("
			        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
			        + "name TEXT NOT NULL UNIQUE)");
			statement.execute("CREATE TABLE IF NOT EXISTS key_session ("
			        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
			        + "start_ts INTEGER NOT NULL,"
			        + "end_ts INTEGER NOT NULL,"
			        + "key_count INTEGER NOT NULL,"
			        + "approx INTEGER NOT NULL DEFAULT 0,"
			        + "source TEXT)");
			statement.execute("CREATE INDEX IF NOT EXISTS idx_key_session_ts ON key_session(start_ts, end_ts)");
			statement.execute("CREATE TABLE IF NOT EXISTS key_chunk ("
			        + "session_id INTEGER PRIMARY KEY,"
			        + "blob BLOB NOT NULL)");
			statement.execute("CREATE TABLE IF NOT EXISTS key_hour_stat ("
			        + "hour_ts INTEGER PRIMARY KEY,"
			        + "key_count INTEGER NOT NULL)");
			LogUtils.info("Keylog database ready: " + dbFile.getAbsolutePath());
		}
		catch (SQLException e) {
			LogUtils.warn("Keylog database init failed: " + e.getMessage(), e);
		}
		finally {
			closeQuietly(statement);
			closeQuietly(connection);
		}
	}

	private int lookupKeyId(final Connection connection, final String name) throws SQLException {
		PreparedStatement select = null;
		ResultSet rs = null;
		try {
			select = connection.prepareStatement("SELECT id FROM key_dict WHERE name = ?");
			select.setString(1, name);
			rs = select.executeQuery();
			return rs.next() ? rs.getInt(1) : 0;
		}
		finally {
			closeQuietly(rs);
			closeQuietly(select);
		}
	}

	private Connection openConnection() throws SQLException {
		try {
			Class.forName("org.sqlite.JDBC", true, KeyLogDatabase.class.getClassLoader());
		}
		catch (ClassNotFoundException e) {
			throw new SQLException("SQLite JDBC driver not found", e);
		}
		return DriverManager.getConnection(JDBC_PREFIX + dbFile.getAbsolutePath());
	}

	static long floorHour(final long ts) {
		return (ts / 3600000L) * 3600000L;
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
