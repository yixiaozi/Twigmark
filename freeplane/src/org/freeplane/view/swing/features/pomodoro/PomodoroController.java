package org.freeplane.view.swing.features.pomodoro;

import org.freeplane.core.extension.IExtension;
import org.freeplane.features.map.IMapLifeCycleListener;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.text.TextController;

/**
 * Installs pomodoro persistence, display, session manager and actions.
 */
public final class PomodoroController implements IExtension {
	private PomodoroController() {
	}

	public static void install(final ModeController modeController) {
		PomodoroIO.install(modeController);
		final PomodoroSessionManager manager = PomodoroSessionManager.install(modeController);
		TextController.getController(modeController).addTextTransformer(new PomodoroTextTransformer());
		modeController.addAction(new StartPomodoroAction());
		modeController.addAction(new PausePomodoroAction());
		modeController.addAction(new StopPomodoroAction());
		modeController.addAction(new TogglePomodoroAction());
		modeController.addAction(new ShowPomodoroWindowAction());
		modeController.addAction(new ShowPomodoroHistoryAction());
		modeController.addAction(new ExportPomodoroStatsAction());
		modeController.addAction(new SyncPomodoroNoteAction());
		modeController.addAction(new CyclePomodoroSkinAction());
		modeController.addAction(new BackfillPomodoroAction());
		modeController.addExtension(PomodoroController.class, new PomodoroController());
		modeController.getMapController().addMapLifeCycleListener(new IMapLifeCycleListener() {
			public void onCreate(final MapModel map) {
				if (map != null && map.getRootNode() != null) {
					manager.recoverStaleRunning(map.getRootNode());
				}
			}

			public void onRemove(final MapModel map) {
			}

			public void onSavedAs(final MapModel map) {
			}

			public void onSaved(final MapModel map) {
			}
		});
	}
}
