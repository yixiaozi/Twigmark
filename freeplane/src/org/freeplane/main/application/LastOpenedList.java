/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2008 Joerg Mueller, Daniel Polansky, Christian Foltin, Dimitry Polivaev
 *
 *  This file is modified by Dimitry Polivaev in 2008.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.freeplane.main.application;

import java.awt.Component;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.Timer;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.IFreeplaneAction;
import org.freeplane.core.ui.MenuBuilder;
import org.freeplane.core.ui.UIBuilder;
import org.freeplane.core.ui.components.JFreeplaneMenuItem;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.Compat;
import org.freeplane.core.util.ConfigurationUtils;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.TextUtils;
import org.freeplane.core.util.WorkingDirectoryMapPaths;
import org.freeplane.features.map.IMapChangeListener;
import org.freeplane.features.map.MapChangeEvent;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.SessionOpenMapsStore;
import org.freeplane.features.map.mindmapmode.DocuMapAttribute;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.ui.IMapViewChangeListener;
import org.freeplane.features.ui.IMapViewManager;
import org.freeplane.features.url.UrlManager;
import org.freeplane.n3.nanoxml.XMLException;
import org.freeplane.view.swing.map.MapView;

/**
 * This class manages a list of the maps that were opened last. It aims to
 * provide persistence for the last recent maps. Maps should be shown in the
 * format:"mode\:key",ie."Mindmap\:/home/joerg/freeplane.mm"
 */

public class LastOpenedList implements IMapViewChangeListener, IMapChangeListener {
	private static final String MENU_CATEGORY = "main_menu_most_recent_files";
	private static final String LAST_OPENED_LIST_LENGTH = "last_opened_list_length";
	private static final String OPENED_NOW = "openedNow_1.0.20";
	private static final String LAST_OPENED = "lastOpened_1.0.20";
	public static final String LOAD_LAST_MAP = "load_last_map";
	public static final String LOAD_LAST_MAPS = "load_last_maps";
	/**
	 * Legacy property — ignored. Startup restores all session maps; extras are
	 * opened one-by-one after the main window is shown (see
	 * {@link #scheduleDeferredStartupOpens}).
	 */
	public static final String LOAD_LAST_MAPS_MAX = "load_last_maps_max";
// // 	private final Controller controller;
	private static boolean PORTABLE_APP = System.getProperty("portableapp", "false").equals("true");
	private static String USER_DRIVE = System.getProperty("user.home", "").substring(0, 2);
	final private List<String> currenlyOpenedList = new LinkedList<String>();
	/** Timer that opens remaining session maps after the frame is visible. */
	private Timer deferredStartupOpenTimer;
	/**
	 * Contains Restore strings.
	 */
	final private List<String> lastOpenedList = new LinkedList<String>();
	/**
	 * Contains Restore string => map name (map.toString()).
	 */
	final private Map<String, String> mRestorableToMapName = new HashMap<String, String>();
	private Timer persistOpenedNowTimer;

	LastOpenedList() {
//		this.controller = controller;
		restoreList(LAST_OPENED, lastOpenedList);
		restoreList(OPENED_NOW, currenlyOpenedList);
	}

	public void afterViewChange(final Component oldView, final Component newView) {
		if (newView == null) {
			updateMenus();
			schedulePersistOpenedNow();
			return;
		}
		final IMapViewManager mapViewManager = Controller.getCurrentController().getMapViewManager();
		final MapModel map = mapViewManager.getModel(newView);
		final String restoreString = getRestoreable(map);
		updateList(map, restoreString);
		schedulePersistOpenedNow();
	}

	public void afterViewClose(final Component oldView) {
		schedulePersistOpenedNow();
	}

	public void afterViewCreated(final Component mapView) {
		schedulePersistOpenedNow();
	}

	public void beforeViewChange(final Component oldView, final Component newView) {
	}

	private int getMaxMenuEntries() {
		return ResourceController.getResourceController().getIntProperty(LAST_OPENED_LIST_LENGTH, 25);
	}

	private String getRestorable(final File file) {
		if (file == null) {
			return null;
		}
		if (!PORTABLE_APP || !USER_DRIVE.endsWith(":")) {
			return WorkingDirectoryMapPaths.toMindMapRestoreable(file);
		}
		String absolutePath = file.getAbsolutePath();
		try {
			final File canonical = file.getCanonicalFile();
			if (canonical.exists()) {
				absolutePath = canonical.getAbsolutePath();
			}
		}
		catch (final IOException e) {
			// keep absolute path
		}
		final String diskName = absolutePath.substring(0, 2);
		if (!diskName.equals(USER_DRIVE)) {
			return WorkingDirectoryMapPaths.toMindMapRestoreable(file);
		}
		return "MindMap::" + absolutePath.substring(2);
	}

	private String getRestoreable(final Component mapView) {
		final IMapViewManager mapViewManager = Controller.getCurrentController().getMapViewManager();
		final MapModel map = mapViewManager.getModel(mapView);
		final String restoreString = getRestoreable(map);
		return restoreString;
	}

	public String getRestoreable( final MapModel map) {
		if (map == null) {
			return null;
		}
		//ignore documentation maps loaded using documentation actions
		if(map.containsExtension(DocuMapAttribute.class))
			return null;
		final File file = map.getFile();
		if (file == null) {
			return null;
		}
		return getRestorable(file);
	}

	public void mapChanged(final MapChangeEvent event) {
		if (!event.getProperty().equals(UrlManager.MAP_URL)) {
			return;
		}
		final URL before = (URL) event.getOldValue();
		if (before != null) {
			//DOCEAR - decode url string
			final String fileBefore = sun.net.www.ParseUtil.decode(before.getFile());
			if (fileBefore != null) {
				final String restorable = getRestorable(new File(fileBefore));
				currenlyOpenedList.remove(restorable);
			}
		}
		final URL after = (URL) event.getNewValue();
		if (after != null) {

			//DOCEAR - decode url string
			final String fileAfter = sun.net.www.ParseUtil.decode(after.getFile());
			if (fileAfter != null) {
				final String restorable = getRestorable(new File(fileAfter));
				currenlyOpenedList.add(restorable);
				updateList(event.getMap(), restorable);
			}
		}
		schedulePersistOpenedNow();
	}

	public void onNodeDeleted(final NodeModel parent, final NodeModel child, final int index) {
	}

	public void onNodeInserted(final NodeModel parent, final NodeModel child, final int newIndex) {
	}

	public void onNodeMoved(final NodeModel oldParent, final int oldIndex, final NodeModel newParent,
	                        final NodeModel child, final int newIndex) {
	}

	public void onPreNodeDelete(final NodeModel oldParent, final NodeModel selectedNode, final int index) {
	}

	public void open(final String restoreable) throws FileNotFoundException, MalformedURLException,
	        IOException, URISyntaxException, XMLException {
		final boolean changedToMapView = tryToChangeToMapView(restoreable);
		if ((restoreable != null) && !(changedToMapView)) {
			final String mode = extractMode(restoreable);
			if (mode != null) {
				Controller.getCurrentController().selectMode(mode);
				final File mapFile = resolveMapFile(restoreable);
				if (mapFile == null || !mapFile.isFile()) {
					throw new FileNotFoundException(restoreable);
				}
				Controller.getCurrentModeController().getMapController().newMap(Compat.fileToUrl(mapFile));
			}
		}
	}

	public void openMapsOnStart() {
		final boolean loadLastMap = ResourceController.getResourceController().getBooleanProperty(LOAD_LAST_MAP);
		final boolean loadLastMaps = ResourceController.getResourceController().getBooleanProperty(LOAD_LAST_MAPS);
		final SessionOpenMapsStore sessionStore = SessionOpenMapsStore.getInstance();
		final List<String> sessionOpenMaps = new LinkedList<String>(sessionStore.getOpenMaps());
		filterNonRestorableMaps(sessionOpenMaps);

		String lastMap = sessionStore.getLastMap();
		if (lastMap == null || !isSessionRestorableMap(decodeRestoreable(lastMap))
				|| !mapFileExists(decodeRestoreable(lastMap))) {
			lastMap = null;
			if (!lastOpenedList.isEmpty()) {
				lastMap = decodeRestoreable(lastOpenedList.get(0));
			}
		}
		else {
			lastMap = decodeRestoreable(lastMap);
		}

		if (loadLastMaps) {
			final List<String> startList = new LinkedList<String>();
			if (!sessionOpenMaps.isEmpty()) {
				startList.addAll(sessionOpenMaps);
				LogUtils.info("Restoring " + startList.size() + " map(s) from session-open-maps.properties");
			}
			else {
				restoreList(OPENED_NOW, startList);
				filterNonRestorableMaps(startList);
				if (!startList.isEmpty()) {
					LogUtils.info("Restoring " + startList.size() + " map(s) from auto.properties openedNow");
				}
			}
			if (startList.isEmpty()) {
				appendMostRecentRestorableMap(startList);
			}
			openSessionMapsWithoutBlockingStartup(startList, lastMap);
			return;
		}
		if (loadLastMap && lastMap != null && isSessionRestorableMap(lastMap)) {
			safeOpen(lastMap, true);
		}
	}

	/**
	 * Open the focused map immediately so the first paint has content; queue the
	 * rest one-by-one on a Swing timer so the main window can show and stay responsive.
	 */
	private void openSessionMapsWithoutBlockingStartup(final List<String> startList, final String lastMap) {
		if (startList == null || startList.isEmpty()) {
			return;
		}
		String focusedEntry = null;
		if (lastMap != null) {
			for (int i = 0; i < startList.size(); i++) {
				final String entry = startList.get(i);
				if (lastMap.equals(decodeRestoreable(entry))) {
					focusedEntry = entry;
					break;
				}
			}
		}
		if (focusedEntry == null) {
			focusedEntry = startList.get(0);
		}
		safeOpen(focusedEntry, true);
		final List<String> deferred = new LinkedList<String>();
		for (int i = 0; i < startList.size(); i++) {
			final String entry = startList.get(i);
			if (!entry.equals(focusedEntry)) {
				deferred.add(entry);
			}
		}
		if (deferred.isEmpty()) {
			if (lastMap != null) {
				tryToChangeToMapView(lastMap);
			}
			return;
		}
		scheduleDeferredStartupOpens(deferred, lastMap);
	}

	/**
	 * Opens each restoreable after a short delay so {@code frame.setVisible} and
	 * paints can run between maps. Must be called from the EDT.
	 */
	private void scheduleDeferredStartupOpens(final List<String> maps, final String lastMapFocus) {
		if (deferredStartupOpenTimer != null) {
			deferredStartupOpenTimer.stop();
			deferredStartupOpenTimer = null;
		}
		final LinkedList<String> queue = new LinkedList<String>(maps);
		LogUtils.info("Deferring restore of " + queue.size()
		        + " map(s) until after the main window is shown");
		deferredStartupOpenTimer = new Timer(40, new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				if (queue.isEmpty()) {
					deferredStartupOpenTimer.stop();
					deferredStartupOpenTimer = null;
					if (lastMapFocus != null) {
						tryToChangeToMapView(lastMapFocus);
					}
					LogUtils.info("Deferred startup map restore finished");
					return;
				}
				final String next = queue.removeFirst();
				safeOpen(next, true);
			}
		});
		// Wait until createFrame finishes setVisible / toFront on the same EDT burst.
		deferredStartupOpenTimer.setInitialDelay(80);
		deferredStartupOpenTimer.setRepeats(true);
		deferredStartupOpenTimer.start();
	}

	public boolean hasRestorableSessionMaps() {
		if (SessionOpenMapsStore.getInstance().hasOpenMaps()) {
			final List<String> sessionOpenMaps = new LinkedList<String>(SessionOpenMapsStore.getInstance().getOpenMaps());
			filterNonRestorableMaps(sessionOpenMaps);
			if (!sessionOpenMaps.isEmpty()) {
				return true;
			}
		}
		final List<String> openedNow = new LinkedList<String>();
		restoreList(OPENED_NOW, openedNow);
		for (int i = 0; i < openedNow.size(); i++) {
			if (isSessionRestorableMap(decodeRestoreable(openedNow.get(i)))) {
				return true;
			}
		}
		for (int i = 0; i < lastOpenedList.size(); i++) {
			if (isSessionRestorableMap(decodeRestoreable(lastOpenedList.get(i)))) {
				return true;
			}
		}
		return false;
	}


	public void remove(final String restoreable) {
		if(!lastOpenedList.remove(restoreable)) {
			try {
				//DOCEAR - decode url string fallback
				lastOpenedList.remove(sun.net.www.ParseUtil.decode(restoreable));
			}
			catch (Exception e) {
				//ignore
			}
		}
		updateMenus();
	}

	private void restoreList(final String key, final List<String> list) {
		final ResourceController resourceController = ResourceController.getResourceController();
		final String scopedKey = WorkingDirectoryMapPaths.propertyKey(key);
		String restored = resourceController.getProperty(scopedKey, null);
		if ((restored == null || restored.equals("")) && !scopedKey.equals(key)) {
			restored = migrateLegacyRecentList(resourceController, key, scopedKey);
		}
		if (restored != null && !restored.equals("")) {
			list.addAll(ConfigurationUtils.decodeListValue(restored, true));
		}
	}

	/**
	 * Copy entries that belong to the current working directory from the legacy
	 * unscoped {@code lastOpened_*} / {@code openedNow_*} keys into the scoped key.
	 */
	private static String migrateLegacyRecentList(final ResourceController resourceController, final String legacyKey,
	        final String scopedKey) {
		final String legacy = resourceController.getProperty(legacyKey, null);
		if (legacy == null || legacy.equals("")) {
			return null;
		}
		final List<String> legacyEntries = ConfigurationUtils.decodeListValue(legacy, true);
		final List<String> migrated = new LinkedList<String>();
		final Set<String> seen = new LinkedHashSet<String>();
		for (int i = 0; i < legacyEntries.size(); i++) {
			final String entry = legacyEntries.get(i);
			final File resolved = WorkingDirectoryMapPaths.resolveStoredFile(decodeRestoreable(entry));
			if (resolved == null || !resolved.isFile()) {
				continue;
			}
			if (!WorkingDirectoryMapPaths.belongsToCurrentWorkingDirectory(entry)
			        && !WorkingDirectoryMapPaths.belongsToCurrentWorkingDirectory(resolved.getAbsolutePath())) {
				continue;
			}
			final String relative = WorkingDirectoryMapPaths.toMindMapRestoreable(resolved);
			if (relative != null && seen.add(relative)) {
				migrated.add(relative);
			}
		}
		if (migrated.isEmpty()) {
			return null;
		}
		final String encoded = ConfigurationUtils.encodeListValue(migrated, true);
		resourceController.setProperty(scopedKey, encoded);
		LogUtils.info("Migrated " + migrated.size() + " recent map(s) into " + scopedKey);
		return encoded;
	}

	void safeOpen(final List<String> maps) {
		for (final String restoreable : maps) {
			safeOpen(restoreable, false);
		}
	}

	void safeOpenOnStart(final List<String> maps) {
		for (final String restoreable : maps) {
			safeOpen(restoreable, true);
		}
	}

	public void safeOpen(final String restoreable) {
		safeOpen(restoreable, false);
	}

	public void safeOpen(final String restoreable, final boolean silentOnMissing) {
		try {
			open(restoreable);
		}
		catch (final Exception ex) {
			LogUtils.warn(ex);
			final File resolved = resolveMapFile(restoreable);
			if (silentOnMissing && (resolved == null || !resolved.isFile())) {
				LogUtils.info("Skipping missing session map: " + restoreable);
				return;
			}
			final String message = TextUtils.format("remove_file_from_list_on_error", restoreable);
			UITools.showFrame();
			final Frame frame = UITools.getFrame();
			final int remove = JOptionPane.showConfirmDialog(frame, message, "Freeplane", JOptionPane.YES_NO_OPTION);
			if (remove == JOptionPane.YES_OPTION) {
				remove(restoreable);
			}
		}
	}

	public void saveProperties() {
		if (persistOpenedNowTimer != null) {
			persistOpenedNowTimer.stop();
			persistOpenedNowTimer = null;
		}
		if (deferredStartupOpenTimer != null) {
			deferredStartupOpenTimer.stop();
			deferredStartupOpenTimer = null;
		}
		// Snapshot open maps while views still exist, then freeze so the subsequent
		// quit-time tab closes cannot rewrite the file to open.count=0.
		if (!SessionOpenMapsStore.getInstance().isFrozen()) {
			syncCurrentlyOpenedFromViews();
			persistOpenedNowNow();
			SessionOpenMapsStore.getInstance().freeze();
		}
		ResourceController.getResourceController().setProperty(WorkingDirectoryMapPaths.propertyKey(LAST_OPENED),
		    ConfigurationUtils.encodeListValue(lastOpenedList, true));
	}

	private void schedulePersistOpenedNow() {
		if (SessionOpenMapsStore.getInstance().isFrozen()) {
			return;
		}
		if (persistOpenedNowTimer == null) {
			persistOpenedNowTimer = new Timer(400, new java.awt.event.ActionListener() {
				public void actionPerformed(final java.awt.event.ActionEvent e) {
					((Timer) e.getSource()).stop();
					if (SessionOpenMapsStore.getInstance().isFrozen()) {
						return;
					}
					syncCurrentlyOpenedFromViews();
					persistOpenedNowNow();
				}
			});
			persistOpenedNowTimer.setRepeats(false);
		}
		persistOpenedNowTimer.restart();
	}

	private void persistOpenedNowNow() {
		if (SessionOpenMapsStore.getInstance().isFrozen()) {
			return;
		}
		final String encoded = ConfigurationUtils.encodeListValue(currenlyOpenedList, true);
		ResourceController.getResourceController().setProperty(WorkingDirectoryMapPaths.propertyKey(OPENED_NOW), encoded);

		String lastRestoreable = null;
		try {
			final Controller controller = Controller.getCurrentController();
			if (controller != null) {
				final MapModel map = controller.getMap();
				lastRestoreable = getRestoreable(map);
			}
		}
		catch (Exception e) {
		}
		if (lastRestoreable == null && !currenlyOpenedList.isEmpty()) {
			lastRestoreable = currenlyOpenedList.get(0);
		}
		SessionOpenMapsStore.getInstance().saveOpenMaps(currenlyOpenedList, lastRestoreable);

		if (currenlyOpenedList.isEmpty()) {
			LogUtils.info("Session restore list is empty (session-open-maps.properties)");
		}
		else {
			LogUtils.info("Session restore list: " + currenlyOpenedList.size()
					+ " map(s) written to session-open-maps.properties");
		}
	}

	private void syncCurrentlyOpenedFromViews() {
		try {
			final Controller controller = Controller.getCurrentController();
			if (controller == null) {
				return;
			}
			final IMapViewManager mapViewManager = controller.getMapViewManager();
			if (mapViewManager == null) {
				return;
			}
			final List<String> openRestoreables = new LinkedList<String>();
			final Set<String> seen = new LinkedHashSet<String>();
			final MapViewTabs bottomTabs = MapViewTabs.getInstance();
			if (bottomTabs != null) {
				final List tabOrder = bottomTabs.getMindMapViewsInTabOrder();
				for (int i = 0; i < tabOrder.size(); i++) {
					addRestoreable(openRestoreables, seen, getRestoreable((MapView) tabOrder.get(i)));
				}
			}
			final List<? extends Component> views = mapViewManager.getMapViewVector();
			if (views != null) {
				for (int i = 0; i < views.size(); i++) {
					addRestoreable(openRestoreables, seen, getRestoreable(views.get(i)));
				}
			}
			currenlyOpenedList.clear();
			currenlyOpenedList.addAll(openRestoreables);
		}
		catch (Exception e) {
			LogUtils.warn("Could not sync open maps before save: " + e.getMessage(), e);
		}
	}

	private static void addRestoreable(final List<String> openRestoreables, final Set<String> seen,
	        final String restoreable) {
		if (restoreable != null && isSessionRestorableMap(restoreable) && !seen.contains(restoreable)) {
			openRestoreables.add(restoreable);
			seen.add(restoreable);
		}
	}

	private void appendMostRecentRestorableMap(final List<String> startList) {
		for (int i = 0; i < lastOpenedList.size(); i++) {
			final String restoreable = decodeRestoreable(lastOpenedList.get(i));
			if (isSessionRestorableMap(restoreable) && mapFileExists(restoreable)) {
				startList.add(lastOpenedList.get(i));
				return;
			}
		}
	}

	private static void filterNonRestorableMaps(final List<String> maps) {
		for (int i = maps.size() - 1; i >= 0; i--) {
			final String restoreable = decodeRestoreable(maps.get(i));
			if (!isSessionRestorableMap(restoreable) || !mapFileExists(restoreable)) {
				maps.remove(i);
			}
		}
	}

	private static boolean mapFileExists(final String restoreable) {
		final File file = resolveMapFileStatic(restoreable);
		return file != null && file.isFile();
	}

	private File resolveMapFile(final String restoreable) {
		return resolveMapFileStatic(decodeRestoreable(restoreable));
	}

	private static File resolveMapFileStatic(final String restoreable) {
		final String path = extractFilePath(restoreable);
		if (path == null || path.length() == 0) {
			return null;
		}
		final File resolved = WorkingDirectoryMapPaths.resolveStoredFile(path);
		if (resolved != null && resolved.isFile()) {
			return resolved;
		}
		File file = new File(path);
		if (file.isFile()) {
			return file;
		}
		try {
			final File canonical = file.getCanonicalFile();
			if (canonical.isFile()) {
				return canonical;
			}
		}
		catch (final IOException e) {
			// ignore
		}
		return findByFileName(new File(path).getName());
	}

	private static File findByFileName(final String fileName) {
		if (fileName == null || fileName.length() == 0) {
			return null;
		}
		final ResourceController resourceController = ResourceController.getResourceController();
		final String lastOpened = resourceController.getProperty(WorkingDirectoryMapPaths.propertyKey(LAST_OPENED), null);
		if (lastOpened == null || lastOpened.length() == 0) {
			return null;
		}
		final List<String> candidates = ConfigurationUtils.decodeListValue(lastOpened, true);
		for (int i = 0; i < candidates.size(); i++) {
			final String candidatePath = extractFilePath(decodeRestoreable(candidates.get(i)));
			if (candidatePath == null) {
				continue;
			}
			final File candidate = new File(candidatePath);
			if (fileName.equalsIgnoreCase(candidate.getName()) && candidate.isFile()) {
				return candidate;
			}
		}
		return null;
	}

	private static String extractMode(final String restoreable) {
		final String decoded = decodeRestoreable(restoreable);
		if (decoded == null) {
			return null;
		}
		final int separator = decoded.indexOf(':');
		if (separator <= 0) {
			return null;
		}
		return decoded.substring(0, separator);
	}

	private static String extractFilePath(final String restoreable) {
		final String decoded = decodeRestoreable(restoreable);
		if (decoded == null) {
			return null;
		}
		final int separator = decoded.indexOf(':');
		if (separator < 0 || separator >= decoded.length() - 1) {
			return null;
		}
		String path = decoded.substring(separator + 1);
		if (PORTABLE_APP && path.startsWith(":") && USER_DRIVE.endsWith(":")) {
			path = USER_DRIVE + path.substring(1);
		}
		return path;
	}

	private static boolean isSessionRestorableMap(final String restoreable) {
		if (restoreable == null || restoreable.length() == 0) {
			return false;
		}
		final String lower = restoreable.toLowerCase();
		if (lower.indexOf("docear-welcome.mm") >= 0) {
			return false;
		}
		if (lower.indexOf("freeplaneapplications.mm") >= 0) {
			return false;
		}
		if (lower.indexOf("doceardist") >= 0 && lower.indexOf("\\doc\\") >= 0) {
			return false;
		}
		return true;
	}

	private static String decodeRestoreable(final String restoreable) {
		if (restoreable == null) {
			return null;
		}
		try {
			return sun.net.www.ParseUtil.decode(restoreable);
		}
		catch (Exception e) {
			return restoreable;
		}
	}

	private boolean tryToChangeToMapView(final String restoreable) {
		return Controller.getCurrentController().getMapViewManager().tryToChangeToMapView(mRestorableToMapName.get(restoreable));
	}

	private void updateList(final MapModel map, final String restoreString) {
		//ignore documentation maps loaded using documentation actions
		if(map.containsExtension(DocuMapAttribute.class))
			return;
		if (restoreString != null && !isSessionRestorableMap(restoreString)) {
			return;
		}
		if (restoreString != null) {
			String str = restoreString;
			if (lastOpenedList.contains(str)) {
				lastOpenedList.remove(str);
			}
			lastOpenedList.add(0, str);
			mRestorableToMapName.put(str, map.getTitle());
		}
		updateMenus();
	}

	private void updateMenus() {
		Controller controller = Controller.getCurrentController();
		final ModeController modeController = controller.getModeController();
		final MenuBuilder menuBuilder = modeController.getUserInputListenerFactory().getMenuBuilder();
		menuBuilder.removeChildElements(MENU_CATEGORY);
		int i = 0;
		int maxEntries = getMaxMenuEntries();
		for (final String key : lastOpenedList) {
			if (i == 0
			        && (!modeController.getModeName().equals(MModeController.MODENAME) || controller.getMap() == null || controller
			            .getMap().getURL() == null)) {
				i++;
				maxEntries++;
			}
			if (i == maxEntries) {
				break;
			}
			final AFreeplaneAction lastOpenedActionListener = new OpenLastOpenedAction(i++, this);
			final IFreeplaneAction decoratedAction = menuBuilder.decorateAction(lastOpenedActionListener);
			final JMenuItem item = new JFreeplaneMenuItem(decoratedAction);
			String text = createOpenMapItemName(key);
			item.setText(createOpenMapItemName(text));
			item.setMnemonic(0);
			menuBuilder.addMenuItem(MENU_CATEGORY, item, MENU_CATEGORY + '/' + lastOpenedActionListener.getKey(),
			    UIBuilder.AS_CHILD);
		}
	}

	private String createOpenMapItemName(final String restorable) {
		final int separatorIndex = restorable.indexOf(':');
		if(separatorIndex == -1)
			return restorable;
		String key = restorable.substring(0, separatorIndex);
		return TextUtils.getText("open_as" + key, key) + restorable.substring(separatorIndex);
		
    }

	public void onPreNodeMoved(final NodeModel oldParent, final int oldIndex, final NodeModel newParent,
	                           final NodeModel child, final int newIndex) {
	}
}
