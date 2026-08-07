package org.freeplane.features.encrypt;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.freeplane.core.util.LogUtils;
import org.freeplane.features.usagestats.DeviceIdentifier;

/**
 * Encrypts short local secrets (e.g. saved encryption password) with AES-128/CBC.
 * Key is derived from this machine's fingerprint so copying the properties file
 * to another PC does not reveal the plaintext.
 */
final class LocalMachineSecretCodec {
	static final String PREFIX_V1 = "v1:";
	private static final String KEY_SALT = "docear.encryption.local.v1";
	private static final Charset UTF8 = Charset.forName("UTF-8");
	private static final int IV_BYTES = 16;
	private static final int KEY_BYTES = 16;

	private LocalMachineSecretCodec() {
	}

	static String encrypt(final String plaintext) {
		if (plaintext == null || plaintext.length() == 0) {
			return "";
		}
		try {
			final byte[] iv = new byte[IV_BYTES];
			new SecureRandom().nextBytes(iv);
			final Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE, deriveKey(), new IvParameterSpec(iv));
			final byte[] encrypted = cipher.doFinal(plaintext.getBytes(UTF8));
			final byte[] payload = new byte[iv.length + encrypted.length];
			System.arraycopy(iv, 0, payload, 0, iv.length);
			System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
			return PREFIX_V1 + Base64Coding.encode64(payload);
		}
		catch (Exception e) {
			LogUtils.warn("LocalMachineSecretCodec: encrypt failed", e);
			throw new IllegalStateException("Could not encrypt local secret", e);
		}
	}

	static String decrypt(final String encoded) {
		if (encoded == null || encoded.length() == 0) {
			return "";
		}
		if (!encoded.startsWith(PREFIX_V1)) {
			throw new IllegalArgumentException("Unsupported secret encoding");
		}
		try {
			final byte[] payload = Base64Coding.decode64(encoded.substring(PREFIX_V1.length()));
			if (payload == null || payload.length <= IV_BYTES) {
				throw new IllegalArgumentException("Invalid encrypted payload");
			}
			final byte[] iv = new byte[IV_BYTES];
			System.arraycopy(payload, 0, iv, 0, IV_BYTES);
			final byte[] ciphertext = new byte[payload.length - IV_BYTES];
			System.arraycopy(payload, IV_BYTES, ciphertext, 0, ciphertext.length);
			final Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.DECRYPT_MODE, deriveKey(), new IvParameterSpec(iv));
			final byte[] plain = cipher.doFinal(ciphertext);
			return new String(plain, UTF8);
		}
		catch (Exception e) {
			LogUtils.warn("LocalMachineSecretCodec: decrypt failed", e);
			throw new IllegalStateException("Could not decrypt local secret", e);
		}
	}

	private static SecretKeySpec deriveKey() throws Exception {
		final MessageDigest md = MessageDigest.getInstance("SHA-256");
		// Keep legacy fingerprint so existing encrypted secrets still decrypt after MAC-based device ids.
		final String material = DeviceIdentifier.getLegacyFingerprintHash() + "|" + KEY_SALT;
		final byte[] digest = md.digest(material.getBytes(UTF8));
		final byte[] key = new byte[KEY_BYTES];
		System.arraycopy(digest, 0, key, 0, KEY_BYTES);
		return new SecretKeySpec(key, "AES");
	}
}
