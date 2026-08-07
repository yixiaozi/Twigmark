package org.docear.plugin.ai.chat;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.docear.plugin.ai.DocearAiConfig;
import org.docear.plugin.core.settings.McpRuntimeFacade;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.MapModel;

/**
 * 管理每个思维导图对应的聊天会话。
 * 完整历史优先读写 webchat 数据库（与网页聊天统一）；文件与 .mm 扩展作为兼容/备份。
 */
public class AiChatSessionManager {

    private final AiChatSessionStore store;
    private final Map<String, AiChatSession> cache = new HashMap<String, AiChatSession>();
    private final Map<String, Integer> dbPersistedCount = new HashMap<String, Integer>();

    public AiChatSessionManager() {
        this(new AiChatSessionStore());
    }

    public AiChatSessionManager(AiChatSessionStore store) {
        this.store = store;
        this.store.ensureDirectoryExists();
    }

    public AiChatSessionStore getStore() {
        return store;
    }

    public AiChatSession getOrCreateSession(MapModel map) {
        String mapKey = resolveMapKey(map);
        if (cache.containsKey(mapKey)) {
            return cache.get(mapKey);
        }

        AiChatSession dbSession = loadFromDatabase(mapKey);
        if (dbSession != null && !dbSession.getMessages().isEmpty()) {
            cache.put(mapKey, dbSession);
            dbPersistedCount.put(mapKey, Integer.valueOf(dbSession.getMessages().size()));
            syncToMapExtension(map, dbSession);
            return dbSession;
        }

        AiChatSession fileSession = store.load(mapKey);
        if (!fileSession.getMessages().isEmpty()) {
            cache.put(mapKey, fileSession);
            migrateFileSessionToDatabase(map, fileSession);
            syncToMapExtension(map, fileSession);
            return fileSession;
        }

        AiChatHistoryExtension extension = AiChatHistoryExtension.get(map);
        if (extension != null && extension.getSession() != null
                && !extension.getSession().getMessages().isEmpty()) {
            cache.put(mapKey, extension.getSession());
            store.save(extension.getSession());
            migrateFileSessionToDatabase(map, extension.getSession());
            return extension.getSession();
        }

        cache.put(mapKey, fileSession);
        dbPersistedCount.put(mapKey, Integer.valueOf(0));
        syncToMapExtension(map, fileSession);
        return fileSession;
    }

    public void saveSession(MapModel map, AiChatSession session) {
        if (session == null) {
            return;
        }
        String mapKey = resolveMapKey(map);
        cache.put(mapKey, session);
        store.save(session);
        syncToMapExtension(map, session);
        persistNewTurnsToDatabase(map, session);
    }

    public void clearSession(MapModel map) {
        String mapKey = resolveMapKey(map);
        AiChatSession session = cache.containsKey(mapKey) ? cache.get(mapKey) : store.load(mapKey);
        if (session == null) {
            session = new AiChatSession(mapKey);
        }
        session.clear();
        cache.put(mapKey, session);
        dbPersistedCount.put(mapKey, Integer.valueOf(0));
        store.save(session);
        syncToMapExtension(map, session);
        final McpRuntimeFacade.Backend backend = McpRuntimeFacade.get();
        if (backend != null) {
            try {
                backend.clearDesktopConversation(mapKey);
            }
            catch (Exception e) {
                LogUtils.warn("clearDesktopConversation failed: " + e.getMessage());
            }
        }
    }

    private void persistNewTurnsToDatabase(MapModel map, AiChatSession session) {
        final McpRuntimeFacade.Backend backend = McpRuntimeFacade.get();
        if (backend == null || session == null) {
            return;
        }
        final String mapKey = resolveMapKey(map);
        final List messages = session.getMessages();
        int persisted = 0;
        if (dbPersistedCount.containsKey(mapKey)) {
            persisted = dbPersistedCount.get(mapKey).intValue();
        }
        final String title = map != null && map.getTitle() != null ? map.getTitle() : mapKey;
        int i = persisted;
        while (i + 1 < messages.size()) {
            final AiChatMessage first = (AiChatMessage) messages.get(i);
            final AiChatMessage second = (AiChatMessage) messages.get(i + 1);
            if (first.getRole() == AiChatMessage.Role.USER && second.getRole() == AiChatMessage.Role.ASSISTANT) {
                try {
                    backend.appendDesktopChatTurn(mapKey, title, first.getContent(), second.getContent(), "");
                }
                catch (Exception e) {
                    LogUtils.warn("appendDesktopChatTurn failed: " + e.getMessage());
                    break;
                }
                i += 2;
            }
            else {
                // Skip unpaired trailing user message until assistant arrives.
                break;
            }
        }
        dbPersistedCount.put(mapKey, Integer.valueOf(i));
    }

    private void migrateFileSessionToDatabase(MapModel map, AiChatSession session) {
        final McpRuntimeFacade.Backend backend = McpRuntimeFacade.get();
        if (backend == null || session == null || session.getMessages().isEmpty()) {
            return;
        }
        final String mapKey = resolveMapKey(map);
        try {
            final List existing = backend.loadDesktopMessages(mapKey);
            if (existing != null && !existing.isEmpty()) {
                dbPersistedCount.put(mapKey, Integer.valueOf(countDbUserAssistantPairs(existing) * 2));
                return;
            }
        }
        catch (Exception ignored) {
        }
        dbPersistedCount.put(mapKey, Integer.valueOf(0));
        persistNewTurnsToDatabase(map, session);
    }

    private static int countDbUserAssistantPairs(final List rows) {
        int users = 0;
        int assistants = 0;
        for (int i = 0; i < rows.size(); i++) {
            final Object row = rows.get(i);
            if (!(row instanceof Map)) {
                continue;
            }
            final String role = String.valueOf(((Map) row).get("role"));
            if ("user".equals(role)) {
                users++;
            }
            else if ("assistant".equals(role)) {
                assistants++;
            }
        }
        return Math.min(users, assistants);
    }

    private AiChatSession loadFromDatabase(final String mapKey) {
        final McpRuntimeFacade.Backend backend = McpRuntimeFacade.get();
        if (backend == null) {
            return null;
        }
        try {
            final List rows = backend.loadDesktopMessages(mapKey);
            if (rows == null || rows.isEmpty()) {
                return null;
            }
            final AiChatSession session = new AiChatSession(mapKey);
            for (int i = 0; i < rows.size(); i++) {
                final Object rowObj = rows.get(i);
                if (!(rowObj instanceof Map)) {
                    continue;
                }
                final Map row = (Map) rowObj;
                final String role = String.valueOf(row.get("role"));
                final String content = row.get("content") == null ? "" : String.valueOf(row.get("content"));
                if ("user".equals(role)) {
                    session.addMessage(new AiChatMessage(AiChatMessage.Role.USER, content));
                }
                else if ("assistant".equals(role)) {
                    session.addMessage(new AiChatMessage(AiChatMessage.Role.ASSISTANT, content));
                }
            }
            return session;
        }
        catch (Exception e) {
            LogUtils.warn("loadFromDatabase failed: " + e.getMessage());
            return null;
        }
    }

    private void syncToMapExtension(MapModel map, AiChatSession fullSession) {
        if (map == null || fullSession == null) {
            return;
        }
        int maxRounds = new DocearAiConfig().getMaxMmChatRounds();
        AiChatSession mmSession = fullSession.trimToLastRounds(maxRounds);
        AiChatHistoryExtension extension = AiChatHistoryExtension.getOrCreate(map);
        extension.setSession(mmSession);
    }

    public static String resolveMapKey(MapModel map) {
        if (map == null) {
            return "no_map";
        }
        File file = map.getFile();
        if (file != null) {
            return file.getAbsolutePath();
        }
        String title = map.getTitle();
        if (title != null && title.trim().length() > 0) {
            return "unsaved_" + title.trim();
        }
        return "unsaved_" + System.identityHashCode(map);
    }
}
