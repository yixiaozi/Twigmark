package org.docear.plugin.core.graph;

import java.awt.Component;

import javax.swing.JComponent;

import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.main.application.MapViewTabOrder;
import org.freeplane.plugin.workspace.WorkspaceController;
import org.freeplane.plugin.workspace.components.RelationshipGraphTabBridge;
import org.freeplane.plugin.workspace.mindmapmode.MModeWorkspaceController;

/**
 * Registers the relationship graph with the workspace side-tab bridge.
 */
public final class RelationshipGraphIntegration {

	private static RelationshipGraphSideTabPanel sideTabPanel;

	private RelationshipGraphIntegration() {
	}

	public static void install(final MModeController modeController) {
		RelationshipGraphService.install(modeController);
		MapViewTabOrder.setSameTabClickListener(new MapViewTabOrder.SameTabClickListener() {
			public boolean onSameTabClicked(final int tabIndex, final Component mapView) {
				final RelationshipGraphService service = RelationshipGraphService.getService();
				if (service == null || !service.isHoldingViewport()) {
					return false;
				}
				exitGraphViewDueToMapSwitch();
				return true;
			}
		});
		MapViewTabOrder.setMapTabActivationListener(new MapViewTabOrder.MapTabActivationListener() {
			public void onMapTabActivated(final Component mapView) {
				final RelationshipGraphService service = RelationshipGraphService.getService();
				if (service == null || !service.isHoldingViewport()) {
					return;
				}
				exitGraphViewDueToMapSwitch();
			}
		});
		RelationshipGraphTabBridge.setProvider(new RelationshipGraphTabBridge.Provider() {
			public JComponent createSideTabPanel() {
				if (sideTabPanel == null) {
					sideTabPanel = new RelationshipGraphSideTabPanel();
				}
				return sideTabPanel;
			}

			public void onTabSelected() {
				// ensureSideTabLoaded may have raced before the bridge was ready; create now.
				if (sideTabPanel == null) {
					createSideTabPanel();
				}
				if (sideTabPanel != null) {
					sideTabPanel.onTabActivated();
				}
			}

			public void onTabDeselected() {
				if (sideTabPanel != null) {
					sideTabPanel.onTabDeactivated();
				}
			}

			public void preloadMetrics() {
				if (sideTabPanel != null) {
					sideTabPanel.preloadMetrics();
				}
				else {
					RelationshipGraphMetricsPreloader.preload();
				}
			}
		});
	}

	/**
	 * User left the graph for a mind map (bottom-tab click / Alt+tab / open from graph).
	 * Clears the viewport override and selects the workspace side tab.
	 */
	public static void exitGraphViewDueToMapSwitch() {
		final RelationshipGraphService service = RelationshipGraphService.getService();
		if (service != null) {
			if (service.isHoldingViewport() || service.isGraphInViewport()) {
				service.hideFromViewport();
			}
			else {
				service.setHoldingViewport(false);
			}
		}
		final MModeWorkspaceController wsController = (MModeWorkspaceController) WorkspaceController
		        .getModeExtension(Controller.getCurrentModeController());
		if (wsController != null) {
			wsController.selectSideTab("workspace");
		}
	}
}
