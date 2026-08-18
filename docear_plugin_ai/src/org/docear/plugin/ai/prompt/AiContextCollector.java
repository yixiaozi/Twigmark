package org.docear.plugin.ai.prompt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.docear.plugin.ai.DocearAiConfig;
import org.freeplane.core.util.HtmlUtils;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.link.NodeLinks;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;

/**
 * 在 Docear 进程内尽可能全面地收集导图及关联文件内容。
 */
public final class AiContextCollector {

    private static final Pattern WINDOWS_PATH = Pattern.compile("[A-Za-z]:[\\\\/][^\\s<>\"|?*\\n\\r]{2,}");
    private static final Pattern UNIX_PATH = Pattern.compile("(?:/[\\w.$~_-]+)+");
    private static final String[] TEXT_EXTENSIONS = new String[] {
            ".mm", ".txt", ".md", ".markdown", ".java", ".py", ".js", ".ts", ".json", ".xml",
            ".properties", ".bib", ".csv", ".html", ".htm", ".yaml", ".yml", ".sql", ".ini", ".cfg", ".log"
    };
    private static final int MAX_STRUCTURE_CHARS = 60000;
    private static final int MAX_STRUCTURE_NODES = 800;
    private static final int MAX_FILE_SCAN_NODES = 600;

    private AiContextCollector() {
    }

    public static ContextBundle collect(MapModel map, String userQuestion) {
        return collect(map, userQuestion, null);
    }

    public static ContextBundle collect(MapModel map, String userQuestion, AiPromptBuildProgress progress) {
        return collect(map, userQuestion, progress, false);
    }

    public static ContextBundle collect(MapModel map, String userQuestion, AiPromptBuildProgress progress,
            boolean skipLinkedFiles) {
        DocearAiConfig config = new DocearAiConfig();
        int maxFiles = config.getMaxLinkedFiles();
        int maxFileBytes = config.getMaxFileSizeBytes();
        int maxTotalChars = config.getMaxTotalContextChars();

        report(progress, 1, 6, "\u8bfb\u53d6\u5f53\u524d\u5bfc\u56fe\u7ed3\u6784...");
        String mapStructure = extractMapStructure(map);
        File mapFile = map != null ? map.getFile() : null;
        File promptTemplate = new File(config.getPromptTemplateFile());
        if (skipLinkedFiles) {
            report(progress, 2, 6, "\u7b80\u5355\u95ee\u9898\uff0c\u8df3\u8fc7\u5173\u8054\u6587\u4ef6...");
            return new ContextBundle(mapStructure, "", 0, 0);
        }
        Set<File> files = collectReferencedFiles(map, userQuestion, mapFile, promptTemplate);
        StringBuilder referenced = new StringBuilder();
        int totalChars = mapStructure.length();
        int filesIncluded = 0;
        int fileIndex = 0;

        for (File file : files) {
            if (filesIncluded >= maxFiles || totalChars >= maxTotalChars) {
                break;
            }
            fileIndex++;
            report(progress, 2, 6, "\u8bfb\u53d6\u5173\u8054\u6587\u4ef6 (" + fileIndex + "/" + files.size()
                    + "): " + file.getName());
            String content = readTextFile(file, maxFileBytes);
            if (content == null || content.trim().length() == 0) {
                continue;
            }
            if (totalChars + content.length() > maxTotalChars) {
                int remaining = maxTotalChars - totalChars;
                if (remaining <= 200) {
                    break;
                }
                content = content.substring(0, remaining)
                        + "\n[\u5185\u5bb9\u8fc7\u957f\uff0c\u5df2\u622a\u65ad]";
            }
            if (referenced.length() > 0) {
                referenced.append("\n\n");
            }
            referenced.append("=== \u6587\u4ef6: ").append(file.getAbsolutePath()).append(" ===\n");
            referenced.append(content);
            totalChars += content.length();
            filesIncluded++;
        }

        if (files.size() > filesIncluded) {
            referenced.append("\n\n[\u8fd8\u6709 ").append(files.size() - filesIncluded)
                    .append(" \u4e2a\u5173\u8054\u6587\u4ef6\u672a\u5c55\u5f00\uff0c\u5df2\u8fbe\u4e0a\u9650]");
        }

        return new ContextBundle(mapStructure, referenced.toString(), filesIncluded, files.size());
    }

    private static void report(AiPromptBuildProgress progress, int step, int total, String label) {
        if (progress != null) {
            progress.onStep(step, total, label);
        }
    }

    private static Set<File> collectReferencedFiles(MapModel map, String userQuestion, File mapFile,
            File promptTemplate) {
        LinkedHashSet<File> files = new LinkedHashSet<File>();
        File baseDir = mapFile != null ? mapFile.getParentFile() : null;

        if (map != null) {
            collectFilesFromNode(map.getRootNode(), baseDir, files, mapFile, promptTemplate);
        }
        collectPathsFromText(userQuestion, baseDir, files, mapFile, promptTemplate);

        return files;
    }

    private static void collectFilesFromNode(NodeModel node, File baseDir, Set<File> files, File mapFile,
            File promptTemplate) {
        collectFilesFromNode(node, baseDir, files, mapFile, promptTemplate, new int[] { MAX_FILE_SCAN_NODES });
    }

    private static void collectFilesFromNode(NodeModel node, File baseDir, Set<File> files, File mapFile,
            File promptTemplate, int[] remaining) {
        if (node == null || remaining[0] <= 0) {
            return;
        }
        remaining[0]--;
        URI link = NodeLinks.getValidLink(node);
        addFileFromUri(link, baseDir, files, mapFile, promptTemplate);

        collectPathsFromText(safePlainText(node), baseDir, files, mapFile, promptTemplate);

        List<NodeModel> children = node.getChildren();
        for (int i = 0; i < children.size(); i++) {
            if (remaining[0] <= 0) {
                break;
            }
            collectFilesFromNode(children.get(i), baseDir, files, mapFile, promptTemplate, remaining);
        }
    }

    private static void collectPathsFromText(String text, File baseDir, Set<File> files, File mapFile,
            File promptTemplate) {
        if (text == null || text.trim().length() == 0) {
            return;
        }
        Matcher windowsMatcher = WINDOWS_PATH.matcher(text);
        while (windowsMatcher.find()) {
            addCandidateFile(new File(windowsMatcher.group()), baseDir, files, mapFile, promptTemplate);
        }
        Matcher unixMatcher = UNIX_PATH.matcher(text);
        while (unixMatcher.find()) {
            addCandidateFile(new File(unixMatcher.group()), baseDir, files, mapFile, promptTemplate);
        }
    }

    private static void addFileFromUri(URI uri, File baseDir, Set<File> files, File mapFile, File promptTemplate) {
        if (uri == null) {
            return;
        }
        try {
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                addCandidateFile(new File(uri), baseDir, files, mapFile, promptTemplate);
            } else {
                String raw = uri.toString();
                if (raw.length() > 2 && raw.charAt(1) == ':') {
                    addCandidateFile(new File(raw), baseDir, files, mapFile, promptTemplate);
                } else if (raw.startsWith("/") || raw.matches("[A-Za-z]:.*")) {
                    addCandidateFile(new File(raw), baseDir, files, mapFile, promptTemplate);
                }
            }
        } catch (Exception e) {
            LogUtils.warn("Failed to resolve link: " + uri);
        }
    }

    private static void addCandidateFile(File candidate, File baseDir, Set<File> files, File mapFile,
            File promptTemplate) {
        if (candidate == null) {
            return;
        }
        File resolved = candidate;
        if (!resolved.isAbsolute() && baseDir != null) {
            resolved = new File(baseDir, candidate.getPath());
        }
        if (!resolved.exists() || !resolved.isFile() || !isReadableTextFile(resolved)) {
            return;
        }
        resolved = resolved.getAbsoluteFile();
        if (shouldSkipReferencedFile(resolved, mapFile, promptTemplate)) {
            return;
        }
        files.add(resolved);
    }

    private static boolean shouldSkipReferencedFile(File file, File mapFile, File promptTemplate) {
        if (mapFile != null) {
            try {
                if (file.equals(mapFile.getAbsoluteFile())) {
                    return true;
                }
            }
            catch (Exception e) {
                if (file.getAbsolutePath().equalsIgnoreCase(mapFile.getAbsolutePath())) {
                    return true;
                }
            }
        }
        if (promptTemplate != null) {
            try {
                if (file.equals(promptTemplate.getAbsoluteFile())) {
                    return true;
                }
            }
            catch (Exception e) {
                if (file.getAbsolutePath().equalsIgnoreCase(promptTemplate.getAbsolutePath())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String extractMapStructure(MapModel map) {
        if (map == null) {
            return "\uff08\u672a\u6253\u5f00\u601d\u7ef4\u5bfc\u56fe\uff09";
        }
        NodeModel root = map.getRootNode();
        if (root == null) {
            return "\uff08\u5bfc\u56fe\u65e0\u6839\u8282\u70b9\uff09";
        }
        StringBuilder sb = new StringBuilder();
        appendNode(root, sb, 0, new int[] { MAX_STRUCTURE_NODES });
        return truncate(sb.toString(), MAX_STRUCTURE_CHARS);
    }

    private static String safePlainText(NodeModel node) {
        if (node == null) {
            return "";
        }
        try {
            Object user = node.getUserObject();
            if (user == null) {
                return "";
            }
            String raw = user instanceof String ? (String) user : String.valueOf(user);
            if (HtmlUtils.isHtmlNode(raw)) {
                return HtmlUtils.htmlToPlain(raw);
            }
            return raw;
        }
        catch (Exception e) {
            return "";
        }
    }

    private static void appendNode(NodeModel node, StringBuilder sb, int depth, int[] remaining) {
        if (node == null || remaining[0] <= 0 || sb.length() >= MAX_STRUCTURE_CHARS) {
            return;
        }
        remaining[0]--;
        String text = safePlainText(node).trim();
        URI link = NodeLinks.getValidLink(node);
        if (text.length() > 0 || link != null) {
            for (int i = 0; i < depth; i++) {
                sb.append("  ");
            }
            sb.append("- ");
            if (text.length() > 0) {
                sb.append(text);
            }
            if (link != null) {
                if (text.length() > 0) {
                    sb.append(" ");
                }
                sb.append("[\u94fe\u63a5: ").append(link.toString()).append(']');
            }
            sb.append('\n');
        }
        List<NodeModel> children = node.getChildren();
        for (int i = 0; i < children.size(); i++) {
            if (remaining[0] <= 0 || sb.length() >= MAX_STRUCTURE_CHARS) {
                break;
            }
            appendNode(children.get(i), sb, depth + 1, remaining);
        }
    }

    private static boolean isReadableTextFile(File file) {
        String name = file.getName().toLowerCase();
        for (int i = 0; i < TEXT_EXTENSIONS.length; i++) {
            if (name.endsWith(TEXT_EXTENSIONS[i])) {
                return true;
            }
        }
        return false;
    }

    private static String readTextFile(File file, int maxBytes) {
        BufferedReader reader = null;
        try {
            long size = file.length();
            if (size > maxBytes) {
                return readHead(file, maxBytes) + "\n[\u6587\u4ef6\u8fc7\u5927\uff0c\u5df2\u8bfb\u53d6\u524d " + maxBytes + " \u5b57\u8282]";
            }
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            LogUtils.warn("Failed to read file for AI context: " + file + ", " + e.getMessage());
            return "[\u65e0\u6cd5\u8bfb\u53d6\u6587\u4ef6: " + file.getAbsolutePath() + "]";
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static String readHead(File file, int maxBytes) throws Exception {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            char[] buffer = new char[maxBytes];
            int read = reader.read(buffer, 0, maxBytes);
            if (read <= 0) {
                return "";
            }
            return new String(buffer, 0, read);
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    private static String truncate(String content, int maxLength) {
        if (content == null || content.length() <= maxLength) {
            return content != null ? content : "";
        }
        return content.substring(0, maxLength)
                + "\n\n[\u5185\u5bb9\u8fc7\u957f\uff0c\u5df2\u622a\u65ad\u81f3 " + maxLength + " \u5b57\u7b26]";
    }

    public static final class ContextBundle {
        private final String mapStructure;
        private final String referencedFiles;
        private final int filesIncluded;
        private final int filesDiscovered;

        public ContextBundle(String mapStructure, String referencedFiles, int filesIncluded, int filesDiscovered) {
            this.mapStructure = mapStructure;
            this.referencedFiles = referencedFiles;
            this.filesIncluded = filesIncluded;
            this.filesDiscovered = filesDiscovered;
        }

        public String getMapStructure() {
            return mapStructure;
        }

        public String getReferencedFiles() {
            return referencedFiles;
        }

        public String getCombinedMapContent() {
            StringBuilder sb = new StringBuilder();
            sb.append("--- \u601d\u7ef4\u5bfc\u56fe\u7ed3\u6784 ---\n");
            sb.append(mapStructure);
            if (referencedFiles != null && referencedFiles.trim().length() > 0) {
                sb.append("\n\n--- \u5173\u8054\u6587\u4ef6\u5185\u5bb9 ---\n");
                sb.append(referencedFiles);
            }
            return sb.toString();
        }

        public int getFilesIncluded() {
            return filesIncluded;
        }

        public int getFilesDiscovered() {
            return filesDiscovered;
        }
    }
}
