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

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

import javax.swing.JOptionPane;
import javax.swing.UIManager;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.ShowSelectionAsRectangleAction;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.Compat;
import org.freeplane.core.util.ConfigurationUtils;
import org.freeplane.core.util.FileUtils;
import org.freeplane.core.util.FreeplaneVersion;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MenuUtils;
import org.freeplane.features.attribute.ModelessAttributeController;
import org.freeplane.features.filter.FilterController;
import org.freeplane.features.filter.NextNodeAction;
import org.freeplane.features.filter.NextPresentationItemAction;
import org.freeplane.features.format.FormatController;
import org.freeplane.features.format.ScannerController;
import org.freeplane.features.help.HelpController;
import org.freeplane.features.icon.IconController;
import org.freeplane.features.link.LinkController;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapController.Direction;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.mode.QuitAction;
import org.freeplane.features.mode.browsemode.BModeController;
import org.freeplane.features.mode.filemode.FModeController;
import org.freeplane.features.mode.mindmapmode.LoadAcceleratorPresetsAction;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.print.PrintController;
import org.freeplane.features.styles.LogicalStyleFilterController;
import org.freeplane.features.styles.MapViewLayout;
import org.freeplane.features.text.TextController;
import org.freeplane.features.time.TimeController;
import org.freeplane.features.ui.FrameController;
import org.freeplane.features.url.mindmapmode.MFileManager;
import org.freeplane.main.addons.AddOnsController;
import org.freeplane.main.application.CommandLineParser.Options;
import org.freeplane.main.browsemode.BModeControllerFactory;
import org.freeplane.main.filemode.FModeControllerFactory;
import org.freeplane.main.mindmapmode.MModeControllerFactory;
import org.freeplane.view.swing.features.nodehistory.NodeHistory;
import org.freeplane.view.swing.map.ViewLayoutTypeAction;
import org.freeplane.features.map.IMapLifeCycleListener;
import org.freeplane.features.map.IMapSelectionListener;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.usagestats.UsageStatsManager;
import org.freeplane.view.swing.map.mindmapmode.MMapViewController;
import org.pushingpixels.flamingo.api.ribbon.JRibbonFrame;

public class FreeplaneGUIStarter implements FreeplaneStarter {
	public static String getResourceBaseDir() {
		return System.getProperty(FreeplaneStarter.ORG_FREEPLANE_GLOBALRESOURCEDIR,
		    FreeplaneStarter.DEFAULT_ORG_FREEPLANE_GLOBALRESOURCEDIR);
	}

	public static void showSysInfo() {
		final StringBuilder info = new StringBuilder();
		info.append("freeplane_version = ");
		info.append(FreeplaneVersion.getVersion());
		String revision = FreeplaneVersion.getVersion().getRevision();

		info.append("; freeplane_xml_version = ");
		info.append(FreeplaneVersion.XML_VERSION);
		if(! revision.equals("")){
			info.append("\ngit revision = ");
			info.append(revision);
		}
		info.append("\njava_version = ");
		info.append(System.getProperty("java.version"));
		info.append("; os_name = ");
		info.append(System.getProperty("os.name"));
		info.append("; os_version = ");
		info.append(System.getProperty("os.version"));
		LogUtils.info(info.toString());
	}

	private ApplicationResourceController applicationResourceController;
// // 	private Controller controller;
	private FreeplaneSplashModern splash = null;
    private boolean startupFinished = false;
	private ApplicationViewController viewController;
	/** allows to disable loadLastMap(s) if there already is a second instance running. */
	private boolean dontLoadLastMaps;
	final private boolean firstRun;
	public FreeplaneGUIStarter() {
		super();
		final File userPreferencesFile = ApplicationResourceController.getUserPreferencesFile();
		firstRun = !userPreferencesFile.exists();
		new UserPropertiesUpdater().importOldProperties();
		applicationResourceController = new ApplicationResourceController();
	}

	public void setDontLoadLastMaps() {
		dontLoadLastMaps = true;
    }

	public Controller createController() {
		try {
			Controller controller = new Controller(applicationResourceController);
			Controller.setCurrentController(controller);
			Compat.macAppChanges();
			controller.addAction(new QuitAction());
			applicationResourceController.init();
			LogUtils.createLogger();
			FreeplaneGUIStarter.showSysInfo();
			org.freeplane.core.ui.theme.DocearUiTheme.registerLookAndFeels();
			registerSubstanceLookAndFeels();
			final String lookandfeel = System.getProperty("lookandfeel", applicationResourceController
			    .getProperty("lookandfeel"));
			FrameController.setLookAndFeel(lookandfeel);
			org.freeplane.core.ui.theme.DocearUiTheme.applyAfterLookAndFeel();
			UIManager.put("RibbonUI", "org.freeplane.core.ui.ribbon.ZeroTaskbarRibbonUI");
			final JRibbonFrame frame = new JRibbonFrame("Freeplane");
			frame.setName(UITools.MAIN_FREEPLANE_FRAME);
			splash = new FreeplaneSplashModern(frame);
			//splash.setVisible(false);
			final MMapViewController mapViewController = new MMapViewController(controller);
			viewController = new ApplicationViewController(controller, mapViewController, frame);
			System.setSecurityManager(new FreeplaneSecurityManager());
			mapViewController.addMapViewChangeListener(applicationResourceController.getLastOpenedList());
			FilterController.install();
			PrintController.install();
			FormatController.install(new FormatController());
	        final ScannerController scannerController = new ScannerController();
	        ScannerController.install(scannerController);
	        scannerController.addParsersForStandardFormats();
			ModelessAttributeController.install();
			TextController.install();
			TimeController.install();
			LinkController.install();
			IconController.install();
			HelpController.install();
			controller.addAction(new UpdateCheckAction());
			controller.addAction(new NextNodeAction(Direction.FORWARD));
			controller.addAction(new NextNodeAction(Direction.BACK));
			controller.addAction(new NextNodeAction(Direction.FORWARD_N_FOLD));
			controller.addAction(new NextNodeAction(Direction.BACK_N_FOLD));
			controller.addAction(new NextPresentationItemAction());
			controller.addAction(new ShowSelectionAsRectangleAction());
			controller.addAction(new ViewLayoutTypeAction(MapViewLayout.OUTLINE));
			FilterController.getCurrentFilterController().getConditionFactory().addConditionController(7,
			    new LogicalStyleFilterController());
			MapController.install();

			NodeHistory.install(controller);
			return controller;
		}
		catch (final Exception e) {
			LogUtils.severe(e);
			throw new RuntimeException(e);
		}
	}

	public void createModeControllers(final Controller controller) {
		MModeControllerFactory.createModeController();
		final ModeController mindMapModeController = controller.getModeController(MModeController.MODENAME);
		mindMapModeController.getMapController().addMapChangeListener(applicationResourceController.getLastOpenedList());
		mindMapModeController.addMenuContributor(FilterController.getController(controller).getMenuContributor());
		BModeControllerFactory.createModeController();
		FModeControllerFactory.createModeController();
    }

	//RIBBONS build menu from these files --> starting point
	public void buildMenus(final Controller controller, final Set<String> plugins) {
	    buildMenus(controller, plugins, MModeController.MODENAME, "/xml/mindmapmodemenu.xml");
	    LoadAcceleratorPresetsAction.install();
	    buildMenus(controller, plugins, BModeController.MODENAME, "/xml/browsemodemenu.xml");
	    buildMenus(controller, plugins, FModeController.MODENAME, "/xml/filemodemenu.xml");
    }

	private void buildMenus(final Controller controller, final Set<String> plugins, String mode, String xml) {
		ModeController modeController = controller.getModeController(mode);
		controller.selectModeForBuild(modeController);
		modeController.updateMenus(xml, plugins);
		controller.selectModeForBuild(null);
	}

	public void createFrame(final String[] args) {
		final Controller controller = Controller.getCurrentController();
		final ModeController modeController = controller.getModeController(MModeController.MODENAME);
		controller.selectModeForBuild(modeController);
		Compat.macMenuChanges();
		new UserPropertiesUpdater().importOldDefaultStyle();
		EventQueue.invokeLater(new Runnable() {
			public void run() {
			    final Options options = CommandLineParser.parse(args);
				loadMaps(options.getFilesToOpenAsArray());
				viewController.init(Controller.getCurrentController());
				if (splash != null) {
					splash.toBack();
				}
				final Frame frame = viewController.getFrame();
				final int extendedState = frame.getExtendedState();
				frame.setVisible(true);
				if (extendedState != frame.getExtendedState()) {
					frame.setExtendedState(extendedState);
				}
				if (splash != null) {
					splash.dispose();
					splash = null;
				}
				frame.toFront();
				startupFinished = true;
		        System.setProperty("nonInteractive", Boolean.toString(options.isNonInteractive()));
		        
		        // Initialize and start UsageStatsManager
		        final UsageStatsManager statsManager = UsageStatsManager.getInstance();
		        statsManager.start();
		        
		        // Add window event listeners
		        frame.addWindowListener(new WindowAdapter() {
		            @Override
		            public void windowActivated(WindowEvent e) {
		                statsManager.onWindowActivated();
		            }
		            
		            @Override
		            public void windowDeactivated(WindowEvent e) {
		                // Opening in-app dialogs also deactivates the frame — that must NOT
		                // end the usage session (it was a major under-count of activity time).
		                final java.awt.Window opposite = e.getOppositeWindow();
		                if (opposite != null && isSameAppWindow(frame, opposite)) {
		                    return;
		                }
		                statsManager.onWindowDeactivated();
		            }
		            
		            @Override
		            public void windowClosing(WindowEvent e) {
		                statsManager.stop();
		            }
		        });
		        
		        // Add map lifecycle listeners
        modeController.getMapController().addMapLifeCycleListener(new IMapLifeCycleListener() {
            @Override
            public void onCreate(MapModel map) {
                // Handle map creation
            }
            
            @Override
            public void onRemove(MapModel map) {
                String file = map.getFile() != null ? map.getFile().getAbsolutePath() : "";
                statsManager.onMapClosed(file);
            }
            
            @Override
            public void onSavedAs(MapModel map) {
                // Handle map saved as
            }
            
            @Override
            public void onSaved(MapModel map) {
                // Handle map saved
            }
        });
        
        // Add map selection listener for map change events
        Controller.getCurrentController().getMapViewManager().addMapSelectionListener(new IMapSelectionListener() {
            @Override
            public void afterMapChange(MapModel oldMap, MapModel newMap) {
                if (oldMap != null) {
                    String oldFile = oldMap.getFile() != null ? oldMap.getFile().getAbsolutePath() : "";
                    statsManager.onMapClosed(oldFile);
                }
                if (newMap != null) {
                    String newFile = newMap.getFile() != null ? newMap.getFile().getAbsolutePath() : "";
                    statsManager.onMapOpened(newFile);
                }
            }
            
            @Override
            public void beforeMapChange(MapModel oldMap, MapModel newMap) {
                // Handle before map change
            }
        });
		        
		        // If a map is already open, notify UsageStatsManager
		        if (controller.getMap() != null) {
		            String mapPath = controller.getMap().getFile() != null ? 
		                controller.getMap().getFile().getAbsolutePath() : "";
		            statsManager.onMapOpened(mapPath);
		        }
		        
		        try {
                    Thread.sleep(1000);
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
                MenuUtils.executeMenuItems(options.getMenuItemsToExecute());
            }
		});
	}

	private void loadMaps( final String[] args) {
		final Controller controller = Controller.getCurrentController();
		final boolean alwaysLoadLastMaps = ResourceController.getResourceController().getBooleanProperty(
		    "always_load_last_maps");
		if (alwaysLoadLastMaps && !dontLoadLastMaps) {
			applicationResourceController.getLastOpenedList().openMapsOnStart();
		}
		if (loadMaps(controller, args)) {
			return;
		}
		if (!alwaysLoadLastMaps && !dontLoadLastMaps) {
			final AddOnsController addonsController = AddOnsController.getController();
			addonsController.setAutoInstallEnabled(false);
			applicationResourceController.getLastOpenedList().openMapsOnStart();
			addonsController.setAutoInstallEnabled(true);
		}
		// Open welcome map only on first run, or when there is no restorable session.
		if (!dontLoadLastMaps && null == controller.getMap()) {
			final boolean hasRestorableSession = applicationResourceController.getLastOpenedList()
			    .hasRestorableSessionMaps();
			if (firstRun || !hasRestorableSession) {
				final File baseDir = new File(FreeplaneGUIStarter.getResourceBaseDir()).getAbsoluteFile().getParentFile();
				final String map = ResourceController.getResourceController().getProperty("first_start_map");
				final File absolutFile = ConfigurationUtils.getLocalizedFile(new File[]{baseDir}, map, Locale.getDefault().getLanguage());
				if (absolutFile != null) {
					loadMaps(controller, new String[]{absolutFile.getAbsolutePath()});
				}
			}
		}
//		if (null != controller.getMap()) {
//			return;
//		}
//		controller.selectMode(MModeController.MODENAME);
//		final MModeController modeController = (MModeController) controller.getModeController();

		//MFileManager.getController(modeController).newMapFromDefaultTemplate();

	}

	public void loadMapsLater(final String[] args){
	    EventQueue.invokeLater(new Runnable() {

            public void run() {
                if(startupFinished && EventQueue.isDispatchThread()){
                    loadMaps(Controller.getCurrentController(), args);
                    toFront();
                    return;
                }
                EventQueue.invokeLater(this);
            }
        });
	}

    private void toFront() {
        final Frame frame = UITools.getFrame();
        if(frame == null)
            return;
        final int state = frame.getExtendedState();
        if ((state & Frame.ICONIFIED) != 0)
            frame.setExtendedState(state & ~Frame.ICONIFIED);
        if (!frame.isVisible())
            frame.setVisible(true);
        frame.toFront();
        frame.requestFocus();
    }

    private boolean loadMaps(final Controller controller, final String[] args) {
        boolean fileLoaded = false;
		for (int i = 0; i < args.length; i++) {
			String fileArgument = args[i];
			if (fileArgument.toLowerCase().endsWith(
			    org.freeplane.features.url.UrlManager.FREEPLANE_FILE_EXTENSION)) {
				try {
					final URL url;
					if(fileArgument.startsWith("http://")){
						url = new URL(fileArgument);
					}
					else{
						if (!FileUtils.isAbsolutePath(fileArgument)) {
							fileArgument = System.getProperty("user.dir") + System.getProperty("file.separator") + fileArgument;
						}
						url = Compat.fileToUrl(new File(fileArgument));
					}
					if (!fileLoaded) {
						controller.selectMode(MModeController.MODENAME);
					}
					final MModeController modeController = (MModeController) controller.getModeController();
					modeController.getMapController().newMap(url);
					fileLoaded = true;
				}
				catch (final Exception ex) {
					System.err.println("File " + fileArgument + " not loaded");
				}
			}
		}
        return fileLoaded;
    }

	/**
	 */
	public void run(final String[] args) {
		try {
			if (null == System.getProperty("org.freeplane.core.dir.lib", null)) {
				System.setProperty("org.freeplane.core.dir.lib", "/lib/");
			}
			final Controller controller = createController();
			createModeControllers(controller);
			FilterController.getController(controller).loadDefaultConditions();
			final Set<String> emptySet = Collections.emptySet();
			buildMenus(controller, emptySet);
			createFrame(args);
		}
		catch (final Exception e) {
			LogUtils.severe(e);
			JOptionPane.showMessageDialog(UITools.getFrame(), "freeplane.main.Freeplane can't be started",
			    "Startup problem", JOptionPane.ERROR_MESSAGE);
			System.exit(1);
		}
	}

	public void stop() {
		try {
			if (EventQueue.isDispatchThread()) {
				Controller.getCurrentController().shutdown();
				return;
			}
			EventQueue.invokeAndWait(new Runnable() {
				public void run() {
					Controller.getCurrentController().shutdown();
				}
			});
		}
		catch (final InterruptedException e) {
			LogUtils.severe(e);
		}
		catch (final InvocationTargetException e) {
			LogUtils.severe(e);
		}
	}

	public ResourceController getResourceController() {
	    return applicationResourceController;
	}
	
	private static void registerSubstanceLookAndFeels() {
		try {
			Class.forName("org.pushingpixels.substance.api.SubstanceLookAndFeel");
			
			UIManager.installLookAndFeel("Business Black Steel", "org.pushingpixels.substance.api.skin.BusinessBlackSteelSkin");
			UIManager.installLookAndFeel("Business Blue Steel", "org.pushingpixels.substance.api.skin.BusinessBlueSteelSkin");
			UIManager.installLookAndFeel("Business", "org.pushingpixels.substance.api.skin.BusinessSkin");
			UIManager.installLookAndFeel("Creme Coffee", "org.pushingpixels.substance.api.skin.CremeCoffeeSkin");
			UIManager.installLookAndFeel("Creme", "org.pushingpixels.substance.api.skin.CremeSkin");
			UIManager.installLookAndFeel("Dust Coffee", "org.pushingpixels.substance.api.skin.DustCoffeeSkin");
			UIManager.installLookAndFeel("Dust", "org.pushingpixels.substance.api.skin.DustSkin");
			UIManager.installLookAndFeel("Graphite Aqua", "org.pushingpixels.substance.api.skin.GraphiteAquaSkin");
			UIManager.installLookAndFeel("Graphite Glass", "org.pushingpixels.substance.api.skin.GraphiteGlassSkin");
			UIManager.installLookAndFeel("Graphite", "org.pushingpixels.substance.api.skin.GraphiteSkin");
			UIManager.installLookAndFeel("Mist Aqua", "org.pushingpixels.substance.api.skin.MistAquaSkin");
			UIManager.installLookAndFeel("Mist Silver", "org.pushingpixels.substance.api.skin.MistSilverSkin");
			UIManager.installLookAndFeel("Nebula Brick Wall", "org.pushingpixels.substance.api.skin.NebulaBrickWallSkin");
			UIManager.installLookAndFeel("Nebula", "org.pushingpixels.substance.api.skin.NebulaSkin");
			UIManager.installLookAndFeel("Obsidian", "org.pushingpixels.substance.api.skin.ObsidianSkin");
			UIManager.installLookAndFeel("Sahara", "org.pushingpixels.substance.api.skin.SaharaSkin");
		}
		catch (Exception e) {
		}
	}

	/** True when focus moved between the main frame and one of its owned dialogs/windows. */
	private static boolean isSameAppWindow(final java.awt.Window frame, final java.awt.Window other) {
		if (frame == null || other == null) {
			return false;
		}
		return isInOwnerChain(other, frame) || isInOwnerChain(frame, other) || frame == other;
	}

	private static boolean isInOwnerChain(final java.awt.Window start, final java.awt.Window target) {
		java.awt.Window current = start;
		while (current != null) {
			if (current == target) {
				return true;
			}
			current = current.getOwner();
		}
		return false;
	}
}
