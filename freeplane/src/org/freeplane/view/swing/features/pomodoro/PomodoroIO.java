package org.freeplane.view.swing.features.pomodoro;

import org.freeplane.core.io.IAttributeHandler;
import org.freeplane.core.io.IAttributeWriter;
import org.freeplane.core.io.ITreeWriter;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.NodeBuilder;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.ModeController;

/**
 * Registers read/write of pomodoro attributes on {@code <node>} elements.
 */
final class PomodoroIO {
	private PomodoroIO() {
	}

	static void install(final ModeController modeController) {
		final MapController mapController = modeController.getMapController();
		mapController.getReadManager().addAttributeHandler(NodeBuilder.XML_NODE, PomodoroAttributes.POMODORO,
				new IAttributeHandler() {
					public void setAttribute(final Object userObject, final String value) {
						PomodoroExtension.getOrCreateExtension((NodeModel) userObject)
								.setEnabled(PomodoroAttributes.parseBoolean(value));
					}
				});
		mapController.getReadManager().addAttributeHandler(NodeBuilder.XML_NODE, PomodoroAttributes.POMODORO_MS,
				new IAttributeHandler() {
					public void setAttribute(final Object userObject, final String value) {
						PomodoroExtension.getOrCreateExtension((NodeModel) userObject)
								.setTotalMs(PomodoroAttributes.parseLong(value, 0L));
					}
				});
		mapController.getReadManager().addAttributeHandler(NodeBuilder.XML_NODE, PomodoroAttributes.POMODORO_ACTIVE_MS,
				new IAttributeHandler() {
					public void setAttribute(final Object userObject, final String value) {
						PomodoroExtension.getOrCreateExtension((NodeModel) userObject)
								.setActiveMs(PomodoroAttributes.parseLong(value, 0L));
					}
				});
		mapController.getReadManager().addAttributeHandler(NodeBuilder.XML_NODE, PomodoroAttributes.POMODORO_STATE,
				new IAttributeHandler() {
					public void setAttribute(final Object userObject, final String value) {
						PomodoroExtension.getOrCreateExtension((NodeModel) userObject).setState(value);
					}
				});
		mapController.getReadManager().addAttributeHandler(NodeBuilder.XML_NODE, PomodoroAttributes.POMODORO_STARTED_AT,
				new IAttributeHandler() {
					public void setAttribute(final Object userObject, final String value) {
						PomodoroExtension.getOrCreateExtension((NodeModel) userObject)
								.setStartedAt(PomodoroAttributes.parseLong(value, 0L));
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
			}
		});
	}
}
