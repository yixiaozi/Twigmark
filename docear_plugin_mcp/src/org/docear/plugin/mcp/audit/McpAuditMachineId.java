package org.docear.plugin.mcp.audit;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.freeplane.core.util.LogUtils;

/**
 * Machine identity derived from the primary NIC MAC address (no on-disk id file).
 * Safe for synced data folders: each PC keeps its own {@code audit-&lt;mac&gt;.db}.
 */
public final class McpAuditMachineId {

	private static final Object LOCK = new Object();
	private static volatile String CACHED_ID;
	private static volatile String CACHED_NAME;
	private static volatile String CACHED_MAC_HEX;

	private McpAuditMachineId() {
	}

	/** Stable id, e.g. {@code mac-aabbccddeeff}. */
	public static String getMachineId() {
		ensureLoaded();
		return CACHED_ID;
	}

	/** Hostname (display only). */
	public static String getMachineName() {
		ensureLoaded();
		return CACHED_NAME;
	}

	/** Lowercase hex MAC without separators, e.g. {@code aabbccddeeff}. */
	public static String getMacHex() {
		ensureLoaded();
		return CACHED_MAC_HEX;
	}

	/** Test helper: force id/name/mac without probing NICs. */
	static void setForTests(final String machineId, final String machineName) {
		synchronized (LOCK) {
			CACHED_ID = machineId;
			CACHED_NAME = machineName != null ? machineName : machineId;
			String hex = machineId != null ? machineId : "000000000000";
			if (hex.startsWith("mac-")) {
				hex = hex.substring(4);
			}
			hex = hex.replace(":", "").replace("-", "").toLowerCase();
			if (hex.length() < 12) {
				hex = (hex + "000000000000").substring(0, 12);
			}
			CACHED_MAC_HEX = hex;
		}
	}

	static void resetForTests() {
		synchronized (LOCK) {
			CACHED_ID = null;
			CACHED_NAME = null;
			CACHED_MAC_HEX = null;
		}
	}

	private static void ensureLoaded() {
		if (CACHED_ID != null && CACHED_ID.length() > 0) {
			return;
		}
		synchronized (LOCK) {
			if (CACHED_ID != null && CACHED_ID.length() > 0) {
				return;
			}
			final String macHex = detectMacHex();
			CACHED_MAC_HEX = macHex;
			CACHED_ID = "mac-" + macHex;
			CACHED_NAME = resolveHostName();
			LogUtils.info("Docear MCP audit machine id from MAC: " + CACHED_ID + " (" + CACHED_NAME + ")");
		}
	}

	private static String detectMacHex() {
		final List candidates = new ArrayList();
		try {
			final Enumeration nis = NetworkInterface.getNetworkInterfaces();
			if (nis != null) {
				while (nis.hasMoreElements()) {
					final NetworkInterface ni = (NetworkInterface) nis.nextElement();
					try {
						if (ni == null || ni.isLoopback() || ni.isVirtual() || !ni.isUp()) {
							continue;
						}
						final String name = ni.getName() != null ? ni.getName().toLowerCase() : "";
						if (name.startsWith("docker") || name.startsWith("veth") || name.startsWith("br-")
						    || name.startsWith("virbr") || name.startsWith("vmnet") || name.startsWith("vbox")) {
							continue;
						}
						final byte[] mac = ni.getHardwareAddress();
						if (mac == null || mac.length < 6) {
							continue;
						}
						if (isAllZero(mac) || isLocallyAdministeredOnly(mac)) {
							// still accept if nothing better; prefer globally administered
						}
						final String hex = toHex(mac);
						final boolean preferred = !isLocallyAdministered(mac) && !isAllZero(mac);
						candidates.add(new Object[] { Boolean.valueOf(preferred), name, hex });
					}
					catch (Exception ignored) {
					}
				}
			}
		}
		catch (Exception e) {
			LogUtils.warn("Enumerate NICs for MAC failed: " + e.getMessage(), e);
		}
		Collections.sort(candidates, new java.util.Comparator() {
			public int compare(final Object a, final Object b) {
				final Object[] x = (Object[]) a;
				final Object[] y = (Object[]) b;
				final boolean px = ((Boolean) x[0]).booleanValue();
				final boolean py = ((Boolean) y[0]).booleanValue();
				if (px != py) {
					return px ? -1 : 1;
				}
				return String.valueOf(x[1]).compareTo(String.valueOf(y[1]));
			}
		});
		if (!candidates.isEmpty()) {
			return (String) ((Object[]) candidates.get(0))[2];
		}
		// Last resort: deterministic 12-hex from hostname (still unique enough per PC name).
		final String host = resolveHostName();
		int h = host.hashCode();
		final StringBuilder sb = new StringBuilder(12);
		for (int i = 0; i < 6; i++) {
			final int b = (h >> (i * 5)) & 0xff;
			sb.append(Character.forDigit((b >> 4) & 0xf, 16));
			sb.append(Character.forDigit(b & 0xf, 16));
		}
		LogUtils.warn("No hardware MAC found; using host-hash fallback: " + sb);
		return sb.toString();
	}

	private static boolean isAllZero(final byte[] mac) {
		for (int i = 0; i < mac.length; i++) {
			if (mac[i] != 0) {
				return false;
			}
		}
		return true;
	}

	private static boolean isLocallyAdministered(final byte[] mac) {
		return mac.length > 0 && (mac[0] & 0x02) != 0;
	}

	private static boolean isLocallyAdministeredOnly(final byte[] mac) {
		return isLocallyAdministered(mac);
	}

	private static String toHex(final byte[] mac) {
		final StringBuilder sb = new StringBuilder(mac.length * 2);
		final int n = Math.min(mac.length, 6);
		for (int i = 0; i < n; i++) {
			final int b = mac[i] & 0xff;
			sb.append(Character.forDigit((b >> 4) & 0xf, 16));
			sb.append(Character.forDigit(b & 0xf, 16));
		}
		while (sb.length() < 12) {
			sb.append('0');
		}
		return sb.toString();
	}

	private static String resolveHostName() {
		try {
			final String host = InetAddress.getLocalHost().getHostName();
			if (host != null && host.trim().length() > 0) {
				return host.trim();
			}
		}
		catch (Exception ignored) {
		}
		final String env = System.getenv("COMPUTERNAME");
		if (env != null && env.trim().length() > 0) {
			return env.trim();
		}
		return System.getProperty("user.name", "pc") + "-host";
	}
}
