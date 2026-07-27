package org.docear.plugin.ai.snapshot;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.docear.plugin.ai.DocearAiConfig;
import org.freeplane.core.util.HtmlUtils;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;
import org.freeplane.core.util.MindMapWorkspaceContextScanner;
import org.freeplane.core.util.MindMapWorkspaceContextScanner.IconItem;
import org.freeplane.core.util.MindMapWorkspaceContextScanner.ModifiedItem;
import org.freeplane.core.util.MindMapWorkspaceContextScanner.ReminderItem;
import org.freeplane.core.util.MindMapWorkspaceContextScanner.TodoItem;
import org.freeplane.core.util.WorkspaceSideTabSnapshot;
import org.freeplane.core.util.WorkspaceSideTabSnapshot.ModifiedEntry;
import org.freeplane.core.util.WorkspaceSideTabSnapshot.PinnedEntry;
import org.freeplane.core.util.WorkspaceSideTabSnapshot.ReminderEntry;
import org.freeplane.core.util.WorkspaceSideTabSnapshot.TodoEntry;
import org.freeplane.core.util.WorkspaceSideTabSnapshotRegistry;

/**
 * 将侧栏 Tab 快照导出为 Markdown，供外部 AI 读取。
 */
public final class AiWorkspaceSnapshotExporter {

    private static final int RECENTLY_MODIFIED_LIMIT = 200;
    private static final DateFormat DATE_TIME = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
    private static final DateFormat RECENT_DISPLAY = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
    private static final String TREE_STAMP_FILE = ".tree-export-stamp";

    private final DocearAiConfig config;
    private final File outputDir;
    private final File dataRoot;

    public AiWorkspaceSnapshotExporter(DocearAiConfig config) {
        this.config = config;
        this.outputDir = new File(config.getWorkspaceSnapshotDirectory());
        this.dataRoot = MindMapDataRootResolver.getFixedDataRoot();
    }

    public void export() {
        if (!config.isWorkspaceSnapshotExportEnabled()) {
            return;
        }
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            LogUtils.warn("AI snapshot export: could not create " + outputDir.getAbsolutePath());
            return;
        }
        final long started = System.currentTimeMillis();
        final WorkspaceSideTabSnapshot snapshot = WorkspaceSideTabSnapshotRegistry.getSnapshot();
        // Prefer side-tab cache; only fall back to full-library scans when the cache is still empty.
        final boolean allowDiskFallback = !snapshot.hasAnyItems();

        final List reminders = !snapshot.getOneTimeReminders().isEmpty()
                ? snapshot.getOneTimeReminders()
                : (allowDiskFallback
                        ? toReminderEntries(MindMapWorkspaceContextScanner.scanOneTimeReminders(), false)
                        : Collections.EMPTY_LIST);
        final List recurring = !snapshot.getRecurringReminders().isEmpty()
                ? snapshot.getRecurringReminders()
                : (allowDiskFallback
                        ? toReminderEntries(MindMapWorkspaceContextScanner.scanRecurringReminders(), true)
                        : Collections.EMPTY_LIST);
        final List todos = !snapshot.getTodos().isEmpty()
                ? snapshot.getTodos()
                : (allowDiskFallback ? toTodoEntries(MindMapWorkspaceContextScanner.scanAllTodos())
                        : Collections.EMPTY_LIST);
        final List pinned = !snapshot.getPinnedEntries().isEmpty()
                ? snapshot.getPinnedEntries()
                : (allowDiskFallback ? scanPinnedEntries() : Collections.EMPTY_LIST);
        final List published = !snapshot.getPublishedEntries().isEmpty()
                ? snapshot.getPublishedEntries()
                : (allowDiskFallback
                        ? toItemEntries(MindMapWorkspaceContextScanner.scanPublishedItems())
                        : Collections.EMPTY_LIST);
        final List recent = !snapshot.getRecentlyModifiedEntries().isEmpty()
                ? snapshot.getRecentlyModifiedEntries()
                : (allowDiskFallback
                        ? toModifiedEntries(
                                MindMapWorkspaceContextScanner.scanRecentlyModified(RECENTLY_MODIFIED_LIMIT))
                        : Collections.EMPTY_LIST);
        final List favorites = loadFavorites();

        writeReminders(new File(outputDir, "01-\u5168\u90e8\u63d0\u9192.md"), reminders, "\u5168\u90e8\u63d0\u9192",
                "\u4e00\u6b21\u6027\u63d0\u9192\uff08REMINDERTYPE=onetime \u6216\u672a\u8bbe\u7f6e\uff09\u3002");
        writeReminders(new File(outputDir, "02-\u5468\u671f\u63d0\u9192.md"), recurring, "\u5468\u671f\u63d0\u9192",
                "\u5468\u671f\u6027\u63d0\u9192\uff08REMINDERTYPE \u975e onetime\uff09\u3002");
        writeSimpleItems(new File(outputDir, "03-\u5168\u90e8\u5f85\u529e.md"), todos, "\u5168\u90e8\u5f85\u529e",
                "\u8282\u70b9\u5e26 hourglass\uff08\u6c99\u6f0f\uff09\u56fe\u6807\u7684\u5f85\u529e\u9879\u3002");
        writePinned(new File(outputDir, "04-\u6211\u7684\u9489\u9009.md"), pinned);
        writeSimpleItems(new File(outputDir, "05-\u5168\u90e8\u53d1\u5e03.md"), published, "\u5168\u90e8\u53d1\u5e03",
                "\u8282\u70b9\u5e26 internet\uff08\u53d1\u5e03\uff09\u56fe\u6807\u7684\u9879\u3002");
        writeRecentlyModified(new File(outputDir, "06-\u6700\u8fd1\u4fee\u6539.md"), recent);
        writeFavorites(new File(outputDir, "07-\u6536\u85cf.md"), favorites);
        maybeWriteTreeFile();
        LogUtils.info("AI workspace snapshot exported to " + outputDir.getAbsolutePath() + " in "
                + (System.currentTimeMillis() - started) + " ms");
    }

    private List toReminderEntries(List items, boolean recurring) {
        final List entries = new ArrayList();
        for (int i = 0; i < items.size(); i++) {
            ReminderItem item = (ReminderItem) items.get(i);
            entries.add(new ReminderEntry(item.mapFile, item.nodeId, item.nodeText, item.remindAt, recurring,
                    item.remindType));
        }
        return entries;
    }

    private List toTodoEntries(List items) {
        final List entries = new ArrayList();
        for (int i = 0; i < items.size(); i++) {
            TodoItem item = (TodoItem) items.get(i);
            entries.add(new TodoEntry(item.mapFile, item.nodeId, item.nodeText));
        }
        return entries;
    }

    private List toItemEntries(List items) {
        final List entries = new ArrayList();
        for (int i = 0; i < items.size(); i++) {
            IconItem item = (IconItem) items.get(i);
            entries.add(new WorkspaceSideTabSnapshot.ItemEntry(item.mapFile, item.nodeId, item.nodeText));
        }
        return entries;
    }

    private List toModifiedEntries(List items) {
        final List entries = new ArrayList();
        for (int i = 0; i < items.size(); i++) {
            ModifiedItem item = (ModifiedItem) items.get(i);
            entries.add(new ModifiedEntry(item.mapFile, item.nodeId, item.nodeText, item.modifiedAt));
        }
        return entries;
    }

    private List scanPinnedEntries() {
        final List entries = new ArrayList();
        try {
            final Class scannerClass = Class
                    .forName("org.freeplane.plugin.workspace.features.nodepins.NodeDetailsTagScanner");
            final List scanned = (List) scannerClass.getMethod("scanAllProjects", new Class[0]).invoke(null,
                    new Object[0]);
            final String pinTag = "\u9489\u9009";
            final String archivedTag = "\u5df2\u5f52\u6863";
            for (int i = 0; i < scanned.size(); i++) {
                final Object entry = scanned.get(i);
                final boolean pinned = ((Boolean) entry.getClass().getMethod("isPinned", new Class[0]).invoke(entry,
                        new Object[0])).booleanValue();
                if (!pinned) {
                    continue;
                }
                final Set tags = (Set) entry.getClass().getMethod("getTags", new Class[0]).invoke(entry, new Object[0]);
                if (tags != null && tags.contains(archivedTag)) {
                    continue;
                }
                final File mapFile = (File) entry.getClass().getMethod("getMapFile", new Class[0]).invoke(entry,
                        new Object[0]);
                final String nodeId = (String) entry.getClass().getMethod("getNodeId", new Class[0]).invoke(entry,
                        new Object[0]);
                final String label = (String) entry.getClass().getMethod("getListNodeLabel", new Class[0]).invoke(entry,
                        new Object[0]);
                final StringBuilder tagText = new StringBuilder();
                if (tags != null) {
                    for (final Iterator it = tags.iterator(); it.hasNext();) {
                        final String tag = (String) it.next();
                        if (pinTag.equals(tag)) {
                            continue;
                        }
                        if (tagText.length() > 0) {
                            tagText.append(", ");
                        }
                        tagText.append(tag);
                    }
                }
                entries.add(new PinnedEntry(mapFile, nodeId, label, tagText.toString()));
            }
        }
        catch (final Exception e) {
            LogUtils.warn("AI snapshot export: pinned scan unavailable: " + e.getMessage());
        }
        return entries;
    }

    private List loadFavorites() {
        try {
            final Class storeClass = Class.forName("org.freeplane.plugin.workspace.features.favorites.FavoritesAndTagsStore");
            final Object store = storeClass.getMethod("getInstance", new Class[0]).invoke(null, new Object[0]);
            return (List) storeClass.getMethod("getFavorites", new Class[0]).invoke(store, new Object[0]);
        }
        catch (final Exception e) {
            LogUtils.warn("AI snapshot export: favorites unavailable: " + e.getMessage());
            return Collections.EMPTY_LIST;
        }
    }

    private void writeReminders(File file, List items, String title, String description) {
        final List sorted = new ArrayList(items);
        Collections.sort(sorted, new Comparator() {
            public int compare(Object o1, Object o2) {
                ReminderEntry a = (ReminderEntry) o1;
                ReminderEntry b = (ReminderEntry) o2;
                return a.remindAt < b.remindAt ? -1 : (a.remindAt == b.remindAt ? 0 : 1);
            }
        });
        BufferedWriter writer = null;
        try {
            writer = openWriter(file);
            writeHeader(writer, title, sorted.size(), description);
            for (int i = 0; i < sorted.size(); i++) {
                ReminderEntry item = (ReminderEntry) sorted.get(i);
                writer.write("## " + (i + 1) + ". " + safeText(item.nodeText) + "\n\n");
                writer.write("- \u63d0\u9192\u65f6\u95f4: " + formatTs(item.remindAt) + "\n");
                writer.write("- \u63d0\u9192\u7c7b\u578b: " + safeText(item.remindType) + "\n");
                writeLocation(writer, item.mapFile, item.nodeId);
                writer.write("\n");
            }
        }
        catch (final Exception e) {
            LogUtils.warn("AI snapshot export failed for " + file.getName() + ": " + e.getMessage());
        }
        finally {
            closeWriter(writer);
        }
    }

    private void writeSimpleItems(File file, List items, String title, String description) {
        BufferedWriter writer = null;
        try {
            writer = openWriter(file);
            writeHeader(writer, title, items.size(), description);
            for (int i = 0; i < items.size(); i++) {
                Object raw = items.get(i);
                File mapFile;
                String nodeId;
                String nodeText;
                if (raw instanceof TodoEntry) {
                    TodoEntry item = (TodoEntry) raw;
                    mapFile = item.mapFile;
                    nodeId = item.nodeId;
                    nodeText = item.nodeText;
                }
                else {
                    WorkspaceSideTabSnapshot.ItemEntry item = (WorkspaceSideTabSnapshot.ItemEntry) raw;
                    mapFile = item.mapFile;
                    nodeId = item.nodeId;
                    nodeText = item.nodeText;
                }
                writer.write("## " + (i + 1) + ". " + safeText(nodeText) + "\n\n");
                writeLocation(writer, mapFile, nodeId);
                writer.write("\n");
            }
        }
        catch (final Exception e) {
            LogUtils.warn("AI snapshot export failed for " + file.getName() + ": " + e.getMessage());
        }
        finally {
            closeWriter(writer);
        }
    }

    private void writePinned(File file, List items) {
        BufferedWriter writer = null;
        try {
            writer = openWriter(file);
            writeHeader(writer, "\u6211\u7684\u9489\u9009", items.size(),
                    "\u8282\u70b9\u8be6\u60c5\u4e2d\u542b #\u9489\u9009 \u6807\u7b7e\u4e14\u672a\u6807\u8bb0 #\u5df2\u5f52\u6863 \u7684\u8282\u70b9\u3002");
            for (int i = 0; i < items.size(); i++) {
                PinnedEntry item = (PinnedEntry) items.get(i);
                writer.write("## " + (i + 1) + ". " + safeText(item.nodeText) + "\n\n");
                writeLocation(writer, item.mapFile, item.nodeId);
                if (item.tags != null && item.tags.length() > 0) {
                    writer.write("- \u5176\u4ed6\u6807\u7b7e: " + item.tags + "\n");
                }
                writer.write("\n");
            }
        }
        catch (final Exception e) {
            LogUtils.warn("AI snapshot export failed for " + file.getName() + ": " + e.getMessage());
        }
        finally {
            closeWriter(writer);
        }
    }

    private void writeRecentlyModified(File file, List items) {
        BufferedWriter writer = null;
        try {
            writer = openWriter(file);
            writeHeader(writer, "\u6700\u8fd1\u4fee\u6539\uff08\u524d200\u6761\uff09", items.size(),
                    "\u6309\u8282\u70b9 MODIFIED \u65f6\u95f4\u6233\u964d\u5e8f\u6392\u5217\uff0c\u53d6\u524d200\u6761\uff08\u4e0d\u9650\u5929\u6570\uff09\u3002");
            String currentGroup = null;
            for (int i = 0; i < items.size(); i++) {
                ModifiedEntry item = (ModifiedEntry) items.get(i);
                final String group = timeGroupLabel(item.modifiedAt);
                if (!group.equals(currentGroup)) {
                    currentGroup = group;
                    writer.write("# " + group + "\n\n");
                }
                writer.write("## " + (i + 1) + ". " + RECENT_DISPLAY.format(new Date(item.modifiedAt)) + " "
                        + safeText(item.nodeText) + "\n\n");
                writeLocation(writer, item.mapFile, item.nodeId);
                writer.write("\n");
            }
        }
        catch (final Exception e) {
            LogUtils.warn("AI snapshot export failed for " + file.getName() + ": " + e.getMessage());
        }
        finally {
            closeWriter(writer);
        }
    }

    private void writeFavorites(File file, List favorites) {
        BufferedWriter writer = null;
        try {
            writer = openWriter(file);
            writeHeader(writer, "\u6536\u85cf", favorites.size(),
                    "\u6765\u81ea favorites.settings\uff0c\u542b\u6536\u85cf\u8def\u5f84\u4e0e\u5206\u7c7b\u6807\u7b7e\u3002");
            final Map tagCounts = new LinkedHashMap();
            tagCounts.put("\u5168\u90e8", Integer.valueOf(favorites.size()));
            for (int i = 0; i < favorites.size(); i++) {
                Object entry = favorites.get(i);
                final String tag = getFavoriteTagLabel(entry);
                final Integer count = (Integer) tagCounts.get(tag);
                tagCounts.put(tag, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
            }
            writer.write("## \u5206\u7c7b\u7edf\u8ba1\n\n");
            for (final Iterator it = tagCounts.entrySet().iterator(); it.hasNext();) {
                Map.Entry e = (Map.Entry) it.next();
                writer.write("- " + e.getKey() + ": " + e.getValue() + "\n");
            }
            writer.write("\n---\n\n");
            for (int i = 0; i < favorites.size(); i++) {
                Object entry = favorites.get(i);
                writer.write("## " + (i + 1) + ". " + getFavoriteName(entry) + "\n\n");
                writer.write("- \u76f8\u5bf9\u8def\u5f84: " + getFavoriteUri(entry) + "\n");
                writer.write("- \u6807\u7b7e: " + getFavoriteTagLabel(entry) + "\n");
                final File favFile = getFavoriteFile(entry);
                writer.write("- \u6587\u4ef6\u5b58\u5728: " + (favFile != null && favFile.isFile() ? "\u662f" : "\u5426") + "\n");
                if (favFile != null && favFile.isFile()) {
                    writer.write("- \u7edd\u5bf9\u8def\u5f84: " + favFile.getAbsolutePath() + "\n");
                }
                writer.write("\n");
            }
        }
        catch (final Exception e) {
            LogUtils.warn("AI snapshot export failed for " + file.getName() + ": " + e.getMessage());
        }
        finally {
            closeWriter(writer);
        }
    }

    private void maybeWriteTreeFile() {
        final File stamp = new File(outputDir, TREE_STAMP_FILE);
        final long interval = config.getWorkspaceSnapshotTreeIntervalMs();
        if (stamp.isFile() && System.currentTimeMillis() - stamp.lastModified() < interval) {
            return;
        }
        writeTreeFile();
        try {
            final BufferedWriter stampWriter = openWriter(stamp);
            try {
                stampWriter.write(Long.toString(System.currentTimeMillis()));
                stampWriter.write('\n');
            }
            finally {
                closeWriter(stampWriter);
            }
        }
        catch (final Exception e) {
            // stamp is best-effort
        }
    }

    private void writeTreeFile() {
        final File treeFile = new File(outputDir, "00-\u6587\u4ef6\u76ee\u5f55\u6811.txt");
        BufferedWriter writer = null;
        try {
            writer = openWriter(treeFile);
            final int fileCount = dataRoot != null ? AiWorkspaceFileTreeBuilder.countWorkspaceFiles(dataRoot) : 0;
            writer.write("# \u5de5\u4f5c\u533a\u5b8c\u6574\u6587\u4ef6\u76ee\u5f55\u6811\n");
            writer.write("# \u751f\u6210\u65f6\u95f4: " + DATE_TIME.format(new Date()) + "\n");
            if (dataRoot != null) {
                writer.write("# \u6570\u636e\u6839\u76ee\u5f55: " + dataRoot.getAbsolutePath() + "\n");
            }
            writer.write("# \u6587\u4ef6\u603b\u6570: " + fileCount + "\n");
            writer.write("# \u8bf4\u660e: \u76f8\u5bf9\u6570\u636e\u6839\u7684\u6811\u5f62\u5217\u8868\uff0c\u5df2\u6392\u9664 _data\u3001bin\u3001.git \u76ee\u5f55\uff0c\u542b\u9690\u85cf\u76ee\u5f55\uff08\u5982 .AI\u8bf7\u67e5\u770b\u8fd9\u91cc\uff09\n\n");
            if (dataRoot != null) {
                writer.write(AiWorkspaceFileTreeBuilder.buildTree(dataRoot));
            }
        }
        catch (final Exception e) {
            LogUtils.warn("AI snapshot tree export failed: " + e.getMessage());
        }
        finally {
            closeWriter(writer);
        }
    }

    private void writeHeader(BufferedWriter writer, String title, int count, String description) throws Exception {
        writer.write("# " + title + "\n\n");
        writer.write("- \u5bfc\u51fa\u65f6\u95f4: " + DATE_TIME.format(new Date()) + "\n");
        if (dataRoot != null) {
            writer.write("- \u6570\u636e\u6839\u76ee\u5f55: " + dataRoot.getAbsolutePath() + "\n");
        }
        writer.write("- \u6761\u76ee\u6570: " + count + "\n");
        writer.write("- \u8bf4\u660e: " + description + "\n\n");
        writer.write("---\n\n");
    }

    private void writeLocation(BufferedWriter writer, File mapFile, String nodeId) throws Exception {
        writer.write("- \u5bfc\u56fe: " + relativePath(mapFile) + "\n");
        writer.write("- \u8282\u70b9ID: " + safeText(nodeId) + "\n");
        writer.write("- \u5b9a\u4f4d: " + relativePath(mapFile) + "#node=" + safeText(nodeId) + "\n");
    }

    private String relativePath(File file) {
        if (file == null) {
            return "";
        }
        if (dataRoot != null) {
            try {
                final String path = dataRoot.toURI().relativize(file.toURI()).getPath();
                if (path != null && path.length() > 0) {
                    return path.startsWith("/") ? path.substring(1) : path;
                }
            }
            catch (final Exception e) {
            }
        }
        return MindMapDataRootResolver.getRelativePathWithinScanRoots(file.getParentFile()) + "/" + file.getName();
    }

    private String formatTs(long ts) {
        return ts > 0L ? DATE_TIME.format(new Date(ts)) : "";
    }

    private String safeText(String text) {
        if (text == null) {
            return "";
        }
        return HtmlUtils.removeHtmlTagsFromString(text).replaceAll("\\s+", " ").trim();
    }

    private String timeGroupLabel(long timestamp) {
        final Calendar now = Calendar.getInstance();
        final Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        final long todayStart = startOfDay(now);
        final long yesterdayStart = todayStart - 24L * 60L * 60L * 1000L;
        final long twoDaysAgoStart = yesterdayStart - 24L * 60L * 60L * 1000L;
        if (timestamp >= todayStart) {
            return "\u4eca\u5929";
        }
        if (timestamp >= yesterdayStart) {
            return "\u6628\u5929";
        }
        if (timestamp >= twoDaysAgoStart) {
            return "\u524d\u5929";
        }
        return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date(timestamp));
    }

    private long startOfDay(Calendar cal) {
        final Calendar copy = (Calendar) cal.clone();
        copy.set(Calendar.HOUR_OF_DAY, 0);
        copy.set(Calendar.MINUTE, 0);
        copy.set(Calendar.SECOND, 0);
        copy.set(Calendar.MILLISECOND, 0);
        return copy.getTimeInMillis();
    }

    private String getFavoriteUri(Object entry) throws Exception {
        return (String) entry.getClass().getMethod("getUri", new Class[0]).invoke(entry, new Object[0]);
    }

    private String getFavoriteName(Object entry) throws Exception {
        return (String) entry.getClass().getMethod("getDisplayName", new Class[0]).invoke(entry, new Object[0]);
    }

    private File getFavoriteFile(Object entry) throws Exception {
        return (File) entry.getClass().getMethod("getFile", new Class[0]).invoke(entry, new Object[0]);
    }

    private String getFavoriteTagLabel(Object entry) throws Exception {
        final Set tags = (Set) entry.getClass().getMethod("getTags", new Class[0]).invoke(entry, new Object[0]);
        if (tags == null || tags.isEmpty()) {
            return "\uff08\u65e0\uff09";
        }
        final StringBuilder builder = new StringBuilder();
        for (final Iterator it = tags.iterator(); it.hasNext();) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(it.next());
        }
        return builder.toString();
    }

    private BufferedWriter openWriter(File file) throws Exception {
        return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
    }

    private void closeWriter(BufferedWriter writer) {
        if (writer != null) {
            try {
                writer.close();
            }
            catch (final Exception e) {
            }
        }
    }
}
