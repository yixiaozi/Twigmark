package org.freeplane.core.util;

import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;

/**
 * Stable per-PC identity from the primary NIC MAC (no on-disk id file — safe for synced folders).
 * Used for machine-local filenames: {@code audit-&lt;mac&gt;.db}, {@code clipboard_history-&lt;mac&gt;.db}, etc.
 */
public final class LocalMachineId {

	private static final Object LOCK = new Object();
	private static volatile String CACHED_HEX;
	private static volatile String CACHED_ID;
	private static volatile String CACHED_NAME;

	private LocalMachineId() {
	}

	/** Lowercase 12-hex MAC, e.g. {@code aabbccddeeff}. */
	public static String getMacHex() {
		ensureLoaded();
		return CACHED_HEX;
	}

	/** Id used in records/folders: {@code mac-aabbccddeeff}. */
	public static String getId() {
		ensureLoaded();
		return CACHED_ID;
	}

	public static String getHostName() {
		ensureLoaded();
		return CACHED_NAME;
	}

	/** {@code prefix + "-" + macHex + suffix}, e.g. clipboard_history-aabb.db */
	public static File fileIn(final File dir, final String prefix, final String suffix) {
		return new File(dir, prefix + "-" + getMacHex() + suffix);
	}

	/**
	 * Rename legacy shared file to this machine's file when the MAC file is missing.
	 * Also renames SQLite {@code -wal}/{@code -shm} siblings.
	 */
	public static File migrateLegacyFile(final File dir, final String legacyName, final String prefix,
	        final String suffix) {
		final File local = fileIn(dir, prefix, suffix);
		if (local.exists()) {
			return local;
		}
		final File legacy = new File(dir, legacyName);
		if (legacy.isFile() && legacy.renameTo(local)) {
			renameSibling(legacy, local, "-wal");
			renameSibling(legacy, local, "-shm");
		}
		return local;
	}

	/** List {@code prefix-*.suffix} plus optional legacy {@code legacyName} in dir. */
	public static File[] listMachineFiles(final File dir, final String prefix, final String suffix,
	        final String legacyName) {
		final List list = new ArrayList();
		final File local = fileIn(dir, prefix, suffix);
		list.add(local);
		if (dir != null && dir.isDirectory()) {
			final File[] kids = dir.listFiles();
			if (kids != null) {
				final String start = prefix + "-";
				for (int i = 0; i < kids.length; i++) {
					final File f = kids[i];
					if (f == null || !f.isFile()) {
						continue;
					}
					final String name = f.getName();
					final boolean matchLegacy = legacyName != null && legacyName.equalsIgnoreCase(name);
					final boolean matchPrefixed = name.regionMatches(true, 0, start, 0, start.length())
					        && name.regionMatches(true, name.length() - suffix.length(), suffix, 0, suffix.length());
					if (!matchLegacy && !matchPrefixed) {
						continue;
					}
					if (!containsCanonical(list, f)) {
						list.add(f);
					}
				}
			}
		}
		return (File[]) list.toArray(new File[list.size()]);
	}

	static void setForTests(final String macHex, final String hostName) {
		synchronized (LOCK) {
			String hex = macHex == null ? "000000000000" : macHex.replace(":", "").replace("-", "").toLowerCase();
			if (hex.startsWith("mac")) {
				hex = hex.substring(3);
				if (hex.startsWith("-")) {
					hex = hex.substring(1);
				}
			}
			while (hex.length() < 12) {
				hex = hex + "0";
			}
			if (hex.length() > 12) {
				hex = hex.substring(0, 12);
			}
			CACHED_HEX = hex;
			CACHED_ID = "mac-" + hex;
			CACHED_NAME = hostName != null ? hostName : CACHED_ID;
		}
	}

	static void resetForTests() {
		synchronized (LOCK) {
			CACHED_HEX = null;
			CACHED_ID = null;
			CACHED_NAME = null;
		}
	}

	private static void ensureLoaded() {
		if (CACHED_HEX != null && CACHED_HEX.length() > 0) {
			return;
		}
		synchronized (LOCK) {
			if (CACHED_HEX != null && CACHED_HEX.length() > 0) {
				return;
			}
			final String hex = detectMacHex();
			CACHED_HEX = hex;
			CACHED_ID = "mac-" + hex;
			CACHED_NAME = resolveHostName();
			LogUtils.info("LocalMachineId from MAC: " + CACHED_ID + " (" + CACHED_NAME + ")");
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
						if (mac == null || mac.length < 6 || isAllZero(mac)) {
							continue;
						}
						final boolean preferred = (mac[0] & 0x02) == 0;
						candidates.add(new Object[] { Boolean.valueOf(preferred), name, toHex(mac) });
					}
					catch (Exception ignored) {
					}
				}
			}
		}
		catch (Exception e) {
			LogUtils.warn("LocalMachineId NIC scan failed: " + e.getMessage(), e);
		}
		Collections.sort(candidates, new Comparator() {
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
		final String host = resolveHostName();
		int h = host.hashCode();
		final StringBuilder sb = new StringBuilder(12);
		for (int i = 0; i < 6; i++) {
			final int b = (h >> (i * 5)) & 0xff;
			sb.append(Character.forDigit((b >> 4) & 0xf, 16));
			sb.append(Character.forDigit(b & 0xf, 16));
		}
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

	private static String toHex(final byte[] mac) {
		final StringBuilder sb = new StringBuilder(12);
		final int n = Math.min(6, mac.length);
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

	private static void renameSibling(final File fromBase, final File toBase, final String suffix) {
		final File from = new File(fromBase.getAbsolutePath() + suffix);
		if (from.isFile()) {
			from.renameTo(new File(toBase.getAbsolutePath() + suffix));
		}
	}

	private static boolean containsCanonical(final List list, final File file) {
		try {
			final String path = file.getCanonicalPath();
			for (int i = 0; i < list.size(); i++) {
				final File other = (File) list.get(i);
				if (other != null && path.equals(other.getCanonicalPath())) {
					return true;
				}
			}
		}
		catch (Exception e) {
			for (int i = 0; i < list.size(); i++) {
				final File other = (File) list.get(i);
				if (other != null && file.getAbsolutePath().equals(other.getAbsolutePath())) {
					return true;
				}
			}
		}
		return false;
	}
}
