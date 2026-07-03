package org.docear.plugin.ai.backend;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.docear.plugin.ai.DocearAiConfig;
import org.freeplane.core.util.LogUtils;

/**
 * 基于 GitHub Copilot CLI 的 AI 后端实现。
 * Windows 下通过 PowerShell 调用 copilot，提示词经 UTF-8 临时文件传递以避免编码问题。
 *
 * 注意：本类使用 Java 1.6 兼容语法编写。
 */
public class CopilotCliBackend implements AiBackend {

    private static final String COPILOT_COMMAND = "copilot";
    private static final long PROCESS_TIMEOUT_MILLIS = 120000; // 120 秒

    private String resolvedCopilotPath;
    private volatile Process activeProcess;
    private volatile boolean cancelRequested;

    private boolean isWindows() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase().contains("windows");
    }

    @Override
    public List<String> generateSubNodes(String prompt, int count) {
        String output = runCopilot(prompt, null);
        return parseSubNodes(output, count);
    }

    @Override
    public String chat(String message) {
        if (message == null || message.trim().length() == 0) {
            return "";
        }
        final StringBuilder full = new StringBuilder();
        chatStreaming(message.trim(), new AiChatStreamListener() {
            public void onChunk(String chunk) {
                full.append(chunk);
            }

            public void onComplete(String fullText) {
            }

            public void onError(String message) {
            }

            public boolean isCancelled() {
                return false;
            }
        });
        return full.toString().trim();
    }

    @Override
    public void chatStreaming(String message, AiChatStreamListener listener) {
        if (message == null || message.trim().length() == 0) {
            if (listener != null) {
                listener.onComplete("");
            }
            return;
        }
        if (listener == null) {
            runCopilot(message.trim(), null);
            return;
        }
        runCopilot(message.trim(), listener);
    }

    @Override
    public void cancelCurrentRequest() {
        cancelRequested = true;
        Process process = activeProcess;
        if (process != null) {
            process.destroy();
        }
    }

    @Override
    public boolean isAvailable() {
        if (isWindows()) {
            return resolveCopilotExecutable() != null;
        }
        return testCopilotDirect(COPILOT_COMMAND);
    }

    private String runCopilot(String prompt, AiChatStreamListener listener) {
        cancelRequested = false;
        String copilotPath = resolveCopilotExecutable();
        if (copilotPath == null) {
            LogUtils.warn("Copilot CLI is not available. Please install it via 'npm install -g @github/copilot'.");
            if (listener != null) {
                listener.onError("\u672a\u68c0\u6d4b\u5230 Copilot CLI\u3002");
                listener.onComplete("");
            }
            return "";
        }

        File promptFile = null;
        File mcpConfigFile = null;
        Process process = null;
        BufferedReader reader = null;
        try {
            promptFile = writePromptToTempFile(prompt);
            final DocearAiConfig aiConfig = new DocearAiConfig();
            if (aiConfig.isMcpEnabled()) {
                mcpConfigFile = writeMcpConfigToTempFile(aiConfig.getMcpUrl());
            }
            ProcessBuilder pb = new ProcessBuilder(buildPowerShellCommand(promptFile, copilotPath, mcpConfigFile));
            pb.redirectErrorStream(true);
            enrichPath(pb.environment());
            process = pb.start();
            activeProcess = process;

            StringBuilder output = new StringBuilder();
            reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
            final BlockingQueue<String> lineQueue = new LinkedBlockingQueue<String>();
            final AtomicBoolean readerFinished = new AtomicBoolean(false);
            final BufferedReader streamReader = reader;
            Thread readerThread = new Thread(new Runnable() {
                public void run() {
                    try {
                        String nextLine;
                        while ((nextLine = streamReader.readLine()) != null) {
                            lineQueue.put(nextLine);
                        }
                    }
                    catch (Exception e) {
                        LogUtils.warn("Copilot CLI reader stopped: " + e.getMessage());
                    }
                    finally {
                        readerFinished.set(true);
                    }
                }
            }, "CopilotCliReader");
            readerThread.setDaemon(true);
            readerThread.start();

            long deadline = System.currentTimeMillis() + PROCESS_TIMEOUT_MILLIS;
            try {
                while (true) {
                    if (cancelRequested || (listener != null && listener.isCancelled())) {
                        process.destroy();
                        readerThread.interrupt();
                        if (listener != null) {
                            listener.onError("\u5df2\u53d6\u6d88\u751f\u6210\u3002");
                            listener.onComplete(output.toString().trim());
                        }
                        return output.toString().trim();
                    }
                    if (System.currentTimeMillis() >= deadline) {
                        process.destroy();
                        readerThread.interrupt();
                        LogUtils.warn("Copilot CLI process timed out.");
                        if (listener != null) {
                            listener.onError("\u8bf7\u6c42\u8d85\u65f6\u3002");
                            listener.onComplete(output.toString().trim());
                        }
                        return output.toString().trim();
                    }
                    String line = lineQueue.poll(500, TimeUnit.MILLISECONDS);
                    if (line == null) {
                        if (readerFinished.get() && lineQueue.isEmpty()) {
                            break;
                        }
                        continue;
                    }
                    String chunk = line + "\n";
                    output.append(chunk);
                    if (listener != null) {
                        listener.onChunk(chunk);
                    }
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroy();
                readerThread.interrupt();
                if (listener != null) {
                    listener.onError("\u8bf7\u6c42\u88ab\u4e2d\u65ad\u3002");
                    listener.onComplete(output.toString().trim());
                }
                return output.toString().trim();
            }

            if (!waitForProcess(process, 5000)) {
                process.destroy();
                LogUtils.warn("Copilot CLI process did not exit cleanly.");
            }

            if (process.exitValue() != 0) {
                LogUtils.warn("Copilot CLI exited with code: " + process.exitValue() + ", output: " + output);
            }

            String result = output.toString().trim();
            if (looksLikeCommandEcho(result)) {
                LogUtils.warn("Copilot CLI returned command echo instead of content: " + result);
                if (listener != null) {
                    listener.onError("\u672a\u6536\u5230\u6709\u6548\u56de\u590d\u3002");
                    listener.onComplete("");
                }
                return "";
            }
            if (looksLikeQuotaExceeded(result)) {
                LogUtils.warn("Copilot CLI reports quota or rate limit: " + result);
                if (listener != null) {
                    listener.onError("\u914d\u989d\u4e0d\u8db3\uff1a\u5df2\u89e6\u53d1 Copilot \u9650\u5236\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5\u3002");
                    listener.onComplete("");
                }
                return "";
            }
            if (looksLikeAuthenticationFailure(result)) {
                LogUtils.warn("Copilot CLI reports authentication issue: " + result);
                if (listener != null) {
                    listener.onError("Copilot \u672a\u767b\u5f55\u6216\u5df2\u8fc7\u671f\uff0c\u8bf7\u5148\u6267\u884c copilot login\u3002");
                    listener.onComplete("");
                }
                return "";
            }
            if (listener != null) {
                listener.onComplete(result);
            }
            return result;
        } catch (Exception e) {
            LogUtils.severe("Error calling Copilot CLI: " + e.getMessage());
            if (listener != null) {
                listener.onError("\u8c03\u7528\u5931\u8d25: " + e.getMessage());
                listener.onComplete("");
            }
            return "";
        } finally {
            activeProcess = null;
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
            if (process != null) {
                process.destroy();
            }
            if (promptFile != null) {
                promptFile.delete();
            }
            if (mcpConfigFile != null) {
                mcpConfigFile.delete();
            }
        }
    }

    private File writeMcpConfigToTempFile(String mcpUrl) throws Exception {
        final String url = mcpUrl != null && mcpUrl.trim().length() > 0
                ? mcpUrl.trim()
                : new DocearAiConfig().getMcpUrl();
        final File file = File.createTempFile("docear_ai_mcp_", ".json");
        FileOutputStream fos = new FileOutputStream(file);
        try {
            // Copilot CLI JSON parser rejects UTF-8 BOM (Invalid JSON at column 1).
            OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8");
            writer.write("{\"mcpServers\":{\"docear\":{\"type\":\"http\",\"url\":\"");
            writer.write(escapeJsonString(url));
            writer.write("\",\"tools\":[\"*\"]}}}");
            writer.flush();
            writer.close();
        }
        finally {
            fos.close();
        }
        return file;
    }

    private static String escapeJsonString(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '"') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private List<String> buildPowerShellCommand(File promptFile, String copilotPath, File mcpConfigFile) {
        String promptPath = escapePowerShellSingleQuoted(promptFile.getAbsolutePath());
        String copilotLiteral = escapePowerShellSingleQuoted(copilotPath);
        StringBuilder script = new StringBuilder();
        script.append("[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false); ");
        script.append("$OutputEncoding = [Console]::OutputEncoding; ");
        script.append("chcp 65001 | Out-Null; ");
        script.append("& '").append(copilotLiteral).append("' -p '@").append(promptPath).append("' -s");
        if (mcpConfigFile != null) {
            String mcpPath = escapePowerShellSingleQuoted(mcpConfigFile.getAbsolutePath());
            script.append(" --allow-all-tools --allow-all-mcp-server-instructions");
            script.append(" --additional-mcp-config '@").append(mcpPath).append("'");
            script.append(" --allow-tool=docear");
        }

        List<String> command = new ArrayList<String>();
        command.add("powershell.exe");
        command.add("-NoProfile");
        command.add("-ExecutionPolicy");
        command.add("Bypass");
        command.add("-Command");
        command.add(script.toString());
        return command;
    }

    private File writePromptToTempFile(String prompt) throws Exception {
        File file = File.createTempFile("docear_ai_prompt_", ".txt");
        FileOutputStream fos = new FileOutputStream(file);
        try {
            fos.write(0xEF);
            fos.write(0xBB);
            fos.write(0xBF);
            OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8");
            writer.write(prompt);
            writer.flush();
            writer.close();
        } finally {
            fos.close();
        }
        return file;
    }

    private String escapePowerShellSingleQuoted(String value) {
        return value.replace("'", "''");
    }

    /**
     * 解析 copilot 可执行文件路径。
     * GUI 应用启动时通常拿不到用户级 PATH，因此需要主动搜索常见安装位置。
     */
    private String resolveCopilotExecutable() {
        if (resolvedCopilotPath != null) {
            return resolvedCopilotPath;
        }

        List<String> candidates = new ArrayList<String>();
        String appData = System.getenv("APPDATA");
        if (appData != null) {
            candidates.add(appData + "\\npm\\copilot.cmd");
            candidates.add(appData + "\\npm\\copilot.ps1");
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) {
            candidates.add(localAppData + "\\npm\\copilot.cmd");
            candidates.add(localAppData + "\\npm\\copilot.ps1");
            candidates.add(localAppData + "\\Microsoft\\WinGet\\Links\\copilot.exe");
        }
        String programFiles = System.getenv("ProgramFiles");
        if (programFiles != null) {
            candidates.add(programFiles + "\\nodejs\\copilot.cmd");
        }
        String programFilesX86 = System.getenv("ProgramFiles(x86)");
        if (programFilesX86 != null) {
            candidates.add(programFilesX86 + "\\nodejs\\copilot.cmd");
        }

        for (int i = 0; i < candidates.size(); i++) {
            File file = new File(candidates.get(i));
            if (file.exists()) {
                resolvedCopilotPath = file.getAbsolutePath();
                LogUtils.info("Resolved Copilot CLI path: " + resolvedCopilotPath);
                return resolvedCopilotPath;
            }
        }

        String[] pathEntries = getEffectivePath().split(";");
        for (int i = 0; i < pathEntries.length; i++) {
            String entry = pathEntries[i].trim();
            if (entry.length() == 0) {
                continue;
            }
            String[] names = new String[] { "copilot.cmd", "copilot.ps1", "copilot.exe", "copilot" };
            for (int j = 0; j < names.length; j++) {
                File file = new File(entry, names[j]);
                if (file.exists()) {
                    resolvedCopilotPath = file.getAbsolutePath();
                    LogUtils.info("Resolved Copilot CLI path from PATH: " + resolvedCopilotPath);
                    return resolvedCopilotPath;
                }
            }
        }

        String whereResult = resolveViaWhereCommand();
        if (whereResult != null) {
            resolvedCopilotPath = whereResult;
            LogUtils.info("Resolved Copilot CLI path via where: " + resolvedCopilotPath);
            return resolvedCopilotPath;
        }

        return null;
    }

    private String resolveViaWhereCommand() {
        Process process = null;
        BufferedReader reader = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "where", "copilot");
            pb.redirectErrorStream(true);
            enrichPath(pb.environment());
            process = pb.start();
            reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.length() == 0) {
                    continue;
                }
                File file = new File(trimmed);
                if (file.exists()) {
                    if (!waitForProcess(process, 5000)) {
                        process.destroy();
                    }
                    return file.getAbsolutePath();
                }
            }
            if (!waitForProcess(process, 5000)) {
                process.destroy();
            }
        } catch (Exception e) {
            LogUtils.warn("Failed to resolve copilot via where: " + e.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
            if (process != null) {
                process.destroy();
            }
        }
        return null;
    }

    private String getEffectivePath() {
        StringBuilder path = new StringBuilder();
        appendPathValue(path, System.getenv("PATH"));
        appendPathValue(path, queryRegistryPath("HKCU\\Environment"));
        appendPathValue(path, queryRegistryPath("HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment"));

        String appData = System.getenv("APPDATA");
        if (appData != null) {
            appendPathValue(path, appData + "\\npm");
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) {
            appendPathValue(path, localAppData + "\\Microsoft\\WinGet\\Links");
        }
        String programFiles = System.getenv("ProgramFiles");
        if (programFiles != null) {
            appendPathValue(path, programFiles + "\\nodejs");
        }
        return path.toString();
    }

    private void appendPathValue(StringBuilder target, String value) {
        if (value == null || value.trim().length() == 0) {
            return;
        }
        if (target.length() > 0) {
            target.append(";");
        }
        target.append(value.trim());
    }

    private String queryRegistryPath(String registryKey) {
        Process process = null;
        BufferedReader reader = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("reg", "query", registryKey, "/v", "Path");
            pb.redirectErrorStream(true);
            process = pb.start();
            reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("Path") && trimmed.contains("REG_")) {
                    int index = trimmed.indexOf("REG_");
                    String tail = trimmed.substring(index);
                    int space = tail.indexOf("    ");
                    if (space >= 0 && space + 4 < tail.length()) {
                        return tail.substring(space + 4).trim();
                    }
                }
            }
            if (!waitForProcess(process, 5000)) {
                process.destroy();
            }
        } catch (Exception e) {
            LogUtils.warn("Failed to read registry PATH from " + registryKey + ": " + e.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
            if (process != null) {
                process.destroy();
            }
        }
        return null;
    }

    private void enrichPath(Map<String, String> environment) {
        String effectivePath = getEffectivePath();
        if (effectivePath.length() > 0) {
            environment.put("PATH", effectivePath);
        }
    }

    private boolean waitForProcess(Process process, long timeoutMillis) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while ((System.currentTimeMillis() - startTime) < timeoutMillis) {
            try {
                process.exitValue();
                return true;
            } catch (IllegalThreadStateException e) {
                Thread.sleep(200);
            }
        }
        return false;
    }

    private List<String> parseSubNodes(String output, int count) {
        List<String> subNodes = new ArrayList<String>();
        if (output == null || output.trim().length() == 0) {
            return subNodes;
        }

        String[] lines = output.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.length() == 0 || looksLikeCommandEcho(trimmed)) {
                continue;
            }
            trimmed = trimmed.replaceAll("^[-*\\d.]+\\s*", "");
            if (trimmed.length() > 0 && subNodes.size() < count) {
                subNodes.add(trimmed);
            }
        }
        return subNodes;
    }

    private boolean looksLikeCommandEcho(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase();
        return lower.contains("copilot -p")
                || lower.contains("powershell.exe")
                || lower.contains("get-content -literalpath")
                || lower.contains("objectnotfound")
                || lower.contains("commandnotfoundexception");
    }

    private boolean looksLikeQuotaExceeded(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase();
        return lower.contains("rate limit")
                || lower.contains("quota")
                || lower.contains("too many requests")
                || lower.contains("429")
                || lower.contains("usage limit")
                || lower.contains("premium request")
                || lower.contains("usage has been capped")
                || lower.contains("you have reached")
                || lower.contains("monthly limit");
    }

    private boolean looksLikeAuthenticationFailure(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase();
        return lower.contains("not signed in")
                || lower.contains("not authenticated")
                || lower.contains("auth required")
                || lower.contains("unauthorized")
                || lower.contains("please sign in")
                || lower.contains("401")
                || lower.contains("403");
    }

    private boolean testCopilotDirect(String command) {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command, "--version");
            pb.redirectErrorStream(true);
            process = pb.start();
            if (!waitForProcess(process, 10000)) {
                process.destroy();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
