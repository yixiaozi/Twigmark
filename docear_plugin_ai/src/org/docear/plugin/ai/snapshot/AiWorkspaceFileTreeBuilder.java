package org.docear.plugin.ai.snapshot;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.freeplane.core.util.MindMapDataRootResolver;

/**
 * 生成工作区完整文件目录树（相对数据根目录）。
 */
public final class AiWorkspaceFileTreeBuilder {

    private static final class TreeNode {
        private final Map subdirs = new TreeMap();
        private final List files = new ArrayList();
    }

    private AiWorkspaceFileTreeBuilder() {
    }

    public static int countWorkspaceFiles(final File dataRoot) {
        return collectWorkspaceFiles(dataRoot).size();
    }

    public static String buildTree(final File dataRoot) {
        if (dataRoot == null || !dataRoot.isDirectory()) {
            return "";
        }
        final List files = collectWorkspaceFiles(dataRoot);
        final TreeNode root = new TreeNode();
        for (int i = 0; i < files.size(); i++) {
            addRelativePath(root, toRelativePath(dataRoot, (File) files.get(i)));
        }
        final StringBuilder sb = new StringBuilder();
        sb.append(dataRoot.getName()).append("/\n");
        renderNode(root, "", sb);
        return sb.toString();
    }

    private static List collectWorkspaceFiles(final File dataRoot) {
        final List result = new ArrayList();
        final Set seen = new HashSet();
        final File[] roots = MindMapDataRootResolver.getScanRoots();
        for (int i = 0; i < roots.length; i++) {
            if (roots[i] != null && roots[i].exists()) {
                collectRecursive(roots[i], result, seen);
            }
        }
        if (result.isEmpty() && dataRoot != null && dataRoot.exists()) {
            collectRecursive(dataRoot, result, seen);
        }
        return result;
    }

    private static boolean isSkippedDirectory(final String name) {
        if (name == null || name.length() == 0) {
            return true;
        }
        return org.freeplane.core.util.MindMapDataRootResolver.isConfigDirectoryName(name)
                || "bin".equalsIgnoreCase(name) || ".git".equalsIgnoreCase(name);
    }

    private static void collectRecursive(final File dir, final List result, final Set seen) {
        final File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (int i = 0; i < children.length; i++) {
            final File child = children[i];
            if (child.isDirectory()) {
                if (isSkippedDirectory(child.getName())) {
                    continue;
                }
                collectRecursive(child, result, seen);
            }
            else if (child.isFile() && isIncludedFile(child)) {
                final String key = child.getAbsolutePath();
                if (seen.add(key)) {
                    result.add(child);
                }
            }
        }
    }

    private static boolean isIncludedFile(final File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        final String name = file.getName();
        if (name.startsWith("~") || name.contains("\u51b2\u7a81\u526f\u672c")) {
            return false;
        }
        return true;
    }

    private static String toRelativePath(final File dataRoot, final File file) {
        if (dataRoot == null || file == null) {
            return file != null ? file.getName() : "";
        }
        try {
            final String rootPath = dataRoot.getCanonicalPath();
            final String filePath = file.getCanonicalPath();
            if (filePath.equals(rootPath)) {
                return file.getName();
            }
            final String prefix = rootPath + File.separator;
            if (filePath.startsWith(prefix)) {
                return filePath.substring(prefix.length()).replace('\\', '/');
            }
        }
        catch (final Exception e) {
        }
        return MindMapDataRootResolver.getRelativePathWithinScanRoots(file.getParentFile()) + "/" + file.getName();
    }

    private static void addRelativePath(final TreeNode root, final String relativePath) {
        if (relativePath == null || relativePath.length() == 0) {
            return;
        }
        final String normalized = relativePath.replace('\\', '/');
        final String[] parts = normalized.split("/");
        TreeNode current = root;
        for (int i = 0; i < parts.length; i++) {
            final String part = parts[i];
            if (part == null || part.length() == 0) {
                continue;
            }
            if (i == parts.length - 1) {
                if (!current.files.contains(part)) {
                    current.files.add(part);
                }
            }
            else {
                TreeNode next = (TreeNode) current.subdirs.get(part);
                if (next == null) {
                    next = new TreeNode();
                    current.subdirs.put(part, next);
                }
                current = next;
            }
        }
    }

    private static void renderNode(final TreeNode node, final String prefix, final StringBuilder sb) {
        final List childNames = new ArrayList();
        childNames.addAll(node.subdirs.keySet());
        childNames.addAll(node.files);
        Collections.sort(childNames);
        for (int i = 0; i < childNames.size(); i++) {
            final String name = (String) childNames.get(i);
            final boolean last = i == childNames.size() - 1;
            final String branch = last ? "\u2514\u2500\u2500 " : "\u251c\u2500\u2500 ";
            final String nextPrefix = prefix + (last ? "    " : "\u2502   ");
            final TreeNode subdir = (TreeNode) node.subdirs.get(name);
            if (subdir != null) {
                sb.append(prefix).append(branch).append(name).append("/\n");
                renderNode(subdir, nextPrefix, sb);
            }
            else {
                sb.append(prefix).append(branch).append(name).append("\n");
            }
        }
    }
}
