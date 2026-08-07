package org.freeplane.features.help;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.TextUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Maps action keys to user-facing categories (节点 / 图标 / 链接 …)
 * derived from {@code mindmapmoderibbon.xml} task/band structure.
 */
final class HotKeyCategoryResolver {

	private static final Map CACHE = load();

	private HotKeyCategoryResolver() {
	}

	static String categoryFor(final String actionKey) {
		if (actionKey == null) {
			return other();
		}
		final String cat = (String) CACHE.get(actionKey);
		return cat == null ? other() : cat;
	}

	static Map all() {
		return Collections.unmodifiableMap(CACHE);
	}

	private static String other() {
		return TextUtils.getText("hot_keys_editor.category_other");
	}

	private static Map load() {
		final Map map = new HashMap();
		InputStream in = null;
		try {
			in = HotKeyCategoryResolver.class.getResourceAsStream("/xml/mindmapmoderibbon.xml");
			if (in == null) {
				return map;
			}
			final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
			final NodeList tasks = doc.getElementsByTagName("ribbon_task");
			for (int t = 0; t < tasks.getLength(); t++) {
				final Element task = (Element) tasks.item(t);
				final String taskName = task.getAttribute("name");
				walkBands(task, taskName, map);
			}
		}
		catch (Exception e) {
			LogUtils.warn("HotKeyCategoryResolver: " + e.getMessage());
		}
		finally {
			if (in != null) {
				try {
					in.close();
				}
				catch (Exception e) {
				}
			}
		}
		return map;
	}

	private static void walkBands(final Element parent, final String taskName, final Map map) {
		final NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			final Node n = children.item(i);
			if (n.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			final Element el = (Element) n;
			final String tag = el.getTagName();
			if ("ribbon_band".equals(tag)) {
				final String bandName = el.getAttribute("name");
				final String category = resolveCategory(taskName, bandName);
				collectActions(el, category, map);
			}
			else if ("ribbon_contributor".equals(tag)) {
				// dynamic contributors — leave uncategorized
			}
		}
	}

	private static void collectActions(final Element parent, final String category, final Map map) {
		final NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			final Node n = children.item(i);
			if (n.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			final Element el = (Element) n;
			if (!"ribbon_action".equals(el.getTagName())) {
				continue;
			}
			final String action = el.getAttribute("action");
			if (action != null && action.length() > 0) {
				// Later / more specific task wins over home duplicates.
				map.put(action, category);
			}
			collectActions(el, category, map);
		}
	}

	/**
	 * Prefer concrete feature names the user asked for (节点 / 图标 / 链接),
	 * not the generic 「其他 / Ribbon」 bucket.
	 */
	static String resolveCategory(final String taskName, final String bandName) {
		if ("icons".equals(bandName)) {
			return TextUtils.getText("ribbon.band.icons");
		}
		if ("links".equals(bandName)) {
			return TextUtils.getText("ribbon.band.links");
		}
		if ("images".equals(bandName)) {
			return TextUtils.getText("ribbon.band.images");
		}
		if ("notes".equals(bandName)) {
			return TextUtils.getText("ribbon.band.notes");
		}
		if ("cloud".equals(bandName)) {
			return TextUtils.getText("ribbon.band.cloud");
		}
		if ("nodes".equals(taskName) || "nodes".equals(bandName) || "add_node".equals(bandName)
		        || "edit_node".equals(bandName) || "node_details".equals(bandName)
		        || "node_settings".equals(bandName)) {
			return TextUtils.getText("ribbon.nodes");
		}
		if ("basics".equals(bandName) || "file_management".equals(bandName)) {
			return TextUtils.getText("hot_keys_editor.category_file");
		}
		if ("formatting".equals(taskName) || "font".equals(bandName) || "manage_styles".equals(bandName)
		        || "EdgeProperties".equals(bandName)) {
			return TextUtils.getText("ribbon.formatting");
		}
		if ("view".equals(taskName) || "view_mode".equals(bandName) || "elements".equals(bandName)
		        || "toolbars".equals(bandName) || "tooltips".equals(bandName)) {
			return TextUtils.getText("ribbon.view");
		}
		if ("search_and_filter".equals(taskName) || "filter".equals(bandName) || "navigate_main".equals(bandName)
		        || "navigate_nodes".equals(bandName) || "navigate_select".equals(bandName)
		        || "goto".equals(bandName)) {
			return TextUtils.getText("hot_keys_editor.category_nav");
		}
		if ("tools_and_settings".equals(taskName) || "tools_misc".equals(bandName) || "help_misc".equals(bandName)
		        || "add_ons".equals(bandName)) {
			return TextUtils.getText("hot_keys_editor.category_tools");
		}
		if ("home".equals(taskName)) {
			return TextUtils.getText("ribbon.home");
		}
		if ("resources".equals(taskName)) {
			return TextUtils.getText("ribbon.resources");
		}
		final String bandLabel = TextUtils.getText("ribbon.band." + bandName, "");
		if (bandLabel != null && bandLabel.length() > 0 && !bandLabel.startsWith("ribbon.band.")) {
			return bandLabel;
		}
		final String taskLabel = TextUtils.getText("ribbon." + taskName, "");
		if (taskLabel != null && taskLabel.length() > 0 && !taskLabel.startsWith("ribbon.")) {
			return taskLabel;
		}
		return other();
	}
}
