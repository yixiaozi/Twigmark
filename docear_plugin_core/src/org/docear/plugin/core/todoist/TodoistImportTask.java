package org.docear.plugin.core.todoist;

final class TodoistImportTask {
	final String id;
	final String content;
	final String description;
	final String projectId;
	final String sectionId;
	final long dueAtMillis;
	final boolean recurring;
	final String dueString;
	/** Todoist API priority 1..4 (4 = urgent). */
	final int priority;
	/** Duration in minutes (0 = unset). */
	final int durationMinutes;

	TodoistImportTask(String id, String content, String description, String projectId, String sectionId,
			long dueAtMillis, boolean recurring, String dueString) {
		this(id, content, description, projectId, sectionId, dueAtMillis, recurring, dueString, 1, 0);
	}

	TodoistImportTask(String id, String content, String description, String projectId, String sectionId,
			long dueAtMillis, boolean recurring, String dueString, int priority, int durationMinutes) {
		this.id = id;
		this.content = content == null ? "" : content;
		this.description = description == null ? "" : description;
		this.projectId = projectId;
		this.sectionId = sectionId;
		this.dueAtMillis = dueAtMillis;
		this.recurring = recurring;
		this.dueString = dueString;
		this.priority = priority <= 0 ? 1 : priority;
		this.durationMinutes = durationMinutes < 0 ? 0 : durationMinutes;
	}
}
