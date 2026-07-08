package org.freeplane.core.util;

public final class Compat {
	public static String getApplicationUserDirectory() {
		return System.getProperty("user.home") + java.io.File.separator + "Docear";
	}
}
