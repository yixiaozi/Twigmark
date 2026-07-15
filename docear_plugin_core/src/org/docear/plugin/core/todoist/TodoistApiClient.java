package org.docear.plugin.core.todoist;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;

final class TodoistApiClient {
	private static final String API_BASE = "https://api.todoist.com/api/v1";
	private static final int CONNECT_TIMEOUT_MS = 15000;
	private static final int READ_TIMEOUT_MS = 30000;
	private static final int MAX_RETRIES = 3;
	private static final int RETRY_BASE_MS = 1200;
	private static final int PAGE_LIMIT = 50;

	private final String apiToken;
	private final Map sectionIdCache = new HashMap();

	TodoistApiClient(String apiToken) {
		this.apiToken = apiToken;
	}

	String ensureProject(String projectName) throws IOException {
		String storedId = TodoistConfig.getProjectId();
		if (storedId != null && storedId.length() > 0 && projectExists(storedId)) {
			LogUtils.info("Todoist: reusing project " + storedId);
			return storedId;
		}
		String projectId = findProjectIdByName(projectName);
		if (projectId != null) {
			TodoistConfig.setProjectId(projectId, projectName);
			LogUtils.info("Todoist: found existing project " + projectName);
			return projectId;
		}
		String body = "{\"name\":\"" + TodoistJson.escape(projectName) + "\"}";
		String response = request("POST", "/projects", body);
		projectId = TodoistJson.extractIdField(response);
		if (projectId == null || projectId.length() == 0) {
			throw new IOException("Todoist create project: missing id");
		}
		TodoistConfig.setProjectId(projectId, projectName);
		LogUtils.info("Todoist: created project " + projectName);
		return projectId;
	}

	String ensureSection(String projectId, String sectionName, TodoistSectionStore store) throws IOException {
		String cacheKey = projectId + "|" + sectionName;
		if (sectionIdCache.containsKey(cacheKey)) {
			return (String) sectionIdCache.get(cacheKey);
		}
		String stored = store.getSectionId(projectId, sectionName);
		if (stored != null && stored.length() > 0) {
			sectionIdCache.put(cacheKey, stored);
			return stored;
		}
		String sectionId = findSectionIdByName(projectId, sectionName);
		if (sectionId == null) {
			String body = "{\"name\":\"" + TodoistJson.escape(truncate(sectionName, 120))
					+ "\",\"project_id\":\"" + TodoistJson.escape(projectId) + "\"}";
			String response = request("POST", "/sections", body);
			sectionId = TodoistJson.extractIdField(response);
			if (sectionId == null || sectionId.length() == 0) {
				throw new IOException("Todoist create section: missing id");
			}
			LogUtils.info("Todoist: created section " + sectionName);
		}
		store.putSectionId(projectId, sectionName, sectionId);
		sectionIdCache.put(cacheKey, sectionId);
		return sectionId;
	}

	static String sectionNameForFile(File file) {
		String base = file.getName();
		if (base.toLowerCase().endsWith(".mm")) {
			base = base.substring(0, base.length() - 3);
		}
		String parentPath = MindMapDataRootResolver.getRelativePathWithinScanRoots(file.getParentFile());
		if (parentPath != null && parentPath.length() > 0) {
			return parentPath.replace("/", " / ") + " / " + base;
		}
		return base;
	}

	String createTask(TodoistReminderRecord record, String projectId, String sectionId) throws IOException {
		String body = buildCreateTaskJson(record, projectId, sectionId);
		String response = request("POST", "/tasks", body);
		String taskId = TodoistJson.extractIdField(response);
		if (taskId == null || taskId.length() == 0) {
			throw new IOException("Todoist create task: missing id in response");
		}
		return taskId;
	}

	void updateTask(String taskId, TodoistReminderRecord record, String projectId, String sectionId)
			throws IOException {
		if (projectId != null && sectionId != null) {
			relocateTaskTo(taskId, projectId, sectionId);
		}
		updateTaskContent(taskId, record);
	}

	void updateTaskContent(String taskId, TodoistReminderRecord record) throws IOException {
		String body = buildUpdateTaskJson(record);
		request("POST", "/tasks/" + taskId, body);
	}

	void relocateTaskTo(String taskId, String projectId, String sectionId) throws IOException {
		TodoistTaskLocation before = getTaskLocation(taskId);
		if (!before.exists) {
			throw new IOException("Todoist task not found: " + taskId);
		}
		if (isTaskInLocation(before, projectId, sectionId)) {
			return;
		}
		try {
			moveTaskTo(taskId, projectId, sectionId);
		}
		catch (IOException firstError) {
			if (!projectId.equals(before.projectId)) {
				moveTaskToProject(taskId, projectId);
				moveTaskToSection(taskId, sectionId);
			}
			else {
				throw firstError;
			}
		}
		TodoistTaskLocation after = getTaskLocation(taskId);
		if (!isTaskInLocation(after, projectId, sectionId)) {
			throw new IOException("Todoist move failed for task " + taskId + " (project="
					+ after.projectId + ", section=" + after.sectionId + ")");
		}
		LogUtils.info("Todoist: moved task " + taskId + " to project " + projectId + " section " + sectionId);
	}

	private void moveTaskTo(String taskId, String projectId, String sectionId) throws IOException {
		StringBuilder sb = new StringBuilder(96);
		sb.append('{');
		sb.append("\"project_id\":\"").append(TodoistJson.escape(projectId)).append('"');
		sb.append(",\"section_id\":\"").append(TodoistJson.escape(sectionId)).append('"');
		sb.append('}');
		request("POST", "/tasks/" + taskId + "/move", sb.toString());
	}

	private void moveTaskToProject(String taskId, String projectId) throws IOException {
		String body = "{\"project_id\":\"" + TodoistJson.escape(projectId) + "\"}";
		request("POST", "/tasks/" + taskId + "/move", body);
	}

	private void moveTaskToSection(String taskId, String sectionId) throws IOException {
		String body = "{\"section_id\":\"" + TodoistJson.escape(sectionId) + "\"}";
		request("POST", "/tasks/" + taskId + "/move", body);
	}

	void closeTask(String taskId) throws IOException {
		request("POST", "/tasks/" + taskId + "/close", null);
	}

	TodoistTaskLocation getTaskLocation(String taskId) throws IOException {
		try {
			String response = request("GET", "/tasks/" + taskId, null);
			return TodoistTaskLocation.found(TodoistJson.extractStringField(response, "project_id"),
					TodoistJson.extractStringField(response, "section_id"));
		}
		catch (IOException e) {
			String message = e.getMessage();
			if (message != null && message.indexOf("404") >= 0) {
				return TodoistTaskLocation.notFound();
			}
			throw e;
		}
	}

	boolean isTaskInLocation(TodoistTaskLocation location, String projectId, String sectionId) {
		if (location == null || !location.exists || projectId == null) {
			return false;
		}
		if (!projectId.equals(location.projectId)) {
			return false;
		}
		return sameId(location.sectionId, sectionId);
	}

	java.util.List findAllProjectIdsByName(String projectName) throws IOException {
		java.util.List ids = new java.util.ArrayList();
		String cursor = null;
		do {
			String path = "/projects?limit=" + PAGE_LIMIT;
			if (cursor != null) {
				path += "&cursor=" + urlEncode(cursor);
			}
			String response = request("GET", path, null);
			TodoistJson.collectIdsByExactName(response, projectName, ids);
			cursor = TodoistJson.extractNextCursor(response);
		}
		while (cursor != null);
		return ids;
	}

	java.util.List listDocearTaskIdsInProject(String projectId) throws IOException {
		java.util.List tasks = listDocearTasksInProject(projectId);
		java.util.List taskIds = new java.util.ArrayList();
		for (int i = 0; i < tasks.size(); i++) {
			taskIds.add(((TodoistImportTask) tasks.get(i)).id);
		}
		return taskIds;
	}

	/** Active Docear-linked tasks in a project (id + content + description + due). */
	java.util.List listDocearTasksInProject(String projectId) throws IOException {
		java.util.List tasks = new java.util.ArrayList();
		String cursor = null;
		do {
			String path = "/tasks?project_id=" + urlEncode(projectId) + "&limit=" + PAGE_LIMIT;
			if (cursor != null) {
				path += "&cursor=" + urlEncode(cursor);
			}
			String response = request("GET", path, null);
			TodoistJson.collectImportTasks(response, tasks);
			cursor = TodoistJson.extractNextCursor(response);
		}
		while (cursor != null);
		java.util.List docear = new java.util.ArrayList();
		for (int i = 0; i < tasks.size(); i++) {
			TodoistImportTask task = (TodoistImportTask) tasks.get(i);
			if (task.description != null && task.description.indexOf("Docear reminder") >= 0) {
				docear.add(task);
			}
		}
		return docear;
	}

	String getTaskDescription(String taskId) throws IOException {
		String response = request("GET", "/tasks/" + taskId, null);
		return TodoistJson.extractStringField(response, "description");
	}

	java.util.List fetchAllActiveTasks() throws IOException {
		java.util.List tasks = new java.util.ArrayList();
		String cursor = null;
		do {
			String path = "/tasks?limit=" + PAGE_LIMIT;
			if (cursor != null) {
				path += "&cursor=" + urlEncode(cursor);
			}
			String response = request("GET", path, null);
			TodoistJson.collectImportTasks(response, tasks);
			cursor = TodoistJson.extractNextCursor(response);
		}
		while (cursor != null);
		return tasks;
	}

	java.util.Map fetchProjectNames() throws IOException {
		java.util.Map names = new java.util.HashMap();
		String cursor = null;
		do {
			String path = "/projects?limit=" + PAGE_LIMIT;
			if (cursor != null) {
				path += "&cursor=" + urlEncode(cursor);
			}
			String response = request("GET", path, null);
			names.putAll(TodoistJson.collectIdNamePairs(response));
			cursor = TodoistJson.extractNextCursor(response);
		}
		while (cursor != null);
		return names;
	}

	java.util.Map fetchSectionNames() throws IOException {
		return fetchSectionNamesForProjects(null);
	}

	java.util.Map fetchSectionNamesForTasks(java.util.List tasks) {
		java.util.Set projectIds = new java.util.HashSet();
		if (tasks != null) {
			for (int i = 0; i < tasks.size(); i++) {
				TodoistImportTask task = (TodoistImportTask) tasks.get(i);
				if (task.sectionId != null && task.sectionId.length() > 0 && task.projectId != null
						&& task.projectId.length() > 0) {
					projectIds.add(task.projectId);
				}
			}
		}
		java.util.Map names = new java.util.HashMap();
		if (projectIds.isEmpty()) {
			return names;
		}
		java.util.Iterator it = projectIds.iterator();
		while (it.hasNext()) {
			String projectId = (String) it.next();
			try {
				names.putAll(fetchSectionNamesByProject(projectId));
			}
			catch (IOException e) {
				LogUtils.warn("Todoist: could not load sections for project " + projectId, e);
			}
			pauseBetweenRequests();
		}
		return names;
	}

	private java.util.Map fetchSectionNamesForProjects(java.util.Set projectIds) throws IOException {
		if (projectIds != null && !projectIds.isEmpty()) {
			java.util.Map names = new java.util.HashMap();
			java.util.Iterator it = projectIds.iterator();
			while (it.hasNext()) {
				names.putAll(fetchSectionNamesByProject((String) it.next()));
				pauseBetweenRequests();
			}
			return names;
		}
		java.util.Map names = new java.util.HashMap();
		String cursor = null;
		do {
			String path = "/sections?limit=" + PAGE_LIMIT;
			if (cursor != null) {
				path += "&cursor=" + urlEncode(cursor);
			}
			String response = request("GET", path, null);
			names.putAll(TodoistJson.collectIdNamePairs(response));
			cursor = TodoistJson.extractNextCursor(response);
			pauseBetweenRequests();
		}
		while (cursor != null);
		return names;
	}

	private java.util.Map fetchSectionNamesByProject(String projectId) throws IOException {
		java.util.Map names = new java.util.HashMap();
		String cursor = null;
		do {
			String path = "/sections?project_id=" + urlEncode(projectId) + "&limit=" + PAGE_LIMIT;
			if (cursor != null) {
				path += "&cursor=" + urlEncode(cursor);
			}
			String response = request("GET", path, null);
			names.putAll(TodoistJson.collectIdNamePairs(response));
			cursor = TodoistJson.extractNextCursor(response);
			pauseBetweenRequests();
		}
		while (cursor != null);
		return names;
	}

	private static void pauseBetweenRequests() {
		try {
			Thread.sleep(150);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	static String syncKeyFromDescription(String description) {
		if (description == null || description.indexOf("Docear reminder") < 0) {
			return null;
		}
		String path = extractLinePrefix(description, "Path: ");
		String nodeId = extractLinePrefix(description, "Node ID: ");
		if (path == null || nodeId == null || path.length() == 0 || nodeId.length() == 0) {
			return null;
		}
		return path + "|" + nodeId;
	}

	private static String extractLinePrefix(String text, String prefix) {
		int idx = text.indexOf(prefix);
		if (idx < 0) {
			return null;
		}
		int start = idx + prefix.length();
		int end = text.indexOf('\n', start);
		if (end < 0) {
			return text.substring(start).trim();
		}
		return text.substring(start, end).trim();
	}

	private static boolean sameId(String a, String b) {
		String left = a == null ? "" : a;
		String right = b == null ? "" : b;
		return left.equals(right);
	}

	private boolean projectExists(String projectId) {
		try {
			request("GET", "/projects/" + projectId, null);
			return true;
		}
		catch (IOException e) {
			String message = e.getMessage();
			if (message != null && message.indexOf("404") >= 0) {
				return false;
			}
			LogUtils.warn("Todoist: could not verify project " + projectId, e);
			return false;
		}
	}

	private String findProjectIdByName(String projectName) throws IOException {
		String cursor = null;
		do {
			String path = "/projects?limit=" + PAGE_LIMIT;
			if (cursor != null) {
				path += "&cursor=" + urlEncode(cursor);
			}
			String response = request("GET", path, null);
			String id = TodoistJson.findIdByExactName(response, projectName);
			if (id != null) {
				return id;
			}
			cursor = TodoistJson.extractNextCursor(response);
		}
		while (cursor != null);
		return null;
	}

	private String findSectionIdByName(String projectId, String sectionName) throws IOException {
		String cursor = null;
		do {
			String path = "/sections?project_id=" + urlEncode(projectId) + "&limit=" + PAGE_LIMIT;
			if (cursor != null) {
				path += "&cursor=" + urlEncode(cursor);
			}
			String response = request("GET", path, null);
			String id = TodoistJson.findIdByExactName(response, sectionName);
			if (id != null) {
				return id;
			}
			cursor = TodoistJson.extractNextCursor(response);
		}
		while (cursor != null);
		return null;
	}

	private String buildCreateTaskJson(TodoistReminderRecord record, String projectId, String sectionId) {
		StringBuilder sb = new StringBuilder(384);
		sb.append("{\"content\":\"").append(TodoistJson.escape(truncate(record.nodeText, 500))).append("\"");
		sb.append(",\"description\":\"").append(TodoistJson.escape(buildDescription(record))).append("\"");
		sb.append(",\"project_id\":\"").append(TodoistJson.escape(projectId)).append("\"");
		sb.append(",\"section_id\":\"").append(TodoistJson.escape(sectionId)).append("\"");
		sb.append(",\"priority\":").append(TodoistPriority.toTodoistApi(record.jinji));
		appendDurationFields(sb, record.durationMinutes);
		appendDueFields(sb, record);
		sb.append('}');
		return sb.toString();
	}

	private String buildUpdateTaskJson(TodoistReminderRecord record) {
		StringBuilder sb = new StringBuilder(384);
		sb.append("{\"content\":\"").append(TodoistJson.escape(truncate(record.nodeText, 500))).append("\"");
		sb.append(",\"description\":\"").append(TodoistJson.escape(buildDescription(record))).append("\"");
		sb.append(",\"priority\":").append(TodoistPriority.toTodoistApi(record.jinji));
		appendDurationFields(sb, record.durationMinutes);
		appendDueFields(sb, record);
		sb.append('}');
		return sb.toString();
	}

	private static void appendDurationFields(StringBuilder sb, int durationMinutes) {
		if (durationMinutes > 0) {
			sb.append(",\"duration\":").append(durationMinutes);
			sb.append(",\"duration_unit\":\"minute\"");
		}
	}

	private void appendDueFields(StringBuilder sb, TodoistReminderRecord record) {
		if (record.remindAt <= 0 && !record.recurring) {
			return;
		}
		if (record.recurring) {
			String dueString = TodoistCycleMapper.toDueString(record.cycle(), record.remindAt);
			if (dueString != null && dueString.length() > 0) {
				sb.append(",\"due_string\":\"").append(TodoistJson.escape(dueString)).append("\"");
				sb.append(",\"due_lang\":\"en\"");
			}
			else if (record.remindAt > 0) {
				sb.append(",\"due_datetime\":\"").append(TodoistJson.escape(formatUtcDateTime(record.remindAt)))
						.append("\"");
			}
		}
		else if (record.remindAt > 0) {
			sb.append(",\"due_datetime\":\"").append(TodoistJson.escape(formatUtcDateTime(record.remindAt))).append("\"");
		}
	}

	private static String toRecurringDueString(TodoistReminderRecord record) {
		return TodoistCycleMapper.toDueString(record.cycle(), record.remindAt);
	}

	static String buildDescription(TodoistReminderRecord record) {
		StringBuilder sb = new StringBuilder();
		sb.append("Docear reminder\n");
		sb.append("Map: ").append(record.file.getName()).append('\n');
		sb.append("Path: ").append(TodoistSyncKeys.absolutePath(record.file)).append('\n');
		sb.append("Node ID: ").append(record.nodeId);
		return sb.toString();
	}

	private static String formatUtcDateTime(long millis) {
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		format.setTimeZone(TimeZone.getTimeZone("UTC"));
		return format.format(new Date(millis));
	}

	private static String truncate(String text, int maxLen) {
		if (text == null) {
			return "";
		}
		if (text.length() <= maxLen) {
			return text;
		}
		return text.substring(0, maxLen - 3) + "...";
	}

	private static String urlEncode(String value) throws UnsupportedEncodingException {
		return URLEncoder.encode(value, "UTF-8");
	}

	private String request(String method, String path, String jsonBody) throws IOException {
		IOException lastError = null;
		for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
			try {
				return requestOnce(method, path, jsonBody);
			}
			catch (IOException e) {
				lastError = e;
				if (!isRetryable(e) || attempt >= MAX_RETRIES) {
					throw e;
				}
				LogUtils.warn("Todoist: retry " + (attempt + 1) + " for " + path + " (" + e.getMessage() + ")");
				try {
					Thread.sleep(RETRY_BASE_MS * (attempt + 1));
				}
				catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw e;
				}
			}
		}
		if (lastError != null) {
			throw lastError;
		}
		throw new IOException("Todoist request failed for " + path);
	}

	private static boolean isRetryable(IOException e) {
		String message = e.getMessage();
		if (message == null) {
			return false;
		}
		return message.indexOf("503") >= 0 || message.indexOf("502") >= 0 || message.indexOf("429") >= 0
				|| message.indexOf("504") >= 0;
	}

	private String requestOnce(String method, String path, String jsonBody) throws IOException {
		URL url = new URL(API_BASE + path);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		InputStream in = null;
		OutputStream out = null;
		try {
			connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
			connection.setReadTimeout(READ_TIMEOUT_MS);
			connection.setRequestMethod(method);
			connection.setRequestProperty("Authorization", "Bearer " + apiToken);
			connection.setRequestProperty("Accept", "application/json");
			if (jsonBody != null) {
				connection.setDoOutput(true);
				connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
				out = connection.getOutputStream();
				out.write(jsonBody.getBytes("UTF-8"));
				out.flush();
			}
			int code = connection.getResponseCode();
			in = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
			if (in == null) {
				throw new IOException("Todoist HTTP " + code + " (empty body) for " + path);
			}
			String response = readStream(in);
			if (code >= 400) {
				throw new IOException("Todoist HTTP " + code + " for " + path + ": " + response);
			}
			return response;
		}
		finally {
			if (out != null) {
				try {
					out.close();
				}
				catch (IOException e) {
				}
			}
			if (in != null) {
				try {
					in.close();
				}
				catch (IOException e) {
				}
			}
			connection.disconnect();
		}
	}

	private static String readStream(InputStream in) throws IOException {
		StringBuilder sb = new StringBuilder();
		byte[] buffer = new byte[4096];
		int read;
		while ((read = in.read(buffer)) >= 0) {
			sb.append(new String(buffer, 0, read, "UTF-8"));
		}
		return sb.toString();
	}
}
