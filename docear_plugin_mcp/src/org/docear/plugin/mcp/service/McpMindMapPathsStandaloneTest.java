package org.docear.plugin.mcp.service;

import java.io.File;

/**
 * Headless checks: create_mindmap paths stay inside the library root.
 */
public final class McpMindMapPathsStandaloneTest {
	private McpMindMapPathsStandaloneTest() {
	}

	public static void main(final String[] args) throws Exception {
		final File root = new File("/tmp/twigmark-library-root").getCanonicalFile();
		root.mkdirs();
		final File[] roots = new File[] { root };

		final File relative = McpMindMapPaths.resolveCreateTarget(roots, "09记录存档/相册整理/相册日记.mm");
		final File expected = new File(root, "09记录存档/相册整理/相册日记.mm").getCanonicalFile();
		if (!expected.equals(relative)) {
			throw new IllegalStateException("relative: " + relative + " expected " + expected);
		}

		final File absInside = McpMindMapPaths.resolveCreateTarget(roots, expected.getAbsolutePath());
		if (!expected.equals(absInside)) {
			throw new IllegalStateException("abs inside: " + absInside);
		}

		try {
			McpMindMapPaths.resolveCreateTarget(roots, "/tmp/outside/evil.mm");
			throw new IllegalStateException("outside path must fail");
		}
		catch (IllegalArgumentException e) {
			if (e.getMessage() == null || e.getMessage().indexOf("inside the mind map library") < 0) {
				throw new IllegalStateException("outside message: " + e.getMessage());
			}
		}

		try {
			McpMindMapPaths.resolveCreateTarget(roots, "../escape.mm");
			throw new IllegalStateException("escape path must fail");
		}
		catch (IllegalArgumentException e) {
			if (e.getMessage() == null || e.getMessage().indexOf("inside the mind map library") < 0) {
				throw new IllegalStateException("escape message: " + e.getMessage());
			}
		}

		try {
			McpMindMapPaths.resolveCreateTarget(roots, "notes.txt");
			throw new IllegalStateException(".mm required");
		}
		catch (IllegalArgumentException e) {
			if (e.getMessage() == null || e.getMessage().indexOf(".mm") < 0) {
				throw new IllegalStateException("mm message: " + e.getMessage());
			}
		}

		if (!McpMindMapPaths.isUnderRoot(expected, root)) {
			throw new IllegalStateException("isUnderRoot");
		}
		System.out.println("McpMindMapPathsStandaloneTest OK");
	}
}
