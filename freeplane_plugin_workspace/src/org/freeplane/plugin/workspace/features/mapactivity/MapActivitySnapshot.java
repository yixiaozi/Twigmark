package org.freeplane.plugin.workspace.features.mapactivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable-ish snapshot of current-map activity for the overlay.
 */
public final class MapActivitySnapshot {

	private final List pomodoro;
	private final List overdue;
	private final List reminders;
	private final List todos;
	private final List flags;
	private final boolean hasRunningPomodoro;
	private final String runningTitle;

	public MapActivitySnapshot(final List pomodoro, final List overdue, final List reminders, final List todos,
	        final List flags, final boolean hasRunningPomodoro, final String runningTitle) {
		this.pomodoro = pomodoro != null ? pomodoro : Collections.EMPTY_LIST;
		this.overdue = overdue != null ? overdue : Collections.EMPTY_LIST;
		this.reminders = reminders != null ? reminders : Collections.EMPTY_LIST;
		this.todos = todos != null ? todos : Collections.EMPTY_LIST;
		this.flags = flags != null ? flags : Collections.EMPTY_LIST;
		this.hasRunningPomodoro = hasRunningPomodoro;
		this.runningTitle = runningTitle != null ? runningTitle : "";
	}

	public static MapActivitySnapshot empty() {
		return new MapActivitySnapshot(Collections.EMPTY_LIST, Collections.EMPTY_LIST, Collections.EMPTY_LIST,
		        Collections.EMPTY_LIST, Collections.EMPTY_LIST, false, "");
	}

	public List getPomodoro() {
		return pomodoro;
	}

	public List getOverdue() {
		return overdue;
	}

	public List getReminders() {
		return reminders;
	}

	public List getTodos() {
		return todos;
	}

	public List getFlags() {
		return flags;
	}

	public boolean hasRunningPomodoro() {
		return hasRunningPomodoro;
	}

	public String getRunningTitle() {
		return runningTitle;
	}

	public int pomodoroCount() {
		return pomodoro.size();
	}

	public int overdueCount() {
		return overdue.size();
	}

	public int reminderCount() {
		return reminders.size();
	}

	public int todoCount() {
		return todos.size();
	}

	public int flagCount() {
		return flags.size();
	}

	public int totalCount() {
		return pomodoroCount() + overdueCount() + reminderCount() + todoCount() + flagCount();
	}

	/** Items that need attention: overdue + live focus. */
	public int attentionCount() {
		int n = overdueCount();
		if (hasRunningPomodoro) {
			n += 1;
		}
		return n;
	}

	public boolean isEmpty() {
		return totalCount() == 0;
	}

	public List allItems() {
		final List all = new ArrayList();
		all.addAll(pomodoro);
		all.addAll(overdue);
		all.addAll(reminders);
		all.addAll(todos);
		all.addAll(flags);
		return all;
	}
}
