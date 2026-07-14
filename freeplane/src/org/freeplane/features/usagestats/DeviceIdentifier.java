package org.freeplane.features.usagestats;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Per-computer id for usage-stats folders under {@code .docear_stats/data/{deviceId}/}.
 * <p>
 * Computed from this machine's hardware fingerprint (MAC addresses + optional board/OS
 * machine UUID), then kept only in memory. No file on disk — so a synced project tree
 * cannot leak identity across PCs, and a reinstall on the same hardware yields the same id.
 */
public class DeviceIdentifier {
	private static String cachedDeviceId = null;

	public static synchronized String getDeviceId() {
		if (cachedDeviceId != null) {
			return cachedDeviceId;
		}
		cachedDeviceId = hashString(buildFingerprintMaterial());
		if (cachedDeviceId == null) {
			cachedDeviceId = "unknown-device";
		}
		return cachedDeviceId;
	}

	/**
	 * Stable material: sorted physical MAC addresses, plus board/platform UUID when the OS
	 * exposes one. Same PC → same string across reinstalls; different PC → different string.
	 */
	private static String buildFingerprintMaterial() {
		final StringBuilder material = new StringBuilder();
		final List macs = collectPhysicalMacAddresses();
		for (int i = 0; i < macs.size(); i++) {
			if (i > 0) {
				material.append(',');
			}
			material.append(macs.get(i));
		}
		final String platformUuid = getPlatformMachineUuid();
		if (platformUuid != null && platformUuid.length() > 0) {
			if (material.length() > 0) {
				material.append('|');
			}
			material.append(platformUuid);
		}
		if (material.length() == 0) {
			// Last resort: deterministic, not random — weaker uniqueness but survives restarts.
			material.append(System.getProperty("os.name", ""));
			material.append('|').append(System.getProperty("os.arch", ""));
			material.append('|').append(getDeviceName());
			material.append('|').append(System.getProperty("user.name", ""));
		}
		return material.toString();
	}

	private static List collectPhysicalMacAddresses() {
		final List macs = new ArrayList();
		try {
			final Enumeration interfaces = NetworkInterface.getNetworkInterfaces();
			if (interfaces == null) {
				return macs;
			}
			while (interfaces.hasMoreElements()) {
				final NetworkInterface iface = (NetworkInterface) interfaces.nextElement();
				try {
					if (iface.isLoopback() || isProbablyVirtualAdapter(iface)) {
						continue;
					}
					final byte[] mac = iface.getHardwareAddress();
					if (mac == null || mac.length == 0 || isZeroMac(mac)) {
						continue;
					}
					macs.add(formatMac(mac));
				}
				catch (final Exception ignored) {
					// Skip this interface.
				}
			}
		}
		catch (final Exception ignored) {
			// No interfaces available.
		}
		Collections.sort(macs);
		return macs;
	}

	private static boolean isProbablyVirtualAdapter(final NetworkInterface iface) {
		final String name = String.valueOf(iface.getName()).toLowerCase();
		final String display = String.valueOf(iface.getDisplayName()).toLowerCase();
		final String haystack = name + " " + display;
		return haystack.indexOf("virtual") >= 0 || haystack.indexOf("vmware") >= 0
		        || haystack.indexOf("vbox") >= 0 || haystack.indexOf("virtualbox") >= 0
		        || haystack.indexOf("hyper-v") >= 0 || haystack.indexOf("hyperv") >= 0
		        || haystack.indexOf("docker") >= 0 || haystack.indexOf("veth") >= 0
		        || haystack.indexOf("tap") >= 0 || haystack.indexOf("tun") >= 0
		        || haystack.indexOf("vpn") >= 0 || haystack.indexOf("loopback") >= 0;
	}

	private static boolean isZeroMac(final byte[] mac) {
		for (int i = 0; i < mac.length; i++) {
			if (mac[i] != 0) {
				return false;
			}
		}
		return true;
	}

	private static String formatMac(final byte[] mac) {
		final StringBuilder sb = new StringBuilder(mac.length * 3);
		for (int i = 0; i < mac.length; i++) {
			if (i > 0) {
				sb.append('-');
			}
			sb.append(String.format("%02X", Integer.valueOf(mac[i] & 0xff)));
		}
		return sb.toString();
	}

	/**
	 * Board / platform UUID that usually survives OS reinstall on the same machine.
	 * Windows MachineGuid is intentionally not used (it is recreated with a new Windows install).
	 */
	private static String getPlatformMachineUuid() {
		final String os = System.getProperty("os.name", "").toLowerCase();
		if (os.indexOf("win") >= 0) {
			return readWindowsProductUuid();
		}
		if (os.indexOf("mac") >= 0) {
			return readMacPlatformUuid();
		}
		return readLinuxProductUuid();
	}

	private static String readLinuxProductUuid() {
		final String fromDmi = readFirstLine(new File("/sys/class/dmi/id/product_uuid"));
		if (isUsableUuid(fromDmi)) {
			return fromDmi.trim().toLowerCase();
		}
		return null;
	}

	private static String readMacPlatformUuid() {
		return runCommandCaptureFirstMatch(new String[] { "/usr/sbin/ioreg", "-rd1", "-c", "IOPlatformExpertDevice" },
		        "IOPlatformUUID");
	}

	private static String readWindowsProductUuid() {
		// SMBIOS UUID — same hardware keeps it across Windows reinstalls more often than MachineGuid.
		final String fromWmic = runCommandCaptureFirstMatch(
		        new String[] { "wmic", "csproduct", "get", "UUID" }, null);
		if (isUsableUuid(fromWmic)) {
			return fromWmic.trim().toLowerCase();
		}
		final String fromCim = runCommandCaptureFirstMatch(
		        new String[] { "powershell", "-NoProfile", "-Command",
		                "(Get-CimInstance -ClassName Win32_ComputerSystemProduct).UUID" },
		        null);
		if (isUsableUuid(fromCim)) {
			return fromCim.trim().toLowerCase();
		}
		return null;
	}

	private static boolean isUsableUuid(final String value) {
		if (value == null) {
			return false;
		}
		final String v = value.trim();
		if (v.length() < 8) {
			return false;
		}
		final String lower = v.toLowerCase();
		return lower.indexOf("ffffffff") < 0 && lower.indexOf("00000000-0000-0000-0000-000000000000") < 0
		        && !lower.equals("uuid") && !lower.equals("to be filled by o.e.m.");
	}

	private static String readFirstLine(final File file) {
		if (file == null || !file.isFile()) {
			return null;
		}
		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
			return br.readLine();
		}
		catch (final Exception e) {
			return null;
		}
		finally {
			if (br != null) {
				try {
					br.close();
				}
				catch (final Exception ignored) {
				}
			}
		}
	}

	private static String runCommandCaptureFirstMatch(final String[] command, final String keyHint) {
		Process process = null;
		BufferedReader br = null;
		try {
			process = Runtime.getRuntime().exec(command);
			br = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
			String line;
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (line.length() == 0) {
					continue;
				}
				if (keyHint != null) {
					final int idx = line.indexOf(keyHint);
					if (idx < 0) {
						continue;
					}
					final int quote = line.indexOf('"', idx);
					if (quote >= 0) {
						final int end = line.indexOf('"', quote + 1);
						if (end > quote) {
							return line.substring(quote + 1, end);
						}
					}
					continue;
				}
				if (line.equalsIgnoreCase("UUID")) {
					continue;
				}
				return line;
			}
			process.waitFor();
		}
		catch (final Exception e) {
			return null;
		}
		finally {
			if (br != null) {
				try {
					br.close();
				}
				catch (final Exception ignored) {
				}
			}
			if (process != null) {
				try {
					process.getErrorStream().close();
				}
				catch (final Exception ignored) {
				}
				try {
					process.destroy();
				}
				catch (final Exception ignored) {
				}
			}
		}
		return null;
	}

	private static String hashString(final String input) {
		if (input == null) {
			return null;
		}
		try {
			final MessageDigest md = MessageDigest.getInstance("SHA-256");
			final byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
			final StringBuilder hexString = new StringBuilder(32);
			for (int i = 0; i < hash.length && hexString.length() < 32; i++) {
				final String hex = Integer.toHexString(0xff & hash[i]);
				if (hex.length() == 1) {
					hexString.append('0');
				}
				hexString.append(hex);
			}
			return hexString.toString();
		}
		catch (final NoSuchAlgorithmException e) {
			return null;
		}
	}

	public static String getDeviceName() {
		String computerName = System.getenv("COMPUTERNAME");
		if (computerName == null) {
			computerName = System.getenv("HOSTNAME");
		}
		if (computerName == null) {
			computerName = "Unknown";
		}
		return computerName;
	}

	public static String getPlatform() {
		return System.getProperty("os.name", "Unknown");
	}
}
