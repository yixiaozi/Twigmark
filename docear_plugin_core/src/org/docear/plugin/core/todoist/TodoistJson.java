package org.docear.plugin.core.todoist;

final class TodoistJson {
	private TodoistJson() {
	}

	static String escape(String value) {
		if (value == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder(value.length() + 16);
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
			case '\\':
				sb.append("\\\\");
				break;
			case '"':
				sb.append("\\\"");
				break;
			case '\n':
				sb.append("\\n");
				break;
			case '\r':
				sb.append("\\r");
				break;
			case '\t':
				sb.append("\\t");
				break;
			default:
				if (c < 0x20) {
					sb.append(String.format("\\u%04x", (int) c));
				}
				else {
					sb.append(c);
				}
			}
		}
		return sb.toString();
	}

	static String extractStringField(String json, String fieldName) {
		return extractFieldValue(json, fieldName);
	}

	static String extractIdField(String json) {
		return extractFieldValue(json, "id");
	}

	static String findIdByExactName(String json, String name) {
		if (json == null || name == null) {
			return null;
		}
		int idx = 0;
		while (idx >= 0 && idx < json.length()) {
			idx = json.indexOf("\"name\"", idx);
			if (idx < 0) {
				return null;
			}
			idx += 6;
			idx = skipWhitespaceAndColon(json, idx);
			if (idx < 0 || idx >= json.length() || json.charAt(idx) != '"') {
				idx++;
				continue;
			}
			String foundName = readQuotedString(json, idx + 1);
			if (foundName != null && foundName.equals(name)) {
				String object = extractObjectAround(json, idx);
				String id = extractIdField(object);
				if (id != null && id.length() > 0) {
					return id;
				}
			}
			idx++;
		}
		return null;
	}

	static String extractNextCursor(String json) {
		String cursor = extractFieldValue(json, "next_cursor");
		if (cursor == null || cursor.length() == 0 || "null".equals(cursor)) {
			return null;
		}
		return cursor;
	}

	static void collectIdsByExactName(String json, String name, java.util.List ids) {
		if (json == null || name == null || ids == null) {
			return;
		}
		int idx = 0;
		while (idx >= 0 && idx < json.length()) {
			idx = json.indexOf("\"name\"", idx);
			if (idx < 0) {
				return;
			}
			idx += 6;
			idx = skipWhitespaceAndColon(json, idx);
			if (idx < 0 || idx >= json.length() || json.charAt(idx) != '"') {
				idx++;
				continue;
			}
			String foundName = readQuotedString(json, idx + 1);
			if (foundName != null && foundName.equals(name)) {
				String object = extractObjectAround(json, idx);
				String id = extractIdField(object);
				if (id != null && id.length() > 0 && !ids.contains(id)) {
					ids.add(id);
				}
			}
			idx++;
		}
	}

	static void collectDocearTaskIds(String json, java.util.List taskIds) {
		if (json == null || taskIds == null) {
			return;
		}
		int idx = 0;
		while (true) {
			idx = json.indexOf("Docear reminder", idx);
			if (idx < 0) {
				return;
			}
			String object = extractObjectAround(json, idx);
			if (object != null) {
				String id = extractIdField(object);
				if (id != null && id.length() > 0 && !taskIds.contains(id)) {
					taskIds.add(id);
				}
			}
			idx++;
		}
	}

	static void collectImportTasks(String json, java.util.List tasks) {
		if (json == null || tasks == null) {
			return;
		}
		int idx = 0;
		while (idx >= 0 && idx < json.length()) {
			idx = findJsonKey(json, idx, "\"id\"");
			if (idx < 0) {
				return;
			}
			String object = extractObjectAround(json, idx);
			if (isTaskObject(object)) {
				TodoistImportTask task = parseImportTask(object);
				if (task != null && !containsTaskId(tasks, task.id)) {
					tasks.add(task);
				}
			}
			idx += 4;
		}
	}

	private static int findJsonKey(String json, int fromIndex, String key) {
		int idx = fromIndex;
		while (idx >= 0 && idx < json.length()) {
			idx = json.indexOf(key, idx);
			if (idx < 0) {
				return -1;
			}
			if (!isJsonKeyAt(json, idx)) {
				idx += key.length();
				continue;
			}
			return idx;
		}
		return -1;
	}

	private static boolean isJsonKeyAt(String json, int keyStart) {
		if (keyStart < 0 || keyStart >= json.length()) {
			return false;
		}
		if (keyStart > 0) {
			char before = json.charAt(keyStart - 1);
			if (before != '{' && before != ',' && !Character.isWhitespace(before)) {
				return false;
			}
		}
		if (json.charAt(keyStart) != '"') {
			return false;
		}
		int keyEnd = keyStart + 1;
		while (keyEnd < json.length()) {
			char c = json.charAt(keyEnd);
			if (c == '"') {
				break;
			}
			if (c == '\\') {
				keyEnd++;
			}
			keyEnd++;
		}
		if (keyEnd >= json.length()) {
			return false;
		}
		int afterKey = skipWhitespaceAndColon(json, keyEnd + 1);
		if (afterKey < 0 || afterKey >= json.length()) {
			return false;
		}
		char valueStart = json.charAt(afterKey);
		return valueStart == '"' || valueStart == '-' || Character.isDigit(valueStart) || valueStart == '{'
				|| valueStart == '[' || valueStart == 't' || valueStart == 'f' || valueStart == 'n';
	}

	private static boolean isTaskObject(String object) {
		if (object == null) {
			return false;
		}
		if (object.indexOf("\"content\"") < 0) {
			return false;
		}
		if (object.indexOf("\"project_id\"") < 0) {
			return false;
		}
		return object.indexOf("\"checked\"") >= 0 || object.indexOf("\"is_deleted\"") >= 0
				|| object.indexOf("\"user_id\"") >= 0 || object.indexOf("\"added_at\"") >= 0
				|| object.indexOf("\"is_completed\"") >= 0;
	}

	static java.util.Map collectIdNamePairs(String json) {
		java.util.Map names = new java.util.HashMap();
		if (json == null) {
			return names;
		}
		int idx = 0;
		while (idx >= 0 && idx < json.length()) {
			idx = json.indexOf("\"name\"", idx);
			if (idx < 0) {
				return names;
			}
			idx += 6;
			idx = skipWhitespaceAndColon(json, idx);
			if (idx < 0 || idx >= json.length() || json.charAt(idx) != '"') {
				idx++;
				continue;
			}
			String foundName = readQuotedString(json, idx + 1);
			String object = extractObjectAround(json, idx);
			String id = extractIdField(object);
			if (id != null && id.length() > 0 && foundName != null && foundName.length() > 0) {
				names.put(id, foundName);
			}
			idx++;
		}
		return names;
	}

	private static boolean containsTaskId(java.util.List tasks, String id) {
		for (int i = 0; i < tasks.size(); i++) {
			TodoistImportTask task = (TodoistImportTask) tasks.get(i);
			if (id.equals(task.id)) {
				return true;
			}
		}
		return false;
	}

	private static TodoistImportTask parseImportTask(String object) {
		String id = extractIdField(object);
		if (id == null || id.length() == 0) {
			return null;
		}
		String content = extractFieldValue(object, "content");
		String description = extractFieldValue(object, "description");
		String projectId = extractFieldValue(object, "project_id");
		String sectionId = extractFieldValue(object, "section_id");
		long dueAt = parseDueMillis(object);
		String dueString = extractDueString(object);
		boolean recurring = extractDueIsRecurring(object)
				|| (dueString != null && dueString.toLowerCase().indexOf("every") >= 0);
		int priority = parseIntField(object, "priority", 1);
		int durationMinutes = parseDurationMinutes(object);
		return new TodoistImportTask(id, content, description, projectId, sectionId, dueAt, recurring, dueString,
				priority, durationMinutes);
	}

	private static boolean extractDueIsRecurring(String taskJson) {
		int dueIdx = findJsonKey(taskJson, 0, "\"due\"");
		if (dueIdx < 0) {
			return false;
		}
		String dueObject = extractDueValueObject(taskJson, dueIdx);
		if (dueObject == null) {
			return false;
		}
		String flag = extractFieldValue(dueObject, "is_recurring");
		return "true".equalsIgnoreCase(flag);
	}

	private static int parseDurationMinutes(String taskJson) {
		int durationIdx = findJsonKey(taskJson, 0, "\"duration\"");
		if (durationIdx < 0) {
			return 0;
		}
		int after = skipWhitespaceAndColon(taskJson, durationIdx + "\"duration\"".length());
		if (after < 0 || after >= taskJson.length()) {
			return 0;
		}
		char start = taskJson.charAt(after);
		if (start == '{') {
			String durationObject = extractObjectAround(taskJson, after);
			if (durationObject == null) {
				return 0;
			}
			int amount = parseIntField(durationObject, "amount", 0);
			String unit = extractFieldValue(durationObject, "unit");
			if (amount <= 0) {
				return 0;
			}
			if (unit != null && unit.toLowerCase().indexOf("day") >= 0) {
				return amount * 24 * 60;
			}
			return amount;
		}
		if (start == '-' || Character.isDigit(start)) {
			int amount = parseIntAt(taskJson, after, 0);
			String unit = extractFieldValue(taskJson, "duration_unit");
			if (amount <= 0) {
				return 0;
			}
			if (unit != null && unit.toLowerCase().indexOf("day") >= 0) {
				return amount * 24 * 60;
			}
			return amount;
		}
		return 0;
	}

	private static int parseIntField(String json, String fieldName, int defaultValue) {
		String raw = extractFieldValue(json, fieldName);
		if (raw == null || raw.length() == 0 || "null".equals(raw)) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(raw.trim());
		}
		catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	private static int parseIntAt(String json, int start, int defaultValue) {
		int i = start;
		while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
			i++;
		}
		int end = i;
		if (end < json.length() && json.charAt(end) == '-') {
			end++;
		}
		while (end < json.length() && Character.isDigit(json.charAt(end))) {
			end++;
		}
		if (end <= i) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(json.substring(i, end));
		}
		catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	private static long parseDueMillis(String taskJson) {
		String dueDateTime = extractFieldValue(taskJson, "due_datetime");
		if (dueDateTime != null && dueDateTime.length() > 0) {
			long parsed = parseIsoDateTime(dueDateTime);
			if (parsed > 0) {
				return parsed;
			}
		}
		long dueObjectMillis = parseDueObjectMillis(taskJson);
		if (dueObjectMillis > 0) {
			return dueObjectMillis;
		}
		return parseDeadlineMillis(taskJson);
	}

	private static long parseDueObjectMillis(String taskJson) {
		int dueIdx = findJsonKey(taskJson, 0, "\"due\"");
		if (dueIdx < 0) {
			return 0L;
		}
		String dueObject = extractDueValueObject(taskJson, dueIdx);
		if (dueObject == null || dueObject.indexOf("\"date\"") < 0) {
			return 0L;
		}
		String datetime = extractFieldValue(dueObject, "datetime");
		if (datetime != null && datetime.length() > 0) {
			long parsed = parseIsoDateTime(datetime);
			if (parsed > 0) {
				return parsed;
			}
		}
		String date = extractFieldValue(dueObject, "date");
		if (date != null && date.length() > 0) {
			// Todoist often embeds time in due.date ("2026-06-27T11:00:00"); do not append T09:00:00.
			if (date.indexOf('T') >= 0) {
				return parseIsoDateTime(date);
			}
			return parseIsoDateTime(date + "T09:00:00");
		}
		return 0L;
	}

	/** Returns the JSON object that is the value of the due field, not the parent task. */
	private static String extractDueValueObject(String taskJson, int dueKeyIdx) {
		int valueStart = skipWhitespaceAndColon(taskJson, dueKeyIdx + "\"due\"".length());
		if (valueStart >= 0 && valueStart < taskJson.length() && taskJson.charAt(valueStart) == '{') {
			return extractObjectAround(taskJson, valueStart);
		}
		if (valueStart >= 0 && valueStart < taskJson.length() && taskJson.startsWith("null", valueStart)) {
			return null;
		}
		return extractObjectAround(taskJson, dueKeyIdx);
	}

	private static long parseDeadlineMillis(String taskJson) {
		int deadlineIdx = findJsonKey(taskJson, 0, "\"deadline\"");
		if (deadlineIdx < 0) {
			return 0L;
		}
		String deadlineObject = extractObjectAround(taskJson, deadlineIdx);
		if (deadlineObject == null) {
			return 0L;
		}
		if (deadlineObject.indexOf("null") >= 0 && deadlineObject.length() < 16) {
			return 0L;
		}
		String date = extractFieldValue(deadlineObject, "date");
		if (date != null && date.length() > 0) {
			if (date.indexOf('T') >= 0) {
				return parseIsoDateTime(date);
			}
			return parseIsoDateTime(date + "T09:00:00");
		}
		return 0L;
	}

	private static String extractDueString(String taskJson) {
		int dueIdx = findJsonKey(taskJson, 0, "\"due\"");
		if (dueIdx < 0) {
			return extractFieldValue(taskJson, "due_string");
		}
		String dueObject = extractDueValueObject(taskJson, dueIdx);
		if (dueObject == null) {
			return null;
		}
		return extractFieldValue(dueObject, "string");
	}

	private static long parseIsoDateTime(String value) {
		if (value == null || value.length() < 10) {
			return 0L;
		}
		try {
			String normalized = value.trim();
			if (normalized.endsWith("Z")) {
				normalized = normalized.substring(0, normalized.length() - 1) + "+0000";
			}
			// Strip fractional seconds: 2026-06-27T11:00:00.000000+0000
			final int dot = normalized.indexOf('.');
			if (dot > 10) {
				int end = dot + 1;
				while (end < normalized.length() && Character.isDigit(normalized.charAt(end))) {
					end++;
				}
				normalized = normalized.substring(0, dot) + normalized.substring(end);
			}
			// Normalize +00:00 → +0000 for SimpleDateFormat Z
			if (normalized.length() >= 6 && (normalized.charAt(normalized.length() - 3) == ':')
					&& (normalized.charAt(normalized.length() - 6) == '+' || normalized.charAt(normalized.length() - 6) == '-')) {
				normalized = normalized.substring(0, normalized.length() - 3)
						+ normalized.substring(normalized.length() - 2);
			}
			java.text.SimpleDateFormat format;
			if (normalized.indexOf('T') >= 0) {
				if (normalized.indexOf('+') > 10 || normalized.lastIndexOf('-') > 10) {
					format = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
				}
				else {
					format = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
				}
			}
			else {
				format = new java.text.SimpleDateFormat("yyyy-MM-dd");
				format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
			}
			java.util.Date date = format.parse(normalized);
			return date != null ? date.getTime() : 0L;
		}
		catch (Exception e) {
			return 0L;
		}
	}

	private static String extractFieldValue(String json, String fieldName) {
		if (json == null) {
			return null;
		}
		String needle = "\"" + fieldName + "\"";
		int idx = 0;
		while (idx >= 0) {
			idx = json.indexOf(needle, idx);
			if (idx < 0) {
				return null;
			}
			int afterKey = idx + needle.length();
			if (afterKey < json.length() && json.charAt(afterKey) == '"') {
				idx++;
				continue;
			}
			idx = skipWhitespaceAndColon(json, afterKey);
			if (idx < 0 || idx >= json.length()) {
				return null;
			}
			char c = json.charAt(idx);
			if (c == '"') {
				return readQuotedString(json, idx + 1);
			}
			if (c == 'n' && json.startsWith("null", idx)) {
				return null;
			}
			if (c == 't' && json.startsWith("true", idx)) {
				return "true";
			}
			if (c == 'f' && json.startsWith("false", idx)) {
				return "false";
			}
			if (c == '-' || Character.isDigit(c)) {
				return readNumberToken(json, idx);
			}
			idx = afterKey + 1;
		}
		return null;
	}

	private static int skipWhitespaceAndColon(String json, int idx) {
		while (idx < json.length() && Character.isWhitespace(json.charAt(idx))) {
			idx++;
		}
		if (idx >= json.length() || json.charAt(idx) != ':') {
			return -1;
		}
		idx++;
		while (idx < json.length() && Character.isWhitespace(json.charAt(idx))) {
			idx++;
		}
		return idx;
	}

	private static String readNumberToken(String json, int start) {
		int idx = start;
		if (idx < json.length() && json.charAt(idx) == '-') {
			idx++;
		}
		int begin = start;
		while (idx < json.length()) {
			char c = json.charAt(idx);
			if (Character.isDigit(c)) {
				idx++;
				continue;
			}
			break;
		}
		if (idx == begin || (idx == begin + 1 && json.charAt(begin) == '-')) {
			return null;
		}
		return json.substring(begin, idx);
	}

	private static String readQuotedString(String json, int start) {
		StringBuilder sb = new StringBuilder();
		boolean escaped = false;
		for (int idx = start; idx < json.length(); idx++) {
			char c = json.charAt(idx);
			if (escaped) {
				if (c == 'u') {
					if (idx + 4 < json.length()) {
						try {
							int code = Integer.parseInt(json.substring(idx + 1, idx + 5), 16);
							sb.append((char) code);
							idx += 4;
						}
						catch (NumberFormatException e) {
							sb.append(c);
						}
					}
					else {
						sb.append(c);
					}
				}
				else if (c == 'n') {
					sb.append('\n');
				}
				else if (c == 'r') {
					sb.append('\r');
				}
				else if (c == 't') {
					sb.append('\t');
				}
				else {
					sb.append(c);
				}
				escaped = false;
				continue;
			}
			if (c == '\\') {
				escaped = true;
				continue;
			}
			if (c == '"') {
				return sb.toString();
			}
			sb.append(c);
		}
		return null;
	}

	private static String extractObjectAround(String json, int positionInObject) {
		int start = positionInObject;
		while (start > 0 && json.charAt(start) != '{') {
			start--;
		}
		if (start < 0 || json.charAt(start) != '{') {
			return null;
		}
		int depth = 0;
		for (int i = start; i < json.length(); i++) {
			char c = json.charAt(i);
			if (c == '{') {
				depth++;
			}
			else if (c == '}') {
				depth--;
				if (depth == 0) {
					return json.substring(start, i + 1);
				}
			}
		}
		return null;
	}
}
