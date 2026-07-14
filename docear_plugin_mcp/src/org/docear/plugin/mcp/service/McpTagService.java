package org.docear.plugin.mcp.service;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.docear.plugin.mcp.DocearMcpConfig;
import org.docear.plugin.mcp.json.JsonValue;
import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.workspace.features.favorites.FavoriteEntry;
import org.freeplane.plugin.workspace.features.favorites.FavoritesAndTagsStore;
import org.freeplane.plugin.workspace.features.nodepins.NodePinEntry;
import org.freeplane.plugin.workspace.features.nodepins.NodePinsIndex;
import org.freeplane.plugin.workspace.features.nodepins.TagColorStore;
import org.freeplane.plugin.workspace.features.nodepins.TagGroupStore;

/**
 * MCP access to sidebar tag groups, tag catalog, and reverse lookup (tag → nodes / favorites).
 */
public final class McpTagService {

	public static final String SCOPE_PINS = "pins";
	public static final String SCOPE_FAVORITES = "favorites";
	public static final String SCOPE_ALL = "all";

	private McpTagService() {
	}

	public static String listTagGroups() {
		final TagGroupStore store = TagGroupStore.getInstance();
		final NodePinsIndex index = NodePinsIndex.getInstance();
		final Set allTags = index.getQuickSelectTags();
		final List groupIds = store.getGroupIds();
		final List<JsonValue> groups = new ArrayList<JsonValue>();
		for (final Iterator it = groupIds.iterator(); it.hasNext();) {
			final String groupId = (String) it.next();
			int tagCount = 0;
			for (final Iterator tagIt = allTags.iterator(); tagIt.hasNext();) {
				final String tag = (String) tagIt.next();
				if (groupId.equals(store.getTagGroupId(tag))) {
					tagCount++;
				}
			}
			final Map<String, JsonValue> row = new LinkedHashMap<String, JsonValue>();
			row.put("groupId", JsonValue.ofString(groupId));
			row.put("name", JsonValue.ofString(resolveGroupName(store, groupId)));
			row.put("isDefault", JsonValue.ofBoolean(store.isUngrouped(groupId)));
			row.put("canRename", JsonValue.ofBoolean(store.canRename(groupId)));
			row.put("canRemove", JsonValue.ofBoolean(store.canRemove(groupId)));
			row.put("tagCount", JsonValue.ofNumber(Integer.valueOf(tagCount)));
			row.put("nodeCount", JsonValue.ofNumber(Integer.valueOf(countNodesForGroup(index, store, groupId, allTags))));
			groups.add(JsonValue.ofMap(row));
		}
		final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
		result.put("groups", JsonValue.ofList(groups));
		result.put("count", JsonValue.ofNumber(Integer.valueOf(groups.size())));
		return JsonValue.ofMap(result).toJson();
	}

	public static String listTags(final String scope, final String groupId, final boolean includeEmpty) {
		final String resolvedScope = normalizeScope(scope);
		final TagGroupStore groupStore = TagGroupStore.getInstance();
		final TagColorStore colorStore = TagColorStore.getInstance();
		final Set tagNames = collectTagNames(resolvedScope);
		final List sorted = new ArrayList(tagNames);
		Collections.sort(sorted, new Comparator() {
			public int compare(final Object o1, final Object o2) {
				return ((String) o1).compareTo((String) o2);
			}
		});
		final List<JsonValue> tags = new ArrayList<JsonValue>();
		for (final Iterator it = sorted.iterator(); it.hasNext();) {
			final String tag = (String) it.next();
			final String tagGroupId = groupStore.getTagGroupId(tag);
			if (groupId != null && groupId.length() > 0 && !groupId.equals(tagGroupId)) {
				continue;
			}
			final int pinCount = NodePinsIndex.getInstance().countWithTag(tag);
			final int favoriteCount = FavoritesAndTagsStore.getInstance().countFavoritesWithTag(tag);
			final int scopedCount;
			if (SCOPE_FAVORITES.equals(resolvedScope)) {
				scopedCount = favoriteCount;
			}
			else if (SCOPE_ALL.equals(resolvedScope)) {
				scopedCount = pinCount + favoriteCount;
			}
			else {
				scopedCount = pinCount;
			}
			if (!includeEmpty && scopedCount == 0) {
				continue;
			}
			final Map<String, JsonValue> row = new LinkedHashMap<String, JsonValue>();
			row.put("tag", JsonValue.ofString(tag));
			row.put("groupId", JsonValue.ofString(tagGroupId));
			row.put("groupName", JsonValue.ofString(resolveGroupName(groupStore, tagGroupId)));
			row.put("color", JsonValue.ofString(TagColorStore.toHex(colorStore.getColor(tag))));
			row.put("pinCount", JsonValue.ofNumber(Integer.valueOf(pinCount)));
			row.put("favoriteCount", JsonValue.ofNumber(Integer.valueOf(favoriteCount)));
			row.put("count", JsonValue.ofNumber(Integer.valueOf(scopedCount)));
			tags.add(JsonValue.ofMap(row));
		}
		final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
		result.put("scope", JsonValue.ofString(resolvedScope));
		result.put("groupId", JsonValue.ofString(groupId != null ? groupId : ""));
		result.put("tags", JsonValue.ofList(tags));
		result.put("count", JsonValue.ofNumber(Integer.valueOf(tags.size())));
		return JsonValue.ofMap(result).toJson();
	}

	public static String listNodesByTag(final String tag, final String scope, final int limit) {
		if (tag == null || tag.trim().length() == 0) {
			throw new IllegalArgumentException("tag is required");
		}
		final String resolvedTag = tag.trim();
		final String resolvedScope = normalizeScope(scope);
		final int max = limit > 0 ? limit : 200;
		final List<JsonValue> nodes = new ArrayList<JsonValue>();
		if (SCOPE_PINS.equals(resolvedScope) || SCOPE_ALL.equals(resolvedScope)) {
			final List entries = NodePinsIndex.getInstance().getDisplayEntries(false, resolvedTag);
			for (int i = 0; i < entries.size() && nodes.size() < max; i++) {
				nodes.add(pinEntryToJson((NodePinEntry) entries.get(i)));
			}
		}
		if (SCOPE_FAVORITES.equals(resolvedScope) || SCOPE_ALL.equals(resolvedScope)) {
			final List favorites = FavoritesAndTagsStore.getInstance().getFavorites();
			for (int i = 0; i < favorites.size() && nodes.size() < max; i++) {
				final FavoriteEntry entry = (FavoriteEntry) favorites.get(i);
				if (entry.getTags().contains(resolvedTag)) {
					nodes.add(favoriteEntryToJson(entry));
				}
			}
		}
		final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
		result.put("tag", JsonValue.ofString(resolvedTag));
		result.put("scope", JsonValue.ofString(resolvedScope));
		result.put("groupId", JsonValue.ofString(TagGroupStore.getInstance().getTagGroupId(resolvedTag)));
		result.put("nodes", JsonValue.ofList(nodes));
		result.put("count", JsonValue.ofNumber(Integer.valueOf(nodes.size())));
		result.put("truncated", JsonValue.ofBoolean(Boolean.valueOf(nodes.size() >= max)));
		return JsonValue.ofMap(result).toJson();
	}

	public static String listFavorites(final String tag, final int limit) {
		final int max = limit > 0 ? limit : 200;
		final String filter = tag != null ? tag.trim() : "";
		final List favorites = FavoritesAndTagsStore.getInstance().getFavorites();
		final List<JsonValue> items = new ArrayList<JsonValue>();
		for (int i = 0; i < favorites.size() && items.size() < max; i++) {
			final FavoriteEntry entry = (FavoriteEntry) favorites.get(i);
			if (filter.length() > 0 && !entry.getTags().contains(filter)) {
				continue;
			}
			items.add(favoriteEntryToJson(entry));
		}
		final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
		result.put("tag", JsonValue.ofString(filter));
		result.put("favorites", JsonValue.ofList(items));
		result.put("count", JsonValue.ofNumber(Integer.valueOf(items.size())));
		return JsonValue.ofMap(result).toJson();
	}

	/**
	 * One-shot catalog: pin tag groups (with tags/counts/colors) plus favorite tags.
	 */
	public static String getTagCatalog() {
		final TagGroupStore store = TagGroupStore.getInstance();
		final NodePinsIndex index = NodePinsIndex.getInstance();
		final Set pinTags = index.getQuickSelectTags();
		final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();

		final List groupIds = store.getGroupIds();
		final List<JsonValue> groups = new ArrayList<JsonValue>();
		for (final Iterator it = groupIds.iterator(); it.hasNext();) {
			final String groupId = (String) it.next();
			final List tagsInGroup = new ArrayList();
			for (final Iterator tagIt = pinTags.iterator(); tagIt.hasNext();) {
				final String tag = (String) tagIt.next();
				if (groupId.equals(store.getTagGroupId(tag))) {
					tagsInGroup.add(tag);
				}
			}
			Collections.sort(tagsInGroup);
			final List<JsonValue> tagRows = new ArrayList<JsonValue>();
			for (final Iterator tagIt = tagsInGroup.iterator(); tagIt.hasNext();) {
				final String tag = (String) tagIt.next();
				final Map<String, JsonValue> tagRow = new LinkedHashMap<String, JsonValue>();
				tagRow.put("tag", JsonValue.ofString(tag));
				tagRow.put("count", JsonValue.ofNumber(Integer.valueOf(index.countWithTag(tag))));
				tagRow.put("color", JsonValue.ofString(TagColorStore.toHex(TagColorStore.getInstance().getColor(tag))));
				tagRows.add(JsonValue.ofMap(tagRow));
			}
			final Map<String, JsonValue> groupRow = new LinkedHashMap<String, JsonValue>();
			groupRow.put("groupId", JsonValue.ofString(groupId));
			groupRow.put("name", JsonValue.ofString(resolveGroupName(store, groupId)));
			groupRow.put("isDefault", JsonValue.ofBoolean(store.isUngrouped(groupId)));
			groupRow.put("nodeCount",
					JsonValue.ofNumber(Integer.valueOf(countNodesForGroup(index, store, groupId, pinTags))));
			groupRow.put("tags", JsonValue.ofList(tagRows));
			groups.add(JsonValue.ofMap(groupRow));
		}
		result.put("scope", JsonValue.ofString(SCOPE_PINS));
		result.put("groups", JsonValue.ofList(groups));
		final List favSorted = new ArrayList(FavoritesAndTagsStore.getInstance().getQuickSelectTags());
		Collections.sort(favSorted);
		final List<JsonValue> favRows = new ArrayList<JsonValue>();
		for (final Iterator it = favSorted.iterator(); it.hasNext();) {
			final String tag = (String) it.next();
			final Map<String, JsonValue> row = new LinkedHashMap<String, JsonValue>();
			row.put("tag", JsonValue.ofString(tag));
			row.put("count", JsonValue
					.ofNumber(Integer.valueOf(FavoritesAndTagsStore.getInstance().countFavoritesWithTag(tag))));
			row.put("color", JsonValue.ofString(TagColorStore.toHex(TagColorStore.getInstance().getColor(tag))));
			favRows.add(JsonValue.ofMap(row));
		}
		result.put("favoriteTags", JsonValue.ofList(favRows));
		return JsonValue.ofMap(result).toJson();
	}

	public static String createTagGroup(final String name) {
		ensureWritable();
		final String id = TagGroupStore.getInstance().addGroup(name);
		if (id == null) {
			throw new IllegalArgumentException("group name is required");
		}
		final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
		result.put("groupId", JsonValue.ofString(id));
		result.put("name", JsonValue.ofString(TagGroupStore.getInstance().getGroupName(id)));
		result.put("ok", JsonValue.ofBoolean(true));
		return JsonValue.ofMap(result).toJson();
	}

	public static String renameTagGroup(final String groupId, final String name) {
		ensureWritable();
		if (groupId == null || groupId.length() == 0) {
			throw new IllegalArgumentException("groupId is required");
		}
		final boolean ok = TagGroupStore.getInstance().renameGroup(groupId, name);
		if (!ok) {
			throw new IllegalArgumentException("cannot rename group: " + groupId);
		}
		final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
		result.put("groupId", JsonValue.ofString(groupId));
		result.put("name", JsonValue.ofString(TagGroupStore.getInstance().getGroupName(groupId)));
		result.put("ok", JsonValue.ofBoolean(true));
		return JsonValue.ofMap(result).toJson();
	}

	public static String deleteTagGroup(final String groupId) {
		ensureWritable();
		if (groupId == null || groupId.length() == 0) {
			throw new IllegalArgumentException("groupId is required");
		}
		final boolean ok = TagGroupStore.getInstance().removeGroup(groupId);
		if (!ok) {
			throw new IllegalArgumentException("cannot delete group: " + groupId);
		}
		final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
		result.put("groupId", JsonValue.ofString(groupId));
		result.put("ok", JsonValue.ofBoolean(true));
		result.put("note", JsonValue.ofString("Tags moved to ungrouped"));
		return JsonValue.ofMap(result).toJson();
	}

	public static String setTagGroup(final String tag, final String groupId) {
		ensureWritable();
		if (tag == null || tag.trim().length() == 0) {
			throw new IllegalArgumentException("tag is required");
		}
		final String resolvedTag = tag.trim();
		final String target = groupId != null && groupId.length() > 0 ? groupId : TagGroupStore.UNGROUPED_ID;
		if (!TagGroupStore.getInstance().getGroupIds().contains(target)) {
			throw new IllegalArgumentException("unknown groupId: " + target);
		}
		TagGroupStore.getInstance().setTagGroup(resolvedTag, target);
		final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
		result.put("tag", JsonValue.ofString(resolvedTag));
		result.put("groupId", JsonValue.ofString(TagGroupStore.getInstance().getTagGroupId(resolvedTag)));
		result.put("groupName",
				JsonValue.ofString(resolveGroupName(TagGroupStore.getInstance(),
						TagGroupStore.getInstance().getTagGroupId(resolvedTag))));
		result.put("ok", JsonValue.ofBoolean(true));
		return JsonValue.ofMap(result).toJson();
	}

	public static String setTagColor(final String tag, final String colorHex, final boolean clear) {
		ensureWritable();
		if (tag == null || tag.trim().length() == 0) {
			throw new IllegalArgumentException("tag is required");
		}
		final String resolvedTag = tag.trim();
		if (clear) {
			TagColorStore.getInstance().clearColor(resolvedTag);
		}
		else {
			final Color color = TagColorStore.parseHex(colorHex);
			if (color == null) {
				throw new IllegalArgumentException("color must be #RRGGBB");
			}
			TagColorStore.getInstance().setColor(resolvedTag, color);
		}
		final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
		result.put("tag", JsonValue.ofString(resolvedTag));
		result.put("color", JsonValue.ofString(TagColorStore.toHex(TagColorStore.getInstance().getColor(resolvedTag))));
		result.put("cleared", JsonValue.ofBoolean(clear));
		result.put("ok", JsonValue.ofBoolean(true));
		return JsonValue.ofMap(result).toJson();
	}

	private static Set collectTagNames(final String scope) {
		final Set tags = new LinkedHashSet();
		if (SCOPE_PINS.equals(scope) || SCOPE_ALL.equals(scope)) {
			tags.addAll(NodePinsIndex.getInstance().getQuickSelectTags());
		}
		if (SCOPE_FAVORITES.equals(scope) || SCOPE_ALL.equals(scope)) {
			tags.addAll(FavoritesAndTagsStore.getInstance().getQuickSelectTags());
		}
		return tags;
	}

	private static String normalizeScope(final String scope) {
		if (scope == null || scope.length() == 0) {
			return SCOPE_PINS;
		}
		final String lower = scope.trim().toLowerCase();
		if (SCOPE_FAVORITES.equals(lower) || "fav".equals(lower) || "favorite".equals(lower)) {
			return SCOPE_FAVORITES;
		}
		if (SCOPE_ALL.equals(lower)) {
			return SCOPE_ALL;
		}
		return SCOPE_PINS;
	}

	private static String resolveGroupName(final TagGroupStore store, final String groupId) {
		if (store.isUngrouped(groupId)) {
			final String text = TextUtils.getText("workspace.nodepins.group.ungrouped");
			return text != null && text.length() > 0 ? text : "Ungrouped";
		}
		final String name = store.getGroupName(groupId);
		return name != null ? name : groupId;
	}

	private static int countNodesForGroup(final NodePinsIndex index, final TagGroupStore store, final String groupId,
			final Set allTags) {
		final Set tagSet = new HashSet();
		for (final Iterator it = allTags.iterator(); it.hasNext();) {
			final String tag = (String) it.next();
			if (groupId.equals(store.getTagGroupId(tag))) {
				tagSet.add(tag);
			}
		}
		if (tagSet.isEmpty()) {
			return 0;
		}
		int count = 0;
		final List entries = index.getDisplayEntries(false, null);
		for (int i = 0; i < entries.size(); i++) {
			final NodePinEntry entry = (NodePinEntry) entries.get(i);
			for (final Iterator tagIt = entry.getTags().iterator(); tagIt.hasNext();) {
				if (tagSet.contains(tagIt.next())) {
					count++;
					break;
				}
			}
		}
		return count;
	}

	private static JsonValue pinEntryToJson(final NodePinEntry entry) {
		final Map<String, JsonValue> row = new LinkedHashMap<String, JsonValue>();
		final File mapFile = entry.getMapFile();
		row.put("kind", JsonValue.ofString("pin"));
		row.put("mapFile", JsonValue.ofString(mapFile != null ? mapFile.getAbsolutePath() : ""));
		row.put("nodeId", JsonValue.ofString(nullToEmpty(entry.getNodeId())));
		row.put("nodeText", JsonValue.ofString(entry.getListNodeLabel()));
		row.put("tags", JsonValue.ofString(joinTags(entry.getTags())));
		row.put("exists", JsonValue.ofBoolean(entry.exists()));
		return JsonValue.ofMap(row);
	}

	private static JsonValue favoriteEntryToJson(final FavoriteEntry entry) {
		final Map<String, JsonValue> row = new LinkedHashMap<String, JsonValue>();
		final File file = entry.getFile();
		row.put("kind", JsonValue.ofString("favorite"));
		row.put("uri", JsonValue.ofString(entry.getUri()));
		row.put("mapFile", JsonValue.ofString(file != null ? file.getAbsolutePath() : ""));
		row.put("nodeId", JsonValue.ofString(""));
		row.put("nodeText", JsonValue.ofString(entry.getListDisplayName()));
		row.put("tags", JsonValue.ofString(joinTags(entry.getTags())));
		row.put("exists", JsonValue.ofBoolean(entry.exists()));
		return JsonValue.ofMap(row);
	}

	private static String joinTags(final Set tags) {
		if (tags == null || tags.isEmpty()) {
			return "";
		}
		final StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (final Iterator it = tags.iterator(); it.hasNext();) {
			if (!first) {
				sb.append(',');
			}
			first = false;
			sb.append(String.valueOf(it.next()));
		}
		return sb.toString();
	}

	private static String nullToEmpty(final String value) {
		return value == null ? "" : value;
	}

	private static void ensureWritable() {
		if (DocearMcpConfig.isReadOnly()) {
			throw new IllegalStateException("MCP is in read-only mode");
		}
	}
}
