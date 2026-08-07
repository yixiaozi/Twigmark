package org.docear.plugin.mcp.webchat;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.docear.plugin.mcp.DocearMcpConfig;
import org.docear.plugin.mcp.json.JsonValue;
import org.freeplane.core.util.LogUtils;

/**
 * Web account / LLM profile / conversation facade.
 * <p>
 * Writes always go to {@code webchat-&lt;local-mac&gt;.db}. Reads merge all
 * {@code webchat-*.db} under the data directory (synced peers included).
 */
public final class WebchatService {

	/** Pre-login / desktop-shared owner when no web account exists yet. */
	public static final String LOCAL_OWNER = "local";

	private WebchatService() {
	}

	public static int loadedDatabaseCount() {
		return DocearMcpConfig.listWebchatDbFiles().length;
	}

	public static String localDbPath() {
		return DocearMcpConfig.getWebchatDbFile().getAbsolutePath();
	}

	public static boolean anyUserExists() {
		return findFirstRealUserAcross() != null;
	}

	/**
	 * Shared owner for LLM profiles and desktop chat history.
	 * Prefers the single registered web account; otherwise uses {@link #LOCAL_OWNER}.
	 */
	public static String getSharedOwnerUsername() {
		try {
			final Map across = findFirstRealUserAcross();
			if (across != null) {
				return (String) across.get("username");
			}
		}
		catch (Exception e) {
			LogUtils.warn("getSharedOwnerUsername: " + e.getMessage());
		}
		return LOCAL_OWNER;
	}

	public static boolean isSharedLlmConfigured() {
		try {
			final String owner = getSharedOwnerUsername();
			final Map endpoint = resolveLlmEndpoint(owner, "");
			final String key = nullToEmpty((String) endpoint.get("apiKey"));
			return key.length() > 0;
		}
		catch (Exception e) {
			return DocearMcpConfig.isWebLlmConfigured();
		}
	}

	public static Map<String, Object> register(final String usernameRaw, final String password,
			final String registrationToken) throws Exception {
		WebSecurity.validateRegistrationAllowed(registrationToken);
		// Single-owner product: only the first account may be created.
		if (anyUserExists()) {
			throw new IllegalArgumentException("registration closed: only one account is allowed");
		}
		final String username = normalizeUsername(usernameRaw);
		if (username.length() < 2) {
			throw new IllegalArgumentException("username too short");
		}
		if (LOCAL_OWNER.equals(username)) {
			throw new IllegalArgumentException("username reserved");
		}
		final int minPass = WebSecurity.minPasswordLength();
		if (password == null || password.length() < minPass) {
			throw new IllegalArgumentException("password too short (min " + minPass + ")");
		}
		if (findUserAcross(username) != null) {
			throw new IllegalArgumentException("username already exists");
		}
		final String salt = WebchatPassword.newSalt();
		final String hash = WebchatPassword.hash(password, salt);
		WebchatDatabase.local().insertUser(username, hash, salt);
		migrateLocalOwnerTo(username);
		ensureDefaultProfileFromConfig(username);
		return login(username, password);
	}

	public static Map<String, Object> login(final String usernameRaw, final String password) throws Exception {
		final String username = normalizeUsername(usernameRaw);
		final Map<String, Object> user = findUserAcross(username);
		if (user == null) {
			throw new IllegalArgumentException("invalid username or password");
		}
		if (!WebchatPassword.matches(password, (String) user.get("salt"), (String) user.get("passwordHash"))) {
			throw new IllegalArgumentException("invalid username or password");
		}
		// Mirror user into local DB so this PC can keep sessions/profiles even if peer file disappears.
		mirrorUserLocally(user, password);
		migrateLocalOwnerTo(username);
		ensureDefaultProfileFromConfig(username);
		final long ttlHours = DocearMcpConfig.getWebchatSessionTtlHours();
		final long expires = System.currentTimeMillis() + Math.max(1L, ttlHours) * 3600L * 1000L;
		final String token = WebchatPassword.newToken();
		WebchatDatabase.local().insertSession(token, username, expires);
		final Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("token", token);
		result.put("username", username);
		result.put("expiresAt", Long.valueOf(expires));
		return result;
	}

	public static void logout(final String token) {
		if (token == null || token.length() == 0) {
			return;
		}
		try {
			WebchatDatabase.local().deleteSession(token);
		}
		catch (Exception e) {
			LogUtils.warn("webchat logout: " + e.getMessage());
		}
	}

	public static String requireUsername(final String token) throws Exception {
		if (token == null || token.trim().length() == 0) {
			throw new IllegalArgumentException("login required");
		}
		final String username = WebchatDatabase.local().findUsernameByToken(token.trim());
		if (username == null || username.length() == 0) {
			throw new IllegalArgumentException("session expired or invalid");
		}
		return username;
	}

	public static List<Map<String, Object>> listProfiles(final String username) throws Exception {
		final Map byId = new LinkedHashMap();
		final List dbs = openAll();
		for (int i = 0; i < dbs.size(); i++) {
			final WebchatDatabase db = (WebchatDatabase) dbs.get(i);
			final List rows = db.listProfiles(username, false);
			for (int j = 0; j < rows.size(); j++) {
				final Map row = (Map) rows.get(j);
				final String id = (String) row.get("id");
				if (!byId.containsKey(id)) {
					byId.put(id, row);
				}
				else {
					// Prefer local machine copy when ids collide
					if (db.isLocalMachineFile()) {
						byId.put(id, row);
					}
				}
			}
		}
		final List result = new ArrayList(byId.values());
		Collections.sort(result, new Comparator() {
			public int compare(final Object a, final Object b) {
				final Map ma = (Map) a;
				final Map mb = (Map) b;
				final boolean da = Boolean.TRUE.equals(ma.get("isDefault"));
				final boolean db = Boolean.TRUE.equals(mb.get("isDefault"));
				if (da != db) {
					return da ? -1 : 1;
				}
				final long ua = ((Long) ma.get("updatedAt")).longValue();
				final long ub = ((Long) mb.get("updatedAt")).longValue();
				return ua > ub ? -1 : (ua < ub ? 1 : 0);
			}
		});
		return result;
	}

	public static Map<String, Object> saveProfile(final String username, final Map<String, JsonValue> body)
			throws Exception {
		String id = body.containsKey("id") ? nullToEmpty(body.get("id").asString()) : "";
		if (id.length() == 0) {
			id = WebchatPassword.newId();
		}
		final String name = body.containsKey("name") ? nullToEmpty(body.get("name").asString()) : "Default";
		String baseUrl = body.containsKey("baseUrl") ? nullToEmpty(body.get("baseUrl").asString())
				: DocearMcpConfig.getWebLlmBaseUrl();
		if (baseUrl.endsWith("/")) {
			baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
		}
		WebSecurity.validateLlmBaseUrl(baseUrl);
		String apiKey = body.containsKey("apiKey") ? nullToEmpty(body.get("apiKey").asString()) : "";
		if (apiKey.length() == 0) {
			// Keep existing secret if client omitted it
			final Map existing = findProfileSecret(username, id);
			if (existing != null) {
				apiKey = nullToEmpty((String) existing.get("apiKey"));
			}
		}
		final String model = body.containsKey("model") ? nullToEmpty(body.get("model").asString())
				: DocearMcpConfig.getWebLlmModel();
		boolean isDefault = !body.containsKey("isDefault") || body.get("isDefault").asBoolean();
		if (listProfiles(username).isEmpty()) {
			isDefault = true;
		}
		final Map profile = new LinkedHashMap();
		profile.put("id", id);
		profile.put("username", username);
		profile.put("name", name.length() == 0 ? "Default" : name);
		profile.put("baseUrl", baseUrl);
		profile.put("apiKey", apiKey);
		profile.put("model", model.length() == 0 ? "gpt-4o-mini" : model);
		profile.put("isDefault", Boolean.valueOf(isDefault));
		WebchatDatabase.local().upsertProfile(profile);
		final List listed = listProfiles(username);
		for (int i = 0; i < listed.size(); i++) {
			final Map row = (Map) listed.get(i);
			if (id.equals(row.get("id"))) {
				return row;
			}
		}
		return profile;
	}

	public static void deleteProfile(final String username, final String profileId) throws Exception {
		WebchatDatabase.local().deleteProfile(username, profileId);
	}

	/** Resolve LLM endpoint (with secret) for chat. */
	public static Map<String, Object> resolveLlmEndpoint(final String username, final String profileId)
			throws Exception {
		Map secret = null;
		if (profileId != null && profileId.length() > 0) {
			secret = findProfileSecret(username, profileId);
		}
		if (secret == null) {
			secret = findDefaultProfileSecret(username);
		}
		if (secret == null && DocearMcpConfig.isWebLlmConfigured()) {
			final Map fallback = new LinkedHashMap();
			fallback.put("id", "");
			fallback.put("baseUrl", DocearMcpConfig.getWebLlmBaseUrl());
			fallback.put("apiKey", DocearMcpConfig.getWebLlmApiKey());
			fallback.put("model", DocearMcpConfig.getWebLlmModel());
			fallback.put("name", "settings-fallback");
			return fallback;
		}
		if (secret == null) {
			throw new IllegalStateException("No LLM profile configured. Add one in the web settings.");
		}
		final String key = nullToEmpty((String) secret.get("apiKey"));
		if (key.length() == 0) {
			throw new IllegalStateException("LLM API key is empty for the selected profile.");
		}
		WebSecurity.validateLlmBaseUrl(nullToEmpty((String) secret.get("baseUrl")));
		return secret;
	}

	public static List<Map<String, Object>> listConversations(final String username, final int limit) throws Exception {
		final Map byId = new LinkedHashMap();
		final List dbs = openAll();
		for (int i = 0; i < dbs.size(); i++) {
			final List rows = ((WebchatDatabase) dbs.get(i)).listConversations(username, limit);
			for (int j = 0; j < rows.size(); j++) {
				final Map row = (Map) rows.get(j);
				final String id = (String) row.get("id");
				final Map existing = (Map) byId.get(id);
				if (existing == null) {
					byId.put(id, row);
				}
				else {
					final long eu = ((Long) existing.get("updatedAt")).longValue();
					final long ru = ((Long) row.get("updatedAt")).longValue();
					if (ru >= eu) {
						byId.put(id, row);
					}
				}
			}
		}
		final List result = new ArrayList(byId.values());
		Collections.sort(result, new Comparator() {
			public int compare(final Object a, final Object b) {
				final long ua = ((Long) ((Map) a).get("updatedAt")).longValue();
				final long ub = ((Long) ((Map) b).get("updatedAt")).longValue();
				return ua > ub ? -1 : (ua < ub ? 1 : 0);
			}
		});
		if (result.size() > limit && limit > 0) {
			return result.subList(0, limit);
		}
		return result;
	}

	public static Map<String, Object> getConversationBundle(final String username, final String conversationId)
			throws Exception {
		Map conv = null;
		final List dbs = openAll();
		for (int i = 0; i < dbs.size(); i++) {
			final Map row = ((WebchatDatabase) dbs.get(i)).getConversation(conversationId);
			if (row != null && username.equals(row.get("username"))) {
				if (conv == null
						|| ((Long) row.get("updatedAt")).longValue() >= ((Long) conv.get("updatedAt")).longValue()) {
					conv = row;
				}
			}
		}
		if (conv == null) {
			throw new IllegalArgumentException("conversation not found");
		}
		final Map msgById = new LinkedHashMap();
		for (int i = 0; i < dbs.size(); i++) {
			final List msgs = ((WebchatDatabase) dbs.get(i)).listMessages(conversationId, 1000);
			for (int j = 0; j < msgs.size(); j++) {
				final Map m = (Map) msgs.get(j);
				msgById.put(m.get("id"), m);
			}
		}
		final List messages = new ArrayList(msgById.values());
		Collections.sort(messages, new Comparator() {
			public int compare(final Object a, final Object b) {
				final long ua = ((Long) ((Map) a).get("createdAt")).longValue();
				final long ub = ((Long) ((Map) b).get("createdAt")).longValue();
				return ua < ub ? -1 : (ua > ub ? 1 : 0);
			}
		});
		final Map result = new LinkedHashMap();
		result.put("conversation", conv);
		result.put("messages", sanitizeMessagesForWeb(messages));
		return result;
	}

	/** Strip tool traces from web API responses — they may contain file paths and private context. */
	private static List sanitizeMessagesForWeb(final List messages) {
		final List out = new ArrayList();
		for (int i = 0; i < messages.size(); i++) {
			final Map src = (Map) messages.get(i);
			final Map copy = new LinkedHashMap();
			copy.put("id", src.get("id"));
			copy.put("conversationId", src.get("conversationId"));
			copy.put("role", src.get("role"));
			copy.put("content", src.get("content"));
			copy.put("model", src.get("model"));
			copy.put("createdAt", src.get("createdAt"));
			out.add(copy);
		}
		return out;
	}

	public static String createConversation(final String username, final String title) throws Exception {
		final String id = WebchatPassword.newId();
		final long now = System.currentTimeMillis();
		WebchatDatabase.local().upsertConversation(id, username, title == null ? "" : title, now, now);
		return id;
	}

	public static void renameConversation(final String username, final String conversationId, final String titleRaw)
			throws Exception {
		final Map conv = findConversationAcross(username, conversationId);
		if (conv == null) {
			throw new IllegalArgumentException("conversation not found");
		}
		String title = titleRaw == null ? "" : titleRaw.trim();
		if (title.length() > 120) {
			title = title.substring(0, 120);
		}
		if (title.length() == 0) {
			title = "未命名对话";
		}
		final long now = System.currentTimeMillis();
		final String source = nullToEmpty((String) conv.get("source"));
		final String mapKey = nullToEmpty((String) conv.get("mapKey"));
		final long created = ((Long) conv.get("createdAt")).longValue();
		WebchatDatabase.local().upsertConversation(conversationId, username, title, created, now,
				source.length() == 0 ? WebchatDatabase.SOURCE_WEB : source, mapKey);
	}

	/** @return assistant message id */
	public static String appendChatTurn(final String username, final String conversationId, final String userText,
			final String assistantText, final String toolTraceJson, final String model) throws Exception {
		final long now = System.currentTimeMillis();
		Map conv = WebchatDatabase.local().getConversation(conversationId);
		if (conv == null) {
			// Conversation may live only on a peer DB; create a local shell so new messages land here.
			final Map remote = findConversationAcross(username, conversationId);
			final String title = remote != null ? nullToEmpty((String) remote.get("title")) : titleFrom(userText);
			final long created = remote != null ? ((Long) remote.get("createdAt")).longValue() : now;
			final String source = remote != null ? nullToEmpty((String) remote.get("source")) : WebchatDatabase.SOURCE_WEB;
			final String mapKey = remote != null ? nullToEmpty((String) remote.get("mapKey")) : "";
			WebchatDatabase.local().upsertConversation(conversationId, username, title, created, now, source, mapKey);
		}
		else {
			String title = nullToEmpty((String) conv.get("title"));
			if (title.length() == 0) {
				title = titleFrom(userText);
			}
			WebchatDatabase.local().touchConversation(conversationId, title, now);
		}
		WebchatDatabase.local().insertMessage(WebchatPassword.newId(), conversationId, "user", userText, "", "");
		final String assistantId = WebchatPassword.newId();
		WebchatDatabase.local().insertMessage(assistantId, conversationId, "assistant", assistantText,
				toolTraceJson == null ? "" : toolTraceJson, model == null ? "" : model);
		return assistantId;
	}

	/**
	 * Create a permanent public share for one assistant message (includes paired user question).
	 * Security: opaque token; public payload excludes tool traces, paths, and account secrets.
	 */
	public static Map<String, Object> createMessageShare(final String username, final String messageId,
			final boolean includeTitle) throws Exception {
		final Map message = findMessageAcross(messageId);
		if (message == null) {
			throw new IllegalArgumentException("message not found");
		}
		final String role = nullToEmpty((String) message.get("role"));
		if (!"assistant".equals(role)) {
			throw new IllegalArgumentException("only assistant replies can be shared");
		}
		final String conversationId = nullToEmpty((String) message.get("conversationId"));
		final Map conv = findConversationAcross(username, conversationId);
		if (conv == null) {
			throw new IllegalArgumentException("conversation not found");
		}
		final Map userMsg = findPrecedingUserMessage(conversationId,
				((Long) message.get("createdAt")).longValue());
		final String userMessageId = userMsg == null ? "" : nullToEmpty((String) userMsg.get("id"));
		final String questionText = userMsg == null ? "" : nullToEmpty((String) userMsg.get("content"));
		final String token = "pub_" + WebchatPassword.newId() + WebchatPassword.newId();
		final long now = System.currentTimeMillis();
		WebchatDatabase.local().upsertMessageShare(token, messageId, userMessageId, questionText, conversationId,
				username, now, 0L, includeTitle);
		final Map out = new LinkedHashMap();
		out.put("token", token);
		out.put("messageId", messageId);
		out.put("expiresAt", Long.valueOf(0L));
		out.put("path", "/web/share.html?t=" + token);
		out.put("galleryPath", "/web/shares.html");
		return out;
	}

	public static void revokeMessageShare(final String username, final String token) throws Exception {
		if (token == null || token.trim().length() < 8) {
			throw new IllegalArgumentException("invalid token");
		}
		WebchatDatabase.local().revokeMessageShare(token.trim(), username);
	}

	/** Public read of a shared assistant message — minimal fields only. */
	public static Map<String, Object> getPublicShare(final String tokenRaw) throws Exception {
		final String token = tokenRaw == null ? "" : tokenRaw.trim();
		if (token.length() < 16 || token.indexOf("..") >= 0) {
			throw new IllegalArgumentException("invalid share token");
		}
		final List dbs = openAll();
		Map share = null;
		for (int i = 0; i < dbs.size(); i++) {
			final Map row = ((WebchatDatabase) dbs.get(i)).getMessageShare(token);
			if (row != null) {
				share = row;
				break;
			}
		}
		if (share == null) {
			throw new IllegalArgumentException("share not found");
		}
		if (Boolean.TRUE.equals(share.get("revoked"))) {
			throw new IllegalArgumentException("share revoked");
		}
		final long expiresAt = ((Long) share.get("expiresAt")).longValue();
		if (expiresAt > 0 && expiresAt < System.currentTimeMillis()) {
			throw new IllegalArgumentException("share expired");
		}
		final String messageId = nullToEmpty((String) share.get("messageId"));
		final Map message = findMessageAcross(messageId);
		if (message == null || !"assistant".equals(nullToEmpty((String) message.get("role")))) {
			throw new IllegalArgumentException("share not found");
		}
		String question = nullToEmpty((String) share.get("questionText"));
		if (question.length() == 0) {
			final Map userMsg = findPrecedingUserMessage(nullToEmpty((String) share.get("conversationId")),
					((Long) message.get("createdAt")).longValue());
			if (userMsg != null) {
				question = nullToEmpty((String) userMsg.get("content"));
			}
		}
		final Map out = new LinkedHashMap();
		out.put("question", question);
		out.put("content", nullToEmpty((String) message.get("content")));
		out.put("model", nullToEmpty((String) message.get("model")));
		out.put("createdAt", message.get("createdAt"));
		out.put("sharedAt", share.get("createdAt"));
		if (Boolean.TRUE.equals(share.get("includeTitle"))) {
			final String owner = nullToEmpty((String) share.get("username"));
			final String conversationId = nullToEmpty((String) share.get("conversationId"));
			try {
				final Map conv = findConversationAcross(owner, conversationId);
				if (conv != null) {
					out.put("title", nullToEmpty((String) conv.get("title")));
				}
			}
			catch (Exception ignored) {
			}
		}
		// Deliberately omit: username, tool traces, map paths, conversation id, message id.
		return out;
	}

	/** Public catalog of active shares — safe fields only. */
	public static List listPublicShares(final int limit, final int offset) throws Exception {
		final int max = limit < 1 ? 50 : (limit > 200 ? 200 : limit);
		final int off = offset < 0 ? 0 : offset;
		final long now = System.currentTimeMillis();
		final Map byToken = new LinkedHashMap();
		final List dbs = openAll();
		for (int i = 0; i < dbs.size(); i++) {
			final List rows = ((WebchatDatabase) dbs.get(i)).listActiveMessageShares(max + off + 50, 0, now);
			for (int j = 0; j < rows.size(); j++) {
				final Map share = (Map) rows.get(j);
				final String token = nullToEmpty((String) share.get("token"));
				if (token.length() == 0) {
					continue;
				}
				final Map existing = (Map) byToken.get(token);
				if (existing == null
						|| ((Long) share.get("createdAt")).longValue() >= ((Long) existing.get("createdAt"))
								.longValue()) {
					byToken.put(token, share);
				}
			}
		}
		final List sorted = new ArrayList(byToken.values());
		Collections.sort(sorted, new Comparator() {
			public int compare(final Object a, final Object b) {
				final long ua = ((Long) ((Map) a).get("createdAt")).longValue();
				final long ub = ((Long) ((Map) b).get("createdAt")).longValue();
				return ua < ub ? 1 : (ua > ub ? -1 : 0);
			}
		});
		final List items = new ArrayList();
		final int end = Math.min(sorted.size(), off + max);
		for (int i = off; i < end; i++) {
			items.add(buildPublicShareListItem((Map) sorted.get(i)));
		}
		return items;
	}

	private static Map buildPublicShareListItem(final Map share) throws Exception {
		final String token = nullToEmpty((String) share.get("token"));
		final String messageId = nullToEmpty((String) share.get("messageId"));
		final Map message = findMessageAcross(messageId);
		final Map item = new LinkedHashMap();
		item.put("token", token);
		item.put("path", "/web/share.html?t=" + token);
		item.put("sharedAt", share.get("createdAt"));
		String question = nullToEmpty((String) share.get("questionText"));
		if (question.length() == 0 && message != null) {
			final Map userMsg = findPrecedingUserMessage(nullToEmpty((String) share.get("conversationId")),
					((Long) message.get("createdAt")).longValue());
			if (userMsg != null) {
				question = nullToEmpty((String) userMsg.get("content"));
			}
		}
		item.put("question", previewText(question, 240));
		if (message != null) {
			item.put("answerPreview", previewText(nullToEmpty((String) message.get("content")), 320));
			item.put("model", nullToEmpty((String) message.get("model")));
			item.put("createdAt", message.get("createdAt"));
		}
		else {
			item.put("answerPreview", "");
			item.put("model", "");
		}
		if (Boolean.TRUE.equals(share.get("includeTitle"))) {
			try {
				final Map conv = findConversationAcross(nullToEmpty((String) share.get("username")),
						nullToEmpty((String) share.get("conversationId")));
				if (conv != null) {
					item.put("title", nullToEmpty((String) conv.get("title")));
				}
			}
			catch (Exception ignored) {
			}
		}
		return item;
	}

	private static String previewText(final String text, final int max) {
		final String raw = text == null ? "" : text.trim().replaceAll("\\s+", " ");
		if (raw.length() <= max) {
			return raw;
		}
		return raw.substring(0, max) + "…";
	}

	private static Map findPrecedingUserMessage(final String conversationId, final long beforeCreatedAt) {
		if (conversationId == null || conversationId.length() == 0) {
			return null;
		}
		final List dbs = openAll();
		Map best = null;
		long bestAt = Long.MIN_VALUE;
		for (int i = 0; i < dbs.size(); i++) {
			try {
				final List msgs = ((WebchatDatabase) dbs.get(i)).listMessages(conversationId, 1000);
				for (int j = 0; j < msgs.size(); j++) {
					final Map m = (Map) msgs.get(j);
					if (!"user".equals(nullToEmpty((String) m.get("role")))) {
						continue;
					}
					final long at = ((Long) m.get("createdAt")).longValue();
					if (at < beforeCreatedAt && at >= bestAt) {
						bestAt = at;
						best = m;
					}
				}
			}
			catch (Exception ignored) {
			}
		}
		return best;
	}

	private static Map findMessageAcross(final String messageId) {
		if (messageId == null || messageId.length() == 0) {
			return null;
		}
		final List dbs = openAll();
		for (int i = 0; i < dbs.size(); i++) {
			try {
				final Map row = ((WebchatDatabase) dbs.get(i)).getMessage(messageId);
				if (row != null) {
					return row;
				}
			}
			catch (Exception ignored) {
			}
		}
		return null;
	}

	/** Seed / refresh default shared LLM profile from Product Settings prefs. */
	public static void syncLlmFromProductSettings(final String baseUrl, final String apiKey, final String model) {
		try {
			final String owner = getSharedOwnerUsername();
			final String key = nullToEmpty(apiKey);
			if (key.length() == 0 && !DocearMcpConfig.isWebLlmConfigured()) {
				// Nothing to seed; keep existing DB profiles.
				if (listProfiles(owner).isEmpty()) {
					return;
				}
			}
			final List profiles = listProfiles(owner);
			String id = "";
			for (int i = 0; i < profiles.size(); i++) {
				final Map p = (Map) profiles.get(i);
				if (Boolean.TRUE.equals(p.get("isDefault"))) {
					id = nullToEmpty((String) p.get("id"));
					break;
				}
			}
			final Map jsonBody = new LinkedHashMap();
			if (id.length() > 0) {
				jsonBody.put("id", JsonValue.ofString(id));
			}
			jsonBody.put("name", JsonValue.ofString("OpenRouter"));
			jsonBody.put("baseUrl", JsonValue.ofString(
					baseUrl == null || baseUrl.trim().length() == 0 ? DocearMcpConfig.getWebLlmBaseUrl()
							: baseUrl.trim()));
			if (key.length() > 0) {
				jsonBody.put("apiKey", JsonValue.ofString(key));
			}
			jsonBody.put("model", JsonValue.ofString(
					model == null || model.trim().length() == 0 ? DocearMcpConfig.getWebLlmModel() : model.trim()));
			jsonBody.put("isDefault", JsonValue.ofBoolean(true));
			saveProfile(owner, jsonBody);
		}
		catch (Exception e) {
			LogUtils.warn("syncLlmFromProductSettings: " + e.getMessage());
		}
	}

	public static List loadDesktopMessages(final String mapKey) throws Exception {
		final String owner = getSharedOwnerUsername();
		final Map conv = findDesktopConversation(owner, mapKey);
		if (conv == null) {
			return new ArrayList();
		}
		final Map bundle = getConversationBundle(owner, (String) conv.get("id"));
		final List messages = (List) bundle.get("messages");
		return messages == null ? new ArrayList() : messages;
	}

	public static void appendDesktopChatTurn(final String mapKey, final String title, final String userText,
			final String assistantText, final String model) throws Exception {
		final String owner = getSharedOwnerUsername();
		final String conversationId = ensureDesktopConversation(owner, mapKey, title);
		appendChatTurn(owner, conversationId, userText, assistantText, "", model);
	}

	public static void clearDesktopConversation(final String mapKey) throws Exception {
		final String owner = getSharedOwnerUsername();
		final Map conv = findDesktopConversation(owner, mapKey);
		if (conv == null) {
			return;
		}
		final String id = (String) conv.get("id");
		WebchatDatabase.local().deleteConversationMessages(id);
		WebchatDatabase.local().deleteConversation(id);
	}

	public static String ensureDesktopConversation(final String username, final String mapKey, final String title)
			throws Exception {
		Map conv = findDesktopConversation(username, mapKey);
		final long now = System.currentTimeMillis();
		if (conv != null) {
			final String id = (String) conv.get("id");
			final String existingTitle = nullToEmpty((String) conv.get("title"));
			final String nextTitle = title != null && title.trim().length() > 0 ? title.trim() : existingTitle;
			WebchatDatabase.local().upsertConversation(id, username, nextTitle,
					((Long) conv.get("createdAt")).longValue(), now, WebchatDatabase.SOURCE_DESKTOP, mapKey);
			return id;
		}
		final String id = WebchatPassword.newId();
		final String t = title == null || title.trim().length() == 0 ? "Desktop" : title.trim();
		WebchatDatabase.local().upsertConversation(id, username, t, now, now, WebchatDatabase.SOURCE_DESKTOP, mapKey);
		return id;
	}

	public static List toJsonMaps(final List rows) {
		final List list = new ArrayList();
		if (rows == null) {
			return list;
		}
		for (int i = 0; i < rows.size(); i++) {
			list.add(JsonValue.ofMap(plainToJson((Map) rows.get(i))));
		}
		return list;
	}

	public static Map<String, JsonValue> plainToJson(final Map row) {
		final Map map = new LinkedHashMap();
		if (row == null) {
			return map;
		}
		final Object[] keys = row.keySet().toArray();
		for (int i = 0; i < keys.length; i++) {
			final String key = String.valueOf(keys[i]);
			final Object value = row.get(key);
			if (value == null) {
				map.put(key, JsonValue.ofNull());
			}
			else if (value instanceof Boolean) {
				map.put(key, JsonValue.ofBoolean(((Boolean) value).booleanValue()));
			}
			else if (value instanceof Integer) {
				map.put(key, JsonValue.ofNumber((Integer) value));
			}
			else if (value instanceof Long) {
				map.put(key, JsonValue.ofNumber((Long) value));
			}
			else if (value instanceof List) {
				map.put(key, JsonValue.ofList(toJsonMaps((List) value)));
			}
			else if (value instanceof Map) {
				map.put(key, JsonValue.ofMap(plainToJson((Map) value)));
			}
			else {
				map.put(key, JsonValue.ofString(String.valueOf(value)));
			}
		}
		return map;
	}

	private static void ensureDefaultProfileFromConfig(final String username) {
		try {
			if (!listProfiles(username).isEmpty()) {
				return;
			}
			if (!DocearMcpConfig.isWebLlmConfigured()) {
				return;
			}
			final Map jsonBody = new LinkedHashMap();
			jsonBody.put("name", JsonValue.ofString("OpenRouter"));
			jsonBody.put("baseUrl", JsonValue.ofString(DocearMcpConfig.getWebLlmBaseUrl()));
			jsonBody.put("apiKey", JsonValue.ofString(DocearMcpConfig.getWebLlmApiKey()));
			jsonBody.put("model", JsonValue.ofString(DocearMcpConfig.getWebLlmModel()));
			jsonBody.put("isDefault", JsonValue.ofBoolean(true));
			saveProfile(username, jsonBody);
		}
		catch (Exception e) {
			LogUtils.warn("ensureDefaultProfileFromConfig: " + e.getMessage());
		}
	}

	private static void migrateLocalOwnerTo(final String username) {
		try {
			WebchatDatabase.local().reassignUsername(LOCAL_OWNER, username);
			try {
				WebchatDatabase.local().deleteUser(LOCAL_OWNER);
			}
			catch (Exception ignored) {
			}
		}
		catch (Exception e) {
			LogUtils.warn("migrateLocalOwnerTo: " + e.getMessage());
		}
	}

	private static Map findFirstRealUserAcross() {
		final List dbs = openAll();
		for (int i = 0; i < dbs.size(); i++) {
			try {
				final String u = ((WebchatDatabase) dbs.get(i)).findAnyUsername();
				if (u != null && u.length() > 0 && !LOCAL_OWNER.equals(u)) {
					final Map user = ((WebchatDatabase) dbs.get(i)).findUser(u);
					if (user != null) {
						return user;
					}
				}
			}
			catch (Exception ignored) {
			}
		}
		return null;
	}

	private static Map findDesktopConversation(final String username, final String mapKey) {
		if (mapKey == null || mapKey.length() == 0) {
			return null;
		}
		final List dbs = openAll();
		Map best = null;
		for (int i = 0; i < dbs.size(); i++) {
			try {
				final WebchatDatabase db = (WebchatDatabase) dbs.get(i);
				final Map row = db.findConversationByMapKey(username, mapKey);
				if (row == null) {
					continue;
				}
				if (best == null
						|| ((Long) row.get("updatedAt")).longValue() >= ((Long) best.get("updatedAt")).longValue()) {
					best = row;
				}
			}
			catch (Exception ignored) {
			}
		}
		return best;
	}

	private static void mirrorUserLocally(final Map user, final String password) {
		try {
			final String username = (String) user.get("username");
			if (WebchatDatabase.local().findUser(username) != null) {
				return;
			}
			final String salt = (String) user.get("salt");
			final String hash = (String) user.get("passwordHash");
			// Prefer copying hash/salt as-is so password stays valid without rehash mismatch.
			if (salt != null && hash != null) {
				WebchatDatabase.local().insertUser(username, hash, salt);
			}
			else {
				final String newSalt = WebchatPassword.newSalt();
				WebchatDatabase.local().insertUser(username, WebchatPassword.hash(password, newSalt), newSalt);
			}
		}
		catch (Exception e) {
			LogUtils.warn("mirrorUserLocally: " + e.getMessage());
		}
	}

	private static Map findUserAcross(final String username) {
		final List dbs = openAll();
		for (int i = 0; i < dbs.size(); i++) {
			try {
				final Map user = ((WebchatDatabase) dbs.get(i)).findUser(username);
				if (user != null) {
					return user;
				}
			}
			catch (Exception e) {
				LogUtils.warn("findUserAcross: " + e.getMessage());
			}
		}
		return null;
	}

	private static Map findConversationAcross(final String username, final String id) {
		final List dbs = openAll();
		for (int i = 0; i < dbs.size(); i++) {
			try {
				final Map row = ((WebchatDatabase) dbs.get(i)).getConversation(id);
				if (row != null && username.equals(row.get("username"))) {
					return row;
				}
			}
			catch (Exception ignored) {
			}
		}
		return null;
	}

	private static Map findProfileSecret(final String username, final String profileId) {
		final List dbs = openAll();
		Map best = null;
		for (int i = 0; i < dbs.size(); i++) {
			try {
				final WebchatDatabase db = (WebchatDatabase) dbs.get(i);
				final Map row = db.getProfile(username, profileId, true);
				if (row == null) {
					continue;
				}
				if (best == null || db.isLocalMachineFile()) {
					best = row;
				}
			}
			catch (Exception ignored) {
			}
		}
		return best;
	}

	private static Map findDefaultProfileSecret(final String username) {
		final List profiles = listProfilesSafe(username);
		for (int i = 0; i < profiles.size(); i++) {
			final Map p = (Map) profiles.get(i);
			if (Boolean.TRUE.equals(p.get("isDefault"))) {
				return findProfileSecret(username, (String) p.get("id"));
			}
		}
		if (!profiles.isEmpty()) {
			return findProfileSecret(username, (String) ((Map) profiles.get(0)).get("id"));
		}
		return null;
	}

	private static List listProfilesSafe(final String username) {
		try {
			return listProfiles(username);
		}
		catch (Exception e) {
			return new ArrayList();
		}
	}

	private static List openAll() {
		final File[] files = DocearMcpConfig.listWebchatDbFiles();
		final List dbs = new ArrayList();
		final File localFile = DocearMcpConfig.getWebchatDbFile();
		dbs.add(WebchatDatabase.local());
		for (int i = 0; i < files.length; i++) {
			final File f = files[i];
			if (f == null || !f.isFile()) {
				continue;
			}
			try {
				if (f.getCanonicalPath().equals(localFile.getCanonicalPath())) {
					continue;
				}
			}
			catch (Exception e) {
				if (f.getAbsolutePath().equals(localFile.getAbsolutePath())) {
					continue;
				}
			}
			try {
				dbs.add(new WebchatDatabase(f, true));
			}
			catch (Exception e) {
				LogUtils.warn("open peer webchat db failed: " + f + " - " + e.getMessage());
			}
		}
		return dbs;
	}

	private static String normalizeUsername(final String raw) {
		return raw == null ? "" : raw.trim().toLowerCase();
	}

	private static String titleFrom(final String text) {
		final String t = text == null ? "" : text.trim().replace('\n', ' ');
		if (t.length() <= 40) {
			return t;
		}
		return t.substring(0, 40) + "...";
	}

	private static String nullToEmpty(final String value) {
		return value == null ? "" : value;
	}
}
