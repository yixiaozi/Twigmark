package org.docear.plugin.core.workspace.actions;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.io.IOException;

import javax.swing.SwingUtilities;

import org.docear.plugin.core.ui.ImportProjectPagePanel;
import org.docear.plugin.core.ui.wizard.Wizard;
import org.docear.plugin.core.ui.wizard.WizardSession;
import org.docear.plugin.core.ui.wizard.WizardPageDescriptor;
import org.docear.plugin.core.workspace.compatible.DocearWorkspaceToProjectConverter;
import org.docear.plugin.core.workspace.controller.DocearConversionDescriptor;
import org.docear.plugin.core.workspace.model.DocearWorkspaceProject;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.workspace.WorkspaceController;
import org.freeplane.plugin.workspace.actions.AWorkspaceAction;
import org.freeplane.plugin.workspace.io.IProjectSettingsIOHandler.LOAD_RETURN_TYPE;
import org.freeplane.plugin.workspace.model.WorkspaceModelException;
import org.freeplane.plugin.workspace.model.project.AWorkspaceProject;

public class DocearImportProjectAction extends AWorkspaceAction {
	
	private static final long serialVersionUID = 1L;
	public static final String KEY = "workspace.action.project.import";
	
	public DocearImportProjectAction() {
		super(KEY);
		setEnabled(false);
	}

	public void actionPerformed(ActionEvent event) {
		// Project import disabled: fixed data root is used automatically.
	}

	public static void importProject(final AWorkspaceProject project) {
		try {
			Runnable runnable = new Runnable() {			
				@Override
				public void run() {
					if(project == null) {
					return;
				}
				String projectName = null;		
				DocearProjectSettings settings = project.getExtensions(DocearProjectSettings.class);
				
				if(settings != null) {
					projectName = settings.getProjectName();
				}
				try {
					WorkspaceController.getCurrentModel().addProject(project);
					if(project.getExtensions(DocearConversionDescriptor.class) != null) {
						try {
							DocearWorkspaceToProjectConverter.convert(project.getExtensions(DocearConversionDescriptor.class));
							project.setLoaded();
						} catch (IOException e) {
							LogUtils.severe(e);
						}
						return;
					} 
					
					try {
						LOAD_RETURN_TYPE return_type = WorkspaceController.getCurrentModeExtension().getProjectLoader().loadProject(project);
						if(return_type == LOAD_RETURN_TYPE.NEW_PROJECT && projectName != null && projectName.length() > 0) {
							project.getModel().changeNodeName(project.getModel().getRoot(), projectName);
						}
					} catch (IOException e) {
						LogUtils.severe(e);
					} catch (WorkspaceModelException e) {
						LogUtils.severe(e);
					}
				}
				finally {
					try {
						WorkspaceController.save();
					}
					catch (Exception e) {
					}
				}
				}
			};
			
			if (EventQueue.isDispatchThread()) {
				runnable.run();
			}
			else {
				SwingUtilities.invokeAndWait(runnable);
			}
		}
		catch (Exception e) {
			LogUtils.warn(e);
		}
		
	}

}
