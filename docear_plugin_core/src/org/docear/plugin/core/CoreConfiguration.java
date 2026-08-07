package org.docear.plugin.core;

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Properties;

import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import org.apache.commons.io.FileUtils;
import org.docear.plugin.core.actions.DocearAboutAction;
import org.docear.plugin.core.actions.DocearOpenUrlAction;
import org.docear.plugin.core.actions.DocearQuitAction;
import org.docear.plugin.core.actions.DocearRemoveNodeLinkAction;
import org.docear.plugin.core.actions.DocearRemoveRepositoryPathRibbonAction;
import org.docear.plugin.core.actions.DocearSetNodePrivacyAction;
import org.docear.plugin.core.actions.DocearShowDataPrivacyStatementAction;
import org.docear.plugin.core.actions.DocearShowDataProcessingTermsAction;
import org.docear.plugin.core.actions.DocearShowTermsOfUseAction;
import org.docear.plugin.core.actions.GPLPanelAction;
import org.docear.plugin.core.actions.LicencesPanelAction;
import org.docear.plugin.core.actions.OpenLogsFolderAction;
import org.docear.plugin.core.actions.SaveAction;
import org.docear.plugin.core.actions.SaveAsAction;
import org.docear.plugin.core.features.DocearLifeCycleObserver;
import org.docear.plugin.core.features.DocearMapModelController;
import org.docear.plugin.core.features.DocearMapModelExtension;
import org.docear.plugin.core.features.DocearMapWriter;
import org.docear.plugin.core.features.DocearMaximizeMapHandler;
import org.docear.plugin.core.features.DocearNodeModifiedExtensionController;
import org.docear.plugin.core.features.DocearNodePrivacyExtensionController;
import org.docear.plugin.core.listeners.DocearCoreOmniListenerAdapter;
import org.docear.plugin.core.listeners.MapLifeCycleAndViewListener;
import org.docear.plugin.core.listeners.PropertyListener;
import org.docear.plugin.core.listeners.PropertyLoadListener;
import org.docear.plugin.core.listeners.WorkspaceOpenDocumentListener;
import org.docear.plugin.core.quickcapture.QuickCaptureService;
import org.docear.plugin.core.calendar.CalendarViewportIntegration;
import org.docear.plugin.core.mapswitcher.MapSwitcherService;
import org.docear.plugin.core.quickcommand.QuickCommandService;
import org.docear.plugin.core.graph.RelationshipGraphIntegration;
import org.docear.plugin.core.todoist.TodoistIntegrationService;
import org.docear.plugin.core.logger.DocearLogEvent;
import org.docear.plugin.core.ui.OverlayViewport;
import org.docear.plugin.core.ui.MapActivityOverlay;
import org.docear.plugin.core.ui.MapTagFilterOverlay;
import org.docear.plugin.core.ui.ribbons.DocearNodePrivacyContributorFactory;
import org.docear.plugin.core.workspace.WorkingDirectoryDefaults;
import org.docear.plugin.core.workspace.actions.DocearAddRepositoryPathAction;
import org.docear.plugin.core.workspace.actions.DocearImportProjectAction;
import org.docear.plugin.core.workspace.actions.DocearLibraryNewMindmap;
import org.docear.plugin.core.workspace.actions.DocearLibraryOpenLocation;
import org.docear.plugin.core.workspace.actions.DocearNewProjectAction;
import org.docear.plugin.core.workspace.actions.DocearRemoveRepositoryPathAction;
import org.docear.plugin.core.workspace.actions.DocearRenameAction;
import org.docear.plugin.core.workspace.controller.DocearProjectLoader;
import org.docear.plugin.core.workspace.model.DocearWorspaceProjectCreator;
import org.docear.plugin.core.workspace.node.FolderTypeLibraryNode;
import org.docear.plugin.core.workspace.node.LiteratureRepositoryPathNode;
import org.freeplane.core.resources.IFreeplanePropertyListener;
import org.freeplane.core.resources.OptionPanelController;
import org.freeplane.core.resources.ResourceBundles;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.resources.components.IPropertyControl;
import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.IMenuContributor;
import org.freeplane.core.ui.MenuBuilder;
import org.freeplane.core.ui.components.JResizer.Direction;
import org.freeplane.core.ui.components.OneTouchCollapseResizer;
import org.freeplane.core.ui.components.OneTouchCollapseResizer.CollapseDirection;
import org.freeplane.core.util.Compat;
import org.freeplane.core.util.ConfigurationUtils;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.help.OnlineDocumentationAction;
import org.freeplane.features.map.IMapSelection;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mapio.MapIO;
import org.freeplane.features.mapio.mindmapmode.MMapIO;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.IControllerExecuteExtension;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.ui.ToggleToolbarAction;
import org.freeplane.features.url.MapConversionException;
import org.freeplane.features.url.MapVersionInterpreter;
import org.freeplane.features.url.UrlManager;
import org.freeplane.plugin.workspace.URIUtils;
import org.freeplane.plugin.workspace.WorkspaceController;
import org.freeplane.plugin.workspace.actions.AWorkspaceAction;
import org.freeplane.plugin.workspace.components.IWorkspaceView;
import org.freeplane.plugin.workspace.event.WorkspaceActionEvent;
import org.freeplane.plugin.workspace.features.AWorkspaceModeExtension;
import org.freeplane.plugin.workspace.mindmapmode.FileFolderDropHandler;
import org.freeplane.plugin.workspace.mindmapmode.VirtualFolderDropHandler;
import org.freeplane.plugin.workspace.model.AWorkspaceTreeNode;
import org.freeplane.plugin.workspace.model.project.AWorkspaceProject;
import org.freeplane.plugin.workspace.nodes.DefaultFileNode;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinDef;


public class CoreConfiguration extends ALanguageController {
	private static boolean enableJnaUsage = true;

	private static final String DOCEAR = "Twigmark";
	private static final String APPLICATION_NAME = "ApplicationName";

	/** Product display name is Twigmark; accept legacy "Docear" profiles. */
	private static boolean isTwigmarkProduct(String applicationName) {
		return DOCEAR.equals(applicationName) || "Docear".equals(applicationName);
	}
	private static final String DOCUMENTATION_ACTION = "DocumentationAction";
	private static final String WEB_DOCU_LOCATION = "webDocuLocation";
	private static final String REQUEST_FEATURE_ACTION = "RequestFeatureAction";
	private static final String DOCEAR_FEATURE_TRACKER_LOCATION = "docear_featureTrackerLocation";
	private static final String FEATURE_TRACKER_LOCATION = "featureTrackerLocation";
	private static final String ASK_FOR_HELP = "AskForHelp";
	private static final String HELP_FORUM_LOCATION = "helpForumLocation";
	private static final String REPORT_BUG_ACTION = "ReportBugAction";
	private static final String DOCEAR_BUG_TRACKER_LOCATION = "docear_bugTrackerLocation";
	private static final String BUG_TRACKER_LOCATION = "bugTrackerLocation";
	private static final String OPEN_FREEPLANE_SITE_ACTION = "OpenFreeplaneSiteAction";
	private static final String WEB_DOCEAR_LOCATION = "webDocearLocation";
	private static final String WEB_FREEPLANE_LOCATION = "webFreeplaneLocation";
	

	public static final String DOCUMENT_REPOSITORY_PATH = "@@literature_repository@@";
	public static final String LIBRARY_PATH = "@@library_mindmaps@@"; 
	private IControllerExecuteExtension docearExecutor;
			
	public CoreConfiguration() {			
		LogUtils.info("org.docear.plugin.core.CoreConfiguration() initializing...");
	}
	
	protected void initController(Controller controller) {
		initIcons(controller);
		Controller.getCurrentController().addExtension(IControllerExecuteExtension.class, getDocearCommandExecutor());
		loadAndStoreVersion(controller);
		adjustProperties(controller);
		DocearController.getController().markApplicationInitialized();

		// Register before workspace sync: seeds empty working dirs chosen on first launch.
		MindMapDataRootResolver.setEmptyDirectorySeeder(new WorkingDirectoryDefaults());
		MindMapDataRootResolver.seedDefaultsIfPending();
		
		AWorkspaceProject.setCurrentProjectCreator(new DocearWorspaceProjectCreator());
		if(isTwigmarkProduct(DocearController.getPropertiesController().getProperty("ApplicationName", DOCEAR))) {
			try {
			URL appIcon = CoreConfiguration.class.getResource("/images/twigmark16.png");
			if (appIcon == null) {
				appIcon = CoreConfiguration.class.getResource("/images/docear16.png");
			}
			ImageIcon docear16Icon = AFreeplaneAction.loadIconSafely(appIcon, "/images/twigmark16.png");
			if (docear16Icon != null) {
				DefaultFileNode.setApplicationIcon(docear16Icon);
			}
			}
			catch (Exception e) {
				LogUtils.warn("ERROR: default file application icon has not been replaced");
			}
		}
		
		WorkspaceController.replaceAction(new DocearAboutAction());
		WorkspaceController.replaceAction(new DocearQuitAction());
		WorkspaceController.replaceAction(new DocearImportProjectAction());
		// Profile files (welcome map, logo) are not copied into the config directory.
	}

	private void initIcons(Controller controller) {
		ResourceController res = ResourceController.getResourceController();
		res.setDefaultProperty(DocearImportProjectAction.KEY+".icon", "/images/docear/project/Project-Import.png");
		res.setDefaultProperty(DocearNewProjectAction.KEY+".icon", "/images/docear/project/Project-NewProject.png");
		
		res.setDefaultProperty(DocearAddRepositoryPathAction.KEY+".icon", "/images/docear/project/Project-AddLiteratureRepository.png");
		res.setDefaultProperty(DocearRemoveRepositoryPathAction.KEY+".icon", "/images/docear/project/Project-RemoveLiteratureRepository.png");
		res.setDefaultProperty(DocearRemoveRepositoryPathRibbonAction.KEY+".icon", "/images/docear/project/Project-RemoveLiteratureRepository.png");
		
		res.setDefaultProperty("ResetNodeLocationAction.icon", "/images/docear/nodes/NodesSettings-ResetPosition.png");
		res.setDefaultProperty("SetBooleanPropertyAction.edit_on_double_click.icon", "/images/docear/nodes/Nodes-EditOnDblClick.png");
		res.setDefaultProperty("NewFreeNodeAction.icon", "/images/docear/nodes/Nodes-NewFreenode.png");
		res.setDefaultProperty("FreeNodeAction.icon", "/images/docear/nodes/NodesSettings-Freenode.png");
		res.setDefaultProperty("NewSummaryAction.icon", "/images/docear/nodes/Nodes-NewSummaryNode.png");
		res.setDefaultProperty("ChangeNodeLevelLeftsAction.icon", "/images/docear/nodes/Nodes-MoveLeft.png");
		res.setDefaultProperty("ChangeNodeLevelRightsAction.icon", "/images/docear/nodes/nodes-MoveRight.png");
		res.setDefaultProperty("NodeUpAction.icon", "/images/docear/nodes/nodes-MoveUp.png");
		res.setDefaultProperty("NodeDownAction.icon", "/images/docear/nodes/nodes-MoveDown.png");
		res.setDefaultProperty("AlwaysUnfoldedNodeAction.icon", "/images/docear/nodes/nodes-AlwaysUnfolded.png");
		
		res.setDefaultProperty("ToggleFoldedAction.icon", "/images/docear/navigate/navigate-NodesUn-fold.png");
		
		res.setDefaultProperty("LatexEditLatexAction.icon", "/images/docear/resources/Resources-LaTeXFormulaEdit.png");
		res.setDefaultProperty("LatexDeleteLatexAction.icon", "/images/docear/resources/Resources-LaTeXFormulaRemove.png");
		res.setDefaultProperty("ExternalImageAddAction.icon", "/images/docear/resources/Resources-AddImage.png");
		
		res.setDefaultProperty("docear4WordLocationAction.icon", "/images/docear/tools/ToolsAndSettings-Docear4Word.png");
		res.setDefaultProperty("docearPdfInspectorLocationAction.icon", "/images/docear/tools/ToolsAndSettings-PDFInspector.png");
		res.setDefaultProperty("freeplaneAddOnLocationAction.icon", "/images/docear/tools/ToolsAndSettings-DocearAddOns.png");
		res.setDefaultProperty("jabrefAddOnLocationAction.icon", "/images/docear/tools/ToolsAndSettings-JabRefAddOns.png");
		res.setDefaultProperty("SetAcceleratorOnNextClickAction.icon", "/images/docear/tools/Tools-AssignHotkey.png");
		res.setDefaultProperty("OpenMapsAddLocation.icon", "/images/docear/tools/tools-AddOpenMaps.png");
		res.setDefaultProperty("OpenMapsRemoveLocation.icon", "/images/docear/tools/tools-RemoveOpenMaps.png");
		res.setDefaultProperty("OpenMapsViewLocation.icon", "/images/docear/tools/tools-ViewOpenMaps.png");
		res.setDefaultProperty("formula.menuname.icon", "/images/docear/tools/tools-formulas.png");
		res.setDefaultProperty("menu_encryption.icon", "/images/docear/tools/tools-PasswordProtection.png");
		res.setDefaultProperty("menu_time.icon", "/images/docear/tools/tools-TimeManagement.png");
		res.setDefaultProperty("scripting.icon", "/images/docear/tools/tools-Scripting.png");
		
		res.setDefaultProperty("attribute_options.icon", "/images/docear/view/view-AttributeOptions.png");
		res.setDefaultProperty("ShowHideNoteAction.icon", "/images/docear/view/view-showNotePanel.png");
		res.setDefaultProperty("ToggleDetailsAction.icon", "/images/docear/view/view-hideNoteDetails.png");
		res.setDefaultProperty("note_window_location.icon", "/images/docear/view/view-NotePanelPosition.png");
		res.setDefaultProperty("menu_noteView.icon", "/images/docear/view/view-NotesSettings.png");
		res.setDefaultProperty("SetBooleanPropertyAction.highlight_formulas.icon", "/images/docear/view/view-HighlightFormulas.png");
		res.setDefaultProperty("SetBooleanPropertyAction.show_node_tooltips.icon", "/images/docear/view/view-DisplayTooltips.png");
		res.setDefaultProperty("SetBooleanPropertyAction.show_styles_in_tooltip.icon", "/images/docear/view/view-displayNodeStylesTooltips.png");
		res.setDefaultProperty("ToggleFBarAction.icon", "/images/docear/view/view-FBar.png");
		res.setDefaultProperty("ToggleStatusAction.icon", "/images/docear/view/view-Statusline.png");
		res.setDefaultProperty("ToggleScrollbarsAction.icon", "/images/docear/view/view-Scrollbars.png");
		res.setDefaultProperty("ToggleLeftToolbarAction.icon", "/images/docear/view/view-IconToolbar.png");
		res.setDefaultProperty("ToggleFullScreenAction.icon", "/images/docear/view/view-FullScreen.png");
		res.setDefaultProperty("ToggleRibbonAction.icon", "/images/docear/view/view-MinimizeRibbon.png");
		res.setDefaultProperty("SetBooleanPropertyAction.presentation_mode.icon", "/images/docear/view/view-PresentationMode.png");
				
		
		res.setDefaultProperty("OpenFreeplaneSiteAction.icon", "/images/docear/help/help-Homepage.png");
		res.setDefaultProperty("AboutAction.icon", "/images/docear/help/help-about.png");
		res.setDefaultProperty("AskForHelp.icon", "/images/docear/help/help-ask4help.png");
		res.setDefaultProperty("RequestFeatureAction.icon", "/images/docear/help/help-requestFeature.png");
		res.setDefaultProperty("ReportBugAction.icon", "/images/docear/help/help-bugReport.png");
		res.setDefaultProperty("HotKeyInfoAction.icon", "/images/docear/help/help-keyReference.png");
		res.setDefaultProperty("UpdateCheckAction.icon", "/images/docear/help/help-check4updates.png");
		res.setDefaultProperty("OpenUserDirAction.icon", "/images/docear/help/help-openUserDirectory.png");
		res.setDefaultProperty("GettingStartedAction.icon", "/images/docear/help/help-tutorial.png");
		res.setDefaultProperty("QuickCaptureAction.icon", "/images/docear/monitoring/PDF-ImportNewAnnotations.png");
		res.setDefaultProperty("QuickCommandAction.icon", "/images/docear/tools/Tools-AssignHotkey.png");
		res.setDefaultProperty("MapSwitcherAction.icon", "/images/docear/navigate/navigate-NodesUn-fold.png");
		res.setDefaultProperty("CalendarViewportAction.icon", "/images/docear/tools/tools-TimeManagement.png");
		res.setDefaultProperty("EncryptionSettingsAction.icon", "/images/docear/tools/tools-PasswordProtection.png");
		res.setDefaultProperty("DocearRemoveNodeLinkAction.icon", "/images/docear/references/References-Remove.png");
		
		res.setDefaultProperty("OpenLogsFolderAction.icon", "/images/docear/help/help-showSystemLog.png");
		res.setDefaultProperty("ManualAction.icon", "/images/docear/help/help-Manual.png");
		res.setDefaultProperty("GPLPanelAction.icon", "/images/docear/help/help-gnuGPL.png");
		res.setDefaultProperty("DataPrivacyPanelAction.icon", "/images/docear/help/help-dataPrivacy.png");
		res.setDefaultProperty("TOSPanelAction.icon", "/images/docear/help/help-termsOfService.png");
		res.setDefaultProperty("DataProcessingPanelAction.icon", "/images/docear/help/help-dataProcessing.png");
		res.setDefaultProperty("ContactAction.icon", "/images/docear/help/help-contact.png");
		res.setDefaultProperty("FAQAction.icon", "/images/docear/help/help-faq.png");
		
	}

	private IControllerExecuteExtension getDocearCommandExecutor() {
		if(docearExecutor == null) {
			docearExecutor = new IControllerExecuteExtension() {
				
				@Override
				public void exec(String command, boolean waitFor) throws IOException {
					if (Compat.isWindowsOS() && enableJnaUsage) {
						LogUtils.info("using jna to execute " + command);
						windowsNativeExec(command, null, waitFor);
					}
					else {
						LogUtils.info("execute " + command);
						Process proc = Runtime.getRuntime().exec(command);
						waiting(waitFor, proc);
					}
				}
				
				public void exec(String[] command, boolean waitFor) throws IOException {
					if (Compat.isWindowsOS() && enableJnaUsage) {
						String commandString = command[0];
						for (int i=1; i<command.length; i++) {
							commandString += " " + command[i];
						}
						LogUtils.info("using jna to execute: " + commandString);
						try {
							windowsNativeExec(commandString, command, waitFor);
						} catch (Exception e) {
							throw new IOException(e.getMessage()+" for command: "+commandString);
						}
					}
					else {
						LogUtils.info("execute: " + Arrays.toString(command));
						Process proc = Runtime.getRuntime().exec(command);
						waiting(waitFor, proc);
					}
				}
				
				private void windowsNativeExec(String commantString, String[] commandArray, boolean waitFor) throws IllegalStateException, IOException {
					try {
    					WinBase.PROCESS_INFORMATION.ByReference processInfo = new WinBase.PROCESS_INFORMATION.ByReference();
    					WinBase.STARTUPINFO startupInfo = new WinBase.STARTUPINFO();
    
    					try {
    			    		if (!Kernel32.INSTANCE.CreateProcess(
    			    		    null,           // Application name, not needed if supplied in command line
    			    		    commantString,        // Command line
    			    		    null,           // Process security attributes
    			    		    null,           // Thread security attributes
    			    		    true,           // Inherit handles
    			    		    new WinDef.DWORD(0) ,              // Creation flags
    			    		    null,           // Environment
    			    		    null,           // Directory
    			    		    startupInfo,
    			    		    processInfo)) {
    			    		    throw new IllegalStateException("Error creating process. Last error: " +
    			    		        Kernel32.INSTANCE.GetLastError());
    			    		}
    			    
    			    		if (waitFor) {
    			    			Kernel32.INSTANCE.WaitForSingleObject(processInfo.hProcess, Kernel32.INFINITE);
    			    		}
    					}
    					finally {
    			    		// The CreateProcess documentation indicates that it is very important to 
    			    		// close the returned handles
    			    		Kernel32.INSTANCE.CloseHandle(processInfo.hThread);
    			    		Kernel32.INSTANCE.CloseHandle(processInfo.hProcess);
    					}
					}
    				catch(NoClassDefFoundError e) {
    					LogUtils.warn(e);
    					
    					enableJnaUsage = false;
    					if (commandArray != null) {
    						exec(commandArray, waitFor);
    					}
    					else {
    						exec(commantString, waitFor);
    					}
    				}
				}
				
				private void waiting(boolean waitFor, Process proc) throws IOException {
					if(waitFor) {
						try {
							proc.waitFor();
						} catch (InterruptedException e) {
							throw new IOException(e);
						}
					}
				}
				
			};
		}
		return docearExecutor;
	}

	private void copyInfoIfNecessary() {	
		File _welcomeFile = new File(URIUtils.getFile(WorkspaceController.getApplicationSettingsHome()), "docear-welcome.mm");
		
		if(!_welcomeFile.exists()) {
			createAndCopy(_welcomeFile, "/conf/docear-welcome.mm");			
		}
		
		File _docearLogo = new File(URIUtils.getFile(WorkspaceController.getApplicationSettingsHome()), "docear-logo.png");
		if(!_docearLogo.exists()) {
			String logoRes = "/images/twigmark_logo.png";
			if (CoreConfiguration.class.getResource(logoRes) == null) {
				logoRes = "/images/docear_logo.png";
			}
			createAndCopy(_docearLogo, logoRes);			
		}
		
		ResourceController resController = Controller.getCurrentController().getResourceController();
		if (isTwigmarkProduct(resController.getProperty("ApplicationName"))) {
			String mapPath = _welcomeFile.toURI().getPath();
			resController.setProperty("first_start_map", mapPath);
			resController.setProperty("icons_url", "");
			resController.setProperty("tutorial_map", mapPath);
		}
	}
	
	private void createAndCopy(File file, String resourcePath) {
		try {
			createFile(file);
			FileUtils.copyInputStreamToFile(CoreConfiguration.class.getResourceAsStream(resourcePath), file);
		}
		catch (IOException e) {
			LogUtils.warn(e);
		}
	}

	/**
	 * @param file
	 * @throws IOException
	 */
	private void createFile(File file) throws IOException {
		if(file.getParentFile() == null) {
			LogUtils.warn("missing parent directory for user.settings: "+file);
			LogUtils.warn("user.settings home: "+WorkspaceController.getApplicationSettingsHome());
			int option = JOptionPane.showConfirmDialog(null, "Your user settings directory has not been set or is set with the system root directory. \nThis might cause further issues. \n Do you want to continue?", "Settings home warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if(option == JOptionPane.NO_OPTION) {
				System.exit(1);
			}
		}
		else if(!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
			return;
		}
		file.createNewFile();
	}
	
	protected void initMode(final ModeController modeController) {
		WorkspaceController.replaceAction(new DocearAddRepositoryPathAction());
		WorkspaceController.replaceAction(new DocearRemoveRepositoryPathAction());
		WorkspaceController.replaceAction(new DocearRemoveRepositoryPathRibbonAction());
		WorkspaceController.replaceAction(new DocearLibraryOpenLocation());
		WorkspaceController.replaceAction(new DocearNewProjectAction());
		WorkspaceController.replaceAction(new DocearLibraryNewMindmap());
		
		final DocearMaximizeMapHandler maximizeMapHandler = DocearMaximizeMapHandler.installMode(modeController);
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				OneTouchCollapseResizer resizer = OneTouchCollapseResizer.findResizerFor(modeController.getUserInputListenerFactory().getRibbonBuilder().getRibbonRootComponent());
				if(resizer == null) {
					EventQueue.invokeLater(this);
					return;
				}
				maximizeMapHandler.addCollapsableResizer(resizer);
				maximizeMapHandler.addCollapsableResizer(getNotePanelResizer());
				maximizeMapHandler.addCollapsableResizer(getFBarResizer());
				maximizeMapHandler.addCollapsableResizer(getIconBarResizer());
				maximizeMapHandler.addCollapsableResizer(getStatusLineResizer());
				maximizeMapHandler.addCollapsableResizer(getFormatPanelResizer());
			}
		});
		
		DocearProjectLoader docearProjectLoader = new DocearProjectLoader();
		WorkspaceController.getModeExtension(modeController).setProjectLoader(docearProjectLoader);
		AWorkspaceModeExtension modeExt = WorkspaceController.getModeExtension(modeController);
		final IWorkspaceView view = modeExt.getView();
		if(view != null) {
			view.getTransferHandler().registerNodeDropHandler(FolderTypeLibraryNode.class, new VirtualFolderDropHandler());
			view.getTransferHandler().registerNodeDropHandler(LiteratureRepositoryPathNode.class, new FileFolderDropHandler());
			EventQueue.invokeLater(new Runnable() {
				public void run() {
					OneTouchCollapseResizer resizer = OneTouchCollapseResizer.findResizerFor(view.getComponent());
					if(resizer == null) {
						EventQueue.invokeLater(this);
						return;
					}
					maximizeMapHandler.addCollapsableResizer(resizer);
				}
			});
			
		}
		
		
		DocearController.getController().getDocearEventLogger().appendToLog(this, DocearLogEvent.APPLICATION_STARTED);
		Toolkit.getDefaultToolkit();		
		DocearController.getController().getDocearEventLogger().appendToLog(this, DocearLogEvent.OS_OPERATING_SYSTEM, System.getProperty("os.name"));
		DocearController.getController().getDocearEventLogger().appendToLog(this, DocearLogEvent.OS_LANGUAGE_CODE, System.getProperty("user.language"));
		DocearController.getController().getDocearEventLogger().appendToLog(this, DocearLogEvent.OS_COUNTRY_CODE, System.getProperty("user.country"));
		DocearController.getController().getDocearEventLogger().appendToLog(this, DocearLogEvent.OS_TIME_ZONE, System.getProperty("user.timezone"));
		DocearController.getController().getDocearEventLogger().appendToLog(this, DocearLogEvent.OS_SCREEN_RESOLUTION, Toolkit.getDefaultToolkit().getScreenSize().toString());
		
		MapVersionInterpreter.addMapVersionInterpreter(new MapVersionInterpreter("Docear", 1, "docear", false, false, "Docear", "url", null, new org.freeplane.features.url.IMapConverter() {
			public void convert(NodeModel root) throws MapConversionException {
				final MapModel mapModel = root.getMap();				
				DocearMapModelExtension docearMapModel = mapModel.getExtension(DocearMapModelExtension.class);
				if (docearMapModel == null) {
					DocearMapModelController.setModelWithCurrentVersion(mapModel);
				}		
			}
		}));
		
		// set up context menu for workspace
		
		addPluginDefaults(Controller.getCurrentController());
		addMenus(modeController);
		
		registerListeners(modeController);
		
		replaceFreeplaneStringsAndActions(modeController);
		
		DocearMapModelController.install(new DocearMapModelController(modeController));
		
		setDocearMapWriter(modeController);
		
		registerController(modeController);
		UrlManager.getController().setLastCurrentDir(URIUtils.getAbsoluteFile(WorkspaceController.getModeExtension(modeController).getDefaultProjectHome()));	
	}

	private void loadAndStoreVersion(Controller controller) {
		//DOCEAR: has to be called before the splash is showing
		final Properties versionProperties = new Properties();
		InputStream in = null;
		try {
			in = this.getClass().getResource("/version.properties").openStream();
			versionProperties.load(in);
		}
		catch (final IOException e) {
			
		}
		
		final Properties buildProperties = new Properties();
		in = null;
		try {
			in = this.getClass().getResource("/build.number").openStream();
			buildProperties.load(in);
		}
		catch (final IOException e) {
			
		}
		final String versionNumber = versionProperties.getProperty("docear_version");
		final String versionStatus = versionProperties.getProperty("docear_version_status");
		final String versionStatusNumber = versionProperties.getProperty("docear_version_status_number");
		final int versionBuild = Integer.parseInt(buildProperties.getProperty("build.number")) -1;
		controller.getResourceController().setProperty("docear_version", versionNumber);
		controller.getResourceController().setProperty("docear_status", versionStatus+" "+versionStatusNumber+" build "+versionBuild);
		LogUtils.info("Docear version: " + versionNumber + "." + versionStatusNumber + " " + versionStatus + " build " + versionBuild);
		LogUtils.info("OS version: " + System.getProperty("os.name")+" "+System.getProperty("os.version")+" "+System.getProperty("os.arch"));
	}
	
	private void adjustProperties(Controller controller) {
		final Properties coreProperties = new Properties();
		InputStream in = null;
		try {
			in = this.getClass().getResource("/core.properties").openStream();
			coreProperties.load(in);
		}
		catch (final IOException e) {			
		}
		
		ResourceController resController = controller.getResourceController();
		
		final URL defaults = this.getClass().getResource(ResourceController.PLUGIN_DEFAULTS_RESOURCE);
		if (defaults == null)
			throw new RuntimeException("cannot open " + ResourceController.PLUGIN_DEFAULTS_RESOURCE);
		resController.addDefaults(defaults);
		
		resController.setProperty(WEB_FREEPLANE_LOCATION, coreProperties.getProperty(WEB_DOCEAR_LOCATION));
		resController.setProperty(BUG_TRACKER_LOCATION, coreProperties.getProperty(DOCEAR_BUG_TRACKER_LOCATION));
		resController.setProperty(HELP_FORUM_LOCATION, coreProperties.getProperty("docear_helpForumLocation"));
		resController.setProperty(FEATURE_TRACKER_LOCATION, coreProperties.getProperty(DOCEAR_FEATURE_TRACKER_LOCATION));
		resController.setProperty("manualLocation", coreProperties.getProperty("docear_manualLocation"));
		resController.setProperty("faqLocation", coreProperties.getProperty("docear_faqLocation"));
		resController.setProperty("contactLocation", coreProperties.getProperty("docear_contactLocation"));
		resController.setProperty("docu-online", "");
		resController.setProperty("docear4WordLocation", coreProperties.getProperty("docear4WordLocation"));
		resController.setProperty("docearPdfInspectorLocation", coreProperties.getProperty("docearPdfInspectorLocation"));
		resController.setProperty("freeplaneAddOnLocation", coreProperties.getProperty("freeplaneAddOnLocation"));
		resController.setProperty("jabrefAddOnLocation", coreProperties.getProperty("jabrefAddOnLocation"));
		resController.setProperty("org.freeplane.plugin.bugreport", "org.freeplane.plugin.bugreport.denied");
		
		if (!isTwigmarkProduct(resController.getProperty(APPLICATION_NAME, ""))) {
			return;
		}

		// Replace Freeplane brand strings with Twigmark in the UI bundle
		replaceResourceBundleStrings();
	}
		
	private void addMenus(ModeController modeController) {
		QuickCaptureService.install(modeController);
		QuickCommandService.install(modeController);
		MapSwitcherService.install(modeController);
		org.docear.plugin.core.settings.ProductSettingsService.install(modeController);
		CalendarViewportIntegration.install((org.freeplane.features.mode.mindmapmode.MModeController) modeController);
		TodoistIntegrationService.install(modeController);
		RelationshipGraphIntegration.install((org.freeplane.features.mode.mindmapmode.MModeController) modeController);
		modeController.addAction(new DocearRemoveNodeLinkAction());
		if("true".equals(System.getProperty("docear.debug", "false"))) {
			modeController.addAction(new DocearSetNodePrivacyAction());
		}
		modeController.addMenuContributor(new IMenuContributor() {
			public void updateMenus(ModeController modeController, MenuBuilder builder) {
				//add entries to project menu
				final String MENU_PROJECT_KEY = "/menu_bar/project";
				builder.addSeparator(MENU_PROJECT_KEY, MenuBuilder.AS_CHILD);
				final AWorkspaceAction addAction = new DocearAddRepositoryPathAction();
				builder.addAction(MENU_PROJECT_KEY, addAction, MenuBuilder.AS_CHILD);
				final AWorkspaceAction removeAction = new DocearRemoveRepositoryPathAction();
				builder.addAction(MENU_PROJECT_KEY, removeAction, MenuBuilder.AS_CHILD);
				Object obj = builder.get(MENU_PROJECT_KEY).getUserObject();
				if(obj instanceof JMenu) {
					((JMenu) obj).getPopupMenu().addPopupMenuListener(new PopupMenuListener() {					
						public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
							addAction.setEnabled();
							removeAction.setEnabled();
						}
						
						public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}
						
						public void popupMenuCanceled(PopupMenuEvent e) {}
					});
				}
				
				//add entries to
				builder.addAction("/menu_bar/help", new DocearShowTermsOfUseAction(),	MenuBuilder.AS_CHILD);
				builder.addAction("/menu_bar/help", new DocearShowDataPrivacyStatementAction(),	MenuBuilder.AS_CHILD);
				builder.addAction("/menu_bar/help", new DocearShowDataProcessingTermsAction(),	MenuBuilder.AS_CHILD);
				//builder.addAction("/menu_bar/help", new DocearShowNotificationBar(),	MenuBuilder.AS_CHILD);
				
				//add node privacy actions
				if("true".equals(System.getProperty("docear.debug", "false"))) {
					builder.addSeparator("/menu_bar/edit", MenuBuilder.AS_CHILD);
					builder.addAction("/menu_bar/edit", new DocearSetNodePrivacyAction(),	MenuBuilder.AS_CHILD);
					builder.addSeparator("/node_popup", MenuBuilder.AS_CHILD);
					builder.addAction("/node_popup", new DocearSetNodePrivacyAction(),	MenuBuilder.AS_CHILD);
					
				}
				String key = (String) builder.getKeyByUserObject(WorkspaceController.getAction(modeController, "ExtractLinkFromTextAction"));
				if(key != null) {
					builder.addAction(key, WorkspaceController.getAction(modeController, DocearRemoveNodeLinkAction.KEY), MenuBuilder.AFTER);
				}
				else {
					builder.addSeparator("popup_navigate", MenuBuilder.AS_CHILD);
					builder.addAction("popup_navigate", WorkspaceController.getAction(modeController, DocearRemoveNodeLinkAction.KEY), MenuBuilder.AS_CHILD);
				}
				
			}
		});
		File file = new File(Compat.getApplicationUserDirectory(), "docear_core_ribbon.xml");		
		if (file.exists()) {
			LogUtils.info("using alternative ribbon configuration file: "+file.getAbsolutePath());
			try {				
				modeController.getUserInputListenerFactory().getRibbonBuilder().updateRibbon(file.toURI().toURL());
			}
			catch (MalformedURLException e) {				
				LogUtils.severe("MModeControllerFactory.createStandardControllers(): "+e.getMessage());
			}
		}
		else {
			modeController.getUserInputListenerFactory().getRibbonBuilder().registerContributorFactory("nodePrivacyActionContributor", new DocearNodePrivacyContributorFactory());
			modeController.getUserInputListenerFactory().getRibbonBuilder().updateRibbon(DocearController.class.getResource("/xml/ribbons.xml"));
		}
		
	}



	private void setDocearMapWriter(ModeController modeController) {
		DocearMapWriter mapWriter = new DocearMapWriter(modeController.getMapController());
		mapWriter.setMapWriteHandler();		
	}

	private void registerController(ModeController modeController) {
		DocearNodeModifiedExtensionController.install(modeController);
		DocearNodePrivacyExtensionController.install(modeController);
	}

	private void replaceFreeplaneStringsAndActions(ModeController modeController) {
		disableAutoUpdater();
		disableBugReporter();
		
		//replace this actions if docear_core is present
		modeController.removeAction("SaveAsAction");
		modeController.addAction(new SaveAsAction());
		modeController.removeAction("SaveAction");
		modeController.addAction(new SaveAction());
		
		
		//remove sidepanel switcher
		//Controller.getCurrentModeController().removeAction("ShowFormatPanel");
		ResourceController resourceController = ResourceController.getResourceController();		
		
		if (!resourceController.getProperty(APPLICATION_NAME, "").equals(DOCEAR)) {
			return;
		}

		//replace if application name is docear
		replaceResourceBundleStrings();
		
		AFreeplaneAction action = modeController.getAction("PropertyAction");
		final URL url = DocearController.getPropertiesController().getResource("/images/docear/tools/tools-Preferences.png");
		if (url != null && action != null) {
			final ImageIcon icon = AFreeplaneAction.loadIconSafely(url, "/images/docear/tools/tools-Preferences.png");
		if (icon == null) {
			return;
		}
			action.putValue(AbstractAction.SMALL_ICON, icon);
		}

		replaceActions();
	}

	private void disableAutoUpdater() {
		final OptionPanelController optionController = Controller.getCurrentController().getOptionPanelController();		
		optionController.addPropertyLoadListener(new OptionPanelController.PropertyLoadListener() {
			
			public void propertiesLoaded(Collection<IPropertyControl> properties) {
				((IPropertyControl) optionController.getPropertyControl("check_updates_automatically")).setEnabled(false);
			}
		});
	}
	
	private void disableBugReporter() {
		final OptionPanelController optionController = Controller.getCurrentController().getOptionPanelController();		
		optionController.addPropertyLoadListener(new OptionPanelController.PropertyLoadListener() {
			public void propertiesLoaded(Collection<IPropertyControl> properties) {
				((IPropertyControl) optionController.getPropertyControl("org.freeplane.plugin.bugreport")).setEnabled(false);
			}
		});
	}

	private void replaceActions() {
		ResourceController res = DocearController.getPropertiesController();		
		
		WorkspaceController.replaceAction(new DocearOpenUrlAction(REQUEST_FEATURE_ACTION, res.getProperty(FEATURE_TRACKER_LOCATION)));
		WorkspaceController.replaceAction(new DocearOpenUrlAction(ASK_FOR_HELP, res.getProperty(HELP_FORUM_LOCATION)));
		WorkspaceController.replaceAction(new DocearOpenUrlAction(REPORT_BUG_ACTION, res.getProperty(BUG_TRACKER_LOCATION)));
		WorkspaceController.replaceAction(new DocearOpenUrlAction(OPEN_FREEPLANE_SITE_ACTION, res.getProperty(WEB_FREEPLANE_LOCATION)));
		WorkspaceController.replaceAction(new DocearOpenUrlAction(DOCUMENTATION_ACTION, res.getProperty(WEB_DOCU_LOCATION)));		
		WorkspaceController.replaceAction(new GettingStartedAction());
		WorkspaceController.replaceAction(new OnlineDocumentationAction("OnlineReference", "docu-online"));		
		WorkspaceController.replaceAction(new DocearOpenUrlAction("ManualAction",  res.getProperty("manualLocation")));
		WorkspaceController.replaceAction(new DocearOpenUrlAction("FAQAction",  res.getProperty("faqLocation")));
		WorkspaceController.replaceAction(new DocearOpenUrlAction("ContactAction",  res.getProperty("contactLocation")));
		WorkspaceController.replaceAction(new DocearOpenUrlAction("docear4WordLocationAction",  res.getProperty("docear4WordLocation")));
		WorkspaceController.replaceAction(new DocearOpenUrlAction("docearPdfInspectorLocationAction",  res.getProperty("docearPdfInspectorLocation")));
		WorkspaceController.replaceAction(new DocearOpenUrlAction("freeplaneAddOnLocationAction",  res.getProperty("freeplaneAddOnLocation")));
		WorkspaceController.replaceAction(new DocearOpenUrlAction("jabrefAddOnLocationAction",  res.getProperty("jabrefAddOnLocation")));		
		
		WorkspaceController.replaceAction(new GPLPanelAction());
		WorkspaceController.replaceAction(new LicencesPanelAction("TOSPanelAction", TextUtils.getText("docear.license.terms_of_use.title"), DocearController.getController().getTermsOfService()));
		WorkspaceController.replaceAction(new LicencesPanelAction("DataPrivacyPanelAction", TextUtils.getText("docear.license.data_privacy.title"), DocearController.getController().getDataPrivacyTerms()));
		WorkspaceController.replaceAction(new LicencesPanelAction("DataProcessingPanelAction", TextUtils.getText("docear.license.data_processing.title"), DocearController.getController().getDataProcessingTerms()));		
		
		WorkspaceController.replaceAction(new DocearShowTermsOfUseAction());
		WorkspaceController.replaceAction(new DocearShowDataPrivacyStatementAction());
		WorkspaceController.replaceAction(new DocearShowDataProcessingTermsAction());
		WorkspaceController.replaceAction(new OpenLogsFolderAction());
	}
	
	private void replaceResourceBundleStrings() {
		ResourceController resourceController = ResourceController.getResourceController();
		ResourceBundles bundles = ((ResourceBundles) resourceController.getResources());
		Controller controller = Controller.getCurrentController();

		for (Enumeration<?> i = bundles.getKeys(); i.hasMoreElements();) {
			String key = i.nextElement().toString();
			String value = bundles.getResourceString(key);
			if (value == null) {
				continue;
			}
			final String lower = value.toLowerCase();
			if (lower.indexOf("freeplane") < 0) {
				continue;
			}
			// Skip URLs, paths, and placeholders like <freeplaneuserdir>
			if (value.matches("(?i).*(https?://|www\\.|[\\\\/]|<[^>]*freeplane[^>]*>).*")) {
				continue;
			}
			String replaced = value.replaceAll("(?i)freeplane", DOCEAR);
			if (replaced.equals(value)) {
				continue;
			}
			bundles.putResourceString(key, replaced);
			if (key.endsWith(".text")) {
				String actionKey = key.substring(0, key.length() - ".text".length());
				AFreeplaneAction action = controller.getAction(actionKey);
				if (action != null) {
					MenuBuilder.setLabelAndMnemonic(action, replaced);
				}
			}
		}
	}

	private void addPluginDefaults(Controller controller) {
		ResourceController resController = controller.getResourceController();
		if (isTwigmarkProduct(resController.getProperty("ApplicationName")) && DocearController.getController().isDocearFirstStart()) {
			resController.setProperty("selection_method", "selection_method_by_click");
			resController.setProperty("links", "relative_to_workspace");
			resController.setProperty("save_folding", "always_save_folding");
			resController.setProperty("leftToolbarVisible", "false");
			resController.setProperty("scrollbarsVisible", "false");
			resController.setProperty("center_selected_node", "true");
			resController.setProperty("status_visible", "false");
			resController.setProperty("styleScrollPaneVisible", "true");
			//resController.setProperty("language", "en");
			resController.setProperty(DocearController.DOCEAR_FIRST_RUN_PROPERTY, true);
		}
		// One-shot layout defaults for existing profiles (user can still change later).
		if (isTwigmarkProduct(resController.getProperty("ApplicationName"))
		        && !"2026-07-layout2".equals(resController.getProperty("docear.ui.layout.defaults", ""))) {
			resController.setProperty("scrollbarsVisible", "false");
			resController.setProperty("center_selected_node", "true");
			resController.setProperty("leftToolbarVisible", "false");
			resController.setProperty("status_visible", "false");
			resController.setProperty("docear.ui.layout.defaults", "2026-07-layout2");
		}
		WorkspaceController.addAction(new DocearRenameAction());
		JViewport viewport = (JViewport) Controller.getCurrentController().getMapViewManager().getViewport();
		final OverlayViewport overlayViewport = new OverlayViewport(viewport);
		Controller.getCurrentController().getMapViewManager().getScrollPane().setViewport(overlayViewport);
		MapTagFilterOverlay.install();
		MapActivityOverlay.install();
	}
	
	private void registerListeners(ModeController modeController) {
		Controller.getCurrentController().getOptionPanelController().addPropertyLoadListener(new PropertyLoadListener());
		Controller.getCurrentController().getResourceController().addPropertyChangeListener(new PropertyListener());
		
		DocearLifeCycleObserver observer = new DocearLifeCycleObserver(modeController);
		DocearController.getController().setLifeCycleObserver(observer);
		DocearCoreOmniListenerAdapter adapter = new DocearCoreOmniListenerAdapter();
		observer.addMapLifeCycleListener(adapter);
		observer.addMapViewChangeListener(adapter);
		observer.addMapLifeCycleListener(new MapLifeCycleAndViewListener());
		observer.addMapViewChangeListener(new MapLifeCycleAndViewListener());
		
		//modeController.getMapController().addMapLifeCycleListener(new MapLifeCycleAndViewListener());
		//modeController.getMapController().addMapLifeCycleListener(adapter);
		modeController.getMapController().addMapChangeListener(adapter);
		modeController.getMapController().addNodeChangeListener(adapter);
		modeController.getMapController().addNodeSelectionListener(adapter);
		DocearController.getController().getEventQueue().addEventListener(adapter);
//		Controller.getCurrentController().getMapViewManager().addMapViewChangeListener(adapter);
//		Controller.getCurrentController().getMapViewManager().addMapViewChangeListener(new MapLifeCycleAndViewListener());
		WorkspaceController.getModeExtension(modeController).getIOController().registerNodeActionListener(AWorkspaceTreeNode.class, WorkspaceActionEvent.WSNODE_OPEN_DOCUMENT, new WorkspaceOpenDocumentListener());
	}
	
	private OneTouchCollapseResizer getFormatPanelResizer() {
		return new OneTouchCollapseResizer(Direction.RIGHT, CollapseDirection.COLLAPSE_RIGHT) {
			private static final long serialVersionUID = 1L;
			private static final String TABBEDPANE_VIEW_COLLAPSED = "tabbed_pane.collapsed";

			private OneTouchCollapseResizer getFormatOtcr() {
				final JComponent formatToolbar = Controller.getCurrentModeController()
				    .getUserInputListenerFactory().getToolBar("/format");
				if (formatToolbar == null || formatToolbar.getComponentCount() == 0) {
					return null;
				}
				final Component component = formatToolbar.getComponent(0);
				if (component instanceof OneTouchCollapseResizer) {
					return (OneTouchCollapseResizer) component;
				}
				return null;
			}

			@Override
			public boolean isExpanded() {
				final OneTouchCollapseResizer otcr = getFormatOtcr();
				if (otcr != null) {
					return otcr.isExpanded();
				}
				return !Boolean.parseBoolean(ResourceController.getResourceController().getProperty(
				    TABBEDPANE_VIEW_COLLAPSED, "false"));
			}

			@Override
			public void setExpanded(boolean enabled) {
				final OneTouchCollapseResizer otcr = getFormatOtcr();
				if (otcr != null && enabled != otcr.isExpanded()) {
					otcr.setExpanded(enabled);
				}
			}

			@Override
			protected void initDefaults() {
				super.initDefaults();
				ResourceController.getResourceController().addPropertyChangeListener(new IFreeplanePropertyListener() {
					@Override
					public void propertyChanged(String propertyName, String newValue, String oldValue) {
						if (TABBEDPANE_VIEW_COLLAPSED.equals(propertyName)) {
							fireCollapseStateChanged(null, !Boolean.parseBoolean(newValue));
						}
					}
				});
			}
		};
	}

	private OneTouchCollapseResizer getStatusLineResizer() {
		return new OneTouchCollapseResizer(Direction.DOWN, CollapseDirection.COLLAPSE_DOWN) {
			private static final long serialVersionUID = 1L;
			ToggleToolbarAction action = (ToggleToolbarAction) WorkspaceController.getAction(Controller.getCurrentModeController(), "ToggleStatusAction");
			@Override
			public boolean isExpanded() {
				return action.isVisible();
			}

			@Override
			public void setExpanded(boolean enabled) {
				if(enabled != isExpanded()) {
					action.actionPerformed(null);
				}
			}

			@Override
			protected void initDefaults() {
				super.initDefaults();
				ResourceController.getResourceController().addPropertyChangeListener(new IFreeplanePropertyListener() {
					
					@Override
					public void propertyChanged(String propertyName, String newValue, String oldValue) {
						if(action.getPropertyName().equals(propertyName)) {
							fireCollapseStateChanged(null, !Boolean.parseBoolean(newValue));
						}
					}
				});
			}
		};
	}

	private OneTouchCollapseResizer getIconBarResizer() {
		return new OneTouchCollapseResizer(Direction.DOWN, CollapseDirection.COLLAPSE_DOWN) {
			private static final long serialVersionUID = 1L;
			ToggleToolbarAction action = (ToggleToolbarAction) WorkspaceController.getAction(Controller.getCurrentModeController(), "ToggleLeftToolbarAction");
			@Override
			public boolean isExpanded() {
				return action.isVisible();
			}

			@Override
			public void setExpanded(boolean enabled) {
				if(enabled != isExpanded()) {
					action.actionPerformed(null);
				}
			}

			@Override
			protected void initDefaults() {
				super.initDefaults();
				ResourceController.getResourceController().addPropertyChangeListener(new IFreeplanePropertyListener() {
					
					@Override
					public void propertyChanged(String propertyName, String newValue, String oldValue) {
						if(action.getPropertyName().equals(propertyName)) {
							fireCollapseStateChanged(null, !Boolean.parseBoolean(newValue));
						}
					}
				});
			}
		};
	}

	private OneTouchCollapseResizer getFBarResizer() {
		return new OneTouchCollapseResizer(Direction.DOWN, CollapseDirection.COLLAPSE_DOWN) {
			private static final long serialVersionUID = 1L;
			ToggleToolbarAction action = (ToggleToolbarAction) WorkspaceController.getAction(Controller.getCurrentModeController(), "ToggleFBarAction");
			@Override
			public boolean isExpanded() {
				return action.isVisible();
			}

			@Override
			public void setExpanded(boolean enabled) {
				if(enabled != isExpanded()) {
					action.actionPerformed(null);
				}
			}

			@Override
			protected void initDefaults() {
				super.initDefaults();
				ResourceController.getResourceController().addPropertyChangeListener(new IFreeplanePropertyListener() {
					@Override
					public void propertyChanged(String propertyName, String newValue, String oldValue) {
						if(propertyName != null && propertyName.equals(action.getPropertyName())) {
							fireCollapseStateChanged(null, !Boolean.parseBoolean(newValue));
						}
					}
				});
			}
		};
	}

	private OneTouchCollapseResizer getNotePanelResizer() {
		
		return new OneTouchCollapseResizer(Direction.DOWN, CollapseDirection.COLLAPSE_DOWN) {
			private static final long serialVersionUID = 1L;
			AFreeplaneAction action = WorkspaceController.getAction(Controller.getCurrentModeController(), "ShowHideNoteAction");
			@Override
			
			public boolean isExpanded() {
				return action.isSelected();
			}

			@Override
			public void setExpanded(boolean enabled) {
				if(enabled != isExpanded()) {
					action.actionPerformed(null);
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							Controller controller = Controller.getCurrentController();
							IMapSelection selection = controller.getSelection();
							if(selection != null) {
								if(selection.getSelected() != null) {
									controller.getMapViewManager().getComponent(selection.getSelected()).requestFocusInWindow();
								}
							}
						}
					});
				}
				
			}

			@Override
			protected void initDefaults() {
				super.initDefaults();
				ResourceController.getResourceController().addPropertyChangeListener(new IFreeplanePropertyListener() {
					
					@Override
					public void propertyChanged(String propertyName, String newValue, String oldValue) {
						if("use_split_pane".equals(propertyName)) {
							fireCollapseStateChanged(null, !Boolean.parseBoolean(newValue));
						}
					}
				});
			}
			
			
		};
	}
	
	class GettingStartedAction extends AFreeplaneAction {
		
		public GettingStartedAction() {
			super("GettingStartedAction");			
		}

		private static final long serialVersionUID = 1L;

		public void actionPerformed(final ActionEvent e) {
			final ResourceController resourceController = DocearController.getPropertiesController();
			final File baseDir = new File(resourceController.getResourceBaseDir()).getAbsoluteFile().getParentFile();
			final String languageCode = resourceController.getLanguageCode();
			final File file = ConfigurationUtils.getLocalizedFile(new File[]{baseDir}, Controller.getCurrentController().getResourceController().getProperty("tutorial_map"), languageCode);
			try {
				final URL endUrl = file.toURI().toURL();
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						try {
							if (endUrl.getFile().endsWith(".mm")) {
								 Controller.getCurrentController().selectMode(MModeController.MODENAME);
								 MMapIO mapIO = (MMapIO) MModeController.getMModeController().getExtension(MapIO.class);
								 mapIO.newDocumentationMap(endUrl);
							}
							else {
								Controller.getCurrentController().getViewController().openDocument(endUrl);
							}
						}
						catch (final Exception e1) {
							LogUtils.severe(e1);
						}
					}
				});
			}
			catch (final MalformedURLException e1) {
				LogUtils.warn(e1);
			}
			
			DocearController.getController().getDocearEventLogger().appendToLog(this, DocearLogEvent.SHOW_HELP);
		}
	}

}
