package org.docear.plugin.core.quickcapture;

import java.awt.EventQueue;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.Compat;
import org.freeplane.core.util.HtmlUtils;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;
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
import org.freeplane.plugin.workspace.URIUtils;
import org.freeplane.plugin.workspace.WorkspaceController;
import org.freeplane.plugin.workspace.actions.WorkspaceNewMapAction;
import org.freeplane.plugin.workspace.model.project.AWorkspaceProject;

final class QuickCaptureController {
	private static final String PROP_INBOX_DIRECTORY = "quickcapture.inbox_directory";
	private static final String PROP_INBOX_FILENAME = "quickcapture.inbox_filename";
	private static final String DEFAULT_INBOX_FILENAME = "\u6536\u4ef6\u7bb1.mm";
	private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm", Locale.CHINA);

	private QuickCaptureController() {
	}

	static boolean capture(final String userText) {
		final String text = userText == null ? "" : userText.trim();
		if (text.length() == 0) {
			return false;
		}
		try {
			final File inboxFile = resolveInboxFile();
			if (inboxFile == null) {
				return false;
			}
			final AWorkspaceProject project = resolveWorkspaceProject(inboxFile.getParentFile());
			final MapModel map = loadOrCreateInboxMap(project, inboxFile);
			if (map == null) {
				return false;
			}
			if (map.getFile() == null) {
				map.setURL(Compat.fileToUrl(inboxFile));
			}
			final MMapController mapController = (MMapController) MModeController.getMModeController().getMapController();
			final NodeModel dayParent = findOrCreateDateHierarchy(map, mapController);
			if (dayParent == null) {
				return false;
			}
			unfoldAncestors(mapController, dayParent);
			final String timeLabel = TIME_FORMAT.format(new Date());
			final NodeModel timeNode = mapController.addNewNode(dayParent, dayParent.getChildCount(),
			        dayParent.isNewChildLeft());
			if (timeNode == null) {
				return false;
			}
			timeNode.setText(timeLabel);
			timeNode.createID();
			final NodeModel contentNode = mapController.addNewNode(timeNode, 0, timeNode.isNewChildLeft());
			if (contentNode == null) {
				return false;
			}
			contentNode.setText(text);
			final String contentNodeId = contentNode.createID();
			LastSelectionMapExtension.getOrCreate(map).setLastSelectedNodeId(contentNodeId);
			if (!persistInboxMap(map, inboxFile)) {
				return false;
			}
			scheduleRevealAfterCapture(map, inboxFile, contentNodeId);
			return true;
		}
		catch (Exception e) {
			LogUtils.warn("QuickCapture failed.", e);
			return false;
		}
	}

	private static void scheduleRevealAfterCapture(final MapModel editedMap, final File inboxFile,
	        final String contentNodeId) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				revealCapturedNode(editedMap, inboxFile, contentNodeId);
			}
		});
	}

	private static void revealCapturedNode(final MapModel editedMap, final File inboxFile, final String contentNodeId) {
		if (contentNodeId == null || contentNodeId.length() == 0) {
			return;
		}
		try {
			final IMapViewManager mapViewManager = Controller.getCurrentController().getMapViewManager();
			MapModel map = editedMap;
			if (mapViewManager.getViews(map) == null || mapViewManager.getViews(map).isEmpty()) {
				final MapModel openMap = findLoadedMap(inboxFile);
				if (openMap != null) {
					map = openMap;
				}
				final URL url = Compat.fileToUrl(inboxFile);
				if (url != null && (mapViewManager.getViews(map) == null || mapViewManager.getViews(map).isEmpty())) {
					if (mapViewManager.tryToChangeToMapView(url)) {
						map = Controller.getCurrentController().getMap();
						reloadMapFromDisk(map);
					}
				}
			}
			else if (findLoadedMap(inboxFile) != editedMap) {
				final URL url = Compat.fileToUrl(inboxFile);
				if (url != null && mapViewManager.tryToChangeToMapView(url)) {
					map = Controller.getCurrentController().getMap();
					reloadMapFromDisk(map);
				}
			}
			final NodeModel node = map.getNodeForID(contentNodeId);
			if (node == null) {
				return;
			}
			final MMapController mapController = (MMapController) MModeController.getMModeController().getMapController();
			unfoldAncestors(mapController, node);
			mapController.select(node);
			mapViewManager.scrollNodeToVisible(node);
		}
		catch (Exception e) {
			LogUtils.warn("QuickCapture: could not reveal captured node.", e);
		}
	}

	private static void reloadMapFromDisk(final MapModel map) {
		if (map == null || map.getFile() == null) {
			return;
		}
		try {
			final MMapController mapController = (MMapController) MModeController.getMModeController().getMapController();
			if (map.equals(Controller.getCurrentController().getMap())) {
				mapController.restoreCurrentMapPreservingSelection();
			}
		}
		catch (Exception e) {
			LogUtils.warn("QuickCapture: inbox reload skipped.", e);
		}
	}

	private static File resolveInboxFile() {
		String dirPath = ResourceController.getResourceController().getProperty(PROP_INBOX_DIRECTORY, "");
		final String filename = ResourceController.getResourceController().getProperty(PROP_INBOX_FILENAME,
		        DEFAULT_INBOX_FILENAME);
		if (dirPath == null || dirPath.trim().length() == 0) {
			dirPath = MindMapDataRootResolver.getWorkingDirectory().getAbsolutePath();
		}
		final File dir = new File(dirPath.trim());
		if (!dir.isDirectory() && !dir.mkdirs()) {
			LogUtils.warn("QuickCapture: inbox directory not available: " + dir.getAbsolutePath());
			return null;
		}
		return new File(dir, filename);
	}

	private static AWorkspaceProject resolveWorkspaceProject(final File inboxDir) {
		if (inboxDir == null) {
			return null;
		}
		try {
			final Collection<AWorkspaceProject> projects = WorkspaceController.getCurrentModel().getProjects();
			if (projects == null) {
				return null;
			}
			File inboxCanonical = inboxDir;
			try {
				inboxCanonical = inboxDir.getCanonicalFile();
			}
			catch (Exception e) {
				// use absolute path
			}
			for (final AWorkspaceProject project : projects) {
				final File home = URIUtils.getAbsoluteFile(project.getProjectHome());
				if (home == null) {
					continue;
				}
				try {
					if (home.getCanonicalFile().equals(inboxCanonical)) {
						return project;
					}
				}
				catch (Exception e) {
					if (home.getAbsolutePath().equalsIgnoreCase(inboxCanonical.getAbsolutePath())) {
						return project;
					}
				}
			}
		}
		catch (Exception e) {
			// workspace not ready
		}
		return null;
	}

	private static MapModel loadOrCreateInboxMap(final AWorkspaceProject project, final File inboxFile) {
		final MapModel loaded = findLoadedMap(inboxFile);
		if (loaded != null) {
			return loaded;
		}
		final MMapIO mapIO = (MMapIO) MModeController.getMModeController().getExtension(MapIO.class);
		if (inboxFile.exists()) {
			try {
				final MapModel map = new MMapModel();
				final URL url = Compat.fileToUrl(inboxFile);
				if (url == null || !mapIO.loadCatchExceptions(url, map)) {
					return null;
				}
				map.setURL(url);
				if (project != null) {
					ensureProjectLink(map, project);
				}
				return map;
			}
			catch (MalformedURLException e) {
				LogUtils.warn("QuickCapture: invalid inbox path.", e);
				return null;
			}
		}
		return createNewInboxMap(project, inboxFile, mapIO);
	}

	private static MapModel createNewInboxMap(final AWorkspaceProject project, final File inboxFile,
	        final MMapIO mapIO) {
		try {
			if (!inboxFile.getParentFile().exists() && !inboxFile.getParentFile().mkdirs()) {
				return null;
			}
			final MapModel map = WorkspaceNewMapAction.createNewMap(project, inboxFile.toURI(), "\u6536\u4ef6\u7bb1",
			        true);
			if (map == null) {
				return null;
			}
			mapIO.writeToFile(map, inboxFile);
			map.setURL(Compat.fileToUrl(inboxFile));
			map.setSaved(true);
			return map;
		}
		catch (Exception e) {
			LogUtils.warn("QuickCapture: could not create inbox map.", e);
			return null;
		}
	}

	private static void ensureProjectLink(final MapModel map, final AWorkspaceProject project) {
		if (WorkspaceController.getMapModelExtension(map, false).getProject() == null) {
			WorkspaceController.getMapModelExtension(map).setProject(project);
		}
	}

	private static MapModel findLoadedMap(final File inboxFile) {
		if (inboxFile == null) {
			return null;
		}
		final IMapViewManager mapViewManager = Controller.getCurrentController().getMapViewManager();
		final Map<String, MapModel> maps = mapViewManager.getMaps();
		for (final MapModel map : maps.values()) {
			if (isSameMapFile(map, inboxFile)) {
				return map;
			}
		}
		return null;
	}

	private static boolean isSameMapFile(final MapModel map, final File inboxFile) {
		if (map == null || inboxFile == null) {
			return false;
		}
		if (isSameFile(map.getFile(), inboxFile)) {
			return true;
		}
		final URL url = map.getURL();
		if (url != null) {
			try {
				final File urlFile = Compat.urlToFile(url);
				if (urlFile != null && isSameFile(urlFile, inboxFile)) {
					return true;
				}
			}
			catch (Exception e) {
				// ignore invalid map URL
			}
		}
		return false;
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

	private static NodeModel findOrCreateDateHierarchy(final MapModel map, final MMapController mapController) {
		final Calendar cal = Calendar.getInstance();
		final String year = String.valueOf(cal.get(Calendar.YEAR));
		final String month = String.valueOf(cal.get(Calendar.MONTH) + 1);
		final String day = String.valueOf(cal.get(Calendar.DAY_OF_MONTH));
		final NodeModel root = map.getRootNode();
		final NodeModel yearNode = findOrCreateChild(mapController, root, year);
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

	private static void unfoldAncestors(final MMapController mapController, final NodeModel node) {
		NodeModel current = node.getParentNode();
		while (current != null) {
			if (mapController.isFolded(current)) {
				mapController.setFolded(current, false);
			}
			current = current.getParentNode();
		}
	}

	private static NodeModel findChildByTitle(final NodeModel parent, final String title) {
		if (parent == null || title == null) {
			return null;
		}
		final MapController mapController = Controller.getCurrentModeController().getMapController();
		for (final NodeModel child : mapController.childrenUnfolded(parent)) {
			final String nodeText = HtmlUtils.removeHtmlTagsFromString(child.getText());
			if (title.equals(nodeText != null ? nodeText.trim() : "")) {
				return child;
			}
		}
		return null;
	}

	private static boolean persistInboxMap(final MapModel map, final File inboxFile) {
		try {
			final MFileManager fileManager = (MFileManager) MFileManager.getController();
			if (fileManager.save(map, inboxFile)) {
				return true;
			}
			final MMapIO mapIO = (MMapIO) MModeController.getMModeController().getExtension(MapIO.class);
			mapIO.writeToFile(map, inboxFile);
			map.setURL(Compat.fileToUrl(inboxFile));
			map.setSaved(true);
			return true;
		}
		catch (Exception e) {
			LogUtils.warn("QuickCapture: save inbox failed.", e);
			return false;
		}
	}
}
