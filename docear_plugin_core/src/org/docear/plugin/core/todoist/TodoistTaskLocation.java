package org.docear.plugin.core.todoist;

final class TodoistTaskLocation {
	final boolean exists;
	final boolean completed;
	final String projectId;
	final String sectionId;
	/** Remote description. Null when the lookup did not read it. */
	final String description;
	/** Todoist {@code updated_at} in epoch millis, or 0 when missing. */
	final long updatedAtMillis;

	private TodoistTaskLocation(boolean exists, boolean completed, String projectId, String sectionId,
			String description, long updatedAtMillis) {
		this.exists = exists;
		this.completed = completed;
		this.projectId = projectId;
		this.sectionId = sectionId;
		this.description = description;
		this.updatedAtMillis = updatedAtMillis < 0 ? 0L : updatedAtMillis;
	}

	static TodoistTaskLocation found(String projectId, String sectionId) {
		return found(projectId, sectionId, false);
	}

	static TodoistTaskLocation found(String projectId, String sectionId, boolean completed) {
		return found(projectId, sectionId, completed, null);
	}

	static TodoistTaskLocation found(String projectId, String sectionId, boolean completed, String description) {
		return found(projectId, sectionId, completed, description, 0L);
	}

	static TodoistTaskLocation found(String projectId, String sectionId, boolean completed, String description,
			long updatedAtMillis) {
		return new TodoistTaskLocation(true, completed, projectId, sectionId, description, updatedAtMillis);
	}

	static TodoistTaskLocation notFound() {
		return new TodoistTaskLocation(false, false, null, null, null, 0L);
	}
}
