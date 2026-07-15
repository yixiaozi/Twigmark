package org.docear.plugin.core.todoist;

/**
 * Mind-map urgency ({@code JINJI}) ↔ Todoist API priority (1=normal … 4=urgent).
 * Values greater than 4 are clamped to 4. Task level ({@code TASKLEVEL}) is never mapped.
 */
final class TodoistPriority {
	private TodoistPriority() {
	}

	/** Push: mind-map 紧急程度 → Todoist API priority. */
	static int toTodoistApi(final int jinji) {
		if (jinji <= 0) {
			return 1;
		}
		if (jinji >= 4) {
			return 4;
		}
		return jinji;
	}

	/**
	 * Pull: Todoist API priority → mind-map 紧急程度.
	 * Default Todoist priority (1) never clears a richer local {@code JINJI} — map settings win.
	 */
	static int toJinji(final int apiPriority, final int localJinji) {
		int clamped = apiPriority;
		if (clamped < 1) {
			clamped = 1;
		}
		if (clamped > 4) {
			clamped = 4;
		}
		if (clamped <= 1) {
			return localJinji;
		}
		return clamped;
	}
}
