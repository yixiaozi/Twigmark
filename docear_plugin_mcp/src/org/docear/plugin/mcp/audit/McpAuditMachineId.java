package org.docear.plugin.mcp.audit;

import org.freeplane.core.util.LocalMachineId;
import org.freeplane.core.util.LogUtils;

/**
 * Machine identity for MCP audit DB filenames. Delegates to {@link LocalMachineId}
 * (sticky OS-local id; safe when mind-map {@code data/} is synced).
 */
public final class McpAuditMachineId {

	private McpAuditMachineId() {
	}

	/** Stable id, e.g. {@code mac-aabbccddeeff}. */
	public static String getMachineId() {
		return LocalMachineId.getId();
	}

	/** Hostname (display only). */
	public static String getMachineName() {
		return LocalMachineId.getHostName();
	}

	/** Lowercase hex fragment without separators, e.g. {@code aabbccddeeff}. */
	public static String getMacHex() {
		return LocalMachineId.getMacHex();
	}

	/** Test helper: force id/name/mac without probing NICs. */
	static void setForTests(final String machineId, final String machineName) {
		LocalMachineId.setForTests(machineId, machineName);
		LogUtils.info("McpAuditMachineId test override → " + LocalMachineId.getId());
	}

	static void resetForTests() {
		LocalMachineId.resetForTests();
	}
}
