package org.freeplane.core.util;

/**
 * System-property flags for the headless MCP server.
 * <p>
 * Core and UI modules must not depend on the MCP plugin, so these live here.
 * VPS startup sets {@code -Dmcp.lowMemory=true} (and already sets
 * {@code -Dmcp.skipFullTagScan=true}).
 */
public final class McpHeadlessFlags {
	private McpHeadlessFlags() {
	}

	public static boolean isHeadlessMcp() {
		return isTrue("mcp.headless") || "headless".equalsIgnoreCase(System.getProperty("mcp.mode", ""));
	}

	/**
	 * Smaller caches and skip full-library warmup indexes. Desktop GUI is
	 * unchanged unless these MCP server properties are set.
	 */
	public static boolean isLeanMemory() {
		return isTrue("mcp.lowMemory") || isTrue("mcp.skipFullTagScan");
	}

	public static boolean isTrue(final String property) {
		if (property == null || property.length() == 0) {
			return false;
		}
		final String value = System.getProperty(property, "");
		return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
	}
}
