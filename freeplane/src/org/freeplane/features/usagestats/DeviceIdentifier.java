package org.freeplane.features.usagestats;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.UUID;

/**
 * Stable per-computer id for usage-stats folders under {@code .docear_stats/data/{deviceId}/}.
 * <p>
 * The project path {@code _data/{projectId}/.docear_stats} is shared when data lives on a
 * synced/network drive. The device identity file must therefore stay on this machine
 * ({@code user.home/.docear/device.id}), not under the shared project tree — otherwise every
 * PC would adopt the first synced {@code .device.id} and write into one folder.
 */
public class DeviceIdentifier {
	private static final String LOCAL_DIR_NAME = ".docear";
	private static final String DEVICE_ID_FILE = "device.id";
	private static String cachedDeviceId = null;

	public static synchronized String getDeviceId() {
		if (cachedDeviceId != null) {
			return cachedDeviceId;
		}

		final File localFile = getLocalDeviceIdFile();
		if (localFile != null && localFile.isFile()) {
			cachedDeviceId = readDeviceIdFromFile(localFile);
			if (cachedDeviceId != null) {
				return cachedDeviceId;
			}
		}

		final String fromHardware = generateDeviceIdFromHardware();
		if (fromHardware != null) {
			cachedDeviceId = fromHardware;
		}
		else {
			cachedDeviceId = UUID.randomUUID().toString().replace("-", "");
		}

		if (localFile != null) {
			saveDeviceIdToFile(localFile, cachedDeviceId);
		}
		return cachedDeviceId;
	}

	/** {@code {user.home}/.docear/device.id} — local to this OS user / machine, not project sync. */
	static File getLocalDeviceIdFile() {
		final String home = System.getProperty("user.home");
		if (home == null || home.length() == 0) {
			return null;
		}
		return new File(new File(home, LOCAL_DIR_NAME), DEVICE_ID_FILE);
	}

	private static String readDeviceIdFromFile(final File file) {
		Reader reader = null;
		BufferedReader br = null;
		try {
			reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
			br = new BufferedReader(reader);
			final String id = br.readLine();
			if (id != null && id.trim().length() > 0) {
				return id.trim();
			}
		}
		catch (final IOException e) {
			// Ignore
		}
		finally {
			if (br != null) {
				try {
					br.close();
				}
				catch (final IOException e) {
				}
			}
			if (reader != null) {
				try {
					reader.close();
				}
				catch (final IOException e) {
				}
			}
		}
		return null;
	}

	private static void saveDeviceIdToFile(final File file, final String deviceId) {
		final File parentDir = file.getParentFile();
		if (parentDir != null && !parentDir.exists()) {
			parentDir.mkdirs();
		}

		Writer writer = null;
		BufferedWriter bw = null;
		try {
			writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
			bw = new BufferedWriter(writer);
			bw.write(deviceId);
		}
		catch (final IOException e) {
			// Ignore
		}
		finally {
			if (bw != null) {
				try {
					bw.close();
				}
				catch (final IOException e) {
				}
			}
			if (writer != null) {
				try {
					writer.close();
				}
				catch (final IOException e) {
				}
			}
		}
	}

	private static String generateDeviceIdFromHardware() {
		final String macAddress = getMacAddress();
		if (macAddress != null && macAddress.length() > 0) {
			return hashString(macAddress);
		}
		return null;
	}

	private static String getMacAddress() {
		try {
			final Enumeration interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements()) {
				final NetworkInterface iface = (NetworkInterface) interfaces.nextElement();
				if (iface.isLoopback() || !iface.isUp()) {
					continue;
				}
				final byte[] mac = iface.getHardwareAddress();
				if (mac != null) {
					final StringBuilder sb = new StringBuilder();
					for (int i = 0; i < mac.length; i++) {
						sb.append(String.format("%02X%s", Integer.valueOf(mac[i] & 0xff),
								(i < mac.length - 1) ? "-" : ""));
					}
					return sb.toString();
				}
			}
		}
		catch (final Exception e) {
			// Ignore
		}
		return null;
	}

	private static String hashString(final String input) {
		try {
			final MessageDigest md = MessageDigest.getInstance("SHA-256");
			final byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
			final StringBuilder hexString = new StringBuilder();
			for (int i = 0; i < hash.length; i++) {
				final String hex = Integer.toHexString(0xff & hash[i]);
				if (hex.length() == 1) {
					hexString.append('0');
				}
				hexString.append(hex);
			}
			return hexString.toString().substring(0, 32);
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
