package org.freeplane.plugin.workspace.features.nodepins;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.freeplane.core.util.HtmlUtils;
import org.freeplane.plugin.workspace.features.favorites.TagTextUtils;

public final class NodeDetailsTagUtils {

	public static final String PIN_TAG = "\u9489\u9009";
	public static final String TAG_ARCHIVED = "\u5df2\u5f52\u6863";
	public static final String[] PRESET_TAGS = {};

	private static final Pattern BRACKET_TAG_PATTERN = Pattern.compile("【([^】]+)】");
	private static final char BRACKET_OPEN = '【';
	/** Hex form used in Freeplane .mm TEXT attributes: &#x3010; */
	private static final String BRACKET_OPEN_HEX_ENTITY = "&#x3010";
	/** Decimal form: &#12304; */
	private static final String BRACKET_OPEN_DEC_ENTITY = "&#12304";

	private NodeDetailsTagUtils() {
	}

	public static Set parseAllTags(final String nodeText) {
		final LinkedHashSet tags = new LinkedHashSet();
		if (!mayContainBracketTags(nodeText)) {
			return tags;
		}
		String plain = fixMixedEncoding(nodeText);
		plain = HtmlUtils.unescapeHTMLUnicodeEntity(plain);
		plain = HtmlUtils.htmlToPlain(plain).trim();
		final Matcher matcher = BRACKET_TAG_PATTERN.matcher(plain);
		while (matcher.find()) {
			final String tag = normalizeTagName(matcher.group(1));
			if (isValidTagName(tag)) {
				tags.add(tag);
			}
		}
		return tags;
	}

	/** Fast reject before HTML/regex parsing. Handles literal and entity-encoded 【. */
	public static boolean mayContainBracketTags(final String nodeText) {
		if (nodeText == null || nodeText.length() == 0) {
			return false;
		}
		if (nodeText.indexOf(BRACKET_OPEN) >= 0) {
			return true;
		}
		final String lower = nodeText.toLowerCase();
		return lower.indexOf(BRACKET_OPEN_HEX_ENTITY) >= 0 || lower.indexOf(BRACKET_OPEN_DEC_ENTITY) >= 0;
	}

	private static String fixMixedEncoding(String text) {
		text = text.replace("&【", "【&#");
		return text;
	}

	public static Set parseUserTags(final String nodeText) {
		final LinkedHashSet tags = new LinkedHashSet(parseAllTags(nodeText));
		tags.remove(PIN_TAG);
		return tags;
	}

	public static boolean isPinnedInDetails(final String nodeText) {
		return parseAllTags(nodeText).contains(PIN_TAG);
	}

	public static boolean hasAnyManagedTag(final String nodeText) {
		if (!mayContainBracketTags(nodeText)) {
			return false;
		}
		return !parseAllTags(nodeText).isEmpty();
	}

	public static String extractNodeTitle(final String nodeText) {
		if (nodeText == null || nodeText.trim().length() == 0) {
			return "";
		}
		String plain = HtmlUtils.htmlToPlain(nodeText).trim();
		plain = HtmlUtils.unescapeHTMLUnicodeEntity(plain);
		plain = BRACKET_TAG_PATTERN.matcher(plain).replaceAll("").trim();
		return plain;
	}

	/**
	 * Ordered plain-text / tag segments as they appear in the node string.
	 * Hidden tags such as {@link #PIN_TAG} are dropped so display matches strip rules.
	 */
	public static List parseDisplaySegments(final String nodeText) {
		final ArrayList segments = new ArrayList();
		if (nodeText == null || nodeText.trim().length() == 0) {
			return segments;
		}
		String plain = fixMixedEncoding(nodeText);
		plain = HtmlUtils.unescapeHTMLUnicodeEntity(plain);
		plain = HtmlUtils.htmlToPlain(plain);
		if (plain == null) {
			return segments;
		}
		final Matcher matcher = BRACKET_TAG_PATTERN.matcher(plain);
		int last = 0;
		while (matcher.find()) {
			if (matcher.start() > last) {
				final String text = plain.substring(last, matcher.start());
				if (text.length() > 0) {
					segments.add(TagDisplaySegment.text(text));
				}
			}
			final String tag = normalizeTagName(matcher.group(1));
			if (isValidTagName(tag) && !PIN_TAG.equals(tag)) {
				segments.add(TagDisplaySegment.tag(tag));
			}
			last = matcher.end();
		}
		if (last < plain.length()) {
			final String text = plain.substring(last);
			if (text.length() > 0) {
				segments.add(TagDisplaySegment.text(text));
			}
		}
		return segments;
	}

	public static String buildNodeText(final String nodeTitle, final Set tags) {
		final StringBuilder builder = new StringBuilder();
		final String title = nodeTitle == null ? "" : HtmlUtils.htmlToPlain(nodeTitle).trim();
		if (title.length() > 0) {
			builder.append(title);
		}
		if (tags != null && !tags.isEmpty()) {
			for (final Iterator it = tags.iterator(); it.hasNext();) {
				final String tag = (String) it.next();
				if (tag == null || tag.trim().length() == 0 || !isValidTagName(tag)) {
					continue;
				}
				builder.append("【").append(tag.trim()).append("】");
			}
		}
		return builder.toString();
	}

	public static String formatHashtagLine(final Set tags) {
		final StringBuilder builder = new StringBuilder();
		for (final Iterator it = tags.iterator(); it.hasNext();) {
			final String tag = (String) it.next();
			if (tag == null || tag.trim().length() == 0 || !isValidTagName(tag)) {
				continue;
			}
			if (builder.length() > 0) {
				builder.append(' ');
			}
			builder.append('#').append(tag.trim());
		}
		return builder.toString();
	}

	public static Set parseTagNamesFromText(final String value) {
		final LinkedHashSet tags = new LinkedHashSet();
		if (value == null) {
			return tags;
		}
		final String[] parts = TagTextUtils.normalizeSeparators(value).split(",");
		for (int i = 0; i < parts.length; i++) {
			String trimmed = parts[i].trim();
			if (trimmed.startsWith("#")) {
				trimmed = trimmed.substring(1).trim();
			}
			trimmed = normalizeTagName(trimmed);
			if (isValidTagName(trimmed)) {
				tags.add(trimmed);
			}
		}
		return tags;
	}

	public static String joinTagNames(final Set tags) {
		final StringBuilder builder = new StringBuilder();
		for (final Iterator it = tags.iterator(); it.hasNext();) {
			final String tag = (String) it.next();
			if (PIN_TAG.equals(tag) || !isValidTagName(tag)) {
				continue;
			}
			if (builder.length() > 0) {
				builder.append(", ");
			}
			builder.append(tag);
		}
		return builder.toString();
	}

	public static boolean isValidTagName(final String tag) {
		if (tag == null) {
			return false;
		}
		final String trimmed = tag.trim();
		if (trimmed.length() == 0) {
			return false;
		}
		return true;
	}

	static String normalizeTagName(final String raw) {
		if (raw == null) {
			return "";
		}
		return HtmlUtils.unescapeHTMLUnicodeEntity(raw.trim());
	}

	public static String stripBracketTags(final String text) {
		if (text == null || text.trim().length() == 0) {
			return text;
		}
		return BRACKET_TAG_PATTERN.matcher(text).replaceAll("").trim();
	}

	public static String appendBracketTags(final String text, final Set tags) {
		if (text == null || text.trim().length() == 0) {
			return "";
		}
		final StringBuilder builder = new StringBuilder(text);
		if (tags != null && !tags.isEmpty()) {
			for (final Iterator it = tags.iterator(); it.hasNext();) {
				final String tag = (String) it.next();
				if (tag == null || tag.trim().length() == 0 || !isValidTagName(tag)) {
					continue;
				}
				builder.append("【").append(tag.trim()).append("】");
			}
		}
		return builder.toString();
	}
}
