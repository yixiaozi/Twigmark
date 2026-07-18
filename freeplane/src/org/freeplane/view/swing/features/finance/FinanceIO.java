package org.freeplane.view.swing.features.finance;

import org.freeplane.core.io.IAttributeHandler;
import org.freeplane.core.io.IAttributeWriter;
import org.freeplane.core.io.ITreeWriter;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.NodeBuilder;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.ModeController;

final class FinanceIO {
	private FinanceIO() {
	}

	static void install(final ModeController modeController) {
		final MapController mapController = modeController.getMapController();
		add(mapController, FinanceAttributes.FINANCE_KIND, new Setter() {
			public void set(final FinanceExtension e, final String v) {
				e.setKind(v);
			}
		});
		add(mapController, FinanceAttributes.FINANCE_AMOUNT, new Setter() {
			public void set(final FinanceExtension e, final String v) {
				e.setAmountCentsString(v);
			}
		});
		add(mapController, FinanceAttributes.FINANCE_CURRENCY, new Setter() {
			public void set(final FinanceExtension e, final String v) {
				e.setCurrency(v);
			}
		});
		add(mapController, FinanceAttributes.FINANCE_DATE, new Setter() {
			public void set(final FinanceExtension e, final String v) {
				e.setDate(v);
			}
		});
		add(mapController, FinanceAttributes.FINANCE_FLOW, new Setter() {
			public void set(final FinanceExtension e, final String v) {
				e.setFlow(v);
			}
		});
		add(mapController, FinanceAttributes.FINANCE_PERIOD, new Setter() {
			public void set(final FinanceExtension e, final String v) {
				e.setPeriod(v);
			}
		});
		add(mapController, FinanceAttributes.FINANCE_CAT_ID, new Setter() {
			public void set(final FinanceExtension e, final String v) {
				e.setCatId(v);
			}
		});
		add(mapController, FinanceAttributes.FINANCE_ACCOUNT_ID, new Setter() {
			public void set(final FinanceExtension e, final String v) {
				e.setAccountId(v);
			}
		});
		add(mapController, FinanceAttributes.FINANCE_ACCOUNT_TO, new Setter() {
			public void set(final FinanceExtension e, final String v) {
				e.setAccountTo(v);
			}
		});
		add(mapController, FinanceAttributes.FINANCE_CYCLE, new Setter() {
			public void set(final FinanceExtension e, final String v) {
				e.setCycle(v);
			}
		});
		add(mapController, FinanceAttributes.FINANCE_STATUS, new Setter() {
			public void set(final FinanceExtension e, final String v) {
				e.setStatus(v);
			}
		});
		add(mapController, FinanceAttributes.FINANCE_MERCHANT, new Setter() {
			public void set(final FinanceExtension e, final String v) {
				e.setMerchant(v);
			}
		});
		add(mapController, FinanceAttributes.FINANCE_NOTE, new Setter() {
			public void set(final FinanceExtension e, final String v) {
				e.setNote(v);
			}
		});
		add(mapController, FinanceAttributes.FINANCE_NEXT, new Setter() {
			public void set(final FinanceExtension e, final String v) {
				e.setNext(v);
			}
		});
		add(mapController, FinanceAttributes.FINANCE_EXPIRES, new Setter() {
			public void set(final FinanceExtension e, final String v) {
				e.setExpires(v);
			}
		});
		mapController.getWriteManager().addAttributeWriter(NodeBuilder.XML_NODE, new IAttributeWriter() {
			public void writeAttributes(final ITreeWriter writer, final Object userObject, final String tag) {
				if (!NodeBuilder.XML_NODE.equals(tag)) {
					return;
				}
				final NodeModel node = (NodeModel) userObject;
				final FinanceExtension extension = FinanceExtension.getExtension(node);
				if (extension == null || extension.isEmpty()) {
					return;
				}
				writer.addAttribute(FinanceAttributes.FINANCE_KIND, extension.getKind());
				// Always persist amount for money-bearing kinds (0 is meaningful for cleared lines).
				if (FinanceAttributes.KIND_TXN.equals(extension.getKind())
						|| FinanceAttributes.KIND_BUDGET.equals(extension.getKind())
						|| FinanceAttributes.KIND_SUBSCRIPTION.equals(extension.getKind())
						|| FinanceAttributes.KIND_COUPON.equals(extension.getKind())
						|| extension.getAmountCents() != 0L) {
					writer.addAttribute(FinanceAttributes.FINANCE_AMOUNT, extension.getAmountCentsString());
				}
				writeIfPresent(writer, FinanceAttributes.FINANCE_CURRENCY, extension.getCurrency());
				writeIfPresent(writer, FinanceAttributes.FINANCE_DATE, extension.getDate());
				writeIfPresent(writer, FinanceAttributes.FINANCE_FLOW, extension.getFlow());
				writeIfPresent(writer, FinanceAttributes.FINANCE_PERIOD, extension.getPeriod());
				writeIfPresent(writer, FinanceAttributes.FINANCE_CAT_ID, extension.getCatId());
				writeIfPresent(writer, FinanceAttributes.FINANCE_ACCOUNT_ID, extension.getAccountId());
				writeIfPresent(writer, FinanceAttributes.FINANCE_ACCOUNT_TO, extension.getAccountTo());
				writeIfPresent(writer, FinanceAttributes.FINANCE_CYCLE, extension.getCycle());
				writeIfPresent(writer, FinanceAttributes.FINANCE_STATUS, extension.getStatus());
				writeIfPresent(writer, FinanceAttributes.FINANCE_MERCHANT, extension.getMerchant());
				writeIfPresent(writer, FinanceAttributes.FINANCE_NOTE, extension.getNote());
				writeIfPresent(writer, FinanceAttributes.FINANCE_NEXT, extension.getNext());
				writeIfPresent(writer, FinanceAttributes.FINANCE_EXPIRES, extension.getExpires());
			}
		});
	}

	private static void writeIfPresent(final ITreeWriter writer, final String name, final String value) {
		if (value != null && value.length() > 0) {
			writer.addAttribute(name, value);
		}
	}

	private static void add(final MapController mapController, final String name, final Setter setter) {
		mapController.getReadManager().addAttributeHandler(NodeBuilder.XML_NODE, name, new IAttributeHandler() {
			public void setAttribute(final Object userObject, final String value) {
				setter.set(FinanceExtension.getOrCreateExtension((NodeModel) userObject), value);
			}
		});
	}

	private interface Setter {
		void set(FinanceExtension extension, String value);
	}
}
