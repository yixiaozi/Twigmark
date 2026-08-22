package org.docear.plugin.mermaid;

import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.freeplane.core.util.LogUtils;

/**
 * Resolves citation identifiers (bib key / DOI / ISBN / URL) to display metadata.
 * Prefers the local JabRef library (via reflection so mermaid does not hard-depend
 * on bibtex); falls back to Crossref for DOI.
 */
final class CitationResolve {

	static final class Meta {
		final String id;
		final String title;
		final String authors;
		final String year;
		final String venue;
		final String source; // "jabref" | "crossref" | "raw"

		Meta(final String id, final String title, final String authors, final String year, final String venue,
				final String source) {
			this.id = id != null ? id : "";
			this.title = title != null ? title : "";
			this.authors = authors != null ? authors : "";
			this.year = year != null ? year : "";
			this.venue = venue != null ? venue : "";
			this.source = source != null ? source : "raw";
		}
	}

	private static final Pattern DOI = Pattern.compile("(?i)\\b(10\\.\\d{4,9}/[-._;()/:A-Z0-9]+)\\b");
	private static final Pattern ISBN = Pattern.compile("(?i)\\b(?:isbn[:\\s]*)?((?:97[89][- ]?)?(?:\\d[- ]?){9}[\\dXx])\\b");
	private static final Pattern URL = Pattern.compile("(?i)^(https?://\\S+)$");

	private CitationResolve() {
	}

	static Meta resolve(final String rawBody) {
		final String body = rawBody != null ? rawBody.trim() : "";
		if (body.length() == 0) {
			return new Meta("", "(empty citation)", "", "", "", "raw");
		}
		final String firstLine = firstNonEmptyLine(body);

		Meta local = resolveLocal(firstLine);
		if (local != null) {
			return local;
		}

		final Matcher doi = DOI.matcher(firstLine);
		if (doi.find()) {
			final String doiStr = doi.group(1);
			local = resolveLocalByField("doi", doiStr);
			if (local != null) {
				return local;
			}
			final Meta remote = resolveCrossref(doiStr);
			if (remote != null) {
				return remote;
			}
			return new Meta(doiStr, doiStr, "", "", "", "raw");
		}

		final Matcher isbn = ISBN.matcher(firstLine.replace("-", "").replace(" ", ""));
		if (isbn.find() || firstLine.toLowerCase().startsWith("isbn")) {
			final String isbnStr = normalizeIsbn(firstLine);
			local = resolveLocalByField("isbn", isbnStr);
			if (local != null) {
				return local;
			}
			return new Meta(isbnStr, "ISBN " + isbnStr, "", "", "", "raw");
		}

		if (URL.matcher(firstLine).matches()) {
			local = resolveLocalByField("url", firstLine);
			if (local != null) {
				return local;
			}
			return new Meta(firstLine, firstLine, "", "", "", "raw");
		}

		return new Meta(firstLine, firstLine, "", "", "not in library", "raw");
	}

	private static String firstNonEmptyLine(final String body) {
		final String[] lines = body.split("\n");
		for (int i = 0; i < lines.length; i++) {
			final String t = lines[i].trim();
			if (t.length() > 0) {
				return t;
			}
		}
		return body;
	}

	private static String normalizeIsbn(final String raw) {
		String s = raw.trim();
		if (s.toLowerCase().startsWith("isbn")) {
			s = s.substring(4).replaceFirst("^[:\\s]+", "");
		}
		return s.replace("-", "").replace(" ", "");
	}

	private static Meta resolveLocal(final String keyOrId) {
		try {
			final Object database = jabrefDatabase();
			if (database == null) {
				return null;
			}
			final Method getByKey = database.getClass().getMethod("getEntryByKey", String.class);
			final Object entry = getByKey.invoke(database, keyOrId);
			if (entry != null) {
				return fromEntry(entry, keyOrId, "jabref");
			}
		}
		catch (Throwable t) {
			LogUtils.warn("Citation: local key lookup failed: " + t.getMessage());
		}
		return null;
	}

	private static Meta resolveLocalByField(final String field, final String value) {
		if (value == null || value.length() == 0) {
			return null;
		}
		try {
			final Object database = jabrefDatabase();
			if (database == null) {
				return null;
			}
			final Method getEntries = database.getClass().getMethod("getEntries");
			final Object entriesObj = getEntries.invoke(database);
			if (!(entriesObj instanceof Collection)) {
				return null;
			}
			final String needle = normalizeField(field, value);
			final Iterator it = ((Collection) entriesObj).iterator();
			while (it.hasNext()) {
				final Object entry = it.next();
				final String fieldVal = entryField(entry, field);
				if (fieldVal != null && normalizeField(field, fieldVal).equalsIgnoreCase(needle)) {
					final String key = entryField(entry, "bibtexkey");
					return fromEntry(entry, key != null ? key : value, "jabref");
				}
			}
		}
		catch (Throwable t) {
			LogUtils.warn("Citation: local field scan failed: " + t.getMessage());
		}
		return null;
	}

	private static String normalizeField(final String field, final String value) {
		if ("doi".equalsIgnoreCase(field)) {
			String v = value.trim();
			final int idx = v.toLowerCase().indexOf("10.");
			if (idx >= 0) {
				v = v.substring(idx);
			}
			return v;
		}
		if ("isbn".equalsIgnoreCase(field)) {
			return value.replace("-", "").replace(" ", "");
		}
		return value.trim();
	}

	private static Object jabrefDatabase() throws Exception {
		final Class<?> refs = Class.forName("org.docear.plugin.bibtex.ReferencesController");
		final Object controller = refs.getMethod("getController").invoke(null);
		if (controller == null) {
			return null;
		}
		final Object wrapper = controller.getClass().getMethod("getJabrefWrapper").invoke(controller);
		if (wrapper == null) {
			return null;
		}
		return wrapper.getClass().getMethod("getDatabase").invoke(wrapper);
	}

	private static Meta fromEntry(final Object entry, final String id, final String source) {
		final String title = entryField(entry, "title");
		final String authors = entryField(entry, "author");
		String year = entryField(entry, "year");
		if (year == null || year.length() == 0) {
			year = entryField(entry, "date");
		}
		String venue = entryField(entry, "journal");
		if (venue == null || venue.length() == 0) {
			venue = entryField(entry, "booktitle");
		}
		if (venue == null || venue.length() == 0) {
			venue = entryField(entry, "publisher");
		}
		final String key = entryField(entry, "bibtexkey");
		return new Meta(key != null && key.length() > 0 ? key : id, cleanLatex(title), cleanAuthors(authors),
		        year != null ? year : "", venue != null ? venue : "", source);
	}

	private static String entryField(final Object entry, final String name) {
		try {
			final Method getField = entry.getClass().getMethod("getField", String.class);
			final Object v = getField.invoke(entry, name);
			return v != null ? v.toString() : null;
		}
		catch (Throwable t) {
			return null;
		}
	}

	private static String cleanLatex(final String s) {
		if (s == null) {
			return "";
		}
		return s.replace("{", "").replace("}", "").replace("\\&", "&").trim();
	}

	private static String cleanAuthors(final String s) {
		if (s == null || s.length() == 0) {
			return "";
		}
		String a = s.replace(" and ", ", ");
		if (a.length() > 80) {
			a = a.substring(0, 77) + "…";
		}
		return a;
	}

	private static Meta resolveCrossref(final String doi) {
		HttpURLConnection conn = null;
		try {
			final String encoded = URLEncoder.encode(doi, "UTF-8").replace("%2F", "/");
			final URL url = new URL("https://api.crossref.org/works/" + encoded);
			conn = (HttpURLConnection) url.openConnection();
			conn.setConnectTimeout(4000);
			conn.setReadTimeout(6000);
			conn.setRequestProperty("Accept", "application/json");
			conn.setRequestProperty("User-Agent", "TwigmarkCitation/1.0 (mailto:support@local)");
			if (conn.getResponseCode() != 200) {
				return null;
			}
			final java.io.InputStream in = conn.getInputStream();
			final StringBuilder sb = new StringBuilder();
			final byte[] buf = new byte[4096];
			int n;
			while ((n = in.read(buf)) >= 0) {
				sb.append(new String(buf, 0, n, "UTF-8"));
				if (sb.length() > 200000) {
					break;
				}
			}
			in.close();
			return parseCrossrefJson(doi, sb.toString());
		}
		catch (Throwable t) {
			LogUtils.warn("Citation: Crossref failed for " + doi + ": " + t.getMessage());
			return null;
		}
		finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
	}

	private static Meta parseCrossrefJson(final String doi, final String json) {
		final String title = firstJsonString(json, "\"title\"");
		final String year = extractYear(json);
		final String authors = extractAuthors(json);
		final String venue = firstJsonString(json, "\"container-title\"");
		if ((title == null || title.length() == 0) && (authors == null || authors.length() == 0)) {
			return null;
		}
		return new Meta(doi, title != null ? title : doi, authors != null ? authors : "", year != null ? year : "",
		        venue != null ? venue : "", "crossref");
	}

	private static String firstJsonString(final String json, final String key) {
		final int k = json.indexOf(key);
		if (k < 0) {
			return null;
		}
		final int arr = json.indexOf('[', k);
		final int colon = json.indexOf(':', k);
		if (arr > 0 && arr < colon + 8) {
			return jsonStringAt(json, arr + 1);
		}
		return jsonStringAt(json, colon + 1);
	}

	private static String jsonStringAt(final String json, int from) {
		while (from < json.length() && Character.isWhitespace(json.charAt(from))) {
			from++;
		}
		if (from >= json.length() || json.charAt(from) != '"') {
			return null;
		}
		final StringBuilder sb = new StringBuilder();
		boolean esc = false;
		for (int i = from + 1; i < json.length(); i++) {
			final char c = json.charAt(i);
			if (esc) {
				sb.append(c);
				esc = false;
				continue;
			}
			if (c == '\\') {
				esc = true;
				continue;
			}
			if (c == '"') {
				return sb.toString();
			}
			sb.append(c);
		}
		return sb.toString();
	}

	private static String extractYear(final String json) {
		final Matcher m = Pattern.compile("\"date-parts\"\\s*:\\s*\\[\\s*\\[\\s*(\\d{4})").matcher(json);
		if (m.find()) {
			return m.group(1);
		}
		return null;
	}

	private static String extractAuthors(final String json) {
		final StringBuilder names = new StringBuilder();
		final Matcher m = Pattern.compile("\"family\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
		int count = 0;
		while (m.find() && count < 4) {
			if (names.length() > 0) {
				names.append(", ");
			}
			names.append(m.group(1));
			count++;
		}
		if (count == 4) {
			names.append("…");
		}
		return names.toString();
	}
}
