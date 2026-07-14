package org.freeplane.plugin.workspace.features.nodepins;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;

/**
 * Persists tag filter groups under {@code {dataRoot}/_data/tag-groups.properties}.
 * Built-in group {@link #UNGROUPED_ID} always exists as a root node; custom groups
 * may nest under any other group with no depth limit.
 */
public final class TagGroupStore {

	public static final String UNGROUPED_ID = "ungrouped";

	private static final String FILE_NAME = "tag-groups.properties";
	private static final String CHARSET = "UTF-8";
	private static final String KEY_GROUPS = "groups";
	private static final String PREFIX_NAME = "name.";
	private static final String PREFIX_PARENT = "parent.";
	private static final String PREFIX_TAG = "tag.";

	private static TagGroupStore instance;

	/** Depth-first ordered group ids; always starts with {@link #UNGROUPED_ID}. */
	private final List groupIds = Collections.synchronizedList(new ArrayList());
	private final Map groupNames = Collections.synchronizedMap(new LinkedHashMap());
	/** Custom group id → parent group id (missing/null means root). Ungrouped has no parent. */
	private final Map groupParents = Collections.synchronizedMap(new LinkedHashMap());
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

	/**
	 * All group ids in depth-first order (roots left-to-right, children nested beneath).
	 */
	public List getGroupIds() {
		ensureLoaded();
		synchronized (groupIds) {
			return new ArrayList(groupIds);
		}
	}

	/** Top-level group ids (ungrouped first, then root custom groups in order). */
	public List getRootGroupIds() {
		ensureLoaded();
		final List roots = new ArrayList();
		synchronized (groupIds) {
			for (int i = 0; i < groupIds.size(); i++) {
				final String id = (String) groupIds.get(i);
				if (getParentId(id) == null) {
					roots.add(id);
				}
			}
		}
		return roots;
	}

	/**
	 * Direct children of {@code parentId}. Pass {@code null} for root-level custom groups
	 * (excluding ungrouped — use {@link #getRootGroupIds()} for the full root list).
	 */
	public List getChildIds(final String parentId) {
		ensureLoaded();
		final List children = new ArrayList();
		final String normalizedParent = normalizeParentKey(parentId);
		synchronized (groupIds) {
			for (int i = 0; i < groupIds.size(); i++) {
				final String id = (String) groupIds.get(i);
				if (UNGROUPED_ID.equals(id)) {
					continue;
				}
				final String p = normalizeParentKey(getParentId(id));
				if (equalsNullable(normalizedParent, p)) {
					children.add(id);
				}
			}
		}
		return children;
	}

	public String getParentId(final String groupId) {
		ensureLoaded();
		if (groupId == null || UNGROUPED_ID.equals(groupId)) {
			return null;
		}
		synchronized (groupParents) {
			final String parent = (String) groupParents.get(groupId);
			if (parent == null || parent.length() == 0 || !containsGroup(parent)) {
				return null;
			}
			return parent;
		}
	}

	public int getDepth(final String groupId) {
		ensureLoaded();
		if (groupId == null || UNGROUPED_ID.equals(groupId)) {
			return 0;
		}
		int depth = 0;
		String current = groupId;
		final Set seen = new HashSet();
		while (current != null && !UNGROUPED_ID.equals(current)) {
			if (!seen.add(current)) {
				break;
			}
			final String parent = getParentId(current);
			if (parent == null) {
				break;
			}
			depth++;
			current = parent;
		}
		return depth;
	}

	public boolean hasChildren(final String groupId) {
		return !getChildIds(groupId).isEmpty();
	}

	/**
	 * Returns true if {@code possibleDescendant} is {@code ancestorId} or nested under it.
	 */
	public boolean isDescendantOf(final String possibleDescendant, final String ancestorId) {
		if (possibleDescendant == null || ancestorId == null) {
			return false;
		}
		if (possibleDescendant.equals(ancestorId)) {
			return true;
		}
		ensureLoaded();
		String current = possibleDescendant;
		final Set seen = new HashSet();
		while (current != null) {
			if (!seen.add(current)) {
				return false;
			}
			current = getParentId(current);
			if (ancestorId.equals(current)) {
				return true;
			}
		}
		return false;
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

	public boolean canAddChild(final String parentId) {
		if (parentId == null || parentId.length() == 0) {
			return true;
		}
		return containsGroup(parentId);
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

	/** Creates a root-level custom group. */
	public String addGroup(final String name) {
		return addGroup(name, null);
	}

	/**
	 * Creates a group under {@code parentId}. Pass {@code null} or empty for a root group.
	 * Parent may be {@link #UNGROUPED_ID} or any custom group; nesting has no depth limit.
	 */
	public String addGroup(final String name, final String parentId) {
		ensureLoaded();
		final String trimmed = name != null ? name.trim() : "";
		if (trimmed.length() == 0) {
			return null;
		}
		final String resolvedParent = resolveParentForCreate(parentId);
		if (parentId != null && parentId.length() > 0 && !UNGROUPED_ID.equals(parentId)
				&& resolvedParent == null) {
			return null;
		}
		final String id = "g" + (++nextId);
		synchronized (groupIds) {
			insertAfterLastSibling(id, resolvedParent);
			groupNames.put(id, trimmed);
			if (resolvedParent != null) {
				groupParents.put(id, resolvedParent);
			}
			rebuildTraversalOrder();
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
	 * Moves {@code groupId} under {@code newParentId} (null = root). Rejects cycles and
	 * moving ungrouped. Sibling order places the moved group last among its new siblings.
	 */
	public boolean moveGroup(final String groupId, final String newParentId) {
		if (!canRename(groupId)) {
			return false;
		}
		ensureLoaded();
		if (!containsGroup(groupId)) {
			return false;
		}
		final String targetParent = resolveParentForCreate(newParentId);
		if (newParentId != null && newParentId.length() > 0 && !UNGROUPED_ID.equals(newParentId)
				&& targetParent == null) {
			return false;
		}
		if (targetParent != null && isDescendantOf(targetParent, groupId)) {
			return false;
		}
		final String currentParent = getParentId(groupId);
		if (equalsNullable(normalizeParentKey(currentParent), normalizeParentKey(targetParent))) {
			return true;
		}
		synchronized (groupIds) {
			if (targetParent == null) {
				groupParents.remove(groupId);
			}
			else {
				groupParents.put(groupId, targetParent);
			}
			rebuildTraversalOrder();
			save();
			return true;
		}
	}

	/**
	 * Removes a custom group. Direct tags fall back to ungrouped. Child groups are
	 * reparented to this group's former parent (or become roots).
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
			final String oldParent = getParentId(groupId);
			final List children = getChildIds(groupId);
			for (int i = 0; i < children.size(); i++) {
				final String childId = (String) children.get(i);
				if (oldParent == null) {
					groupParents.remove(childId);
				}
				else {
					groupParents.put(childId, oldParent);
				}
			}
			groupIds.remove(groupId);
			groupNames.remove(groupId);
			groupParents.remove(groupId);
			synchronized (tagToGroup) {
				for (final Iterator it = tagToGroup.entrySet().iterator(); it.hasNext();) {
					final Map.Entry entry = (Map.Entry) it.next();
					if (groupId.equals(entry.getValue())) {
						it.remove();
					}
				}
			}
			rebuildTraversalOrder();
			save();
			return true;
		}
	}

	/** Null / empty / ungrouped → root level (ungrouped is a leaf tab, not a nestable folder). */
	private String resolveParentForCreate(final String parentId) {
		if (parentId == null || parentId.length() == 0 || UNGROUPED_ID.equals(parentId)) {
			return null;
		}
		if (!containsGroup(parentId)) {
			return null;
		}
		return parentId;
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

	private void insertAfterLastSibling(final String newId, final String parentId) {
		final String normalizedParent = normalizeParentKey(parentId);
		int insertAt = groupIds.size();
		for (int i = groupIds.size() - 1; i >= 0; i--) {
			final String id = (String) groupIds.get(i);
			if (UNGROUPED_ID.equals(id)) {
				continue;
			}
			if (equalsNullable(normalizeParentKey(getParentId(id)), normalizedParent)) {
				insertAt = i + 1;
				break;
			}
		}
		if (insertAt > groupIds.size()) {
			insertAt = groupIds.size();
		}
		groupIds.add(insertAt, newId);
	}

	/**
	 * Rebuilds {@link #groupIds} as depth-first preorder: ungrouped, then each root and
	 * its descendants. Preserves relative sibling order from the current list.
	 */
	private void rebuildTraversalOrder() {
		final List previous = new ArrayList(groupIds);
		final List ordered = new ArrayList();
		ordered.add(UNGROUPED_ID);
		appendChildrenInOrder(ordered, null, previous);
		for (int i = 0; i < previous.size(); i++) {
			final String id = (String) previous.get(i);
			if (!ordered.contains(id) && containsGroupLoose(id, previous)) {
				ordered.add(id);
			}
		}
		groupIds.clear();
		groupIds.addAll(ordered);
	}

	private void appendChildrenInOrder(final List ordered, final String parentId, final List previous) {
		final String normalizedParent = normalizeParentKey(parentId);
		for (int i = 0; i < previous.size(); i++) {
			final String id = (String) previous.get(i);
			if (UNGROUPED_ID.equals(id) || ordered.contains(id)) {
				continue;
			}
			final String p = normalizeParentKey((String) groupParents.get(id));
			if (!equalsNullable(normalizedParent, p)) {
				continue;
			}
			if (p != null && !UNGROUPED_ID.equals(p) && !containsGroupLoose(p, previous)
					&& !ordered.contains(p)) {
				continue;
			}
			ordered.add(id);
			appendChildrenInOrder(ordered, id, previous);
		}
	}

	private boolean containsGroupLoose(final String groupId, final List list) {
		return list.contains(groupId);
	}

	private static String normalizeParentKey(final String parentId) {
		if (parentId == null || parentId.length() == 0) {
			return null;
		}
		return parentId;
	}

	private static boolean equalsNullable(final String a, final String b) {
		if (a == null) {
			return b == null;
		}
		return a.equals(b);
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
		groupParents.clear();
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
			for (int i = 0; i < loadedIds.size(); i++) {
				final String id = (String) loadedIds.get(i);
				if (UNGROUPED_ID.equals(id)) {
					continue;
				}
				final String parent = props.getProperty(PREFIX_PARENT + id);
				if (parent != null && parent.length() > 0 && loadedIds.contains(parent)
						&& !id.equals(parent)) {
					groupParents.put(id, parent);
				}
			}
			breakParentCycles(loadedIds);
			groupIds.clear();
			groupIds.addAll(loadedIds);
			rebuildTraversalOrder();
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

	private void breakParentCycles(final List loadedIds) {
		for (int i = 0; i < loadedIds.size(); i++) {
			final String id = (String) loadedIds.get(i);
			if (UNGROUPED_ID.equals(id)) {
				continue;
			}
			final Set seen = new HashSet();
			String current = id;
			while (current != null) {
				if (!seen.add(current)) {
					groupParents.remove(id);
					break;
				}
				current = (String) groupParents.get(current);
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
				final String parent = (String) groupParents.get(id);
				if (parent != null && parent.length() > 0 && groupIds.contains(parent)) {
					props.setProperty(PREFIX_PARENT + id, parent);
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
			props.store(writer, "Docear tag filter groups (nested via parent.<id>)");
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
