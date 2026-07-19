package org.docear.plugin.core;

import java.net.URL;
import java.util.Collection;

import org.freeplane.core.resources.IFreeplanePropertyListener;
import org.freeplane.core.resources.OptionPanelController;
import org.freeplane.core.resources.OptionPanelController.PropertyLoadListener;
import org.freeplane.core.resources.ResourceBundles;
import org.freeplane.core.resources.components.IPropertyControl;
import org.freeplane.features.mode.Controller;

public abstract class ALanguageController {
	private static final String DEFAULT_LANGUAGE = "en";
	
	public ALanguageController() {
		setLanguage();
		
		final OptionPanelController optionController = Controller.getCurrentController().getOptionPanelController();
		
		optionController.addPropertyLoadListener(new PropertyLoadListener() {			
			public void propertiesLoaded(Collection<IPropertyControl> properties) {
				setLanguage();
			}
		});
		
		Controller.getCurrentController().getResourceController().addPropertyChangeListener(new IFreeplanePropertyListener() {
			
			public void propertyChanged(String propertyName, String newValue, String oldValue) {
				if(propertyName.equalsIgnoreCase("language")){
					setLanguage();
				}
			}
		});
	}

	public void setLanguage() {
		ResourceBundles resBundle = ((ResourceBundles)Controller.getCurrentController().getResourceController().getResources());
		String lang = resBundle.getLanguageCode();
		if (lang == null || lang.equals(ResourceBundles.LANGUAGE_AUTOMATIC)) {
			lang = DEFAULT_LANGUAGE;
		}

		URL res = resolveTranslationUrl(lang);
		if (res == null) {
			return;
		}
		resBundle.addResources(resBundle.getLanguageCode(), res);
	}

	/** Prefer exact locale (zh_CN), then language prefix (zh), then English. */
	private URL resolveTranslationUrl(final String lang) {
		URL res = getClass().getResource("/translations/Resources_" + lang + ".properties");
		if (res != null) {
			return res;
		}
		final int underscore = lang.indexOf('_');
		if (underscore > 0) {
			res = getClass().getResource("/translations/Resources_" + lang.substring(0, underscore) + ".properties");
			if (res != null) {
				return res;
			}
		}
		if (!DEFAULT_LANGUAGE.equals(lang)) {
			return getClass().getResource("/translations/Resources_" + DEFAULT_LANGUAGE + ".properties");
		}
		return null;
	}
	
	
}
