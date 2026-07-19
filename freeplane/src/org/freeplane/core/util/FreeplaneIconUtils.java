package org.freeplane.core.util;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.features.icon.IconController;
import org.freeplane.features.icon.MindIcon;
import org.freeplane.features.icon.factory.MindIconFactory;
import org.freeplane.features.icon.mindmapmode.MIconController;

/** utility methods to access Freeplane's (builtin and user) icons. */
public class FreeplaneIconUtils {

	public static Icon createStandardIcon(String iconKey) {
        return MindIconFactory.create(iconKey).getIcon();
    }

	/** lists all icons that are available in the icon selection dialog. This may include user icons
	 * if there are some installed. */
	public static List<String> listStandardIconKeys() {
		// the source of this list is the property "icons.list" in freeplane.properties
		ArrayList<String> result = new ArrayList<String>();
		final MIconController mIconController = (MIconController) IconController.getController();
		for (MindIcon mindIcon : mIconController.getMindIcons())
			result.add(mindIcon.getName());
		return result;
	}

	public static ImageIcon createImageIcon(final String resourcePath) {
		final URL url = ResourceController.getResourceController().getResource(resourcePath);
		final ImageIcon icon = AFreeplaneAction.loadIconSafely(url, resourcePath);
		return icon != null ? icon : new ImageIcon();
	}
	
	public static ImageIcon createImageIconByResourceKey(final String resourceKey) {
		final ResourceController resourceController = ResourceController.getResourceController();
		final String resourcePath = resourceController.getProperty(resourceKey);
		final URL url = resourceController.getResource(resourcePath);
		final ImageIcon icon = AFreeplaneAction.loadIconSafely(url, resourcePath);
		return icon != null ? icon : new ImageIcon();
	}
}
