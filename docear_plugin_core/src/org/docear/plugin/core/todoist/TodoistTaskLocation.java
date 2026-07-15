package org.docear.plugin.core.todoist;

final class TodoistTaskLocation {
	final boolean exists;
	final boolean completed;
	final String projectId;
	final String sectionId;

	private TodoistTaskLocation(boolean exists, boolean completed, String projectId, String sectionId) {
		this.exists = exists;
		this.completed = completed;
		this.projectId = projectId;
		this.sectionId = sectionId;
	}

	static TodoistTaskLocation found(String projectId, String sectionId) {
		return found(projectId, sectionId, false);
	}

	static TodoistTaskLocation found(String projectId, String sectionId, boolean completed) {
		return new TodoistTaskLocation(true, completed, projectId, sectionId);
	}

	static TodoistTaskLocation notFound() {
		return new TodoistTaskLocation(false, false, null, null);
	}
}
