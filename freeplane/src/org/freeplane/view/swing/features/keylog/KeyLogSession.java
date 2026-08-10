package org.freeplane.view.swing.features.keylog;

/** One continuous typing burst (metadata only). */
public final class KeyLogSession {
	public long id;
	public long startTs;
	public long endTs;
	public int keyCount;
	public boolean approx;
	public String source = "";
	public String sourceDbPath = "";
}
