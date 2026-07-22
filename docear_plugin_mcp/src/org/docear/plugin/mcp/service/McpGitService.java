package org.docear.plugin.mcp.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.docear.plugin.mcp.json.JsonValue;
import org.docear.plugin.mcp.json.JsonWriter;
import org.freeplane.core.util.Compat;
import org.freeplane.core.util.LogUtils;

/**
 * Git helpers for single-writer sync: status / pull / commit mind-map files / push.
 * Intended for "edit on one side at a time" workflows (local ↔ server), not Dropbox-like merge.
 */
public final class McpGitService {
	private static final Charset UTF8 = Charset.forName("UTF-8");
	private static final long DEFAULT_TIMEOUT_SECONDS = 120L;

	private McpGitService() {
	}

	public static String gitStatus(final String repoPath) {
		final File repo = resolveRepo(repoPath);
		final Map<String, JsonValue> out = baseResult(repo);
		final CmdResult branch = run(repo, "rev-parse", "--abbrev-ref", "HEAD");
		out.put("branch", JsonValue.ofString(branch.exitCode == 0 ? branch.stdout.trim() : ""));
		final CmdResult porcelain = run(repo, "status", "--porcelain");
		out.put("clean", JsonValue.ofBoolean(porcelain.exitCode == 0 && porcelain.stdout.trim().length() == 0));
		out.put("status", JsonValue.ofString(porcelain.stdout));
		out.put("ok", JsonValue.ofBoolean(porcelain.exitCode == 0));
		if (porcelain.exitCode != 0) {
			out.put("error", JsonValue.ofString(porcelain.combined()));
		}
		final CmdResult remote = run(repo, "remote", "-v");
		out.put("remotes", JsonValue.ofString(remote.stdout));
		return JsonWriter.write(JsonValue.ofMap(out));
	}

	/**
	 * pull → stage mind-map-related paths → commit if dirty → push.
	 *
	 * @param message commit message; empty uses a timestamped default
	 * @param push whether to push after commit (default true)
	 * @param pull first whether to pull --ff-only before commit (default true)
	 */
	public static String gitSync(final String repoPath, final String message, final boolean push,
			final boolean pullFirst) {
		final File repo = resolveRepo(repoPath);
		final Map<String, JsonValue> out = baseResult(repo);
		final List<JsonValue> steps = new ArrayList<JsonValue>();
		out.put("steps", JsonValue.ofList(steps));

		if (pullFirst) {
			final CmdResult pull = run(repo, DEFAULT_TIMEOUT_SECONDS, "pull", "--ff-only");
			steps.add(step("pull", pull));
			if (pull.exitCode != 0) {
				out.put("ok", JsonValue.ofBoolean(false));
				out.put("error", JsonValue.ofString("pull failed: " + pull.combined()));
				return JsonWriter.write(JsonValue.ofMap(out));
			}
		}

		final CmdResult add = run(repo, "add", "-A", "--", "*.mm", "**/*.mm", "*.mm.bak", ".gitignore");
		// git add with globs may fail on older git without pathspec magic; fall back to add -u + mm scan
		CmdResult addResult = add;
		if (add.exitCode != 0) {
			addResult = run(repo, "add", "-u");
			addMindMapFiles(repo);
		}
		steps.add(step("add", addResult));

		final CmdResult porcelain = run(repo, "status", "--porcelain");
		steps.add(step("status", porcelain));
		final boolean dirty = porcelain.exitCode == 0 && porcelain.stdout.trim().length() > 0;
		out.put("dirtyBeforeCommit", JsonValue.ofBoolean(dirty));

		boolean committed = false;
		if (dirty) {
			final String msg = message == null || message.trim().length() == 0
					? ("mcp sync " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
							.format(new java.util.Date()))
					: message.trim();
			final CmdResult commit = run(repo, "commit", "-m", msg);
			steps.add(step("commit", commit));
			if (commit.exitCode != 0) {
				out.put("ok", JsonValue.ofBoolean(false));
				out.put("error", JsonValue.ofString("commit failed: " + commit.combined()));
				return JsonWriter.write(JsonValue.ofMap(out));
			}
			committed = true;
			out.put("commitMessage", JsonValue.ofString(msg));
		}
		out.put("committed", JsonValue.ofBoolean(committed));

		boolean pushed = false;
		if (push) {
			final CmdResult pushResult = run(repo, DEFAULT_TIMEOUT_SECONDS, "push");
			steps.add(step("push", pushResult));
			if (pushResult.exitCode != 0) {
				// No upstream yet is a common first-run case
				out.put("ok", JsonValue.ofBoolean(false));
				out.put("error", JsonValue.ofString("push failed: " + pushResult.combined()));
				out.put("pushed", JsonValue.ofBoolean(false));
				return JsonWriter.write(JsonValue.ofMap(out));
			}
			pushed = true;
		}
		out.put("pushed", JsonValue.ofBoolean(pushed));
		out.put("ok", JsonValue.ofBoolean(true));
		return JsonWriter.write(JsonValue.ofMap(out));
	}

	private static void addMindMapFiles(final File repo) {
		final List<String> files = new ArrayList<String>();
		collectMmFiles(repo, repo, files, 0);
		if (files.isEmpty()) {
			return;
		}
		final List<String> args = new ArrayList<String>();
		args.add("add");
		args.add("--");
		args.addAll(files);
		run(repo, args.toArray(new String[args.size()]));
	}

	private static void collectMmFiles(final File repoRoot, final File dir, final List<String> out, final int depth) {
		if (depth > 12 || dir == null || !dir.isDirectory()) {
			return;
		}
		final File[] children = dir.listFiles();
		if (children == null) {
			return;
		}
		for (final File child : children) {
			final String name = child.getName();
			if (name.startsWith(".") || "data".equals(name) || "node_modules".equals(name)) {
				continue;
			}
			if (child.isDirectory()) {
				collectMmFiles(repoRoot, child, out, depth + 1);
			}
			else if (name.endsWith(".mm")) {
				out.add(relativize(repoRoot, child));
			}
		}
	}

	private static String relativize(final File root, final File file) {
		final String rootPath = root.getAbsolutePath();
		final String filePath = file.getAbsolutePath();
		if (filePath.startsWith(rootPath)) {
			String rel = filePath.substring(rootPath.length());
			if (rel.startsWith(File.separator)) {
				rel = rel.substring(1);
			}
			return rel.replace('\\', '/');
		}
		return filePath;
	}

	private static Map<String, JsonValue> baseResult(final File repo) {
		final Map<String, JsonValue> out = new LinkedHashMap<String, JsonValue>();
		out.put("repoPath", JsonValue.ofString(repo.getAbsolutePath()));
		return out;
	}

	private static JsonValue step(final String name, final CmdResult result) {
		final Map<String, JsonValue> m = new LinkedHashMap<String, JsonValue>();
		m.put("step", JsonValue.ofString(name));
		m.put("exitCode", JsonValue.ofNumber(result.exitCode));
		m.put("stdout", JsonValue.ofString(trimForJson(result.stdout, 4000)));
		m.put("stderr", JsonValue.ofString(trimForJson(result.stderr, 2000)));
		return JsonValue.ofMap(m);
	}

	private static String trimForJson(final String text, final int max) {
		if (text == null) {
			return "";
		}
		if (text.length() <= max) {
			return text;
		}
		return text.substring(0, max) + "...";
	}

	private static File resolveRepo(final String repoPath) {
		if (repoPath != null && repoPath.trim().length() > 0) {
			final File configured = new File(repoPath.trim());
			if (isGitRepo(configured)) {
				return configured.getAbsoluteFile();
			}
			throw new IllegalArgumentException("Not a git repository: " + configured.getAbsolutePath());
		}
		final String prop = System.getProperty("git.repo.path", "");
		if (prop.trim().length() > 0 && isGitRepo(new File(prop.trim()))) {
			return new File(prop.trim()).getAbsoluteFile();
		}
		final String env = System.getenv("DOCEAR_GIT_REPO");
		if (env != null && env.trim().length() > 0 && isGitRepo(new File(env.trim()))) {
			return new File(env.trim()).getAbsoluteFile();
		}
		final File wd = new File(System.getProperty("org.docear.working.directory",
				System.getProperty("user.dir", ".")));
		File walk = wd.getAbsoluteFile();
		while (walk != null) {
			if (isGitRepo(walk)) {
				return walk;
			}
			walk = walk.getParentFile();
		}
		try {
			final File userDir = new File(Compat.getApplicationUserDirectory());
			File fromUser = userDir.getAbsoluteFile();
			while (fromUser != null) {
				if (isGitRepo(fromUser)) {
					return fromUser;
				}
				fromUser = fromUser.getParentFile();
			}
		}
		catch (Exception e) {
			LogUtils.warn("McpGitService: could not resolve user directory", e);
		}
		throw new IllegalStateException(
				"Git repository not found. Pass repoPath, or set git.repo.path / DOCEAR_GIT_REPO.");
	}

	private static boolean isGitRepo(final File dir) {
		return dir != null && dir.isDirectory() && new File(dir, ".git").exists();
	}

	private static CmdResult run(final File repo, final String... args) {
		return run(repo, 60L, args);
	}

	private static CmdResult run(final File repo, final long timeoutSeconds, final String... args) {
		final List<String> command = new ArrayList<String>();
		command.add("git");
		command.add("-C");
		command.add(repo.getAbsolutePath());
		command.add("-c");
		command.add("core.quotepath=false");
		command.add("-c");
		command.add("core.autocrlf=true");
		command.add("-c");
		command.add("core.safecrlf=false");
		for (final String arg : args) {
			command.add(arg);
		}
		final ProcessBuilder pb = new ProcessBuilder(command);
		pb.directory(repo);
		pb.redirectErrorStream(false);
		final Map<String, String> env = pb.environment();
		env.put("GIT_TERMINAL_PROMPT", "0");
		env.put("LC_ALL", "C");
		try {
			final Process process = pb.start();
			final StreamGobbler outGobbler = new StreamGobbler(process.getInputStream());
			final StreamGobbler errGobbler = new StreamGobbler(process.getErrorStream());
			outGobbler.start();
			errGobbler.start();
			final Watchdog watchdog = new Watchdog(process, timeoutSeconds * 1000L);
			watchdog.start();
			final int code = process.waitFor();
			watchdog.cancel();
			outGobbler.join(2000L);
			errGobbler.join(2000L);
			if (watchdog.timedOut) {
				return new CmdResult(124, outGobbler.text(), errGobbler.text() + "\ntimeout after " + timeoutSeconds + "s");
			}
			return new CmdResult(code, outGobbler.text(), errGobbler.text());
		}
		catch (Exception e) {
			LogUtils.warn("McpGitService git failed: " + e.getMessage(), e);
			return new CmdResult(-1, "", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
		}
	}

	private static final class Watchdog extends Thread {
		private final Process process;
		private final long timeoutMs;
		private volatile boolean cancelled;
		volatile boolean timedOut;

		Watchdog(final Process process, final long timeoutMs) {
			this.process = process;
			this.timeoutMs = timeoutMs;
			setDaemon(true);
		}

		void cancel() {
			cancelled = true;
			interrupt();
		}

		@Override
		public void run() {
			try {
				Thread.sleep(timeoutMs);
				if (!cancelled) {
					timedOut = true;
					process.destroy();
				}
			}
			catch (InterruptedException ignored) {
				// cancelled
			}
		}
	}

	private static final class CmdResult {
		final int exitCode;
		final String stdout;
		final String stderr;

		CmdResult(final int exitCode, final String stdout, final String stderr) {
			this.exitCode = exitCode;
			this.stdout = stdout == null ? "" : stdout;
			this.stderr = stderr == null ? "" : stderr;
		}

		String combined() {
			if (stderr.length() > 0 && stdout.length() > 0) {
				return stderr + " | " + stdout;
			}
			return stderr.length() > 0 ? stderr : stdout;
		}
	}

	private static final class StreamGobbler extends Thread {
		private final InputStream in;
		private final StringBuilder buf = new StringBuilder();

		StreamGobbler(final InputStream in) {
			this.in = in;
			setDaemon(true);
		}

		@Override
		public void run() {
			try {
				final BufferedReader reader = new BufferedReader(new InputStreamReader(in, UTF8));
				String line;
				while ((line = reader.readLine()) != null) {
					if (buf.length() > 0) {
						buf.append('\n');
					}
					buf.append(line);
				}
			}
			catch (Exception ignored) {
				// ignore
			}
		}

		String text() {
			return buf.toString();
		}
	}
}
