package org.freeplane.view.swing.features.git;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.freeplane.core.util.LogUtils;

/**
 * Quiet auto-pull: stash local WIP if needed, pull remote, resolve conflicts
 * preferring the remote side, then restore local WIP.
 */
final class GitAutoPuller {
	static final class Outcome {
		final boolean success;
		final boolean networkFailure;
		final boolean nothingToDo;
		final String message;

		Outcome(final boolean success, final boolean networkFailure, final boolean nothingToDo, final String message) {
			this.success = success;
			this.networkFailure = networkFailure;
			this.nothingToDo = nothingToDo;
			this.message = message == null ? "" : message;
		}

		static Outcome ok(final String message) {
			return new Outcome(true, false, false, message);
		}

		static Outcome nothing() {
			return new Outcome(true, false, true, "");
		}

		static Outcome network(final String message) {
			return new Outcome(false, true, false, message);
		}

		static Outcome fail(final String message) {
			return new Outcome(false, false, false, message);
		}
	}

	private GitAutoPuller() {
	}

	/**
	 * Pull using an already-fetched sync status (avoids a second fetch).
	 */
	static Outcome pull(final File repoDir, final GitSyncStatus status) {
		if (repoDir == null) {
			return Outcome.fail("未找到 Git 仓库");
		}
		if (status == null) {
			return Outcome.fail("无同步状态");
		}
		if (!status.fetchOk) {
			LogUtils.info("Git auto-pull: skipped, fetch failed (silent): " + status.error);
			return Outcome.network(status.error);
		}
		if (!status.hasUpstream) {
			return Outcome.fail(status.error.length() > 0 ? status.error : "未配置 upstream");
		}
		if (!status.needsPull()) {
			return Outcome.nothing();
		}

		boolean stashed = false;
		if (GitSyncChecker.hasUncommittedChanges(repoDir)) {
			final GitCommand.Result stashResult = GitCommand.run(repoDir, "stash", "push", "-u", "-m",
			    "docear-auto-pull");
			if (stashResult.exitCode != 0) {
				return Outcome.fail("自动暂存本地修改失败: " + stashResult.messageText());
			}
			stashed = !isNoLocalChangesStash(stashResult);
		}

		final Outcome pullOutcome = pullPreferRemote(repoDir);
		if (!pullOutcome.success) {
			if (stashed) {
				GitCommand.run(repoDir, "stash", "pop");
			}
			if (looksLikeNetworkFailure(pullOutcome.message)) {
				return Outcome.network(pullOutcome.message);
			}
			return pullOutcome;
		}
		if (stashed) {
			restoreStashPreferLocal(repoDir);
		}
		final String detail = pullOutcome.message.length() > 0 ? "（" + pullOutcome.message + "）" : "";
		return Outcome.ok("已拉取远端 " + status.behind + " 个提交" + detail);
	}

	private static Outcome pullPreferRemote(final File repoDir) {
		final GitCommand.Result ff = GitCommand.runRemote(repoDir, GitCommand.buildPullArgs(repoDir, true));
		if (ff.exitCode == 0) {
			return Outcome.ok("");
		}
		if (looksLikeNetworkFailure(ff.messageText())) {
			return Outcome.network(ff.messageText());
		}

		final String branch = GitCommand.resolveCurrentBranch(repoDir);
		final GitCommand.Result mergePull = GitCommand.runRemote(repoDir, "pull", "--no-rebase", "--no-edit", "-X",
		    "theirs", "--progress", "origin", branch);
		if (mergePull.exitCode == 0) {
			return Outcome.ok("已合并并偏好远端");
		}
		if (looksLikeNetworkFailure(mergePull.messageText())) {
			abortMergeQuietly(repoDir);
			return Outcome.network(mergePull.messageText());
		}

		if (isMergeInProgress(repoDir) || hasUnmergedPaths(repoDir)) {
			final boolean resolved = resolveUnmergedPreferTheirs(repoDir);
			if (!resolved) {
				abortMergeQuietly(repoDir);
				return Outcome.fail("冲突自动解决失败: " + mergePull.messageText());
			}
			final GitCommand.Result commit = GitCommand.run(repoDir, "commit", "--no-edit", "-m",
			    "Docear: auto-resolve merge conflicts (prefer remote)");
			if (commit.exitCode != 0) {
				abortMergeQuietly(repoDir);
				return Outcome.fail("自动提交合并失败: " + commit.messageText());
			}
			return Outcome.ok("冲突已自动解决（偏好远端）");
		}

		return Outcome.fail("自动拉取失败: " + mergePull.messageText());
	}

	private static void restoreStashPreferLocal(final File repoDir) {
		final GitCommand.Result pop = GitCommand.run(repoDir, "stash", "pop");
		if (pop.exitCode == 0) {
			return;
		}
		if (hasUnmergedPaths(repoDir)) {
			resolveUnmergedPreferTheirs(repoDir);
			GitCommand.run(repoDir, "reset", "HEAD");
			GitCommand.run(repoDir, "stash", "drop");
			LogUtils.info("Git auto-pull: stash pop conflicts resolved preferring local WIP");
		}
		else {
			LogUtils.warn("Git auto-pull: stash pop failed: " + pop.messageText());
		}
	}

	private static boolean resolveUnmergedPreferTheirs(final File repoDir) {
		final List<String> unmerged = listUnmergedPaths(repoDir);
		if (unmerged.isEmpty()) {
			return !hasUnmergedPaths(repoDir);
		}
		for (int i = 0; i < unmerged.size(); i++) {
			final String path = unmerged.get(i);
			final GitCommand.Result checkout = GitCommand.run(repoDir, "checkout", "--theirs", "--", path);
			if (checkout.exitCode != 0) {
				GitCommand.run(repoDir, "rm", "-f", "--", path);
			}
			GitCommand.run(repoDir, "add", "--", path);
		}
		return !hasUnmergedPaths(repoDir);
	}

	private static List<String> listUnmergedPaths(final File repoDir) {
		final List<String> paths = new ArrayList<String>();
		final GitCommand.Result result = GitCommand.run(repoDir, "diff", "--name-only", "--diff-filter=U");
		if (result.exitCode == 0) {
			paths.addAll(result.output);
		}
		return paths;
	}

	private static boolean hasUnmergedPaths(final File repoDir) {
		final GitCommand.Result result = GitCommand.run(repoDir, "diff", "--name-only", "--diff-filter=U");
		return result.exitCode == 0 && !result.output.isEmpty();
	}

	private static boolean isMergeInProgress(final File repoDir) {
		final GitCommand.Result result = GitCommand.run(repoDir, "rev-parse", "-q", "--verify", "MERGE_HEAD");
		return result.exitCode == 0;
	}

	private static void abortMergeQuietly(final File repoDir) {
		if (isMergeInProgress(repoDir)) {
			GitCommand.run(repoDir, "merge", "--abort");
		}
	}

	private static boolean isNoLocalChangesStash(final GitCommand.Result stashResult) {
		final String text = stashResult.messageText().toLowerCase();
		return text.contains("no local changes") || text.contains("没有要保存");
	}

	static boolean looksLikeNetworkFailure(final String message) {
		if (message == null || message.length() == 0) {
			return false;
		}
		final String lower = message.toLowerCase();
		return lower.contains("could not resolve host")
		    || lower.contains("unable to access")
		    || lower.contains("failed to connect")
		    || lower.contains("connection timed out")
		    || lower.contains("timed out")
		    || lower.contains("network is unreachable")
		    || lower.contains("no route to host")
		    || lower.contains("ssl")
		    || lower.contains("proxy")
		    || lower.contains("connection reset")
		    || lower.contains("temporarily unavailable")
		    || lower.contains("fetch 失败");
	}
}
