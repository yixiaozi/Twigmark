package org.freeplane.view.swing.features.git;

import java.io.File;
import java.util.List;

/**
 * Standalone test for Git remote operations. Run from repo root:
 * scripts/test-git-remote.ps1
 */
public final class GitRemoteTest {
	public static void main(final String[] args) {
		final File repo = resolveRepo(args);
		System.out.println("=== Git Remote Test ===");
		System.out.println("Repo: " + repo.getAbsolutePath());
		System.out.println("Git:  " + GitCommand.resolveGitExecutable());
		System.out.println("Java: " + System.getProperty("java.version"));
		System.out.println("CWD:  " + System.getProperty("user.dir"));
		System.out.println();

		runCase("status --porcelain", GitCommand.run(repo, "status", "--porcelain"));
		testFileSizes(repo);
		runCase("rev-parse HEAD", GitCommand.run(repo, "rev-parse", "--abbrev-ref", "HEAD"));
		runCase("fetch origin", GitCommand.runRemote(repo, "fetch", "origin"));
		runCase("pull", GitCommand.runRemote(repo, GitCommand.buildPullArgs(repo, false)));
		runCase("sync check", wrapSyncCheck(repo));
		runCase("push", GitCommand.runRemote(repo, "push"));

		System.out.println("=== Done ===");
		if (args.length > 0 && "--pause".equals(args[args.length - 1])) {
			System.out.println("Press Enter to exit...");
			try {
				System.in.read();
			}
			catch (Exception e) {
			}
		}
	}

	private static File resolveRepo(final String[] args) {
		if (args.length > 0 && !args[0].startsWith("--")) {
			return new File(args[0]);
		}
		final String envRepo = System.getenv("DOCEAR_GIT_TEST_REPO");
		if (envRepo != null && envRepo.trim().length() > 0) {
			return new File(envRepo.trim());
		}
		System.err.println("Usage: GitRemoteTest <repo-path> [--pause]");
		System.err.println("Or set DOCEAR_GIT_TEST_REPO to a git repository path.");
		System.exit(1);
		return null;
	}

	private static void testFileSizes(final File repo) {
		System.out.println("--- file size resolution ---");
		final List<GitStatusParser.Entry> entries = GitStatusParser.parsePorcelain(repo);
		for (int i = 0; i < entries.size(); i++) {
			final GitStatusParser.Entry entry = entries.get(i);
			final String relativePath = entry.path;
			final java.io.File direct = new java.io.File(repo, relativePath.replace('/', java.io.File.separatorChar));
			final long size = GitCommand.resolveWorkingTreeSize(repo, relativePath);
			System.out.println("path: " + relativePath);
			System.out.println("  File.isFile=" + direct.isFile() + " File.length=" + (direct.isFile() ? direct.length() : -1));
			System.out.println("  resolveWorkingTreeSize=" + size + " (" + GitCommand.formatFileSize(size) + ")");
		}
		System.out.println();
	}

	private static void runCase(final String label, final GitCommand.Result result) {
		System.out.println("--- " + label + " ---");
		System.out.println("exitCode: " + result.exitCode);
		System.out.println("command:  " + result.commandLine);
		if (!result.output.isEmpty()) {
			System.out.println("stdout:");
			for (int i = 0; i < result.output.size(); i++) {
				System.out.println("  " + result.output.get(i));
			}
		}
		if (!result.errors.isEmpty()) {
			System.out.println("errors:");
			for (int i = 0; i < result.errors.size(); i++) {
				System.out.println("  " + result.errors.get(i));
			}
		}
		if (result.output.isEmpty() && result.errors.isEmpty()) {
			System.out.println("(no output captured)");
		}
		System.out.println("message:  " + result.messageText());
		System.out.println();
	}

	private static GitCommand.Result wrapSyncCheck(final File repo) {
		final GitSyncStatus status = GitSyncChecker.check(repo);
		final java.util.ArrayList<String> output = new java.util.ArrayList<String>();
		final java.util.ArrayList<String> errors = new java.util.ArrayList<String>();
		output.add("ahead=" + status.ahead + " behind=" + status.behind + " branch=" + status.branch);
		output.add("summary: " + status.syncSummary());
		if (status.error != null && status.error.length() > 0) {
			errors.add(status.error);
		}
		return new GitCommand.Result(status.fetchOk ? 0 : 1, output, errors, "GitSyncChecker.check", "");
	}
}
