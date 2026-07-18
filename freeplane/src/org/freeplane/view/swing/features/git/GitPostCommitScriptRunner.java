package org.freeplane.view.swing.features.git;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.LogUtils;

/**
 * Git 面板提交成功后，在后台执行可选的 PowerShell 脚本（非 Git hook）。
 */
public final class GitPostCommitScriptRunner {

	public static final String POST_COMMIT_SCRIPT_PROPERTY = "git.post_commit_script";

	private GitPostCommitScriptRunner() {
	}

	public static void scheduleAfterSuccessfulCommit() {
		Thread worker = new Thread(new Runnable() {
			public void run() {
				runIfScriptExists();
			}
		}, "GitPostCommitScript");
		worker.setDaemon(true);
		worker.start();
	}

	private static void runIfScriptExists() {
		final String configured = ResourceController.getResourceController().getProperty(
		    POST_COMMIT_SCRIPT_PROPERTY, "");
		if (configured == null || configured.trim().length() == 0) {
			return;
		}
		final File script = new File(configured.trim());
		if (!script.isFile()) {
			LogUtils.info("Git post-commit script not found, skipped: " + script.getAbsolutePath());
			return;
		}
		try {
			final List command = new ArrayList();
			command.add("powershell.exe");
			command.add("-NoProfile");
			command.add("-ExecutionPolicy");
			command.add("Bypass");
			command.add("-File");
			command.add(script.getAbsolutePath());
			final ProcessBuilder builder = new ProcessBuilder(command);
			final File workDir = script.getParentFile();
			if (workDir != null && workDir.isDirectory()) {
				builder.directory(workDir);
			}
			builder.redirectErrorStream(true);
			LogUtils.info("Git post-commit: running " + script.getAbsolutePath());
			final Process process = builder.start();
			drainOutput(process);
			final int exitCode = process.waitFor();
			if (exitCode == 0) {
				LogUtils.info("Git post-commit script finished successfully.");
			}
			else {
				LogUtils.warn("Git post-commit script exited with code " + exitCode);
			}
		}
		catch (final Exception e) {
			LogUtils.warn("Git post-commit script failed: " + e.getMessage(), e);
		}
	}

	private static void drainOutput(final Process process) {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
			String line;
			while ((line = reader.readLine()) != null) {
				LogUtils.info("publish-blog: " + line);
			}
		}
		catch (final Exception e) {
			LogUtils.warn("Could not read post-commit script output: " + e.getMessage());
		}
		finally {
			if (reader != null) {
				try {
					reader.close();
				}
				catch (final Exception e) {
				}
			}
		}
	}
}
