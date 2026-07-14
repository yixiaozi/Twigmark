package org.freeplane.plugin.workspace.features.nodepins;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;

/**
 * Persists tag filter groups (tabs) under {@code {dataRoot}/_data/tag-groups.properties}.
 * Built-in group {@link #UNGROUPED_ID} always exists first; unassigned tags belong there.
 */
public final class TagGroupStore {

	public static final String UNGROUPED_ID = "ungrouped";

	private static final String FILE_NAME = "tag-groups.properties";
	private static final String CHARSET = "UTF-8";
	private static final String KEY_GROUPS = "groups";
	private static final String PREFIX_NAME = "name.";
	private static final String PREFIX_TAG = "tag.";

	private static TagGroupStore instance;

	/** Ordered group ids; always starts with {@link #UNGROUPED_ID}. */
	private final List groupIds = Collections.synchronizedList(new ArrayList());
	private final Map groupNames = Collections.synchronizedMap(new LinkedHashMap());
	private final Map tagToGroup = Collections.synchronizedMap(new LinkedHashMap());
	private boolean loaded;
	private long nextId;

	private TagGroupStore() {
	}

	public static synchronized TagGroupStore getInstance() {
		if (instance == null) {
			instance = new TagGroupStore();
		}
		return instance;
	}

	public List getGroupIds() {
		ensureLoaded();
		synchronized (groupIds) {
			return new ArrayList(groupIds);
		}
	}

	public String getGroupName(final String groupId) {
		ensureLoaded();
		if (groupId == null || UNGROUPED_ID.equals(groupId)) {
			return null;
		}
		synchronized (groupNames) {
			final String name = (String) groupNames.get(groupId);
			return name != null ? name : groupId;
		}
	}

	public boolean isUngrouped(final String groupId) {
		return groupId == null || UNGROUPED_ID.equals(groupId);
	}

	public boolean canRename(final String groupId) {
		return groupId != null && !UNGROUPED_ID.equals(groupId);
	}

	public boolean canRemove(final String groupId) {
		return canRename(groupId);
	}

	public String getTagGroupId(final String tag) {
		ensureLoaded();
		if (tag == null || tag.length() == 0) {
			return UNGROUPED_ID;
		}
		synchronized (tagToGroup) {
			final String groupId = (String) tagToGroup.get(tag);
			if (groupId == null || !containsGroup(groupId)) {
				return UNGROUPED_ID;
			}
			return groupId;
		}
	}

	public void setTagGroup(final String tag, final String groupId) {
		if (tag == null || tag.length() == 0) {
			return;
		}
		ensureLoaded();
		final String target = resolveGroupId(groupId);
		synchronized (tagToGroup) {
			if (UNGROUPED_ID.equals(target)) {
				tagToGroup.remove(tag);
			}
			else {
				tagToGroup.put(tag, target);
			}
			save();
		}
	}

	public String addGroup(final String name) {
		ensureLoaded();
		final String trimmed = name != null ? name.trim() : "";
		if (trimmed.length() == 0) {
			return null;
		}
		final String id = "g" + (++nextId);
		synchronized (groupIds) {
			groupIds.add(id);
			groupNames.put(id, trimmed);
			save();
		}
		return id;
	}

	public boolean renameGroup(final String groupId, final String name) {
		if (!canRename(groupId)) {
			return false;
		}
		final String trimmed = name != null ? name.trim() : "";
		if (trimmed.length() == 0) {
			return false;
		}
		ensureLoaded();
		synchronized (groupNames) {
			if (!containsGroup(groupId)) {
				return false;
			}
			groupNames.put(groupId, trimmed);
			save();
			return true;
		}
	}

	/**
	 * Removes a custom group; tags in it fall back to ungrouped.
	 */
	public boolean removeGroup(final String groupId) {
		if (!canRemove(groupId)) {
			return false;
		}
		ensureLoaded();
		synchronized (groupIds) {
			if (!groupIds.contains(groupId)) {
				return false;
			}
			groupIds.remove(groupId);
			groupNames.remove(groupId);
			synchronized (tagToGroup) {
				for (final Iterator it = tagToGroup.entrySet().iterator(); it.hasNext();) {
					final Map.Entry entry = (Map.Entry) it.next();
					if (groupId.equals(entry.getValue())) {
						it.remove();
					}
				}
			}
			save();
			return true;
		}
	}

	private String resolveGroupId(final String groupId) {
		if (groupId == null || UNGROUPED_ID.equals(groupId) || !containsGroup(groupId)) {
			return UNGROUPED_ID;
		}
		return groupId;
	}

	private boolean containsGroup(final String groupId) {
		synchronized (groupIds) {
			return groupIds.contains(groupId);
		}
	}

	private void ensureLoaded() {
		if (loaded) {
			return;
		}
		synchronized (this) {
			if (loaded) {
				return;
			}
			load();
			loaded = true;
		}
	}

	private File resolveFile() {
		final File dir = MindMapDataRootResolver.getApplicationConfigDirectory();
		if (dir == null) {
			return null;
		}
		if (!dir.exists() && !dir.mkdirs()) {
			LogUtils.warn("Could not create tag group dir: " + dir.getAbsolutePath());
			return null;
		}
		return new File(dir, FILE_NAME);
	}

	private void resetDefaults() {
		groupIds.clear();
		groupNames.clear();
		tagToGroup.clear();
		groupIds.add(UNGROUPED_ID);
		nextId = System.currentTimeMillis();
	}

	private void load() {
		resetDefaults();
		final File file = resolveFile();
		if (file == null || !file.isFile()) {
			return;
		}
		final Properties props = new Properties();
		InputStreamReader reader = null;
		try {
			reader = new InputStreamReader(new FileInputStream(file), CHARSET);
			props.load(reader);
			final String groupsValue = props.getProperty(KEY_GROUPS, "");
			final List loadedIds = new ArrayList();
			loadedIds.add(UNGROUPED_ID);
			final String[] parts = groupsValue.split(",");
			for (int i = 0; i < parts.length; i++) {
				final String id = parts[i].trim();
				if (id.length() == 0 || UNGROUPED_ID.equals(id) || loadedIds.contains(id)) {
					continue;
				}
				loadedIds.add(id);
				final String name = props.getProperty(PREFIX_NAME + id, id);
				groupNames.put(id, name);
				updateNextId(id);
			}
			groupIds.clear();
			groupIds.addAll(loadedIds);
			for (final Iterator it = props.keySet().iterator(); it.hasNext();) {
				final String key = (String) it.next();
				if (!key.startsWith(PREFIX_TAG)) {
					continue;
				}
				final String tag = key.substring(PREFIX_TAG.length());
				final String groupId = props.getProperty(key);
				if (tag.length() > 0 && groupId != null && groupIds.contains(groupId)
						&& !UNGROUPED_ID.equals(groupId)) {
					tagToGroup.put(tag, groupId);
				}
			}
		}
		catch (final Exception e) {
			LogUtils.warn("Could not load tag groups from " + file.getAbsolutePath(), e);
			resetDefaults();
		}
		finally {
			if (reader != null) {
				try {
					reader.close();
				}
				catch (final Exception ignore) {
				}
			}
		}
	}

	private void updateNextId(final String id) {
		if (id == null || !id.startsWith("g")) {
			return;
		}
		try {
			final long value = Long.parseLong(id.substring(1));
			if (value >= nextId) {
				nextId = value + 1;
			}
		}
		catch (final NumberFormatException ignore) {
		}
	}

	private void save() {
		final File file = resolveFile();
		if (file == null) {
			return;
		}
		final Properties props = new Properties();
		final StringBuilder groups = new StringBuilder(UNGROUPED_ID);
		synchronized (groupIds) {
			for (int i = 0; i < groupIds.size(); i++) {
				final String id = (String) groupIds.get(i);
				if (UNGROUPED_ID.equals(id)) {
					continue;
				}
				groups.append(',').append(id);
				final String name = (String) groupNames.get(id);
				if (name != null) {
					props.setProperty(PREFIX_NAME + id, name);
				}
			}
		}
		props.setProperty(KEY_GROUPS, groups.toString());
		synchronized (tagToGroup) {
			for (final Iterator it = tagToGroup.entrySet().iterator(); it.hasNext();) {
				final Map.Entry entry = (Map.Entry) it.next();
				props.setProperty(PREFIX_TAG + entry.getKey(), (String) entry.getValue());
			}
		}
		OutputStreamWriter writer = null;
		try {
			writer = new OutputStreamWriter(new FileOutputStream(file), CHARSET);
			props.store(writer, "Docear tag filter groups");
		}
		catch (final Exception e) {
			LogUtils.warn("Could not save tag groups to " + file.getAbsolutePath(), e);
		}
		finally {
			if (writer != null) {
				try {
					writer.close();
				}
				catch (final Exception ignore) {
				}
			}
		}
	}
}
