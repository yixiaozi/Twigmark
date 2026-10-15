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
	/** Todoist rejects further POST /sections once the project hits its plan limit. */
	private static boolean sectionCreationBlocked;

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
			if (sectionCreationBlocked) {
				return null;
			}
			String body = "{\"name\":\"" + TodoistJson.escape(truncate(sectionName, 120))
					+ "\",\"project_id\":\"" + TodoistJson.escape(projectId) + "\"}";
			try {
				String response = request("POST", "/sections", body);
				sectionId = TodoistJson.extractIdField(response);
			}
			catch (IOException e) {
				if (isSectionLimit(e)) {
					sectionCreationBlocked = true;
					LogUtils.warn("Todoist section limit reached; tasks stay in the project without a new section");
					return null;
				}
				throw e;
			}
			if (sectionId == null || sectionId.length() == 0) {
				throw new IOException("Todoist create section: missing id");
			}
			LogUtils.info("Todoist: created section " + sectionName);
		}
		store.putSectionId(projectId, sectionName, sectionId);
		sectionIdCache.put(cacheKey, sectionId);
		return sectionId;
	}

	/** Confirms the token with Todoist. Throws on 401 or other HTTP errors. */
	void checkToken() throws IOException {
		request("GET", "/projects?limit=1", null);
	}

	private static boolean isSectionLimit(IOException e) {
		String message = e.getMessage();
		return message != null && (message.indexOf("MAX_SECTIONS_LIMIT_REACHED") >= 0
				|| message.indexOf("Maximum number of sections") >= 0);
	}

	/**
	 * One section per top-level mind-map folder. Todoist allows 20 active sections per project,
	 * so a section per file path cannot work.
	 */
	static String sectionNameForFile(File file) {
		String top = topLevelFolder(file);
		if (top == null || top.length() == 0 || "_data".equals(top)) {
			return "\u5176\u4ed6";
		}
		return top;
	}

	private static String topLevelFolder(File file) {
		if (file == null) {
			return "";
		}
		File parent = file.getParentFile();
		String relative = MindMapDataRootResolver.getRelativePathWithinScanRoots(parent);
		if (relative == null || relative.length() == 0) {
			return "";
		}
		int slash = relative.indexOf('/');
		return slash < 0 ? relative : relative.substring(0, slash);
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

	/**
	 * Writes only fields that differ from the current Todoist task.
	 * An unchanged name is omitted, so Todoist does not record a name change.
	 *
	 * @return true when a request was sent
	 */
	boolean updateTaskContent(String taskId, TodoistReminderRecord record) throws IOException {
		return updateTaskContent(taskId, record, false);
	}

	/**
	 * @param contentOnly update the title and due only. Used for a task that already existed in
	 *                    another Todoist project: its description and project stay untouched.
	 */
	boolean updateTaskContent(String taskId, TodoistReminderRecord record, boolean contentOnly) throws IOException {
		TodoistImportTask existing = fetchTask(taskId);
		String body = buildUpdateTaskJson(record, existing, contentOnly);
		if (body == null) {
			return false;
		}
		request("POST", "/tasks/" + taskId, body);
		return true;
	}

	TodoistImportTask fetchTask(String taskId) throws IOException {
		try {
			String response = request("GET", "/tasks/" + taskId, null);
			java.util.List tasks = new java.util.ArrayList();
			TodoistJson.collectImportTasks(response, tasks);
			if (tasks.isEmpty()) {
				return null;
			}
			return (TodoistImportTask) tasks.get(0);
		}
		catch (IOException e) {
			String message = e.getMessage();
			if (message != null && message.indexOf("404") >= 0) {
				return null;
			}
			throw e;
		}
	}

	void relocateTaskTo(String taskId, String projectId, String sectionId) throws IOException {
		TodoistTaskLocation before = getTaskLocation(taskId);
		if (!before.exists) {
			throw new IOException("Todoist task not found: " + taskId);
		}
		if (sectionId == null || sectionId.length() == 0) {
			if (projectId != null && !projectId.equals(before.projectId)) {
				moveTaskToProject(taskId, projectId);
			}
			return;
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

	/**
	 * Mark a task complete. A 404 means it is already gone, which is the same end state, so it is
	 * not reported as a failure and the task is not recreated.
	 *
	 * @return false when Todoist no longer has the task
	 */
	boolean closeTask(String taskId) throws IOException {
		try {
			request("POST", "/tasks/" + taskId + "/close", null);
			return true;
		}
		catch (IOException e) {
			final String message = e.getMessage();
			if (message != null && message.indexOf("404") >= 0) {
				return false;
			}
			throw e;
		}
	}

	/** Remove a task. 404 means it is already gone. */
	void deleteTask(String taskId) throws IOException {
		try {
			request("DELETE", "/tasks/" + taskId, null);
		}
		catch (IOException e) {
			final String message = e.getMessage();
			if (message != null && message.indexOf("404") >= 0) {
				return;
			}
			throw e;
		}
	}

	/** Uncomplete a closed task so the same Todoist id stays 1:1 with the mind-map node. */
	void reopenTask(String taskId) throws IOException {
		request("POST", "/tasks/" + taskId + "/reopen", null);
	}

	/** Drop the due date. Does not complete the task or change its description. */
	void clearDueDate(String taskId) throws IOException {
		request("POST", "/tasks/" + taskId, "{\"due_string\":\"no date\"}");
	}

	/**
	 * Ensures the task is active. Reopens when Todoist still has the id but marked completed
	 * (e.g. after a cut/delete race).
	 *
	 * @return true if the task was reopened
	 */
	boolean ensureTaskActive(String taskId) throws IOException {
		TodoistTaskLocation location = getTaskLocation(taskId);
		if (!location.exists || !location.completed) {
			return false;
		}
		reopenTask(taskId);
		return true;
	}

	TodoistTaskLocation getTaskLocation(String taskId) throws IOException {
		try {
			String response = request("GET", "/tasks/" + taskId, null);
			boolean completed = isCompletedTaskJson(response);
			return TodoistTaskLocation.found(TodoistJson.extractStringField(response, "project_id"),
					TodoistJson.extractStringField(response, "section_id"), completed,
					TodoistJson.extractStringField(response, "description"),
					TodoistSyncClock.parseUpdatedAt(TodoistJson.extractStringField(response, "updated_at")));
		}
		catch (IOException e) {
			String message = e.getMessage();
			if (message != null && message.indexOf("404") >= 0) {
				return TodoistTaskLocation.notFound();
			}
			throw e;
		}
	}

	private static boolean isCompletedTaskJson(String response) {
		if (response == null) {
			return false;
		}
		String checked = TodoistJson.extractStringField(response, "checked");
		if ("true".equalsIgnoreCase(checked)) {
			return true;
		}
		String completed = TodoistJson.extractStringField(response, "is_completed");
		return "true".equalsIgnoreCase(completed);
	}

	boolean isTaskInLocation(TodoistTaskLocation location, String projectId, String sectionId) {
		if (location == null || !location.exists || projectId == null) {
			return false;
		}
		if (!projectId.equals(location.projectId)) {
			return false;
		}
		// No section means "anywhere in this project" — do not reshuffle tasks when the
		// project is already at Todoist's section cap.
		if (sectionId == null || sectionId.length() == 0) {
			return true;
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
		return fetchActiveTasks(null);
	}

	/** Active tasks in one project. Does not include the rest of the account. */
	java.util.List fetchActiveTasksInProject(String projectId) throws IOException {
		if (projectId == null || projectId.trim().length() == 0) {
			return new java.util.ArrayList();
		}
		return fetchActiveTasks(projectId.trim());
	}

	private java.util.List fetchActiveTasks(String projectId) throws IOException {
		java.util.List tasks = new java.util.ArrayList();
		String cursor = null;
		do {
			String path = "/tasks?limit=" + PAGE_LIMIT;
			if (projectId != null) {
				path += "&project_id=" + urlEncode(projectId);
			}
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
		if (sectionId != null && sectionId.length() > 0) {
			sb.append(",\"section_id\":\"").append(TodoistJson.escape(sectionId)).append("\"");
		}
		sb.append(",\"priority\":").append(TodoistPriority.toTodoistApi(record.jinji));
		appendDurationFields(sb, record.durationMinutes);
		appendDueFields(sb, record);
		sb.append('}');
		return sb.toString();
	}

	/**
	 * Partial update. Returns null when the remote task already matches, so the caller
	 * does not POST and Todoist does not log a no-op name change.
	 */
	private String buildUpdateTaskJson(TodoistReminderRecord record, TodoistImportTask existing, boolean contentOnly) {
		if (existing == null) {
			return contentOnly ? buildContentOnlyUpdateJson(record) : buildFullUpdateTaskJson(record);
		}
		StringBuilder sb = new StringBuilder(384);
		sb.append('{');
		boolean any = false;
		String desiredContent = truncate(record.nodeText, 500);
		if (!sameTaskName(existing.content, desiredContent)) {
			any = appendStringField(sb, any, "content", desiredContent);
		}
		if (!contentOnly) {
			String desiredDescription = buildDescription(record);
			if (!sameTaskName(existing.description, desiredDescription)) {
				any = appendStringField(sb, any, "description", desiredDescription);
			}
		}
		int priority = TodoistPriority.toTodoistApi(record.jinji);
		if (!contentOnly && existing.priority != priority) {
			if (any) {
				sb.append(',');
			}
			sb.append("\"priority\":").append(priority);
			any = true;
		}
		if (record.durationMinutes > 0 && existing.durationMinutes != record.durationMinutes) {
			int mark = sb.length();
			appendDurationFields(sb, record.durationMinutes);
			if (!any && sb.length() > mark && sb.charAt(mark) == ',') {
				sb.deleteCharAt(mark);
			}
			if (sb.length() > mark) {
				any = true;
			}
		}
		if (dueChanged(record, existing)) {
			int mark = sb.length();
			appendDueFields(sb, record);
			if (!any && sb.length() > mark && sb.charAt(mark) == ',') {
				sb.deleteCharAt(mark);
			}
			if (sb.length() > mark) {
				any = true;
			}
		}
		if (!any) {
			return null;
		}
		sb.append('}');
		return sb.toString();
	}

	/** Title and due only, for a task that already belongs to another project. */
	private String buildContentOnlyUpdateJson(TodoistReminderRecord record) {
		StringBuilder sb = new StringBuilder(256);
		sb.append("{\"content\":\"").append(TodoistJson.escape(truncate(record.nodeText, 500))).append('"');
		appendDurationFields(sb, record.durationMinutes);
		appendDueFields(sb, record);
		sb.append('}');
		return sb.toString();
	}

	private String buildFullUpdateTaskJson(TodoistReminderRecord record) {
		StringBuilder sb = new StringBuilder(384);
		sb.append("{\"content\":\"").append(TodoistJson.escape(truncate(record.nodeText, 500))).append("\"");
		sb.append(",\"description\":\"").append(TodoistJson.escape(buildDescription(record))).append("\"");
		sb.append(",\"priority\":").append(TodoistPriority.toTodoistApi(record.jinji));
		appendDurationFields(sb, record.durationMinutes);
		appendDueFields(sb, record);
		sb.append('}');
		return sb.toString();
	}

	private static boolean appendStringField(StringBuilder sb, boolean any, String name, String value) {
		if (any) {
			sb.append(',');
		}
		sb.append('"').append(name).append("\":\"").append(TodoistJson.escape(value == null ? "" : value)).append('"');
		return true;
	}

	/** Visible task title: ignore surrounding space and runs of whitespace. */
	static boolean sameTaskName(String remote, String desired) {
		return normalizeTaskName(remote).equals(normalizeTaskName(desired));
	}

	private static String normalizeTaskName(String text) {
		if (text == null) {
			return "";
		}
		String source = text.replace('\u00a0', ' ').replace('\u3000', ' ').trim();
		StringBuilder sb = new StringBuilder(source.length());
		boolean pendingSpace = false;
		for (int i = 0; i < source.length(); i++) {
			char c = source.charAt(i);
			if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
				if (!pendingSpace && sb.length() > 0) {
					sb.append(' ');
					pendingSpace = true;
				}
			}
			else {
				sb.append(c);
				pendingSpace = false;
			}
		}
		return sb.toString();
	}

	private static boolean dueChanged(TodoistReminderRecord record, TodoistImportTask existing) {
		if (record.recurring) {
			String dueString = TodoistCycleMapper.toDueString(record.cycle(), record.remindAt);
			if (dueString == null || dueString.length() == 0) {
				return false;
			}
			String remote = existing.dueString == null ? "" : existing.dueString.trim();
			return !dueString.equalsIgnoreCase(remote);
		}
		if (record.remindAt <= 0) {
			return false;
		}
		if (existing.dueAtMillis <= 0) {
			return true;
		}
		return Math.abs(existing.dueAtMillis - record.remindAt) > 60000L;
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

	/**
	 * Todoist tasks have no custom field. The node id is stored in the description.
	 * File path is not written: a node can move to another map without changing id.
	 */
	static String buildDescription(TodoistReminderRecord record) {
		return buildNodeLink(record == null ? null : record.nodeId);
	}

	static String buildNodeLink(String nodeId) {
		StringBuilder sb = new StringBuilder();
		sb.append("Docear reminder\n");
		sb.append("Node ID: ").append(nodeId == null ? "" : nodeId);
		return sb.toString();
	}

	/**
	 * Rewrite the description of a task this sync created. Imported tasks must not be passed here.
	 *
	 * @return true when a request was sent
	 */
	boolean refreshNodeLink(String taskId, String nodeId, String currentDescription) throws IOException {
		if (taskId == null || taskId.length() == 0 || nodeId == null || nodeId.length() == 0) {
			return false;
		}
		if (!keepsDocearNodeLink(currentDescription, nodeId)) {
			return false;
		}
		final String desired = buildNodeLink(nodeId);
		if (sameTaskName(currentDescription, desired)) {
			return false;
		}
		final String body = "{\"description\":\"" + TodoistJson.escape(desired) + "\"}";
		request("POST", "/tasks/" + taskId, body);
		return true;
	}

	/**
	 * Remove a description that is only our mind-map stamp. Leaves a person's own notes alone.
	 *
	 * @return true when a request was sent
	 */
	boolean clearOwnedStamp(String taskId, String description) throws IOException {
		if (taskId == null || taskId.length() == 0 || !isOwnedStamp(description)) {
			return false;
		}
		request("POST", "/tasks/" + taskId, "{\"description\":\"\"}");
		return true;
	}

	/** True when every line is the stamp this sync writes, with no other note. */
	static boolean isOwnedStamp(String description) {
		if (description == null || description.indexOf("Docear reminder") < 0) {
			return false;
		}
		boolean any = false;
		int start = 0;
		while (start <= description.length()) {
			int end = description.indexOf('\n', start);
			if (end < 0) {
				end = description.length();
			}
			final String line = description.substring(start, end).trim();
			start = end + 1;
			if (line.length() == 0) {
				if (end >= description.length()) {
					break;
				}
				continue;
			}
			any = true;
			if ("Docear reminder".equals(line) || line.startsWith("Map: ") || line.startsWith("Path: ")
					|| line.startsWith("Node ID: ")) {
				if (end >= description.length()) {
					break;
				}
				continue;
			}
			return false;
		}
		return any;
	}

	private static boolean keepsDocearNodeLink(String description, TodoistReminderRecord record) {
		return record != null && keepsDocearNodeLink(description, record.nodeId);
	}

	/** True when this task was created by Docear and we know which node it belongs to. */
	private static boolean keepsDocearNodeLink(String description, String nodeId) {
		if (nodeId == null || nodeId.length() == 0 || description == null) {
			return false;
		}
		return description.indexOf("Docear reminder") >= 0;
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
			if (code == 204) {
				return "";
			}
			in = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
			if (in == null) {
				throw new IOException("Todoist HTTP " + code + " (empty body) for " + path);
			}
			String response = readStream(in);
			if (code == 401) {
				throw new IOException("Todoist API 密钥无效（401）。请在「Todoist 设置」重新粘贴密钥并确定。");
			}
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
