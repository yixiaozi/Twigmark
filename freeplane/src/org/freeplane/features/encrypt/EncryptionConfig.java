package org.freeplane.features.encrypt;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.freeplane.core.util.Compat;
import org.freeplane.core.util.LogUtils;

public final class EncryptionConfig {
	/** Legacy plaintext key (migrated automatically on read). */
	public static final String PROP_ENCRYPT_PASSWORD = "encryption.password";
	/** Machine-bound AES ciphertext (preferred). */
	public static final String PROP_ENCRYPT_PASSWORD_ENC = "encryption.password.enc";
	public static final String PROP_STORAGE_VERSION = "encryption.storage.version";
	public static final String STORAGE_VERSION = "2";

	private EncryptionConfig() {
	}

	public static String getPassword() {
		final File file = localPropertiesFile();
		if (!file.isFile()) {
			return "";
		}
		final Properties props = loadProperties(file);
		if (props == null) {
			return "";
		}
		final String encrypted = props.getProperty(PROP_ENCRYPT_PASSWORD_ENC, "").trim();
		if (encrypted.length() > 0) {
			try {
				return LocalMachineSecretCodec.decrypt(encrypted);
			}
			catch (Exception e) {
				LogUtils.warn("Encryption: could not decrypt saved password in " + file.getPath()
						+ " (wrong machine or corrupted file)", e);
			}
		}
		final String legacy = props.getProperty(PROP_ENCRYPT_PASSWORD, "").trim();
		if (legacy.length() > 0) {
			LogUtils.info("Encryption: migrating plaintext password to machine-bound encrypted storage");
			writePassword(file, props, legacy);
			return legacy;
		}
		return "";
	}

	public static void setPassword(final String password) {
		final File file = localPropertiesFile();
		final Properties props = file.isFile() ? loadProperties(file) : new Properties();
		if (props == null) {
			return;
		}
		writePassword(file, props, password == null ? "" : password);
	}

	public static boolean hasPassword() {
		return getPassword().length() > 0;
	}

	private static void writePassword(final File file, final Properties props, final String password) {
		props.remove(PROP_ENCRYPT_PASSWORD);
		if (password.length() == 0) {
			props.remove(PROP_ENCRYPT_PASSWORD_ENC);
			props.remove(PROP_STORAGE_VERSION);
		}
		else {
			props.setProperty(PROP_ENCRYPT_PASSWORD_ENC, LocalMachineSecretCodec.encrypt(password));
			props.setProperty(PROP_STORAGE_VERSION, STORAGE_VERSION);
		}
		saveProperties(file, props);
	}

	private static Properties loadProperties(final File file) {
		FileInputStream in = null;
		try {
			final Properties props = new Properties();
			in = new FileInputStream(file);
			props.load(in);
			return props;
		}
		catch (IOException e) {
			LogUtils.warn("Encryption: could not read " + file.getPath(), e);
			return null;
		}
		finally {
			if (in != null) {
				try {
					in.close();
				}
				catch (IOException e) {
				}
			}
		}
	}

	private static void saveProperties(final File file, final Properties props) {
		FileOutputStream out = null;
		try {
			out = new FileOutputStream(file);
			props.store(out, "Docear encryption settings (local only; password stored encrypted)");
		}
		catch (IOException e) {
			LogUtils.warn("Encryption: could not write " + file.getPath(), e);
		}
		finally {
			if (out != null) {
				try {
					out.close();
				}
				catch (IOException e) {
				}
			}
		}
	}

	private static File localPropertiesFile() {
		return new File(Compat.getApplicationUserDirectory(), "encryption.local.properties");
	}
}
