package org.docear.plugin.core.eagle;

import java.io.File;

public final class EagleItem {
	private final String id;
	private final String name;
	private final String ext;
	private final long size;
	private final String sha256;
	private final File file;
	private final File libraryRoot;

	public EagleItem(final String id, final String name, final String ext, final long size, final String sha256,
			final File file, final File libraryRoot) {
		this.id = id;
		this.name = name;
		this.ext = ext;
		this.size = size;
		this.sha256 = sha256;
		this.file = file;
		this.libraryRoot = libraryRoot;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getExt() {
		return ext;
	}

	public long getSize() {
		return size;
	}

	public String getSha256() {
		return sha256;
	}

	public File getFile() {
		return file;
	}

	public File getLibraryRoot() {
		return libraryRoot;
	}

	public String fileNameLower() {
		final String base = name == null ? id : name;
		final String e = ext == null || ext.length() == 0 ? "" : "." + ext.toLowerCase();
		return (base + e).toLowerCase();
	}

	public String baseNameLower() {
		return (name == null ? id : name).toLowerCase();
	}
}
