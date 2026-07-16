package org.docear.plugin.core.quickcommand;

import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.Frame;
import java.io.File;
import java.net.URL;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.freeplane.core.util.Compat;
import org.freeplane.core.util.HtmlUtils;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.LastSelectionMapExtension;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.map.mindmapmode.MMapModel;
import org.freeplane.features.mapio.MapIO;
import org.freeplane.features.mapio.mindmapmode.MMapIO;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.ui.IMapViewManager;
import org.freeplane.features.url.mindmapmode.MFileManager;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderExtension;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderHook;

/**
 * Parses command-palette input and runs open / add / launch actions.
 *
 * <pre>
 * @教程                 → open map
 * note@教程             → add plain node under today's date tree
 * note@教程 + Shift     → add reminder task under today's date tree
 * @@图标节点             → open map and select icon node
 * note@@图标节点         → add plain child under icon node
 * note@@图标节点 + Shift → add reminder child under icon node
 * chrome / notepad      → quick launch
 * mindmaps / allicons   → rebuild indexes
 * </pre>
 */
final class QuickCommandController {
	private QuickCommandController() {
	}

	static List suggest(final String rawInput) {
		final String input = rawInput == null ? "" : rawInput;
		final String trimmed = input.trim();
		if (trimmed.equalsIgnoreCase("mindmaps") || trimmed.toLowerCase(Locale.ROOT).startsWith("allicon")
		        || trimmed.toLowerCase(Locale.ROOT).startsWith("allnode")) {
			final java.util.ArrayList list = new java.util.ArrayList();
			list.add(QuickCommandCandidate.command(trimmed.toLowerCase(Locale.ROOT).startsWith("mind")
			        ? "mindmaps"
			        : (trimmed.toLowerCase(Locale.ROOT).startsWith("allnode") ? "allnodes" : "allicons"),
			        "回车执行索引重建"));
			return list;
		}
		final int atAt = indexOfAtAt(input);
		if (atAt >= 0) {
			final String query = input.substring(atAt + 2).trim();
			return QuickCommandIndex.getInstance().filterIconNodes(query, 40);
		}
		final int at = input.lastIndexOf('@');
		if (at >= 0) {
			final String query = input.substring(at + 1).trim();
			return QuickCommandIndex.getInstance().filterMaps(query, 40);
		}
		if (trimmed.length() >= 1) {
			final List launch = QuickCommandIndex.getInstance().filterLaunch(trimmed, 30);
			if (!launch.isEmpty()) {
				return launch;
			}
		}
		final java.util.ArrayList hints = new java.util.ArrayList();
		hints.add(QuickCommandCandidate.hint("@导图名", "打开导图；左侧写文字可添加节点"));
		hints.add(QuickCommandCandidate.hint("@@图标节点", "跳转到带图标的节点；左侧写文字可添加子节点"));
		hints.add(QuickCommandCandidate.hint("文字 + Enter", "快速启动；Shift+Enter 在 @/@@ 时添加提醒任务"));
		return hints;
	}

	/**
	 * @return true if the dialog should close
	 */
	static boolean execute(final String rawInput, final QuickCommandCandidate selected, final boolean asTask) {
		final String input = rawInput == null ? "" : rawInput.trim();
		if (input.length() == 0) {
			return false;
		}
		try {
			if (selected != null && selected.kind == QuickCommandCandidate.Kind.COMMAND) {
				return runCommand(selected.command);
			}
			if (isCommand(input)) {
				return runCommand(normalizeCommand(input));
			}
			if (selected != null && selected.kind == QuickCommandCandidate.Kind.LAUNCH) {
				return launch(selected.launchFile);
			}
			final int atAt = indexOfAtAt(input);
			if (atAt >= 0) {
				return executeAtAt(input, atAt, selected, asTask);
			}
			final int at = input.lastIndexOf('@');
			if (at >= 0) {
				return executeAt(input, at, selected, asTask);
			}
			if (selected != null && selected.kind == QuickCommandCandidate.Kind.MAP) {
				return openMap(selected.mapFile);
			}
			if (selected != null && selected.kind == QuickCommandCandidate.Kind.ICON_NODE) {
				return openMapAndSelect(selected.mapFile, selected.nodeId);
			}
			final List launch = QuickCommandIndex.getInstance().filterLaunch(input, 1);
			if (!launch.isEmpty()) {
				return launch(((QuickCommandCandidate) launch.get(0)).launchFile);
			}
			return false;
		}
		catch (Exception e) {
			LogUtils.warn("QuickCommand execute failed: " + input, e);
			return false;
		}
	}

	/**
	 * Apply a suggestion into the text field without executing yet, when useful.
	 * @return replacement text, or null if caller should execute immediately
	 */
	static String completeIntoInput(final String rawInput, final QuickCommandCandidate selected) {
		if (selected == null || selected.kind == QuickCommandCandidate.Kind.HINT) {
			return null;
		}
		if (selected.kind == QuickCommandCandidate.Kind.COMMAND) {
			return null;
		}
		if (selected.kind == QuickCommandCandidate.Kind.LAUNCH) {
			return null;
		}
		final String input = rawInput == null ? "" : rawInput;
		final int atAt = indexOfAtAt(input);
		if (atAt >= 0 && selected.kind == QuickCommandCandidate.Kind.ICON_NODE) {
			final String left = input.substring(0, atAt).trim();
			final String right = input.substring(atAt + 2).trim();
			if (left.length() == 0) {
				return null;
			}
			if (right.equals(selected.label)) {
				return null;
			}
			return left + "@@" + selected.label;
		}
		final int at = input.lastIndexOf('@');
		if (at >= 0 && selected.kind == QuickCommandCandidate.Kind.MAP) {
			final String left = input.substring(0, at).trim();
			final String right = input.substring(at + 1).trim();
			if (left.length() == 0) {
				return null;
			}
			if (right.equalsIgnoreCase(selected.label)) {
				return null;
			}
			return left + "@" + selected.label;
		}
		return null;
	}

	private static boolean executeAt(final String input, final int at, final QuickCommandCandidate selected,
	        final boolean asTask) {
		final String left = input.substring(0, at).trim();
		String right = input.substring(at + 1).trim();
		File mapFile = null;
		if (selected != null && selected.kind == QuickCommandCandidate.Kind.MAP && selected.mapFile != null) {
			mapFile = selected.mapFile;
			right = selected.label;
		}
		else {
			final QuickCommandIndex.MapEntry entry = QuickCommandIndex.getInstance().findMapExact(right);
			if (entry != null) {
				mapFile = entry.file;
				right = entry.name;
			}
		}
		if (mapFile == null) {
			LogUtils.warn("QuickCommand: map not found: " + right);
			return false;
		}
		if (left.length() == 0) {
			return openMap(mapFile);
		}
		return addUnderMapDateTree(mapFile, left, asTask);
	}

	private static boolean executeAtAt(final String input, final int atAt, final QuickCommandCandidate selected,
	        final boolean asTask) {
		final String left = input.substring(0, atAt).trim();
		final String right = input.substring(atAt + 2).trim();
		final QuickCommandIndex.IconEntry entry = QuickCommandIndex.getInstance().findIconNode(right, selected);
		if (entry == null) {
			LogUtils.warn("QuickCommand: icon node not found: " + right);
			return false;
		}
		if (left.length() == 0) {
			return openMapAndSelect(entry.file, entry.nodeId);
		}
		return addUnderNode(entry.file, entry.nodeId, left, asTask);
	}

	private static boolean runCommand(final String command) {
		if ("mindmaps".equals(command)) {
			QuickCommandIndex.getInstance().rebuildMaps();
			return false;
		}
		if ("allicons".equals(command) || "allicon".equals(command)) {
			QuickCommandIndex.getInstance().rebuildIcons();
			return false;
		}
		if ("allnodes".equals(command) || "allnode".equals(command)) {
			QuickCommandIndex.getInstance().rebuildIcons();
			return false;
		}
		return false;
	}

	private static boolean isCommand(final String input) {
		final String c = input.toLowerCase(Locale.ROOT);
		return "mindmaps".equals(c) || c.startsWith("allicon") || c.startsWith("allnode");
	}

	private static String normalizeCommand(final String input) {
		final String c = input.toLowerCase(Locale.ROOT);
		if (c.startsWith("mind")) {
			return "mindmaps";
		}
		if (c.startsWith("allnode")) {
			return "allnodes";
		}
		return "allicons";
	}

	private static boolean launch(final File file) {
		if (file == null || !file.exists()) {
			return false;
		}
		try {
			bringDocearForward();
			if (Desktop.isDesktopSupported()) {
				Desktop.getDesktop().open(file);
				return true;
			}
		}
		catch (Exception e) {
			LogUtils.warn("QuickCommand: launch failed " + file, e);
		}
		return false;
	}

	static boolean openMap(final File mapFile) {
		if (mapFile == null || !mapFile.isFile()) {
			return false;
		}
		bringDocearForward();
		try {
			final URL url = Compat.fileToUrl(mapFile);
			final IMapViewManager mapViewManager = Controller.getCurrentController().getMapViewManager();
			if (!mapViewManager.tryToChangeToMapView(url)) {
				Controller.getCurrentModeController().getMapController().newMap(url);
			}
			return true;
		}
		catch (Exception e) {
			LogUtils.warn("QuickCommand: open map failed " + mapFile, e);
			return false;
		}
	}

	static boolean openMapAndSelect(final File mapFile, final String nodeId) {
		if (!openMap(mapFile)) {
			return false;
		}
		selectNodeWithRetry(mapFile, nodeId, 0);
		return true;
	}

	private static void selectNodeWithRetry(final File mapFile, final String nodeId, final int attempt) {
		if (mapFile == null || nodeId == null || attempt > 12) {
			return;
		}
		try {
			final IMapViewManager mapViewManager = Controller.getCurrentController().getMapViewManager();
			final Map maps = mapViewManager.getMaps(MModeController.MODENAME);
			for (final Object mapObj : maps.values()) {
				final MapModel map = (MapModel) mapObj;
				if (!isSameFile(map.getFile(), mapFile)) {
					continue;
				}
				final NodeModel node = map.getNodeForID(nodeId);
				if (node != null) {
					final MMapController mapController = (MMapController) MModeController.getMModeController()
					        .getMapController();
					unfoldAncestors(mapController, node);
					Controller.getCurrentController().getSelection().selectAsTheOnlyOneSelected(node);
					mapController.centerNode(node);
					return;
				}
			}
		}
		catch (Exception e) {
			LogUtils.warn("QuickCommand: select node failed", e);
		}
		final javax.swing.Timer timer = new javax.swing.Timer(250, new java.awt.event.ActionListener() {
			public void actionPerformed(final java.awt.event.ActionEvent e) {
				selectNodeWithRetry(mapFile, nodeId, attempt + 1);
			}
		});
		timer.setRepeats(false);
		timer.start();
	}

	private static boolean addUnderMapDateTree(final File mapFile, final String text, final boolean asTask) {
		final MapModel map = loadMap(mapFile);
		if (map == null) {
			return false;
		}
		final MMapController mapController = (MMapController) MModeController.getMModeController().getMapController();
		final NodeModel day = findOrCreateDateHierarchy(map, mapController);
		if (day == null) {
			return false;
		}
		final NodeModel node = appendChild(mapController, day, text);
		if (node == null) {
			return false;
		}
		if (asTask) {
			attachReminder(node);
		}
		LastSelectionMapExtension.getOrCreate(map).setLastSelectedNodeId(node.createID());
		if (!persist(map, mapFile)) {
			return false;
		}
		reveal(mapFile, node.getID());
		return true;
	}

	private static boolean addUnderNode(final File mapFile, final String parentId, final String text,
	        final boolean asTask) {
		final MapModel map = loadMap(mapFile);
		if (map == null) {
			return false;
		}
		final NodeModel parent = map.getNodeForID(parentId);
		if (parent == null) {
			LogUtils.warn("QuickCommand: parent node missing " + parentId);
			return false;
		}
		final MMapController mapController = (MMapController) MModeController.getMModeController().getMapController();
		final NodeModel node = appendChild(mapController, parent, text);
		if (node == null) {
			return false;
		}
		if (asTask) {
			attachReminder(node);
		}
		LastSelectionMapExtension.getOrCreate(map).setLastSelectedNodeId(node.createID());
		if (!persist(map, mapFile)) {
			return false;
		}
		reveal(mapFile, node.getID());
		return true;
	}

	private static NodeModel appendChild(final MMapController mapController, final NodeModel parent,
	        final String text) {
		if (mapController.isFolded(parent)) {
			mapController.setFolded(parent, false);
		}
		final NodeModel node = mapController.addNewNode(parent, parent.getChildCount(), parent.isNewChildLeft());
		if (node == null) {
			return null;
		}
		node.setText(text);
		node.createID();
		return node;
	}

	private static void attachReminder(final NodeModel node) {
		final ReminderHook reminderHook = (ReminderHook) Controller.getCurrentModeController()
		        .getExtension(ReminderHook.class);
		if (reminderHook == null) {
			return;
		}
		final ReminderExtension existing = ReminderExtension.getExtension(node);
		if (existing != null) {
			reminderHook.undoableToggleHook(node);
		}
		final ReminderExtension reminder = new ReminderExtension(node);
		reminder.setRemindUserAt(System.currentTimeMillis());
		reminder.setPeriodUnitAsString("DAY");
		reminder.setPeriod(1);
		reminderHook.undoableActivateHook(node, reminder);
	}

	private static MapModel loadMap(final File mapFile) {
		final MapModel loaded = findLoadedMap(mapFile);
		if (loaded != null) {
			return loaded;
		}
		try {
			final MMapIO mapIO = (MMapIO) MModeController.getMModeController().getExtension(MapIO.class);
			final MapModel map = new MMapModel();
			final URL url = Compat.fileToUrl(mapFile);
			if (url == null || !mapIO.loadCatchExceptions(url, map)) {
				return null;
			}
			map.setURL(url);
			return map;
		}
		catch (Exception e) {
			LogUtils.warn("QuickCommand: load map failed " + mapFile, e);
			return null;
		}
	}

	private static MapModel findLoadedMap(final File mapFile) {
		final IMapViewManager mapViewManager = Controller.getCurrentController().getMapViewManager();
		final Map maps = mapViewManager.getMaps();
		for (final Object mapObj : maps.values()) {
			final MapModel map = (MapModel) mapObj;
			if (isSameFile(map.getFile(), mapFile)) {
				return map;
			}
		}
		return null;
	}

	private static boolean persist(final MapModel map, final File mapFile) {
		try {
			final MFileManager fileManager = (MFileManager) MFileManager.getController();
			if (fileManager.save(map, mapFile)) {
				return true;
			}
			final MMapIO mapIO = (MMapIO) MModeController.getMModeController().getExtension(MapIO.class);
			mapIO.writeToFile(map, mapFile);
			map.setURL(Compat.fileToUrl(mapFile));
			map.setSaved(true);
			return true;
		}
		catch (Exception e) {
			LogUtils.warn("QuickCommand: save failed " + mapFile, e);
			return false;
		}
	}

	private static void reveal(final File mapFile, final String nodeId) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				openMapAndSelect(mapFile, nodeId);
			}
		});
	}

	private static NodeModel findOrCreateDateHierarchy(final MapModel map, final MMapController mapController) {
		final Calendar cal = Calendar.getInstance();
		final String year = String.valueOf(cal.get(Calendar.YEAR));
		final String month = String.valueOf(cal.get(Calendar.MONTH) + 1);
		final String day = String.valueOf(cal.get(Calendar.DAY_OF_MONTH));
		final NodeModel yearNode = findOrCreateChild(mapController, map.getRootNode(), year);
		if (yearNode == null) {
			return null;
		}
		final NodeModel monthNode = findOrCreateChild(mapController, yearNode, month);
		if (monthNode == null) {
			return null;
		}
		return findOrCreateChild(mapController, monthNode, day);
	}

	private static NodeModel findOrCreateChild(final MMapController mapController, final NodeModel parent,
	        final String title) {
		final NodeModel found = findChildByTitle(parent, title);
		if (found != null) {
			return found;
		}
		if (mapController.isFolded(parent)) {
			mapController.setFolded(parent, false);
		}
		final NodeModel child = mapController.addNewNode(parent, parent.getChildCount(), parent.isNewChildLeft());
		if (child != null) {
			child.setText(title);
			child.createID();
		}
		return child;
	}

	private static NodeModel findChildByTitle(final NodeModel parent, final String title) {
		final MapController mapController = Controller.getCurrentModeController().getMapController();
		for (final NodeModel child : mapController.childrenUnfolded(parent)) {
			final String nodeText = HtmlUtils.htmlToPlain(child.getText());
			if (title.equals(nodeText != null ? nodeText.trim() : "")) {
				return child;
			}
		}
		return null;
	}

	private static void unfoldAncestors(final MMapController mapController, final NodeModel node) {
		NodeModel current = node.getParentNode();
		while (current != null) {
			if (mapController.isFolded(current)) {
				mapController.setFolded(current, false);
			}
			current = current.getParentNode();
		}
	}

	private static void bringDocearForward() {
		try {
			final Frame frame = Controller.getCurrentController().getViewController().getFrame();
			if (frame == null) {
				return;
			}
			frame.setVisible(true);
			frame.setState(Frame.NORMAL);
			frame.toFront();
			frame.requestFocus();
		}
		catch (Exception e) {
			// ignore
		}
	}

	private static boolean isSameFile(final File a, final File b) {
		if (a == null || b == null) {
			return false;
		}
		try {
			return a.getCanonicalFile().equals(b.getCanonicalFile());
		}
		catch (Exception e) {
			return a.getAbsolutePath().equalsIgnoreCase(b.getAbsolutePath());
		}
	}

	private static int indexOfAtAt(final String input) {
		if (input == null) {
			return -1;
		}
		return input.indexOf("@@");
	}
}
