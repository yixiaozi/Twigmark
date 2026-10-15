package org.docear.plugin.core.todoist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.freeplane.core.util.TextUtils;

/**
 * Places non-Docear Todoist tasks into a mind map:
 * project is a direct child of the root, section (板块) sits under the project,
 * and subtasks nest under their parent task.
 */
final class TodoistImportLayout {
	static final String NO_SECTION = "__no_section__";

	private final List projectKeys = new ArrayList();
	private final Map projectLabels = new HashMap();
	private final Map sectionsByProject = new HashMap();
	private final Map sectionLabels = new HashMap();
	private final Map rootsBySection = new HashMap();
	private final Map childrenByParent = new HashMap();

	private TodoistImportLayout() {
	}

	static TodoistImportLayout build(final List tasks, final Map projectNames, final Map sectionNames) {
		final TodoistImportLayout layout = new TodoistImportLayout();
		if (tasks == null || tasks.isEmpty()) {
			return layout;
		}
		final Map byId = new HashMap();
		for (int i = 0; i < tasks.size(); i++) {
			final TodoistImportTask task = (TodoistImportTask) tasks.get(i);
			byId.put(task.id, task);
		}
		for (int i = 0; i < tasks.size(); i++) {
			final TodoistImportTask task = (TodoistImportTask) tasks.get(i);
			if (byId.get(task.parentId) != null) {
				continue;
			}
			final String projectKey = projectKey(task);
			if (!layout.projectLabels.containsKey(projectKey)) {
				layout.projectLabels.put(projectKey, resolveName(projectNames, projectKey,
						TextUtils.getText("todoist.import.unknown_project")));
				layout.projectKeys.add(projectKey);
				layout.sectionsByProject.put(projectKey, new ArrayList());
			}
			final String key = sectionKey(task);
			if (layout.sectionLabels.containsKey(key)) {
				continue;
			}
			layout.sectionLabels.put(key, sectionLabel(task, sectionNames));
			((List) layout.sectionsByProject.get(projectKey)).add(key);
			layout.rootsBySection.put(key, new ArrayList());
		}
		Collections.sort(layout.projectKeys, new LabelComparator(layout.projectLabels));
		for (int p = 0; p < layout.projectKeys.size(); p++) {
			final List sections = (List) layout.sectionsByProject.get(layout.projectKeys.get(p));
			Collections.sort(sections, new SectionLabelComparator(layout.sectionLabels));
		}
		for (int i = 0; i < tasks.size(); i++) {
			final TodoistImportTask task = (TodoistImportTask) tasks.get(i);
			final TodoistImportTask parent = task.parentId == null ? null : (TodoistImportTask) byId.get(task.parentId);
			if (parent == null) {
				final String key = sectionKey(task);
				((List) layout.rootsBySection.get(key)).add(task);
			}
			else {
				List kids = (List) layout.childrenByParent.get(parent.id);
				if (kids == null) {
					kids = new ArrayList();
					layout.childrenByParent.put(parent.id, kids);
				}
				kids.add(task);
			}
		}
		sortTaskLists(layout.rootsBySection);
		sortTaskLists(layout.childrenByParent);
		return layout;
	}

	List projectKeys() {
		return projectKeys;
	}

	String projectLabel(final String projectKey) {
		final String label = (String) projectLabels.get(projectKey);
		return label == null ? "" : label;
	}

	List sectionKeys(final String projectKey) {
		final List list = (List) sectionsByProject.get(projectKey);
		return list == null ? Collections.EMPTY_LIST : list;
	}

	String sectionLabel(final String sectionKey) {
		final String label = (String) sectionLabels.get(sectionKey);
		return label == null ? "" : label;
	}

	List roots(final String sectionKey) {
		final List list = (List) rootsBySection.get(sectionKey);
		return list == null ? Collections.EMPTY_LIST : list;
	}

	List childrenOf(final String taskId) {
		final List list = (List) childrenByParent.get(taskId);
		return list == null ? Collections.EMPTY_LIST : list;
	}

	private static void sortTaskLists(final Map lists) {
		final Comparator comparator = new TaskOrderComparator();
		for (Iterator it = lists.values().iterator(); it.hasNext();) {
			Collections.sort((List) it.next(), comparator);
		}
	}

	private static String projectKey(final TodoistImportTask task) {
		return task.projectId == null ? "" : task.projectId;
	}

	static String sectionKey(final TodoistImportTask task) {
		final String sectionId = task.sectionId == null || task.sectionId.length() == 0 ? NO_SECTION : task.sectionId;
		return projectKey(task) + "|" + sectionId;
	}

	private static String sectionLabel(final TodoistImportTask task, final Map sectionNames) {
		if (task.sectionId == null || task.sectionId.length() == 0) {
			return TextUtils.getText("todoist.import.no_section");
		}
		return resolveName(sectionNames, task.sectionId, TextUtils.getText("todoist.import.unknown_section"));
	}

	private static String resolveName(final Map names, final String id, final String fallback) {
		if (id == null || id.length() == 0 || names == null) {
			return fallback;
		}
		final String name = (String) names.get(id);
		return name != null && name.length() > 0 ? name : fallback;
	}

	private static final class SectionLabelComparator implements Comparator {
		private final Map labels;

		private SectionLabelComparator(final Map labels) {
			this.labels = labels;
		}

		public int compare(final Object a, final Object b) {
			final boolean aNone = String.valueOf(a).endsWith("|" + NO_SECTION);
			final boolean bNone = String.valueOf(b).endsWith("|" + NO_SECTION);
			if (aNone != bNone) {
				return aNone ? 1 : -1;
			}
			final String left = (String) labels.get(a);
			final String right = (String) labels.get(b);
			if (left == null) {
				return right == null ? 0 : 1;
			}
			if (right == null) {
				return -1;
			}
			return left.compareToIgnoreCase(right);
		}
	}

	private static final class LabelComparator implements Comparator {
		private final Map labels;

		private LabelComparator(final Map labels) {
			this.labels = labels;
		}

		public int compare(final Object a, final Object b) {
			final String left = (String) labels.get(a);
			final String right = (String) labels.get(b);
			if (left == null) {
				return right == null ? 0 : 1;
			}
			if (right == null) {
				return -1;
			}
			return left.compareToIgnoreCase(right);
		}
	}

	private static final class TaskOrderComparator implements Comparator {
		public int compare(final Object a, final Object b) {
			final TodoistImportTask left = (TodoistImportTask) a;
			final TodoistImportTask right = (TodoistImportTask) b;
			if (left.childOrder != right.childOrder) {
				return left.childOrder < right.childOrder ? -1 : 1;
			}
			final String lc = left.content == null ? "" : left.content;
			final String rc = right.content == null ? "" : right.content;
			return lc.compareToIgnoreCase(rc);
		}
	}
}
