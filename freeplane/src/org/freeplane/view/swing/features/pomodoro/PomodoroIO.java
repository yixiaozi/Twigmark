package org.freeplane.view.swing.features.pomodoro;

import org.freeplane.core.io.IAttributeHandler;
import org.freeplane.core.io.IAttributeWriter;
import org.freeplane.core.io.ITreeWriter;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.NodeBuilder;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.ModeController;

final class PomodoroIO {
	private PomodoroIO() {
	}

	static void install(final ModeController modeController) {
		final MapController mapController = modeController.getMapController();
		add(mapController, PomodoroAttributes.POMODORO, new Setter() {
			public void set(final PomodoroExtension e, final String v) {
				e.setEnabled(PomodoroAttributes.parseBoolean(v));
			}
		});
		add(mapController, PomodoroAttributes.POMODORO_MS, new Setter() {
			public void set(final PomodoroExtension e, final String v) {
				e.setTotalMs(PomodoroAttributes.parseLong(v, 0L));
			}
		});
		add(mapController, PomodoroAttributes.POMODORO_ACTIVE_MS, new Setter() {
			public void set(final PomodoroExtension e, final String v) {
				e.setActiveMs(PomodoroAttributes.parseLong(v, 0L));
			}
		});
		add(mapController, PomodoroAttributes.POMODORO_STATE, new Setter() {
			public void set(final PomodoroExtension e, final String v) {
				e.setState(v);
			}
		});
		add(mapController, PomodoroAttributes.POMODORO_STARTED_AT, new Setter() {
			public void set(final PomodoroExtension e, final String v) {
				e.setStartedAt(PomodoroAttributes.parseLong(v, 0L));
			}
		});
		add(mapController, PomodoroAttributes.POMODORO_SESSION_AT, new Setter() {
			public void set(final PomodoroExtension e, final String v) {
				e.setSessionAt(PomodoroAttributes.parseLong(v, 0L));
			}
		});
		add(mapController, PomodoroAttributes.POMODORO_PAUSED_AT, new Setter() {
			public void set(final PomodoroExtension e, final String v) {
				e.setPausedAt(PomodoroAttributes.parseLong(v, 0L));
			}
		});
		add(mapController, PomodoroAttributes.POMODORO_SESSION_PAUSES, new Setter() {
			public void set(final PomodoroExtension e, final String v) {
				e.setSessionPauses(v);
			}
		});
		add(mapController, PomodoroAttributes.POMODORO_LOG, new Setter() {
			public void set(final PomodoroExtension e, final String v) {
				e.setLog(v);
			}
		});
		mapController.getWriteManager().addAttributeWriter(NodeBuilder.XML_NODE, new IAttributeWriter() {
			public void writeAttributes(final ITreeWriter writer, final Object userObject, final String tag) {
				if (!NodeBuilder.XML_NODE.equals(tag)) {
					return;
				}
				final NodeModel node = (NodeModel) userObject;
				final PomodoroExtension extension = PomodoroExtension.getExtension(node);
				if (extension == null || extension.isEmpty()) {
					return;
				}
				if (extension.isEnabled()) {
					writer.addAttribute(PomodoroAttributes.POMODORO, "true");
				}
				if (extension.getTotalMs() > 0) {
					writer.addAttribute(PomodoroAttributes.POMODORO_MS, Long.toString(extension.getTotalMs()));
				}
				if (extension.getActiveMs() > 0) {
					writer.addAttribute(PomodoroAttributes.POMODORO_ACTIVE_MS, Long.toString(extension.getActiveMs()));
				}
				final String state = extension.getState();
				if (state != null && !PomodoroExtension.STATE_IDLE.equals(state)) {
					writer.addAttribute(PomodoroAttributes.POMODORO_STATE, state);
				}
				if (extension.getStartedAt() > 0) {
					writer.addAttribute(PomodoroAttributes.POMODORO_STARTED_AT, Long.toString(extension.getStartedAt()));
				}
				if (extension.getSessionAt() > 0) {
					writer.addAttribute(PomodoroAttributes.POMODORO_SESSION_AT, Long.toString(extension.getSessionAt()));
				}
				if (extension.getPausedAt() > 0) {
					writer.addAttribute(PomodoroAttributes.POMODORO_PAUSED_AT, Long.toString(extension.getPausedAt()));
				}
				if (extension.getSessionPauses().length() > 0) {
					writer.addAttribute(PomodoroAttributes.POMODORO_SESSION_PAUSES, extension.getSessionPauses());
				}
				if (extension.getLog().length() > 0) {
					writer.addAttribute(PomodoroAttributes.POMODORO_LOG, extension.getLog());
				}
			}
		});
	}

	private static void add(final MapController mapController, final String name, final Setter setter) {
		mapController.getReadManager().addAttributeHandler(NodeBuilder.XML_NODE, name, new IAttributeHandler() {
			public void setAttribute(final Object userObject, final String value) {
				setter.set(PomodoroExtension.getOrCreateExtension((NodeModel) userObject), value);
			}
		});
	}

	private interface Setter {
		void set(PomodoroExtension extension, String value);
	}
}
