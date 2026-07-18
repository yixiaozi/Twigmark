package org.docear.plugin.ai.prompt;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.docear.plugin.ai.DocearAiConfig;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;
import org.freeplane.core.util.WorkspaceSideTabScanCache;

/**
 * 导出导图库文件索引（压缩相对路径），供 AI 快速定位关联文件。
 */
public final class AiWorkspaceFileIndexCollector {

    private static final Object CACHE_LOCK = new Object();
    private static String cachedIndex;
    private static long cachedAt;
    private static long cachedFingerprint;
    private static boolean cachedAllFilesMode;

    private AiWorkspaceFileIndexCollector() {
    }

    public static String collectWorkspaceFileIndex(AiPromptBuildProgress progress, String userQuestion) {
        return collectWorkspaceFileIndex(new DocearAiConfig(), progress, userQuestion);
    }

    static String collectWorkspaceFileIndex(DocearAiConfig config, AiPromptBuildProgress progress,
            String userQuestion) {
        long started = System.currentTimeMillis();
        if (!config.isWorkspaceFileIndexEnabled()) {
            return "\uff08\u5df2\u5173\u95ed\u5bfc\u56fe\u5e93\u6587\u4ef6\u7d22\u5f15\uff09";
        }
        boolean allFiles = config.isWorkspaceFileIndexAllFiles();
        String cached = getCachedIndex(config, allFiles);
        if (cached != null) {
            report(progress, 5, 6, "\u4f7f\u7528\u7f13\u5b58\u7684\u5bfc\u56fe\u5e93\u6587\u4ef6\u7d22\u5f15");
            return prioritizeForQuestion(cached, userQuestion, config.getMaxWorkspaceFileIndexChars());
        }

        report(progress, 5, 6, "\u751f\u6210\u5bfc\u56fe\u5e93\u6587\u4ef6\u7d22\u5f15\uff08\u538b\u7f29\u8def\u5f84\uff09...");
        List files = resolveFiles(allFiles);
        if (files.isEmpty()) {
            return "\uff08\u672a\u626b\u63cf\u5230\u5bfc\u56fe\u5e93\u6587\u4ef6\uff09";
        }

        String index = formatCompressedIndex(files, config);
        putCachedIndex(config, allFiles, files, index);
        LogUtils.info("AI workspace file index: " + files.size() + " files, " + index.length() + " chars in "
                + (System.currentTimeMillis() - started) + " ms.");
        return prioritizeForQuestion(index, userQuestion, config.getMaxWorkspaceFileIndexChars());
    }

    private static List resolveFiles(boolean allFiles) {
        List snapshot = allFiles ? WorkspaceSideTabScanCache.getAllFilesSnapshot()
                : WorkspaceSideTabScanCache.getMindMapFilesSnapshot();
        if (snapshot != null && !snapshot.isEmpty()) {
            return snapshot;
        }
        List files = new ArrayList();
        if (allFiles) {
            collectAllFilesFallback(files);
        }
        else {
            MindMapDataRootResolver.collectMindmapFiles(files);
        }
        return files;
    }

    private static void collectAllFilesFallback(List files) {
        java.util.Set seen = new java.util.HashSet();
        File[] roots = MindMapDataRootResolver.getScanRoots();
        for (int i = 0; i < roots.length; i++) {
            if (roots[i] != null && roots[i].exists()) {
                collectAllFilesRecursive(roots[i], files, seen);
            }
        }
    }

    private static void collectAllFilesRecursive(File dir, List result, java.util.Set seen) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (int i = 0; i < children.length; i++) {
            File child = children[i];
            if (child.isDirectory()) {
                String name = child.getName();
                if (!name.startsWith(".")
                        && !org.freeplane.core.util.MindMapDataRootResolver.isConfigDirectoryName(name)) {
                    collectAllFilesRecursive(child, result, seen);
                }
            }
            else if (child.isFile() && isIndexableFile(child)) {
                String key = child.getAbsolutePath();
                if (seen.add(key)) {
                    result.add(child);
                }
            }
        }
    }

    private static boolean isIndexableFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return false;
        }
        String name = file.getName();
        if (name.startsWith("~") || name.contains("\u51b2\u7a81\u526f\u672c")) {
            return false;
        }
        return true;
    }

    private static String formatCompressedIndex(List files, DocearAiConfig config) {
        int maxItems = config.getMaxWorkspaceFileIndexItemsPerSection();
        int maxChars = config.getMaxWorkspaceFileIndexChars();
        Map rootGroups = groupByScanRoot(files);
        StringBuilder sb = new StringBuilder();
        sb.append("\u683c\u5f0f\uff1a\u884c\u9996 @ \u4e3a\u6839\u76ee\u5f55\u7edd\u5bf9\u8def\u5f84\uff1c\u4e0b\u65b9\u4e3a\u76f8\u5bf9\u8def\u5f84\uff08/\u5206\u9694\uff09\uff1b");
        sb.append("\u540c\u76ee\u5f55\u591a\u6587\u4ef6\u7528\u300c\u76ee\u5f55/\u300d\u884c + \u300c  \u6587\u4ef6\u300d\u5217\u51fa\u3002\n");
        sb.append("\u5168\u91cf\u6587\u4ef6\u6570\uff1a").append(files.size()).append('\n');

        int totalListed = 0;
        int rootIndex = 0;
        for (Iterator it = rootGroups.entrySet().iterator(); it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();
            RootGroup group = (RootGroup) entry.getValue();
            if (group.relativePaths.isEmpty()) {
                continue;
            }
            rootIndex++;
            if (sb.length() >= maxChars) {
                break;
            }
            if (group.root == null) {
                sb.append("\n@(\u672a\u5339\u914d\u6839\u76ee\u5f55) (").append(group.relativePaths.size()).append(")\n");
            }
            else {
                sb.append("\n@").append(group.root.getAbsolutePath()).append(" (").append(group.relativePaths.size())
                        .append(")\n");
            }
            totalListed += appendCompressedPathsForRoot(sb, group.relativePaths, maxItems - totalListed, maxChars
                    - sb.length());
            if (totalListed >= maxItems) {
                break;
            }
        }

        if (totalListed < files.size()) {
            sb.append("\n[\u5df2\u622a\u65ad\uff0c\u5171 ").append(files.size()).append(" \u4e2a\u6587\u4ef6\uff0c\u672c\u7d22\u5f15\u5c55\u793a ")
                    .append(totalListed).append(" \u6761]");
        }
        if (sb.length() > maxChars) {
            return sb.substring(0, maxChars) + "\n[\u6587\u4ef6\u7d22\u5f15\u8fc7\u957f\uff0c\u5df2\u622a\u65ad]";
        }
        return sb.toString().trim();
    }

    private static int appendCompressedPathsForRoot(StringBuilder sb, List relativePaths, int maxItems,
            int maxCharsRemaining) {
        Collections.sort(relativePaths);
        Map dirBuckets = new LinkedHashMap();
        List looseFiles = new ArrayList();
        for (int i = 0; i < relativePaths.size(); i++) {
            String path = normalizeRelativePath((String) relativePaths.get(i));
            int slash = path.lastIndexOf('/');
            if (slash > 0) {
                String dir = path.substring(0, slash + 1);
                String name = path.substring(slash + 1);
                List bucket = (List) dirBuckets.get(dir);
                if (bucket == null) {
                    bucket = new ArrayList();
                    dirBuckets.put(dir, bucket);
                }
                bucket.add(name);
            }
            else {
                looseFiles.add(path);
            }
        }

        int listed = 0;
        List dirs = new ArrayList(dirBuckets.keySet());
        Collections.sort(dirs);
        for (int d = 0; d < dirs.size() && listed < maxItems && maxCharsRemaining > 80; d++) {
            String dir = (String) dirs.get(d);
            List names = (List) dirBuckets.get(dir);
            Collections.sort(names);
            if (names.size() >= 2) {
                sb.append(dir).append('\n');
                for (int n = 0; n < names.size() && listed < maxItems; n++) {
                    sb.append("  ").append(names.get(n)).append('\n');
                    listed++;
                }
            }
            else {
                sb.append(dir).append(names.get(0)).append('\n');
                listed++;
            }
            maxCharsRemaining = maxCharsRemaining - 40;
        }
        for (int i = 0; i < looseFiles.size() && listed < maxItems; i++) {
            sb.append(looseFiles.get(i)).append('\n');
            listed++;
        }
        return listed;
    }

    private static Map groupByScanRoot(List files) {
        File[] roots = MindMapDataRootResolver.getScanRoots();
        Map groups = new LinkedHashMap();
        for (int i = 0; i < roots.length; i++) {
            if (roots[i] != null) {
                groups.put(roots[i].getAbsolutePath(), new RootGroup(roots[i]));
            }
        }
        RootGroup fallback = new RootGroup(null);

        for (int i = 0; i < files.size(); i++) {
            File file = (File) files.get(i);
            if (!isIndexableFile(file)) {
                continue;
            }
            RootGroup group = findRootGroup(file, roots, groups);
            String relative = toRelativePath(file, group != null ? group.root : null);
            if (group == null) {
                fallback.relativePaths.add(file.getAbsolutePath());
            }
            else if (relative != null) {
                group.relativePaths.add(relative);
            }
            else {
                group.relativePaths.add(file.getName());
            }
        }
        if (!fallback.relativePaths.isEmpty()) {
            groups.put("__absolute__", fallback);
        }
        return groups;
    }

    private static RootGroup findRootGroup(File file, File[] roots, Map groups) {
        String path = file.getAbsolutePath();
        File best = null;
        int bestLen = -1;
        for (int i = 0; i < roots.length; i++) {
            File root = roots[i];
            if (root == null) {
                continue;
            }
            String rootPath = root.getAbsolutePath();
            if (path.equals(rootPath) || path.startsWith(rootPath + File.separator)) {
                if (rootPath.length() > bestLen) {
                    best = root;
                    bestLen = rootPath.length();
                }
            }
        }
        if (best == null) {
            return null;
        }
        return (RootGroup) groups.get(best.getAbsolutePath());
    }

    private static String toRelativePath(File file, File root) {
        if (root == null) {
            return null;
        }
        String rel = MindMapDataRootResolver.getRelativePathWithinScanRoots(file.getParentFile());
        if (rel == null) {
            try {
                String filePath = file.getCanonicalPath();
                String rootPath = root.getCanonicalPath();
                if (filePath.startsWith(rootPath + File.separator)) {
                    rel = filePath.substring(rootPath.length() + 1);
                }
            }
            catch (Exception e) {
                return file.getName();
            }
        }
        else if (rel.length() == 0) {
            rel = file.getName();
        }
        else {
            rel = rel + "/" + file.getName();
        }
        return normalizeRelativePath(rel);
    }

    private static String normalizeRelativePath(String path) {
        if (path == null) {
            return "";
        }
        return path.replace('\\', '/');
    }

    private static String prioritizeForQuestion(String index, String question, int maxChars) {
        if (index == null || question == null || question.trim().length() < 2) {
            return truncate(index, maxChars);
        }
        List terms = extractQuestionTerms(question);
        if (terms.isEmpty()) {
            return truncate(index, maxChars);
        }
        String[] lines = index.split("\n");
        List matched = new ArrayList();
        List others = new ArrayList();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("\u683c\u5f0f\uff1a") || line.startsWith("\u5168\u91cf") || line.startsWith("@")
                    || line.startsWith("[") || line.trim().length() == 0) {
                matched.add(line);
                continue;
            }
            if (matchesAnyTerm(line, terms)) {
                matched.add(line);
            }
            else {
                others.add(line);
            }
        }
        if (matched.size() <= 3) {
            return truncate(index, maxChars);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < matched.size(); i++) {
            sb.append(matched.get(i)).append('\n');
        }
        sb.append("# \u5176\u4ed6\u6587\u4ef6\n");
        for (int i = 0; i < others.size(); i++) {
            sb.append(others.get(i)).append('\n');
        }
        return truncate(sb.toString().trim(), maxChars);
    }

    private static List extractQuestionTerms(String question) {
        List terms = new ArrayList();
        String q = question.trim();
        for (int i = 0; i < q.length(); i++) {
            if (q.charAt(i) < 0x4E00 || q.charAt(i) > 0x9FFF) {
                continue;
            }
            for (int len = 4; len >= 2; len--) {
                if (i + len <= q.length()) {
                    String candidate = q.substring(i, i + len);
                    if (terms.contains(candidate)) {
                        continue;
                    }
                    terms.add(candidate);
                    i += len - 1;
                    break;
                }
            }
        }
        return terms;
    }

    private static boolean matchesAnyTerm(String text, List terms) {
        if (text == null) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ENGLISH);
        for (int i = 0; i < terms.size(); i++) {
            String term = ((String) terms.get(i)).toLowerCase(Locale.ENGLISH);
            if (normalized.indexOf(term) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static String truncate(String content, int maxChars) {
        if (content == null || content.length() <= maxChars) {
            return content != null ? content : "";
        }
        return content.substring(0, maxChars) + "\n[\u6587\u4ef6\u7d22\u5f15\u8fc7\u957f\uff0c\u5df2\u622a\u65ad]";
    }

    private static void report(AiPromptBuildProgress progress, int step, int total, String label) {
        if (progress != null) {
            progress.onStep(step, total, label);
        }
    }

    private static long computeFingerprint(List files) {
        long hash = files.size();
        int sample = Math.min(files.size(), 32);
        for (int i = 0; i < sample; i++) {
            File file = (File) files.get(i);
            hash = hash * 31 + file.lastModified();
        }
        return hash;
    }

    private static String getCachedIndex(DocearAiConfig config, boolean allFiles) {
        synchronized (CACHE_LOCK) {
            if (cachedIndex == null || cachedAllFilesMode != allFiles) {
                return null;
            }
            long age = System.currentTimeMillis() - cachedAt;
            if (age > config.getWorkspaceFileIndexCacheTtlMs()) {
                return null;
            }
            return cachedIndex;
        }
    }

    private static void putCachedIndex(DocearAiConfig config, boolean allFiles, List files, String index) {
        synchronized (CACHE_LOCK) {
            cachedIndex = index;
            cachedAt = System.currentTimeMillis();
            cachedFingerprint = computeFingerprint(files);
            cachedAllFilesMode = allFiles;
        }
    }

    private static final class RootGroup {
        private final File root;
        private final List relativePaths = new ArrayList();

        private RootGroup(File root) {
            this.root = root;
        }
    }
}
