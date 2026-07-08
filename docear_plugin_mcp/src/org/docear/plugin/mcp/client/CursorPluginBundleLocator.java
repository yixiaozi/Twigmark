package org.docear.plugin.mcp.client;

import java.io.File;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.LogUtils;

public final class CursorPluginBundleLocator {

	private CursorPluginBundleLocator() {
	}

	public static File getBundledCursorPluginDir() {
		try {
			final String resourceBaseDir = ResourceController.getResourceController().getResourceBaseDir();
			if (resourceBaseDir != null && resourceBaseDir.length() > 0) {
				final File installRoot = new File(resourceBaseDir).getAbsoluteFile().getParentFile();
				if (installRoot != null) {
					final File bundled = new File(installRoot, "plugins/org.docear.plugin.mcp/cursor-plugin");
					if (bundled.isDirectory()) {
						return bundled;
					}
				}
			}
		}
		catch (Exception e) {
			LogUtils.warn("Cursor plugin bundle lookup failed: " + e.getMessage());
		}
		return null;
	}
}
