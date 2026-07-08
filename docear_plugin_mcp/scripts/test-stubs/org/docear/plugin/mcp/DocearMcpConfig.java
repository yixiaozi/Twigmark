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
		return new File(getAuditDataDir(), "audit.db");
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
