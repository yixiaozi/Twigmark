package org.freeplane.core.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;

/**
 * Stable per-PC identity for machine-local filenames:
 * {@code audit-&lt;mac&gt;.db}, {@code clipboard_history-&lt;mac&gt;.db}, etc.
 * <p>
 * NIC MAC alone is unstable on modern Macs (Private Wi‑Fi Address, Thunderbolt adapters
 * going up/down). Therefore the chosen id is <b>pinned</b> once in a non-synced OS support
 * directory ({@code ~/Library/Application Support/Docear/local-machine-id.txt} on macOS).
 * The working-directory {@code data/}/{@code _data/} folder must never store this id —
 * it is often cloud-synced across PCs.
 */
public final class LocalMachineId {

	private static final Object LOCK = new Object();
	private static final String STICKY_FILE_NAME = "local-machine-id.txt";
	private static final Charset UTF8 = Charset.forName("UTF-8");

	private static volatile String CACHED_HEX;
	private static volatile String CACHED_ID;
	private static volatile String CACHED_NAME;

	private LocalMachineId() {
	}

	/** Lowercase 12-hex id fragment, e.g. {@code aabbccddeeff}. */
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

	public static void setForTests(final String macHex, final String hostName) {
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

	public static void resetForTests() {
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
			String hex = readStickyHex();
			if (hex == null || hex.length() != 12) {
				hex = detectMacHex();
				writeStickyHex(hex);
			}
			CACHED_HEX = hex;
			CACHED_ID = "mac-" + hex;
			CACHED_NAME = resolveHostName();
			LogUtils.info("LocalMachineId: " + CACHED_ID + " (" + CACHED_NAME + ") sticky="
			        + stickyFile().getAbsolutePath());
		}
	}

	/** OS support dir that is NOT the synced mind-map data folder. */
	static File stickyFile() {
		return new File(getOsSupportDir(), STICKY_FILE_NAME);
	}

	static File getOsSupportDir() {
		final File home = new File(System.getProperty("user.home", "."));
		if (Compat.isWindowsOS()) {
			final String appData = System.getenv("APPDATA");
			if (appData != null && appData.trim().length() > 0) {
				return new File(appData.trim(), "Docear");
			}
			return new File(home, "AppData" + File.separator + "Roaming" + File.separator + "Docear");
		}
		if (Compat.isMacOsX()) {
			return new File(new File(new File(home, "Library"), "Application Support"), "Docear");
		}
		final String xdg = System.getenv("XDG_CONFIG_HOME");
		final File configHome = (xdg != null && xdg.trim().length() > 0) ? new File(xdg.trim())
		        : new File(home, ".config");
		return new File(configHome, "Docear");
	}

	private static String readStickyHex() {
		final File file = stickyFile();
		if (!file.isFile()) {
			return null;
		}
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), UTF8));
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.length() == 0 || line.startsWith("#")) {
					continue;
				}
				final String hex = normalizeHex(line);
				if (hex != null) {
					return hex;
				}
			}
		}
		catch (Exception e) {
			LogUtils.warn("LocalMachineId sticky read failed: " + e.getMessage());
		}
		finally {
			FileUtils.silentlyClose(reader);
		}
		return null;
	}

	private static void writeStickyHex(final String hex) {
		final File file = stickyFile();
		final File parent = file.getParentFile();
		if (parent != null && !parent.exists()) {
			parent.mkdirs();
		}
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), UTF8));
			writer.write("# Docear per-PC id for audit-*.db / clipboard_history-*.db");
			writer.newLine();
			writer.write("# Keep this file on THIS computer only (not in synced mind-map data/).");
			writer.newLine();
			writer.write(hex);
			writer.newLine();
		}
		catch (Exception e) {
			LogUtils.warn("LocalMachineId sticky write failed: " + e.getMessage());
		}
		finally {
			FileUtils.silentlyClose(writer);
		}
	}

	private static String normalizeHex(final String raw) {
		if (raw == null) {
			return null;
		}
		String hex = raw.trim().toLowerCase().replace(":", "").replace("-", "");
		if (hex.startsWith("mac")) {
			hex = hex.substring(3);
			if (hex.startsWith("-")) {
				hex = hex.substring(1);
			}
		}
		if (hex.length() != 12) {
			return null;
		}
		for (int i = 0; i < 12; i++) {
			final char c = hex.charAt(i);
			if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) {
				return null;
			}
		}
		return hex;
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
						if (isSkippedInterfaceName(name)) {
							continue;
						}
						final byte[] mac = ni.getHardwareAddress();
						if (mac == null || mac.length < 6 || isAllZero(mac)) {
							continue;
						}
						final boolean globallyAdministered = (mac[0] & 0x02) == 0;
						// Prefer en0 (built-in Wi‑Fi/Ethernet on Mac), then other en*, then rest.
						int rank = 50;
						if ("en0".equals(name)) {
							rank = 0;
						}
						else if (name.startsWith("en") && name.length() <= 4) {
							rank = 10;
						}
						else if (name.startsWith("eth")) {
							rank = 15;
						}
						candidates.add(new Object[] { Boolean.valueOf(globallyAdministered), Integer.valueOf(rank),
						        name, toHex(mac) });
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
				final int rx = ((Integer) x[1]).intValue();
				final int ry = ((Integer) y[1]).intValue();
				if (rx != ry) {
					return rx < ry ? -1 : 1;
				}
				return String.valueOf(x[2]).compareTo(String.valueOf(y[2]));
			}
		});
		if (!candidates.isEmpty()) {
			return (String) ((Object[]) candidates.get(0))[3];
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

	private static boolean isSkippedInterfaceName(final String name) {
		return name.startsWith("docker") || name.startsWith("veth") || name.startsWith("br-")
		        || name.startsWith("bridge") || name.startsWith("virbr") || name.startsWith("vmnet")
		        || name.startsWith("vbox") || name.startsWith("utun") || name.startsWith("awdl")
		        || name.startsWith("llw") || name.startsWith("ap") || name.startsWith("ipsec")
		        || name.startsWith("p2p");
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
