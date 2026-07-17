package org.freeplane.view.swing.features.finance;

import org.freeplane.core.extension.IExtension;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.view.swing.features.reports.ReportEngine;
import org.freeplane.view.swing.features.reports.ReportNodeSpec;
import org.freeplane.view.swing.features.reports.ReportViewModel;
import org.freeplane.view.swing.features.reports.ReportViewportService;

/**
 * Shows finance report models in the main map viewport by reusing {@link ReportViewportService}.
 */
public final class FinanceViewportService implements IExtension {
	private FinanceViewportService() {
	}

	public static FinanceViewportService get() {
		final Controller controller = Controller.getCurrentController();
		if (controller == null || controller.getModeController() == null) {
			return null;
		}
		FinanceViewportService service = (FinanceViewportService) controller.getModeController()
				.getExtension(FinanceViewportService.class);
		if (service == null && controller.getModeController() instanceof MModeController) {
			service = install(controller.getModeController());
		}
		return service;
	}

	public static FinanceViewportService install(final ModeController modeController) {
		FinanceViewportService service = (FinanceViewportService) modeController
				.getExtension(FinanceViewportService.class);
		if (service != null) {
			return service;
		}
		if (modeController instanceof MModeController) {
			ReportViewportService.install((MModeController) modeController);
		}
		service = new FinanceViewportService();
		modeController.addExtension(FinanceViewportService.class, service);
		LogUtils.info("FinanceViewportService installed");
		return service;
	}

	public void show(final ReportViewModel model) {
		if (model == null) {
			return;
		}
		final ReportViewportService reportService = ReportViewportService.get();
		if (reportService == null) {
			LogUtils.warn("FinanceViewportService: ReportViewportService unavailable");
			return;
		}
		ReportNodeSpec tree = null;
		try {
			tree = ReportEngine.toTree(model);
		}
		catch (Exception e) {
			LogUtils.warn("FinanceViewportService: toTree failed; showing without write tree", e);
		}
		reportService.showReport(model, tree);
	}

	public void showReport(final String reportId, final String fromYmd, final String toYmd) {
		show(FinanceReportEngine.generateView(reportId, fromYmd, toYmd));
	}

	public void hide() {
		final ReportViewportService reportService = ReportViewportService.get();
		if (reportService != null) {
			reportService.hideFromMapViewport();
		}
	}
}
