package org.docear.plugin.core.todoist;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.freeplane.core.util.Compat;
import org.freeplane.core.util.LogUtils;

/**
 * Persistent 1:1 map between mind-map nodes and Todoist tasks.
 * <p>
 * Forward key {@code absPath|nodeId} → {@code taskId|remindAt|contentHash}<br/>
 * Reverse key {@code taskId} → {@code absPath|nodeId} (kept in memory + file with prefix {@code #task:})
 * <p>
 * Process-wide singleton: concurrent sync/import/auto-push previously opened separate in-memory
 * copies and last-writer-won on disk, dropping mappings.
 */
final class TodoistMappingStore {
	private static final String FILE_NAME = "todoist-sync-map.properties";
	private static final String REVERSE_PREFIX = "#task:";
	private static final Object INSTANCE_LOCK = new Object();
	private static TodoistMappingStore shared;

	private final File storeFile;
	private final Map mappings = new HashMap();
	private final Map reverse = new HashMap();

	static TodoistMappingStore get() {
		synchronized (INSTANCE_LOCK) {
			if (shared == null) {
				shared = new TodoistMappingStore();
			}
			return shared;
		}
	}

	/** @deprecated Prefer {@link #get()}; kept for tests that need an isolated empty store. */
	TodoistMappingStore() {
		storeFile = new File(Compat.getApplicationUserDirectory(), FILE_NAME);
		load();
	}

	synchronized void putMapping(String syncKey, String taskId, long remindAt, String contentHash) {
		if (syncKey == null || taskId == null || taskId.length() == 0) {
			return;
		}
		final String previousTaskId = getTaskIdOnly(syncKey);
		if (previousTaskId != null && previousTaskId.length() > 0 && !previousTaskId.equals(taskId)) {
			reverse.remove(previousTaskId);
		}
		mappings.put(syncKey, taskId + "|" + remindAt + "|" + contentHash);
		reverse.put(taskId, syncKey);
	}

	synchronized void removeMapping(String syncKey) {
		final String taskId = getTaskIdOnly(syncKey);
		mappings.remove(syncKey);
		// Only drop reverse if it still points at this exact sync key (path may have
		// been remapped to a canonical key for the same task).
		if (taskId != null && syncKey.equals(reverse.get(taskId))) {
			reverse.remove(taskId);
		}
	}

	synchronized void removeByTaskId(String taskId) {
		if (taskId == null) {
			return;
		}
		final String syncKey = (String) reverse.remove(taskId);
		if (syncKey != null) {
			mappings.remove(syncKey);
		}
	}

	synchronized String getSyncKeyForTaskId(String taskId) {
		if (taskId == null) {
			return null;
		}
		return (String) reverse.get(taskId);
	}

	synchronized String getStoredRemindAt(String syncKey) {
		String value = (String) mappings.get(syncKey);
		if (value == null) {
			return null;
		}
		int first = value.indexOf('|');
		int second = value.indexOf('|', first + 1);
		if (first < 0 || second < 0) {
			return null;
		}
		return value.substring(first + 1, second);
	}

	synchronized String getStoredContentHash(String syncKey) {
		String value = (String) mappings.get(syncKey);
		if (value == null) {
			return null;
		}
		int second = value.lastIndexOf('|');
		if (second < 0) {
			return null;
		}
		return value.substring(second + 1);
	}

	synchronized String getTaskIdOnly(String syncKey) {
		String value = (String) mappings.get(syncKey);
		if (value == null) {
			return null;
		}
		int first = value.indexOf('|');
		if (first < 0) {
			return value;
		}
		return value.substring(0, first);
	}

	/**
	 * Resolve a stored task id when the absolute path drifted but Map file name + node id match.
	 */
	synchronized String findTaskIdByIdentity(String identityKey) {
		if (identityKey == null || identityKey.length() == 0) {
			return null;
		}
		for (Iterator it = mappings.keySet().iterator(); it.hasNext();) {
			String syncKey = (String) it.next();
			if (identityKey.equals(TodoistSyncKeys.identityKeyFromSyncKey(syncKey))) {
				return getTaskIdOnlyUnlocked(syncKey);
			}
		}
		return null;
	}

	private String getTaskIdOnlyUnlocked(String syncKey) {
		String value = (String) mappings.get(syncKey);
		if (value == null) {
			return null;
		}
		int first = value.indexOf('|');
		if (first < 0) {
			return value;
		}
		return value.substring(0, first);
	}

	synchronized Set keySet() {
		return new HashSet(mappings.keySet());
	}

	synchronized Set getAllMappedTaskIds() {
		return new HashSet(reverse.keySet());
	}

	/** True if this task is linked to a reminder outside the Todoist import target map. */
	synchronized boolean isLinkedToSourceMap(String taskId) {
		final String syncKey = getSyncKeyForTaskIdUnlocked(taskId);
		if (syncKey == null) {
			return false;
		}
		final int sep = syncKey.lastIndexOf('|');
		if (sep <= 0) {
			return false;
		}
		final File file = new File(syncKey.substring(0, sep));
		return !TodoistConfig.isImportTargetFile(file);
	}

	private String getSyncKeyForTaskIdUnlocked(String taskId) {
		if (taskId == null) {
			return null;
		}
		return (String) reverse.get(taskId);
	}

	synchronized void save() {
		final File tmp = new File(storeFile.getParentFile(), "~" + storeFile.getName());
		OutputStream out = null;
		try {
			out = new FileOutputStream(tmp);
			Writer writer = new OutputStreamWriter(out, "UTF-8");
			for (Iterator it = mappings.entrySet().iterator(); it.hasNext();) {
				Map.Entry entry = (Map.Entry) it.next();
				writer.write(escape((String) entry.getKey()));
				writer.write('=');
				writer.write(escape((String) entry.getValue()));
				writer.write('\n');
			}
			for (Iterator it = reverse.entrySet().iterator(); it.hasNext();) {
				Map.Entry entry = (Map.Entry) it.next();
				writer.write(escape(REVERSE_PREFIX + entry.getKey()));
				writer.write('=');
				writer.write(escape((String) entry.getValue()));
				writer.write('\n');
			}
			writer.flush();
			writer.close();
			out = null;
			if (storeFile.exists() && !storeFile.delete()) {
				LogUtils.warn("Todoist: could not replace mapping store: " + storeFile.getPath());
				tmp.delete();
				return;
			}
			if (!tmp.renameTo(storeFile)) {
				LogUtils.warn("Todoist: could not rename mapping store into place");
				tmp.delete();
			}
		}
		catch (IOException e) {
			LogUtils.warn("Todoist: could not save mapping store", e);
			if (tmp.exists()) {
				tmp.delete();
			}
		}
		finally {
			if (out != null) {
				try {
					out.close();
				}
				catch (IOException e) {
				}
			}
		}
	}

	private void load() {
		if (!storeFile.isFile()) {
			return;
		}
		InputStream in = null;
		try {
			in = new FileInputStream(storeFile);
			BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.length() == 0 || line.startsWith("#") && !line.startsWith(REVERSE_PREFIX)) {
					continue;
				}
				int eq = line.indexOf('=');
				if (eq <= 0) {
					continue;
				}
				final String key = unescape(line.substring(0, eq));
				final String value = unescape(line.substring(eq + 1));
				if (key.startsWith(REVERSE_PREFIX)) {
					reverse.put(key.substring(REVERSE_PREFIX.length()), value);
				}
				else {
					mappings.put(key, value);
					final String taskId = extractTaskId(value);
					if (taskId != null) {
						reverse.put(taskId, key);
					}
				}
			}
		}
		catch (IOException e) {
			LogUtils.warn("Todoist: could not load mapping store", e);
		}
		finally {
			if (in != null) {
				try {
					in.close();
				}
				catch (IOException e) {
				}
			}
		}
	}

	private static String extractTaskId(String value) {
		if (value == null) {
			return null;
		}
		int first = value.indexOf('|');
		if (first < 0) {
			return value.length() > 0 ? value : null;
		}
		final String id = value.substring(0, first);
		return id.length() > 0 ? id : null;
	}

	private static String escape(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\\", "\\\\").replace("\n", "\\n").replace("=", "\\=");
	}

	private static String unescape(String value) {
		if (value == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		boolean esc = false;
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (esc) {
				if (c == 'n') {
					sb.append('\n');
				}
				else {
					sb.append(c);
				}
				esc = false;
			}
			else if (c == '\\') {
				esc = true;
			}
			else {
				sb.append(c);
			}
		}
		return sb.toString();
	}
}
