package org.freeplane.core.util;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Standalone checks for per-PC filenames (no JUnit runner required).
 * Run: {@code java -cp ... org.freeplane.core.util.LocalMachineIdStandaloneTest}
 */
public final class LocalMachineIdStandaloneTest {

	public static void main(final String[] args) throws Exception {
		final File dir = new File(System.getProperty("java.io.tmpdir"), "docear-local-machine-id-test");
		deleteRecursive(dir);
		if (!dir.mkdirs()) {
			throw new IllegalStateException("mkdir failed: " + dir);
		}
		try {
			LocalMachineId.setForTests("mac-aabbccddeeff", "pc-a");
			if (!"aabbccddeeff".equals(LocalMachineId.getMacHex())) {
				throw new IllegalStateException("mac hex: " + LocalMachineId.getMacHex());
			}
			if (!"mac-aabbccddeeff".equals(LocalMachineId.getId())) {
				throw new IllegalStateException("id: " + LocalMachineId.getId());
			}
			final File expected = new File(dir, "clipboard_history-aabbccddeeff.db");
			final File legacy = new File(dir, "clipboard_history.db");
			writeBytes(legacy, new byte[] { 1, 2, 3 });
			final File wal = new File(dir, "clipboard_history.db-wal");
			writeBytes(wal, new byte[] { 4 });
			final File migrated = LocalMachineId.migrateLegacyFile(dir, "clipboard_history.db", "clipboard_history",
			        ".db");
			if (!migrated.getName().equals(expected.getName()) || !migrated.isFile()) {
				throw new IllegalStateException("migrate failed: " + migrated);
			}
			if (!new File(dir, "clipboard_history-aabbccddeeff.db-wal").isFile()) {
				throw new IllegalStateException("wal sibling not renamed");
			}
			final File peer = new File(dir, "clipboard_history-112233445566.db");
			writeBytes(peer, new byte[] { 9 });
			final File[] listed = LocalMachineId.listMachineFiles(dir, "clipboard_history", ".db",
			        "clipboard_history.db");
			if (listed.length < 2) {
				throw new IllegalStateException("list too short: " + listed.length);
			}
			boolean sawPeer = false;
			for (int i = 0; i < listed.length; i++) {
				if (listed[i].getName().equals(peer.getName())) {
					sawPeer = true;
				}
			}
			if (!sawPeer) {
				throw new IllegalStateException("peer missing from list");
			}
			// Empty local + richer sibling → adopt
			LocalMachineId.setForTests("mac-ccddeeff0011", "pc-b");
			final File emptyLocal = new File(dir, "clipboard_history-ccddeeff0011.db");
			final File rich = new File(dir, "clipboard_history-998877665544.db");
			writeBytes(rich, new byte[] { 7, 7, 7, 7, 7 });
			final File adopted = LocalMachineId.migrateLegacyFile(dir, "clipboard_history.db", "clipboard_history",
			        ".db");
			if (!adopted.isFile() || adopted.length() != 5) {
				throw new IllegalStateException("adopt richest sibling failed: " + adopted + " len="
				        + (adopted.isFile() ? adopted.length() : -1));
			}
			if (!emptyLocal.getName().equals(adopted.getName())) {
				throw new IllegalStateException("adopted wrong file: " + adopted.getName());
			}
			System.out.println("LocalMachineIdStandaloneTest OK");
		}
		finally {
			LocalMachineId.resetForTests();
			deleteRecursive(dir);
		}
	}

	private static void writeBytes(final File file, final byte[] bytes) throws Exception {
		final FileOutputStream out = new FileOutputStream(file);
		try {
			out.write(bytes);
		}
		finally {
			out.close();
		}
	}

	private static void deleteRecursive(final File file) {
		if (file == null || !file.exists()) {
			return;
		}
		if (file.isDirectory()) {
			final File[] kids = file.listFiles();
			if (kids != null) {
				for (int i = 0; i < kids.length; i++) {
					deleteRecursive(kids[i]);
				}
			}
		}
		file.delete();
	}
}
