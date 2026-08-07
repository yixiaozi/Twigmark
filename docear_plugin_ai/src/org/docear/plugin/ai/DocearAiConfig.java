package org.docear.plugin.ai;

import java.io.File;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.Compat;

/**
 * Docear AI 插件配置管理类。
 * 统一管理 AI 后端的选择、参数等设置。
 * 未来切换到 OpenAI 等模型时，只需在此类中扩展配置项即可。
 */
public class DocearAiConfig {

    private static final String PROPERTY_AI_ENABLED = "ai.enabled";
    private static final String PROPERTY_AI_BACKEND = "ai.backend"; // openrouter, copilot_cli, openai
    private static final String PROPERTY_AI_MODEL = "ai.model";
    private static final String PROPERTY_AI_LLM_PROFILE_ID = "ai.llm.profileId";
    private static final String PROPERTY_AI_TEMPERATURE = "ai.temperature";
    private static final String PROPERTY_AI_PROMPT_TEMPLATE_FILE = "ai.prompt_template_file";
    private static final String PROPERTY_AI_INTERACTION_LOG_DIR = "ai.interaction_log_dir";
    private static final String AI_HOME_DIR_NAME = "ai";
    private static final String PROMPT_FILE_NAME = "AI\u63d0\u793a\u8bcd.mm";
    private static final String PROPERTY_AI_LOG_PROMPT = "ai.log_prompt";
    private static final String AI_LOGS_DIR_NAME = "ai_logs";
    private static final String LOG_DIR_NAME = "logs";
    private static final String INTERACTION_HISTORY_DIR_NAME = "interaction_history";
    private static final String PROPERTY_AI_MAX_CONTEXT_TURNS = "ai.max_context_turns";
    private static final String PROPERTY_AI_MAX_CONTEXT_CHARS = "ai.max_context_chars";
    private static final String PROPERTY_AI_MAX_MM_CHAT_ROUNDS = "ai.max_mm_chat_rounds";
    private static final String PROPERTY_AI_MAX_LINKED_FILES = "ai.max_linked_files";
    private static final String PROPERTY_AI_MAX_FILE_SIZE_BYTES = "ai.max_file_size_bytes";
    private static final String PROPERTY_AI_MAX_TOTAL_CONTEXT_CHARS = "ai.max_total_context_chars";
    private static final String PROPERTY_AI_MAX_WORKSPACE_PLAN_CHARS = "ai.max_workspace_plan_chars";
    private static final String PROPERTY_AI_WORKSPACE_PLANS_ENABLED = "ai.workspace_plans_enabled";
    private static final String PROPERTY_AI_WORKSPACE_PLANS_CACHE_SECONDS = "ai.workspace_plans_cache_seconds";
    private static final String PROPERTY_AI_MAX_WORKSPACE_PLAN_ITEMS = "ai.max_workspace_plan_items_per_section";
    private static final String PROPERTY_AI_MAX_OUTBOUND_PROMPT_CHARS = "ai.max_outbound_prompt_chars";
    private static final String PROPERTY_AI_WORKSPACE_FILE_INDEX_ENABLED = "ai.workspace_file_index_enabled";
    private static final String PROPERTY_AI_MAX_WORKSPACE_FILE_INDEX_CHARS = "ai.max_workspace_file_index_chars";
    private static final String PROPERTY_AI_MAX_WORKSPACE_FILE_INDEX_ITEMS = "ai.max_workspace_file_index_items";
    private static final String PROPERTY_AI_WORKSPACE_FILE_INDEX_ALL_FILES = "ai.workspace_file_index_all_files";
    private static final String PROPERTY_AI_WORKSPACE_FILE_INDEX_CACHE_SECONDS = "ai.workspace_file_index_cache_seconds";
    private static final String PROPERTY_AI_MONTHLY_QUOTA = "ai.monthly_quota";
    private static final String PROPERTY_AI_USAGE_WARNING_COOLDOWN_MINUTES = "ai.usage_warning_cooldown_minutes";
    private static final String PROPERTY_AI_WORKSPACE_SNAPSHOT_EXPORT_ENABLED = "ai.workspace_snapshot_export_enabled";
    private static final String PROPERTY_AI_WORKSPACE_SNAPSHOT_DIR = "ai.workspace_snapshot_directory";
    private static final String PROPERTY_AI_WORKSPACE_SNAPSHOT_DEBOUNCE_MS = "ai.workspace_snapshot_debounce_ms";
    private static final String PROPERTY_AI_WORKSPACE_SNAPSHOT_MIN_INTERVAL_MS = "ai.workspace_snapshot_min_interval_ms";
    private static final String PROPERTY_AI_WORKSPACE_SNAPSHOT_TREE_INTERVAL_MS = "ai.workspace_snapshot_tree_interval_ms";
    private static final String PROPERTY_AI_MCP_ENABLED = "ai.mcp.enabled";
    private static final String PROPERTY_AI_MCP_URL = "ai.mcp.url";
    private static final String DEFAULT_MCP_URL = "http://127.0.0.1:7720/mcp";
    private static final String WORKSPACE_SNAPSHOT_DIR_NAME = "workspace_snapshots";

    public DocearAiConfig() {
    }

    public boolean isAiEnabled() {
        return ResourceController.getResourceController().getBooleanProperty(PROPERTY_AI_ENABLED);
    }

    public String getBackendType() {
        final String configured = ResourceController.getResourceController().getProperty(PROPERTY_AI_BACKEND, "");
        if (configured != null && configured.trim().length() > 0) {
            return configured.trim();
        }
        // Prefer shared OpenRouter / webchat LLM when available.
        try {
            if (org.docear.plugin.core.settings.McpRuntimeFacade.safeIsLlmConfigured()) {
                return "openrouter";
            }
        }
        catch (Exception ignored) {
        }
        return "copilot_cli";
    }

    public void setBackendType(final String backendType) {
        ResourceController.getResourceController().setProperty(PROPERTY_AI_BACKEND,
                backendType == null ? "" : backendType.trim());
    }

    public String getModel() {
        return ResourceController.getResourceController().getProperty(PROPERTY_AI_MODEL, "openai/gpt-4o-mini");
    }

    public String getLlmProfileId() {
        return ResourceController.getResourceController().getProperty(PROPERTY_AI_LLM_PROFILE_ID, "");
    }

    public void setLlmProfileId(final String profileId) {
        ResourceController.getResourceController().setProperty(PROPERTY_AI_LLM_PROFILE_ID,
                profileId == null ? "" : profileId.trim());
    }

    public double getTemperature() {
        try {
            return Double.parseDouble(ResourceController.getResourceController().getProperty(PROPERTY_AI_TEMPERATURE, "0.7"));
        } catch (NumberFormatException e) {
            return 0.7;
        }
    }

    public String getAiHomeDirectory() {
        return Compat.getApplicationUserDirectory() + File.separator + AI_HOME_DIR_NAME;
    }

    public String getDefaultPromptTemplateFile() {
        return getAiHomeDirectory() + File.separator + PROMPT_FILE_NAME;
    }

    public String getDefaultInteractionLogDirectory() {
        return getAiHomeDirectory() + File.separator + INTERACTION_HISTORY_DIR_NAME;
    }

    public String getDefaultLocalLogDirectory() {
        return getAiHomeDirectory() + File.separator + LOG_DIR_NAME;
    }

    public String getDefaultWorkspaceSnapshotDirectory() {
        return getAiHomeDirectory() + File.separator + WORKSPACE_SNAPSHOT_DIR_NAME;
    }

    public String getPromptTemplateFile() {
        return ResourceController.getResourceController().getProperty(
                PROPERTY_AI_PROMPT_TEMPLATE_FILE, getDefaultPromptTemplateFile());
    }

    public String getInteractionLogDirectory() {
        return ResourceController.getResourceController().getProperty(
                PROPERTY_AI_INTERACTION_LOG_DIR, getDefaultInteractionLogDirectory());
    }

    public String getAiLogsDirectoryName() {
        return AI_LOGS_DIR_NAME;
    }

    public boolean isLogPromptEnabled() {
        return ResourceController.getResourceController().getBooleanProperty(PROPERTY_AI_LOG_PROMPT);
    }

    public int getMaxContextTurns() {
        try {
            return Integer.parseInt(ResourceController.getResourceController().getProperty(
                    PROPERTY_AI_MAX_CONTEXT_TURNS, "8"));
        } catch (NumberFormatException e) {
            return 8;
        }
    }

    public int getMaxContextChars() {
        try {
            return Integer.parseInt(ResourceController.getResourceController().getProperty(
                    PROPERTY_AI_MAX_CONTEXT_CHARS, "12000"));
        } catch (NumberFormatException e) {
            return 12000;
        }
    }

    public int getMaxMmChatRounds() {
        try {
            return Integer.parseInt(ResourceController.getResourceController().getProperty(
                    PROPERTY_AI_MAX_MM_CHAT_ROUNDS, "30"));
        } catch (NumberFormatException e) {
            return 30;
        }
    }

    public int getMaxLinkedFiles() {
        try {
            return Integer.parseInt(ResourceController.getResourceController().getProperty(
                    PROPERTY_AI_MAX_LINKED_FILES, "30"));
        } catch (NumberFormatException e) {
            return 30;
        }
    }

    public int getMaxFileSizeBytes() {
        try {
            return Integer.parseInt(ResourceController.getResourceController().getProperty(
                    PROPERTY_AI_MAX_FILE_SIZE_BYTES, "500000"));
        } catch (NumberFormatException e) {
            return 500000;
        }
    }

    public int getMaxTotalContextChars() {
        try {
            return Integer.parseInt(ResourceController.getResourceController().getProperty(
                    PROPERTY_AI_MAX_TOTAL_CONTEXT_CHARS, "120000"));
        } catch (NumberFormatException e) {
            return 120000;
        }
    }

    public int getMaxWorkspacePlanChars() {
        try {
            return Integer.parseInt(ResourceController.getResourceController().getProperty(
                    PROPERTY_AI_MAX_WORKSPACE_PLAN_CHARS, "12000"));
        } catch (NumberFormatException e) {
            return 12000;
        }
    }

    public boolean isWorkspacePlansEnabled() {
        String value = ResourceController.getResourceController().getProperty(
                PROPERTY_AI_WORKSPACE_PLANS_ENABLED, "true");
        return !"false".equalsIgnoreCase(value) && !"0".equals(value);
    }

    public long getWorkspacePlansCacheTtlMs() {
        try {
            int seconds = Integer.parseInt(ResourceController.getResourceController().getProperty(
                    PROPERTY_AI_WORKSPACE_PLANS_CACHE_SECONDS, "180"));
            if (seconds <= 0) {
                return 180000L;
            }
            return seconds * 1000L;
        } catch (NumberFormatException e) {
            return 180000L;
        }
    }

    public int getMaxWorkspacePlanItemsPerSection() {
        try {
            return Integer.parseInt(ResourceController.getResourceController().getProperty(
                    PROPERTY_AI_MAX_WORKSPACE_PLAN_ITEMS, "60"));
        } catch (NumberFormatException e) {
            return 60;
        }
    }

    public int getMaxOutboundPromptChars() {
        try {
            return Integer.parseInt(ResourceController.getResourceController().getProperty(
                    PROPERTY_AI_MAX_OUTBOUND_PROMPT_CHARS, "100000"));
        } catch (NumberFormatException e) {
            return 100000;
        }
    }

    public boolean isWorkspaceFileIndexEnabled() {
        String value = ResourceController.getResourceController().getProperty(
                PROPERTY_AI_WORKSPACE_FILE_INDEX_ENABLED, "true");
        return !"false".equalsIgnoreCase(value) && !"0".equals(value);
    }

    public int getMaxWorkspaceFileIndexChars() {
        try {
            return Integer.parseInt(ResourceController.getResourceController().getProperty(
                    PROPERTY_AI_MAX_WORKSPACE_FILE_INDEX_CHARS, "12000"));
        } catch (NumberFormatException e) {
            return 12000;
        }
    }

    public int getMaxWorkspaceFileIndexItemsPerSection() {
        try {
            return Integer.parseInt(ResourceController.getResourceController().getProperty(
                    PROPERTY_AI_MAX_WORKSPACE_FILE_INDEX_ITEMS, "2000"));
        } catch (NumberFormatException e) {
            return 2000;
        }
    }

    public boolean isWorkspaceFileIndexAllFiles() {
        String value = ResourceController.getResourceController().getProperty(
                PROPERTY_AI_WORKSPACE_FILE_INDEX_ALL_FILES, "true");
        return !"false".equalsIgnoreCase(value) && !"0".equals(value);
    }

    public long getWorkspaceFileIndexCacheTtlMs() {
        try {
            int seconds = Integer.parseInt(ResourceController.getResourceController().getProperty(
                    PROPERTY_AI_WORKSPACE_FILE_INDEX_CACHE_SECONDS, "300"));
            if (seconds <= 0) {
                return 300000L;
            }
            return seconds * 1000L;
        } catch (NumberFormatException e) {
            return 300000L;
        }
    }

    /**
     * Copilot CLI 月度调用次数上限。GitHub Pro 约 300 次/月 Premium 请求，
     * 这里设为保守值 250 以避免静默降级。设置为 0 或负数表示不启用限制。
     */
    public int getMonthlyQuota() {
        try {
            return Integer.parseInt(ResourceController.getResourceController().getProperty(
                    PROPERTY_AI_MONTHLY_QUOTA, "250"));
        } catch (NumberFormatException e) {
            return 250;
        }
    }

    /**
     * 用量警告的冷却时间（毫秒）。达到配额 80% 或更高时，
     * 在此冷却期内不重复弹出警告，避免打扰。
     */
    public long getUsageWarningCooldownMs() {
        try {
            int minutes = Integer.parseInt(ResourceController.getResourceController().getProperty(
                    PROPERTY_AI_USAGE_WARNING_COOLDOWN_MINUTES, "360"));
            if (minutes <= 0) {
                minutes = 360;
            }
            return (long) minutes * 60L * 1000L;
        } catch (NumberFormatException e) {
            return 360L * 60L * 1000L;
        }
    }

    public boolean isWorkspaceSnapshotExportEnabled() {
        String value = ResourceController.getResourceController().getProperty(
                PROPERTY_AI_WORKSPACE_SNAPSHOT_EXPORT_ENABLED, "true");
        return !"false".equalsIgnoreCase(value) && !"0".equals(value);
    }

    public String getWorkspaceSnapshotDirectory() {
        return ResourceController.getResourceController().getProperty(
                PROPERTY_AI_WORKSPACE_SNAPSHOT_DIR, getDefaultWorkspaceSnapshotDirectory());
    }

    public int getWorkspaceSnapshotDebounceMs() {
        try {
            int ms = Integer.parseInt(ResourceController.getResourceController().getProperty(
                    PROPERTY_AI_WORKSPACE_SNAPSHOT_DEBOUNCE_MS, "30000"));
            return ms < 5000 ? 30000 : ms;
        } catch (NumberFormatException e) {
            return 30000;
        }
    }

    /** 两次自动导出之间的最小间隔，避免编辑/保存时连续扫盘。 */
    public long getWorkspaceSnapshotMinIntervalMs() {
        try {
            long ms = Long.parseLong(ResourceController.getResourceController().getProperty(
                    PROPERTY_AI_WORKSPACE_SNAPSHOT_MIN_INTERVAL_MS, "180000"));
            return ms < 30000L ? 180000L : ms;
        } catch (NumberFormatException e) {
            return 180000L;
        }
    }

    /** 完整文件目录树导出的最小间隔（比条目快照更重）。 */
    public long getWorkspaceSnapshotTreeIntervalMs() {
        try {
            long ms = Long.parseLong(ResourceController.getResourceController().getProperty(
                    PROPERTY_AI_WORKSPACE_SNAPSHOT_TREE_INTERVAL_MS, "900000"));
            return ms < 60000L ? 900000L : ms;
        } catch (NumberFormatException e) {
            return 900000L;
        }
    }

    /** 内置 AI 聊天是否通过 Copilot CLI 启用 Docear MCP 工具。 */
    public boolean isMcpEnabled() {
        String value = ResourceController.getResourceController().getProperty(PROPERTY_AI_MCP_ENABLED, "true");
        return !"false".equalsIgnoreCase(value) && !"0".equals(value);
    }

    /** Docear MCP HTTP 端点（需 Docear 运行且 org.docear.plugin.mcp 已加载）。 */
    public String getMcpUrl() {
        return ResourceController.getResourceController().getProperty(PROPERTY_AI_MCP_URL, DEFAULT_MCP_URL);
    }

    /** True when OpenRouter / OpenAI-compatible shared profile is usable. */
    public boolean isOpenRouterAvailable() {
        try {
            return org.docear.plugin.core.settings.McpRuntimeFacade.safeIsLlmConfigured();
        }
        catch (Exception e) {
            return false;
        }
    }
}
