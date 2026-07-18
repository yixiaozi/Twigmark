package org.freeplane.view.swing.features.finance;

import org.freeplane.core.extension.IExtension;
import org.freeplane.features.map.NodeModel;

/**
 * Personal-finance metadata stored on a mind-map node (hidden XML attrs).
 */
public final class FinanceExtension implements IExtension {
	private String kind = "";
	private long amountCents;
	private String currency = "";
	private String date = "";
	private String flow = "";
	private String period = "";
	private String catId = "";
	private String accountId = "";
	private String accountTo = "";
	private String cycle = "";
	private String status = "";
	private String merchant = "";
	private String note = "";
	private String next = "";
	private String expires = "";

	public static FinanceExtension getExtension(final NodeModel node) {
		return node == null ? null : (FinanceExtension) node.getExtension(FinanceExtension.class);
	}

	public static FinanceExtension getOrCreateExtension(final NodeModel node) {
		FinanceExtension extension = getExtension(node);
		if (extension == null) {
			extension = new FinanceExtension();
			node.addExtension(extension);
		}
		return extension;
	}

	public boolean isEmpty() {
		return kind == null || kind.trim().length() == 0;
	}

	public FinanceExtension copy() {
		final FinanceExtension copy = new FinanceExtension();
		copy.apply(this);
		return copy;
	}

	public void apply(final FinanceExtension source) {
		if (source == null) {
			kind = "";
			amountCents = 0L;
			currency = "";
			date = "";
			flow = "";
			period = "";
			catId = "";
		accountId = "";
		accountTo = "";
		cycle = "";
		status = "";
		merchant = "";
		note = "";
		next = "";
		expires = "";
		return;
		}
		kind = nullToEmpty(source.kind);
		amountCents = source.amountCents;
		currency = nullToEmpty(source.currency);
		date = nullToEmpty(source.date);
		flow = nullToEmpty(source.flow);
		period = nullToEmpty(source.period);
		catId = nullToEmpty(source.catId);
		accountId = nullToEmpty(source.accountId);
		accountTo = nullToEmpty(source.accountTo);
		cycle = nullToEmpty(source.cycle);
		status = nullToEmpty(source.status);
		merchant = nullToEmpty(source.merchant);
		note = nullToEmpty(source.note);
		next = nullToEmpty(source.next);
		expires = nullToEmpty(source.expires);
	}

	private static String nullToEmpty(final String value) {
		return value == null ? "" : value;
	}

	public String getKind() {
		return kind == null ? "" : kind;
	}

	public void setKind(final String kind) {
		this.kind = kind == null ? "" : kind;
	}

	public long getAmountCents() {
		return amountCents;
	}

	public void setAmountCents(final long amountCents) {
		this.amountCents = amountCents;
	}

	/** Amount as cents string for XML. */
	public String getAmountCentsString() {
		return Long.toString(amountCents);
	}

	public void setAmountCentsString(final String value) {
		this.amountCents = FinanceAttributes.parseLong(value, 0L);
	}

	public String getCurrency() {
		return currency == null ? "" : currency;
	}

	public void setCurrency(final String currency) {
		this.currency = currency == null ? "" : currency;
	}

	public String getDate() {
		return date == null ? "" : date;
	}

	public void setDate(final String date) {
		this.date = date == null ? "" : date;
	}

	public String getFlow() {
		return flow == null ? "" : flow;
	}

	public void setFlow(final String flow) {
		this.flow = flow == null ? "" : flow;
	}

	public String getPeriod() {
		return period == null ? "" : period;
	}

	public void setPeriod(final String period) {
		this.period = period == null ? "" : period;
	}

	public String getCatId() {
		return catId == null ? "" : catId;
	}

	public void setCatId(final String catId) {
		this.catId = catId == null ? "" : catId;
	}

	public String getAccountId() {
		return accountId == null ? "" : accountId;
	}

	public void setAccountId(final String accountId) {
		this.accountId = accountId == null ? "" : accountId;
	}

	public String getAccountTo() {
		return accountTo == null ? "" : accountTo;
	}

	public void setAccountTo(final String accountTo) {
		this.accountTo = accountTo == null ? "" : accountTo;
	}

	public String getCycle() {
		return cycle == null ? "" : cycle;
	}

	public void setCycle(final String cycle) {
		this.cycle = cycle == null ? "" : cycle;
	}

	public String getStatus() {
		return status == null ? "" : status;
	}

	public void setStatus(final String status) {
		this.status = status == null ? "" : status;
	}

	public String getMerchant() {
		return merchant == null ? "" : merchant;
	}

	public void setMerchant(final String merchant) {
		this.merchant = merchant == null ? "" : merchant;
	}

	public String getNote() {
		return note == null ? "" : note;
	}

	public void setNote(final String note) {
		this.note = note == null ? "" : note;
	}

	public String getNext() {
		return next == null ? "" : next;
	}

	public void setNext(final String next) {
		this.next = next == null ? "" : next;
	}

	public String getExpires() {
		return expires == null ? "" : expires;
	}

	public void setExpires(final String expires) {
		this.expires = expires == null ? "" : expires;
	}
}
