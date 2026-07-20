package org.freeplane.view.swing.features.git;

import java.io.File;

final class GitSyncChecker {
	private GitSyncChecker() {
	}

	static GitSyncStatus check(final File repoDir) {
		if (repoDir == null) {
			return new GitSyncStatus(0, 0, false, false, "", "", "未找到 Git 仓库");
		}
		final GitCommand.Result fetchResult = GitCommand.runRemote(repoDir, "fetch", "origin");
		final boolean fetchOk = fetchResult.exitCode == 0;
		String fetchError = "";
		if (!fetchOk) {
			fetchError = fetchResult.errorText();
			if (fetchError.length() == 0) {
				fetchError = "fetch 失败";
			}
		}
		return checkAfterFetch(repoDir, fetchOk, fetchError);
	}

	/** Use after a successful fetch (or when fetch was already attempted). */
	static GitSyncStatus checkAfterFetch(final File repoDir, final boolean fetchOk) {
		return checkAfterFetch(repoDir, fetchOk, fetchOk ? "" : "fetch 失败");
	}

	static GitSyncStatus checkAfterFetch(final File repoDir, final boolean fetchOk, final String fetchError) {
		if (repoDir == null) {
			return new GitSyncStatus(0, 0, false, false, "", "", "未找到 Git 仓库");
		}

		final String branch = readSingleLine(repoDir, "rev-parse", "--abbrev-ref", "HEAD");
		if (branch.length() == 0) {
			return new GitSyncStatus(0, 0, false, fetchOk, "", "", "无法读取当前分支");
		}

		String upstream = readSingleLine(repoDir, "rev-parse", "--abbrev-ref", "@{upstream}");
		if (upstream.length() == 0) {
			upstream = "origin/" + branch;
		}

		final GitCommand.Result upstreamCheck = GitCommand.run(repoDir, "rev-parse", "--verify", upstream);
		if (upstreamCheck.exitCode != 0) {
			return new GitSyncStatus(0, 0, false, fetchOk, branch, upstream, "远端分支不存在: " + upstream);
		}

		final GitCommand.Result countResult = GitCommand.run(repoDir, "rev-list", "--left-right", "--count",
		    upstream + "...HEAD");
		int ahead = 0;
		int behind = 0;
		if (countResult.exitCode == 0 && !countResult.output.isEmpty()) {
			final String[] parts = countResult.output.get(0).split("\\s+");
			if (parts.length >= 2) {
				try {
					behind = Integer.parseInt(parts[0].trim());
					ahead = Integer.parseInt(parts[1].trim());
				}
				catch (NumberFormatException e) {
					return new GitSyncStatus(0, 0, true, fetchOk, branch, upstream, "无法解析远端同步状态");
				}
			}
		}
		else {
			final String error = countResult.errorText();
			return new GitSyncStatus(0, 0, true, fetchOk, branch, upstream,
			    error.length() > 0 ? error : "无法比较本地与远端");
		}

		String error = "";
		if (!fetchOk) {
			error = fetchError == null ? "" : fetchError;
			if (error.length() == 0) {
				error = "fetch 失败";
			}
		}
		return new GitSyncStatus(ahead, behind, true, fetchOk, branch, upstream, error);
	}

	static boolean hasUncommittedChanges(final File repoDir) {
		if (repoDir == null) {
			return false;
		}
		final GitCommand.Result result = GitCommand.run(repoDir, "status", "--porcelain");
		return result.exitCode == 0 && !result.output.isEmpty();
	}

	private static String readSingleLine(final File repoDir, final String... args) {
		final GitCommand.Result result = GitCommand.run(repoDir, args);
		if (result.exitCode != 0 || result.output.isEmpty()) {
			return "";
		}
		return result.output.get(0).trim();
	}
}
