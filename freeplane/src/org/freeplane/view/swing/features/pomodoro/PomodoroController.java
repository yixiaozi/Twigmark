package org.freeplane.view.swing.features.pomodoro;

import org.freeplane.core.extension.IExtension;
import org.freeplane.features.icon.IStateIconProvider;
import org.freeplane.features.icon.IconController;
import org.freeplane.features.icon.UIIcon;
import org.freeplane.features.icon.factory.IconStoreFactory;
import org.freeplane.features.map.IMapLifeCycleListener;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.text.TextController;

/**
 * Installs pomodoro persistence, display, session manager, icons and actions.
 */
public final class PomodoroController implements IExtension {
	private static UIIcon runningIcon;
	private static UIIcon pausedIcon;
	private static UIIcon enabledIcon;

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
		registerStateIcon(modeController);
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

	private static void registerStateIcon(final ModeController modeController) {
		IconController.getController(modeController).addStateIconProvider(new IStateIconProvider() {
			public UIIcon getStateIcon(final NodeModel node) {
				final PomodoroExtension ext = PomodoroExtension.getExtension(node);
				if (ext == null || !ext.isEnabled()) {
					return null;
				}
				if (PomodoroExtension.STATE_RUNNING.equals(ext.getState())) {
					return runningIcon();
				}
				if (PomodoroExtension.STATE_PAUSED.equals(ext.getState())) {
					return pausedIcon();
				}
				return enabledIcon();
			}
		});
	}

	private static UIIcon runningIcon() {
		if (runningIcon == null) {
			runningIcon = IconStoreFactory.create().getUIIcon("clock.png");
		}
		return runningIcon;
	}

	private static UIIcon pausedIcon() {
		if (pausedIcon == null) {
			pausedIcon = IconStoreFactory.create().getUIIcon("hourglass.png");
		}
		return pausedIcon;
	}

	private static UIIcon enabledIcon() {
		if (enabledIcon == null) {
			enabledIcon = IconStoreFactory.create().getUIIcon("clock2.png");
		}
		return enabledIcon;
	}
}
