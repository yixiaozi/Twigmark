package org.docear.plugin.core.eagle;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;

import org.docear.plugin.core.mindmap.MindmapUpdateController;
import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.plugin.workspace.WorkspaceController;
import org.freeplane.plugin.workspace.model.project.AWorkspaceProject;

/**
 * One-shot migration: rebuild Eagle index, then rewrite ExternalObject URIs to eagle://.
 */
public class EagleMigrateAction extends AFreeplaneAction {
	private static final long serialVersionUID = 1L;
	public static final String KEY = "EagleMigrateAction";

	public EagleMigrateAction() {
		super(KEY);
	}

	public void actionPerformed(final ActionEvent e) {
		if (EagleConfig.existingLibraryRoots().isEmpty()) {
			final int option = JOptionPane.showConfirmDialog(owner(),
					TextUtils.getText("eagle.migrate.no_library"), TextUtils.getText("eagle.migrate.title"),
					JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
			if (option == JOptionPane.OK_OPTION) {
				EagleSettingsAction.showSettingsDialog();
			}
			if (EagleConfig.existingLibraryRoots().isEmpty()) {
				return;
			}
		}

		final String[] choices = new String[] {
				TextUtils.getText("eagle.migrate.scope.current"),
				TextUtils.getText("eagle.migrate.scope.open"),
				TextUtils.getText("eagle.migrate.scope.project")
		};
		final Object selected = JOptionPane.showInputDialog(owner(), TextUtils.getText("eagle.migrate.scope.prompt"),
				TextUtils.getText("eagle.migrate.title"), JOptionPane.QUESTION_MESSAGE, null, choices, choices[0]);
		if (selected == null) {
			return;
		}
		final int scope;
		if (choices[1].equals(selected)) {
			scope = 1;
		}
		else if (choices[2].equals(selected)) {
			scope = 2;
		}
		else {
			scope = 0;
		}

		final Frame frame = owner();
		new SwingWorker<EagleImageMigrator.Result, Void>() {
			protected EagleImageMigrator.Result doInBackground() throws Exception {
				EagleItemIndex.getInstance().rebuild(true, null);
				if (scope == 0) {
					final MapModel map = Controller.getCurrentController().getMap();
					final EagleImageMigrator.Result result = EagleImageMigrator.migrateMap(map);
					if (map != null && (result.migrated > 0 || result.imported > 0)) {
						map.setSaved(false);
					}
					return result;
				}
				if (scope == 1) {
					return migrateOpenMaps();
				}
				return migrateProjectMaps();
			}

			protected void done() {
				try {
					final EagleImageMigrator.Result result = get();
					showResult(result);
				}
				catch (Exception ex) {
					JOptionPane.showMessageDialog(frame, String.valueOf(ex.getMessage()),
							TextUtils.getText("eagle.migrate.title"), JOptionPane.ERROR_MESSAGE);
				}
			}
		}.execute();
	}

	private static EagleImageMigrator.Result migrateOpenMaps() {
		final EagleImageMigrator.Result aggregate = new EagleImageMigrator.Result();
		final Collection<MapModel> maps = Controller.getCurrentController().getMapViewManager().getMaps().values();
		for (MapModel map : maps) {
			final EagleImageMigrator.Result one = EagleImageMigrator.migrateMap(map);
			merge(aggregate, one);
			if (map != null && (one.migrated > 0 || one.imported > 0)) {
				map.setSaved(false);
			}
		}
		return aggregate;
	}

	private static EagleImageMigrator.Result migrateProjectMaps() {
		final EagleUriMigrationUpdater updater = new EagleUriMigrationUpdater(
				TextUtils.getText("eagle.migrate.updater_title"));
		final MindmapUpdateController controller = new MindmapUpdateController(true);
		controller.addMindmapUpdater(updater);
		final List<AWorkspaceProject> projects = new ArrayList<AWorkspaceProject>();
		final AWorkspaceProject current = WorkspaceController.getMapProject();
		if (current != null) {
			projects.add(current);
		}
		else {
			for (AWorkspaceProject project : WorkspaceController.getCurrentModel().getProjects()) {
				projects.add(project);
			}
		}
		controller.updateAllMindmapsInProject(projects);
		return updater.getAggregate();
	}

	private static void merge(final EagleImageMigrator.Result into, final EagleImageMigrator.Result from) {
		into.scanned += from.scanned;
		into.alreadyEagle += from.alreadyEagle;
		into.keptPath += from.keptPath;
		into.migrated += from.migrated;
		into.imported += from.imported;
		into.unmatched += from.unmatched;
		into.unmatchedDetails.addAll(from.unmatchedDetails);
		into.migratedDetails.addAll(from.migratedDetails);
	}

	private static void showResult(final EagleImageMigrator.Result result) {
		final StringBuilder sb = new StringBuilder();
		sb.append(TextUtils.format("eagle.migrate.summary", Integer.valueOf(result.scanned),
				Integer.valueOf(result.alreadyEagle), Integer.valueOf(result.keptPath),
				Integer.valueOf(result.migrated), Integer.valueOf(result.imported),
				Integer.valueOf(result.unmatched)));
		if (!result.unmatchedDetails.isEmpty()) {
			sb.append("\n\n").append(TextUtils.getText("eagle.migrate.unmatched_header")).append('\n');
			final int limit = Math.min(40, result.unmatchedDetails.size());
			for (int i = 0; i < limit; i++) {
				sb.append("• ").append(result.unmatchedDetails.get(i)).append('\n');
			}
			if (result.unmatchedDetails.size() > limit) {
				sb.append("… +").append(result.unmatchedDetails.size() - limit).append('\n');
			}
		}
		final JTextArea area = new JTextArea(sb.toString());
		area.setEditable(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		final JScrollPane scroll = new JScrollPane(area);
		scroll.setPreferredSize(new Dimension(560, 360));
		JOptionPane.showMessageDialog(owner(), scroll, TextUtils.getText("eagle.migrate.title"),
				result.unmatched > 0 ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
	}

	private static Frame owner() {
		return Controller.getCurrentController().getViewController().getFrame();
	}
}
