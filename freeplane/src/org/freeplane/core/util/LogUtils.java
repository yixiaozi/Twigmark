/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2008 Joerg Mueller, Daniel Polansky, Christian Foltin, Dimitry Polivaev
 *
 *  This file is modified by Dimitry Polivaev in 2008.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.freeplane.core.util;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.io.PrintStream;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.freeplane.core.resources.ResourceController;

/**
 * Utilities for logging to the standard logfile.
 * <p>
 * In scripts this class can be accessed via the "global" variable <code>logger</code>,
 * so this is the way to log in scripts:
 * <pre>
 *  try {
 *      logger.info("this node as date: " + node.to.date)
 *  } catch (Exception ex) {
 *      logger.severe('error on conversion of "' + node.text + '" to date', ex)
 *  }
 * </pre>
 * 
 * @author foltin
 */
public class LogUtils {
    private static final Logger LOGGER = Logger.global;
	static private boolean loggerCreated = false;

	public static void createLogger() {
		if (loggerCreated) {
			return;
		}
		loggerCreated = true;
		FileHandler mFileHandler = null;
		final Logger parentLogger = Logger.getAnonymousLogger().getParent();
		final Handler[] handlers = parentLogger.getHandlers();
		for (int i = 0; i < handlers.length; i++) {
			final Handler handler = handlers[i];
			if (handler instanceof ConsoleHandler) {
				parentLogger.removeHandler(handler);
			}
		}
		try {
			final String logDirectoryPath = getLogDirectory();
			final File logDirectory = new File(logDirectoryPath);
			logDirectory.mkdirs();
			if(logDirectory.isDirectory()){
				final String pathPattern = logDirectoryPath + File.separatorChar + "log";
				mFileHandler = new FileHandler(pathPattern, 1400000, 5, false);
				mFileHandler.setFormatter(new StdFormatter());
				parentLogger.addHandler(mFileHandler);
			}
			final ConsoleHandler stdConsoleHandler = new ConsoleHandler();
			stdConsoleHandler.setFormatter(new StdFormatter());
			if(System.getProperty("java.util.logging.config.file", null) == null){
				mFileHandler.setLevel(Level.INFO);
				stdConsoleHandler.setLevel(Level.INFO);
			}
			parentLogger.addHandler(stdConsoleHandler);
			LoggingOutputStream los;
			Logger logger = Logger.getLogger(StdFormatter.STDOUT.getName());
			los = new LoggingOutputStream(logger, StdFormatter.STDOUT);
			System.setOut(new PrintStream(los, true));
			logger = Logger.getLogger(StdFormatter.STDERR.getName());
			los = new LoggingOutputStream(logger, StdFormatter.STDERR);
			System.setErr(new PrintStream(los, true));
		}
		catch (final Exception e) {
			LogUtils.warn("Error creating logging File Handler", e);
		}
	}

	public static String getLogDirectory() {
	    final File fixedLogDir = MindMapDataRootResolver.getLogDirectory();
	    if (fixedLogDir != null) {
	        return fixedLogDir.getAbsolutePath();
	    }
	    final String logDirectory = ResourceController.getResourceController().getFreeplaneUserDirectory() + File.separatorChar + "logs";
	    return logDirectory;
    }

	public static void info(final String string) {
		LOGGER.log(Level.INFO, string);
	}

	public static void info(final Transferable t) {
		System.out.println();
		System.out.println("BEGIN OF Transferable:\t" + t);
		final DataFlavor[] dataFlavors = t.getTransferDataFlavors();
		for (int i = 0; i < dataFlavors.length; i++) {
			System.out.println("  Flavor:\t" + dataFlavors[i]);
			System.out.println("    Supported:\t" + t.isDataFlavorSupported(dataFlavors[i]));
			try {
				System.out.println("    Content:\t" + t.getTransferData(dataFlavors[i]));
			}
			catch (final Exception e) {
			}
		}
		System.out.println("END OF Transferable");
		System.out.println();
	}

	public static void severe(final String message) {
		LOGGER.log(Level.SEVERE, message);
	}

	public static void severe(final String comment, final Throwable e) {
		if(e instanceof SecurityException || e.getCause() instanceof SecurityException)
			warn(comment, e);
		else
			LOGGER.log(Level.SEVERE, comment, e);
	}

	public static void severe(final Throwable e) {
		LogUtils.severe("", e);
	}

	public static void warn(final String msg) {
		LOGGER.log(Level.WARNING, msg);
	}

	public static void warn(final String comment, final Throwable e) {
		LOGGER.log(Level.WARNING, comment, e);
	}

	public static void warn(final Throwable e) {
		LogUtils.warn("", e);
	}

	public static Logger getLogger() {
	    return LOGGER;
    }

	/** Flush JUL file handlers so the current {@code log.0} is as complete as possible. */
	public static void flushLogHandlers() {
		try {
			final Logger parentLogger = Logger.getAnonymousLogger().getParent();
			if (parentLogger == null) {
				return;
			}
			final Handler[] handlers = parentLogger.getHandlers();
			for (int i = 0; i < handlers.length; i++) {
				if (handlers[i] != null) {
					handlers[i].flush();
				}
			}
		}
		catch (final Exception e) {
			// ignore
		}
	}

	/**
	 * Newest log files first. Skips lock files. {@code log.0} is often empty right after
	 * rotation and may be locked by the running process.
	 */
	public static File[] listLogFiles() {
		final File dir = new File(getLogDirectory());
		if (!dir.isDirectory()) {
			return new File[0];
		}
		final File[] children = dir.listFiles();
		if (children == null || children.length == 0) {
			return new File[0];
		}
		final java.util.ArrayList list = new java.util.ArrayList();
		for (int i = 0; i < children.length; i++) {
			final File child = children[i];
			if (child == null || !child.isFile()) {
				continue;
			}
			final String name = child.getName();
			if (name.endsWith(".lck") || name.equalsIgnoreCase("Thumbs.db")) {
				continue;
			}
			list.add(child);
		}
		java.util.Collections.sort(list, new java.util.Comparator() {
			public int compare(final Object a, final Object b) {
				final long da = ((File) a).lastModified();
				final long db = ((File) b).lastModified();
				if (da == db) {
					return ((File) b).getName().compareToIgnoreCase(((File) a).getName());
				}
				return da < db ? 1 : -1;
			}
		});
		return (File[]) list.toArray(new File[list.size()]);
	}

	/**
	 * Tail of recent log files (newest first), up to {@code maxBytes}. Locked or empty
	 * {@code log.0} is skipped with a note so the viewer still shows rotated volumes.
	 */
	public static String readRecentLogText(final int maxBytes) {
		flushLogHandlers();
		final int cap = maxBytes < 16 * 1024 ? 16 * 1024 : maxBytes;
		final StringBuffer out = new StringBuffer(Math.min(cap + 2048, 512 * 1024));
		final File dir = new File(getLogDirectory());
		out.append("日志目录: ").append(dir.getAbsolutePath()).append('\n');
		final File[] files = listLogFiles();
		if (files.length == 0) {
			out.append("(目录为空或不存在)\n");
			return out.toString();
		}
		int remaining = cap;
		for (int i = 0; i < files.length && remaining > 0; i++) {
			final File file = files[i];
			final long size = file.length();
			out.append('\n').append("===== ").append(file.getName()).append("  (")
			        .append(size).append(" bytes) =====\n");
			if (size <= 0L) {
				out.append("(空文件。Java 日志轮转后当前 log.0 经常是 0 字节，内容在 log.1)\n");
				continue;
			}
			try {
				final String chunk = tailFile(file, remaining);
				out.append(chunk);
				if (!chunk.endsWith("\n")) {
					out.append('\n');
				}
				remaining -= chunk.length();
			}
			catch (final Exception e) {
				out.append("(无法读取：文件正被软件写入或被系统锁定: ")
				        .append(e.getMessage() == null ? e.getClass().getName() : e.getMessage())
				        .append(")\n");
			}
		}
		return out.toString();
	}

	private static String tailFile(final File file, final int maxBytes) throws Exception {
		final java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r");
		try {
			final long length = raf.length();
			final int take = length > maxBytes ? maxBytes : (int) length;
			final long start = length - take;
			raf.seek(start);
			final byte[] buf = new byte[take];
			raf.readFully(buf);
			String text;
			try {
				text = new String(buf, "UTF-8");
			}
			catch (final Exception e) {
				text = new String(buf);
			}
			if (start > 0L) {
				final int nl = text.indexOf('\n');
				if (nl >= 0 && nl + 1 < text.length()) {
					text = "…(前文已省略)\n" + text.substring(nl + 1);
				}
				else {
					text = "…(前文已省略)\n" + text;
				}
			}
			return text;
		}
		finally {
			try {
				raf.close();
			}
			catch (final Exception e) {
			}
		}
	}

	/** Open the logs folder in Explorer / Finder without going through file: URL handlers. */
	public static boolean openLogDirectory() {
		final File dir = new File(getLogDirectory());
		if (!dir.isDirectory()) {
			dir.mkdirs();
		}
		try {
			if (java.awt.Desktop.isDesktopSupported()) {
				java.awt.Desktop.getDesktop().open(dir);
				return true;
			}
		}
		catch (final Exception e) {
			LogUtils.warn("Desktop.open logs failed: " + e.getMessage());
		}
		try {
			if (Compat.isWindowsOS()) {
				Runtime.getRuntime().exec(new String[] { "explorer.exe", dir.getAbsolutePath() });
				return true;
			}
			if (Compat.isMacOsX()) {
				Runtime.getRuntime().exec(new String[] { "open", dir.getAbsolutePath() });
				return true;
			}
			Runtime.getRuntime().exec(new String[] { "xdg-open", dir.getAbsolutePath() });
			return true;
		}
		catch (final Exception e) {
			LogUtils.warn("Could not open logs folder: " + e.getMessage());
			return false;
		}
	}
}
