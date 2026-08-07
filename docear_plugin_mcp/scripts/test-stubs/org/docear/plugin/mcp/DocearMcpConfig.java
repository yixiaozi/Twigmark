package org.docear.plugin.mcp;

import java.io.File;

public final class DocearMcpConfig {
	public static boolean isAuditEnabled() {
		return true;
	}

	public static File getAuditDataDir() {
		return new File("_data");
	}

	public static File getAuditDbFile() {
		return new File(getAuditDataDir(), "audit-testmac0001.db");
	}

	public static File getAuditOverflowFile() {
		return new File(getAuditDataDir(), "audit_overflow-testmac0001.jsonl");
	}

	public static File[] listAuditDbFiles() {
		final File dir = getAuditDataDir();
		final File[] kids = dir.listFiles();
		if (kids == null) {
			return new File[] { getAuditDbFile() };
		}
		final java.util.List list = new java.util.ArrayList();
		for (int i = 0; i < kids.length; i++) {
			final String n = kids[i].getName().toLowerCase();
			if (n.equals("audit.db") || (n.startsWith("audit-") && n.endsWith(".db"))) {
				list.add(kids[i]);
			}
		}
		if (list.isEmpty()) {
			list.add(getAuditDbFile());
		}
		return (File[]) list.toArray(new File[list.size()]);
	}

	public static int getAuditQueueSize() {
		return 5000;
	}

	public static int getAuditBatchSize() {
		return 200;
	}

	public static int getAuditMaxResponseBytes() {
		return 524288;
	}
}
