package org.docear.plugin.core.workspace;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.docear.plugin.core.io.ReplacingInputStream;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;

/**
 * Copies sample mind maps into an empty working directory.
 * Never overwrites or deletes existing user files.
 */
public final class WorkingDirectoryDefaults implements MindMapDataRootResolver.EmptyDirectorySeeder {

	public WorkingDirectoryDefaults() {
	}

	public void seedDefaults(final File workingDirectory) {
		seedInto(workingDirectory);
	}

	/** Idempotent: only creates missing sample files. */
	public static void seedInto(final File workingDirectory) {
		if (workingDirectory == null) {
			return;
		}
		if (!workingDirectory.isDirectory() && !workingDirectory.mkdirs()) {
			LogUtils.warn("Cannot create working directory for defaults: " + workingDirectory);
			return;
		}
		final Map replaceMapping = new HashMap();
		replaceMapping.put("@PROJECT_ID@", MindMapDataRootResolver.DEFAULT_PROJECT_ID);
		replaceMapping.put("@PROJECT_HOME@", workingDirectory.toURI().toString());
		replaceMapping.put("@LITERATURE_REPO_DEMO@", "literature_repository/Example PDFs");
		replaceMapping.put("@LITERATURE_BIB_DEMO@", "literature_and_annotations.bib");

		copyIfMissing(new File(workingDirectory, "literature_and_annotations.mm"),
		        "/demo/template_litandan.mm", replaceMapping);
		copyIfMissing(new File(workingDirectory, "temp.mm"), "/demo/template_temp.mm", replaceMapping);
		copyIfMissing(new File(workingDirectory, "trash.mm"), "/demo/template_trash.mm", replaceMapping);
		copyIfMissing(new File(workingDirectory, "literature_and_annotations.bib"),
		        "/demo/docear_example.bib", replaceMapping);

		final File drafts = new File(workingDirectory, "My Drafts");
		copyIfMissing(new File(drafts, "My New Paper.mm"),
		        "/demo/docear_example_project/My New Paper.mm", replaceMapping);

		final File welcome = new File(new File(workingDirectory, MindMapDataRootResolver.CONFIG_DIR_NAME),
		        "docear-welcome.mm");
		copyIfMissing(welcome, "/conf/docear-welcome.mm", null);

		LogUtils.info("Default working-directory content ensured under " + workingDirectory.getAbsolutePath());
	}

	private static void copyIfMissing(final File target, final String resourcePath, final Map replaceMapping) {
		if (target == null || target.exists()) {
			return;
		}
		InputStream in = null;
		try {
			final File parent = target.getParentFile();
			if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
				LogUtils.warn("Cannot create parent for " + target.getAbsolutePath());
				return;
			}
			in = WorkingDirectoryDefaults.class.getResourceAsStream(resourcePath);
			if (in == null) {
				LogUtils.warn("Missing resource " + resourcePath);
				return;
			}
			if (replaceMapping == null || replaceMapping.isEmpty()) {
				FileUtils.copyInputStreamToFile(in, target);
			}
			else {
				FileUtils.copyInputStreamToFile(new ReplacingInputStream(replaceMapping, in), target);
			}
		}
		catch (final Exception e) {
			LogUtils.warn("Could not copy default file " + target.getName() + ": " + e.getMessage());
		}
		finally {
			org.freeplane.core.util.FileUtils.silentlyClose(in);
		}
	}
}
