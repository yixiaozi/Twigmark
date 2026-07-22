package org.freeplane.view.swing.features.git;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.freeplane.core.util.LogUtils;

final class GitCommand {
	static final class Result {
		final int exitCode;
		final List<String> output;
		final List<String> errors;
		final String commandLine;
		final String rawText;

		Result(final int exitCode, final List<String> output, final List<String> errors, final String commandLine,
		    final String rawText) {
			this.exitCode = exitCode;
			this.output = output;
			this.errors = errors;
			this.commandLine = commandLine;
			this.rawText = rawText;
		}

		String errorText() {
			return joinLines(filterNoise(errors));
		}

		String outputText() {
			return joinLines(filterNoise(output));
		}

		String messageText() {
			final String err = errorText();
			final String out = outputText();
			if (err.length() > 0 && out.length() > 0) {
				return err + " | " + out;
			}
			if (err.length() > 0) {
				return err;
			}
			return out;
		}

		/** Drop CRLF/LF autocrlf chatter that floods the Git panel. */
		static List<String> filterNoise(final List<String> lines) {
			if (lines == null || lines.isEmpty()) {
				return lines;
			}
			final List<String> kept = new ArrayList<String>();
			for (int i = 0; i < lines.size(); i++) {
				final String line = lines.get(i);
				if (line == null) {
					continue;
				}
				final String lower = line.toLowerCase();
				if (lower.indexOf("lf will be replaced by crlf") >= 0
						|| lower.indexOf("crlf will be replaced by lf") >= 0
						|| lower.indexOf("warning: in the working copy of") >= 0) {
					continue;
				}
				kept.add(line);
			}
			return kept;
		}

		String failureMessage(final String actionLabel) {
			final String detail = messageText();
			if (detail.length() > 0) {
				return actionLabel + "失败: " + detail;
			}
			final StringBuilder sb = new StringBuilder();
			sb.append(actionLabel).append("失败 (退出码 ").append(exitCode).append(')');
			if (commandLine != null && commandLine.length() > 0) {
				sb.append("。请先在命令行测试: ").append(commandLine);
			}
			return sb.toString();
		}

		private static String joinLines(final List<String> lines) {
			final StringBuilder sb = new StringBuilder();
			for (int i = 0; i < lines.size(); i++) {
				if (i > 0) {
					sb.append(' ');
				}
				sb.append(lines.get(i));
			}
			return sb.toString().trim();
		}
	}

	private GitCommand() {
	}

	static Result run(final File repoDir, final String... args) {
		return runInternal(repoDir, false, args);
	}

	static Result runRemote(final File repoDir, final String... args) {
		return runInternal(repoDir, true, args);
	}

	private static Result runInternal(final File repoDir, final boolean remote, final String... args) {
		final List<String> output = new ArrayList<String>();
		final List<String> errors = new ArrayList<String>();
		int exitCode = -1;
		String commandLine = "";
		String rawText = "";
		if (repoDir == null) {
			errors.add("Git 仓库未配置");
			return new Result(exitCode, output, errors, commandLine, "");
		}
		Process process = null;
		try {
			final String[] command = buildCommand(repoDir, remote, args);
			commandLine = formatCommandForLog(repoDir, command);
			LogUtils.info("Git: " + commandLine);
			final ProcessBuilder builder = new ProcessBuilder(command);
			builder.directory(repoDir);
			builder.redirectErrorStream(true);
			ensureMinimalEnvironment(builder);
			process = builder.start();
			final StreamCollector collector = readProcessOutput(process);
			exitCode = process.waitFor();
			collector.join();
			final byte[] combined = collector.getBytes();
			rawText = decodeProcessOutput(combined);
			appendDecodedLines(rawText, output);
			// Keep CRLF autocrlf chatter out of both display lists.
			final List<String> cleanOutput = Result.filterNoise(output);
			output.clear();
			output.addAll(cleanOutput);
			if (exitCode != 0 && output.isEmpty()) {
				errors.add("Git 未返回错误详情");
			}
			else if (exitCode != 0) {
				errors.addAll(output);
			}
		}
		catch (Exception e) {
			LogUtils.warn("Git command failed: " + e.getMessage(), e);
			errors.add(e.getMessage() != null ? e.getMessage() : e.toString());
		}
		finally {
			if (process != null) {
				process.destroy();
			}
		}
		final Result result = new Result(exitCode, output, errors, commandLine, rawText);
		if (exitCode != 0) {
			LogUtils.warn("Git exit " + exitCode + ": " + result.messageText());
		}
		return result;
	}

	private static String formatCommandForLog(final File repoDir, final String[] command) {
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < command.length; i++) {
			if (i > 0) {
				sb.append(' ');
			}
			final String part = command[i];
			if (part.indexOf(' ') >= 0) {
				sb.append('"').append(part).append('"');
			}
			else {
				sb.append(part);
			}
		}
		sb.append("  (cwd: ").append(repoDir.getAbsolutePath()).append(')');
		return sb.toString();
	}

	private static void ensureMinimalEnvironment(final ProcessBuilder builder) {
		final Map<String, String> env = builder.environment();
		if (!env.containsKey("HOME") && env.containsKey("USERPROFILE")) {
			env.put("HOME", env.get("USERPROFILE"));
		}
		copyIfMissing(env, "USERPROFILE");
		copyIfMissing(env, "APPDATA");
		copyIfMissing(env, "LOCALAPPDATA");
		copyIfMissing(env, "SystemRoot");
	}

	private static void copyIfMissing(final Map<String, String> env, final String key) {
		if (env.containsKey(key)) {
			return;
		}
		final String value = System.getenv(key);
		if (value != null && value.length() > 0) {
			env.put(key, value);
		}
	}

	private static String[] buildCommand(final File repoDir, final boolean remote, final String[] args) {
		// Inject per-invocation config so CRLF/LF warnings don't fail or spam the UI.
		final String[] command = new String[args.length + 9];
		int index = 0;
		command[index++] = resolveGitExecutable();
		command[index++] = "-C";
		command[index++] = repoDir.getAbsolutePath();
		command[index++] = "-c";
		command[index++] = "core.quotepath=false";
		command[index++] = "-c";
		command[index++] = "core.autocrlf=true";
		command[index++] = "-c";
		command[index++] = "core.safecrlf=false";
		System.arraycopy(args, 0, command, index, args.length);
		return command;
	}

	private static final class StreamCollector {
		private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		private final Thread thread;
		private IOException ioException;

		StreamCollector(final InputStream stream) {
			thread = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						final byte[] chunk = new byte[4096];
						int read;
						while ((read = stream.read(chunk)) != -1) {
							buffer.write(chunk, 0, read);
						}
					}
					catch (IOException e) {
						ioException = e;
					}
				}
			}, "git-output-reader");
			thread.setDaemon(true);
			thread.start();
		}

		void join() throws InterruptedException {
			thread.join(120000L);
		}

		byte[] getBytes() throws IOException {
			if (ioException != null) {
				throw ioException;
			}
			return buffer.toByteArray();
		}
	}

	private static StreamCollector readProcessOutput(final Process process) {
		return new StreamCollector(process.getInputStream());
	}

	private static String decodeProcessOutput(final byte[] bytes) {
		if (bytes == null || bytes.length == 0) {
			return "";
		}
		final String utf8 = decode(bytes, Charset.forName("UTF-8"));
		if (isWindows()) {
			final String gbk = decode(bytes, Charset.forName("GBK"));
			if (looksLikeValidGitPaths(gbk) && !looksLikeValidGitPaths(utf8)) {
				return gbk;
			}
		}
		return utf8;
	}

	private static boolean isWindows() {
		final String os = System.getProperty("os.name", "");
		return os.toLowerCase().indexOf("win") >= 0;
	}

	private static boolean looksLikeValidGitPaths(final String text) {
		if (text == null || text.length() == 0) {
			return false;
		}
		return text.indexOf('\uFFFD') < 0;
	}

	private static void appendDecodedLines(final String text, final List<String> target) {
		if (text == null || text.length() == 0) {
			return;
		}
		final String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
		final String[] lines = normalized.split("\n", -1);
		for (int i = 0; i < lines.length; i++) {
			final String line = lines[i].trim();
			if (line.length() > 0) {
				target.add(line);
			}
		}
	}

	private static String decode(final byte[] bytes, final Charset charset) {
		return new String(bytes, charset);
	}

	static String resolveCurrentBranch(final File repoDir) {
		final Result result = run(repoDir, "rev-parse", "--abbrev-ref", "HEAD");
		if (result.exitCode == 0 && !result.output.isEmpty()) {
			return result.output.get(0);
		}
		return "master";
	}

	static String[] buildPullArgs(final File repoDir, final boolean ffOnly) {
		final String branch = resolveCurrentBranch(repoDir);
		if (ffOnly) {
			return new String[] { "pull", "--ff-only", "--progress", "origin", branch };
		}
		return new String[] { "pull", "--progress", "origin", branch };
	}

	static long resolveWorkingTreeSize(final File repository, final String relativePath) {
		if (repository == null || relativePath == null || relativePath.length() == 0) {
			return -1L;
		}
		final File file = new File(repository, relativePath.replace('/', File.separatorChar));
		if (file.isFile()) {
			return file.length();
		}
		return resolveSizeFromGitIndex(repository, relativePath);
	}

	private static long resolveSizeFromGitIndex(final File repository, final String relativePath) {
		final Result lsResult = run(repository, "ls-files", "-s", "--", relativePath);
		if (lsResult.exitCode != 0 || lsResult.output.isEmpty()) {
			return -1L;
		}
		final String[] parts = lsResult.output.get(0).trim().split("\\s+");
		if (parts.length >= 4) {
			try {
				return Long.parseLong(parts[3]);
			}
			catch (NumberFormatException e) {
				LogUtils.warn("Git: could not parse ls-files size for " + relativePath);
			}
		}
		return -1L;
	}

	static String resolveGitExecutable() {
		final String gitHome = System.getenv("GIT_HOME");
		if (gitHome != null && gitHome.trim().length() > 0) {
			final File gitExe = new File(gitHome.trim(), "bin/git.exe");
			if (gitExe.isFile()) {
				return gitExe.getAbsolutePath();
			}
		}
		final File programFilesGit = new File("C:\\Program Files\\Git\\cmd\\git.exe");
		if (programFilesGit.isFile()) {
			return programFilesGit.getAbsolutePath();
		}
		return "git";
	}

	static String formatFileSize(final long bytes) {
		if (bytes < 0) {
			return "-";
		}
		if (bytes < 1024) {
			return bytes + " B";
		}
		if (bytes < 1024 * 1024) {
			return (bytes / 1024) + " KB";
		}
		return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
	}

	static String basename(final String path) {
		if (path == null || path.length() == 0) {
			return "";
		}
		final int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
		if (slash >= 0 && slash + 1 < path.length()) {
			return path.substring(slash + 1);
		}
		return path;
	}

	static String formatGitDate(final String rawDate) {
		if (rawDate == null || rawDate.length() == 0) {
			return "";
		}
		final int plus = rawDate.indexOf(" +");
		final int minus = rawDate.indexOf(" -");
		int cut = -1;
		if (plus > 0) {
			cut = plus;
		}
		if (minus > 10 && (cut < 0 || minus < cut)) {
			cut = minus;
		}
		if (cut > 0) {
			return rawDate.substring(0, cut).trim();
		}
		return rawDate.trim();
	}

	static String statusLabel(final GitFileChange.Status status) {
		switch (status) {
		case ADDED:
			return "新增";
		case DELETED:
			return "删除";
		default:
			return "修改";
		}
	}
}
