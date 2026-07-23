/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2008 Joerg Mueller, Daniel Polansky, Christian Foltin, Dimitry Polivaev
 *
 *  This file is created by Dimitry Polivaev in 2008.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.freeplane.main.application;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.freeplane.core.resources.IFreeplanePropertyListener;
import org.freeplane.core.resources.ResourceBundles;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.Compat;
import org.freeplane.core.util.FileUtils;
import org.freeplane.core.util.FreeplaneVersion;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.UserProfileDataMigration;
import org.freeplane.features.filter.FilterController;

/**
 * @author Dimitry Polivaev
 */
public class ApplicationResourceController extends ResourceController {
	private static final String FREEPLANE_MAC_PROPERTIES = "/freeplane_mac.properties";
	final private File autoPropertiesFile;
	final private Properties defProps;
	private LastOpenedList lastOpened;
	final private Properties props;
	final private ClassLoader urlResourceLoader;

	/**
	 * @param controller
	 */
	public ApplicationResourceController() {
		super();
		defProps = readDefaultPreferences();
		props = readUsersPreferences(defProps);
		UserProfileDataMigration.migrateSessionStateIfNeeded(this);
		final File userDir = createUserDirectory(defProps);
		final ArrayList<URL> urls = new ArrayList<URL>(2);
		final String resourceBaseDir = getResourceBaseDir();
		if (resourceBaseDir != null) {
			try {
				final File userResourceDir = new File(userDir, "resources");
				if (userResourceDir.exists()) {
					final URL userResourceUrl = Compat.fileToUrl(userResourceDir);
					urls.add(userResourceUrl);
				}
				final File resourceDir = new File(resourceBaseDir);
				if (resourceDir.exists()) {
					final URL globalResourceUrl = Compat.fileToUrl(resourceDir);
					urls.add(globalResourceUrl);
				}
			}
			catch (final Exception e) {
				e.printStackTrace();
			}
		}
		if(urls.size() > 0)
			urlResourceLoader = new URLClassLoader(urls.toArray(new URL[]{}), null);
		else
			urlResourceLoader = null;
		setDefaultLocale(props);
		autoPropertiesFile = getUserPreferencesFile();
		addPropertyChangeListener(new IFreeplanePropertyListener() {
			public void propertyChanged(final String propertyName, final String newValue, final String oldValue) {
				if (propertyName.equals(ResourceBundles.RESOURCE_LANGUAGE)) {
					loadAnotherLanguage();
				}
			}
		});
	}

	private File createUserDirectory(final Properties pDefaultProperties) {
		final File userPropertiesFolder = new File(getFreeplaneUserDirectory());
		try {
			if (!userPropertiesFolder.exists()) {
				userPropertiesFolder.mkdirs();
			}
			return userPropertiesFolder;
		}
		catch (final Exception e) {
			e.printStackTrace();
			System.err.println("Cannot create folder for user properties and logging: '"
			        + userPropertiesFolder.getAbsolutePath() + "'");
			return null;
		}
	}

	@Override
	public String getDefaultProperty(final String key) {
		return defProps.getProperty(key);
	}

	@Override
	public String getFreeplaneUserDirectory() {
		return Compat.getApplicationUserDirectory();
	}

	public LastOpenedList getLastOpenedList() {
		return lastOpened;
	}

	@Override
	public Properties getProperties() {
		return props;
	}

	@Override
	public String getProperty(final String key) {
		return props.getProperty(key);
	}

	@Override
	public URL getResource(final String name) {
		URL resource = null;
		if (urlResourceLoader == null) {
			resource = super.getResource(name);
		}
		else {
			final String relName;
			if (name.startsWith("/")) {
				relName = name.substring(1);
			}
			else {
				relName = name;
			}
			resource = urlResourceLoader.getResource(relName);
			if (resource == null) {
				resource = super.getResource(name);
			}
		}
		if (resource == null && "/lib/freeplaneviewer.jar".equals(name)) {
			final String rootDir = new File(getResourceBaseDir()).getAbsoluteFile().getParent();
			try {
				final File try1 = new File(rootDir + "/plugins/org.freeplane.core/lib/freeplaneviewer.jar");
				if (try1.exists()) {
					resource = try1.toURL();
				}
				else {
					final File try2 = new File(rootDir + "/lib/freeplaneviewer.jar");
					if (try2.exists()) {
						resource = try2.toURL();
					}
				}
			}
			catch (final MalformedURLException e) {
				e.printStackTrace();
			}
		}
		// macOS + Java 8: ImageIcon/MediaTracker hangs on OSGi bundle:// URLs when
		// ImageFetcher NPEs. Materialize such resources to file: URLs once.
		return toImageFriendlyUrl(resource);
	}

	private static final ConcurrentHashMap<String, URL> IMAGE_FRIENDLY_URL_CACHE = new ConcurrentHashMap<String, URL>();

	private static URL toImageFriendlyUrl(final URL resource) {
		if (resource == null) {
			return null;
		}
		final String protocol = resource.getProtocol();
		if ("file".equals(protocol) || "jar".equals(protocol)) {
			return resource;
		}
		final String key = resource.toExternalForm();
		final URL cached = IMAGE_FRIENDLY_URL_CACHE.get(key);
		if (cached != null) {
			return cached;
		}
		InputStream in = null;
		OutputStream out = null;
		try {
			in = resource.openStream();
			if (in == null) {
				return resource;
			}
			String fileName = resource.getPath();
			final int slash = fileName.lastIndexOf('/');
			if (slash >= 0) {
				fileName = fileName.substring(slash + 1);
			}
			if (fileName.length() == 0) {
				fileName = "resource.bin";
			}
			// Config / text resources must track jar updates. A write-once cache made
			// mindmapmoderibbon.xml stick forever after the first launch (Ribbon edits
			// never appeared). Only materialize binary images for the macOS ImageIcon path.
			final String lowerName = fileName.toLowerCase();
			final boolean cacheToDisk = lowerName.endsWith(".png") || lowerName.endsWith(".jpg")
			        || lowerName.endsWith(".jpeg") || lowerName.endsWith(".gif") || lowerName.endsWith(".bmp")
			        || lowerName.endsWith(".ico");
			if (!cacheToDisk) {
				return resource;
			}
			final File cacheDir = new File(Compat.getApplicationUserDirectory(), "resource-cache");
			if (!cacheDir.exists()) {
				cacheDir.mkdirs();
			}
			final File outFile = new File(cacheDir, Integer.toHexString(key.hashCode()) + "_" + fileName);
			if (!outFile.exists() || outFile.length() == 0) {
				out = new FileOutputStream(outFile);
				final byte[] buf = new byte[8192];
				int n;
				while ((n = in.read(buf)) >= 0) {
					out.write(buf, 0, n);
				}
				out.close();
				out = null;
			}
			final URL fileUrl = outFile.toURI().toURL();
			IMAGE_FRIENDLY_URL_CACHE.put(key, fileUrl);
			return fileUrl;
		}
		catch (final Exception e) {
			return resource;
		}
		finally {
			try {
				if (in != null) {
					in.close();
				}
			}
			catch (final Exception ignored) {
			}
			try {
				if (out != null) {
					out.close();
				}
			}
			catch (final Exception ignored) {
			}
		}
	}

	@Override
	public String getResourceBaseDir() {
		return FreeplaneGUIStarter.getResourceBaseDir();
	}

	@Override
	public String getInstallationBaseDir() {
		return new File(getResourceBaseDir()).getAbsoluteFile().getParent();
    }

	public static File getUserPreferencesFile() {
		final String freeplaneDirectory = Compat.getApplicationUserDirectory();
		final File userPropertiesFolder = new File(freeplaneDirectory);
		final File autoPropertiesFile = new File(userPropertiesFolder, "auto.properties");
		return autoPropertiesFile;
	}

	@Override
	public void init() {
		lastOpened = new LastOpenedList();
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			public void run() {
				try {
					saveProperties(true);
				}
				catch (final Exception e) {
					LogUtils.warn("Could not persist session on shutdown hook: " + e.getMessage());
				}
			}
		}, "Docear-session-save"));
		super.init();
	}

	private Properties readDefaultPreferences() {
		final Properties props = new Properties();
		readDefaultPreferences(props, ResourceController.FREEPLANE_PROPERTIES);
		if (Compat.isMacOsX()) {
			readDefaultPreferences(props, FREEPLANE_MAC_PROPERTIES);
		}
		final String propsLocs = props.getProperty("load_next_properties", "");
		readDefaultPreferences(props, propsLocs.split(";"));
		return props;
	}

	private void readDefaultPreferences(final Properties props, final String[] locArray) {
		for (final String loc : locArray) {
			readDefaultPreferences(props, loc);
		}
	}

	private void readDefaultPreferences(final Properties props, final String propsLoc) {
		final URL defaultPropsURL = getResource(propsLoc);
		loadProperties(props, defaultPropsURL);
	}

	private Properties readUsersPreferences(final Properties defaultPreferences) {
		final Properties auto = new Properties(defaultPreferences);
		InputStream in = null;
		try {
			final File autoPropertiesFile = getUserPreferencesFile();
			in = new FileInputStream(autoPropertiesFile);
			auto.load(in);
		}
		catch (final Exception ex) {
			System.err.println("User properties not found, new file created");
		}
		finally {
			FileUtils.silentlyClose(in);
		}
		return auto;
	}

	@Override
	public void saveProperties() {
		saveProperties(true);
	}

	/** Writes user preferences without recomputing the session map list (safe during early startup). */
	public void saveApplicationFlagsOnly() {
		saveProperties(false);
	}

	private void saveProperties(final boolean includeSessionState) {
		if (includeSessionState && lastOpened != null) {
			lastOpened.saveProperties();
		}
		OutputStream out = null;
		try {
			out = new FileOutputStream(autoPropertiesFile);
			final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(out, "8859_1");
			outputStreamWriter.write("#Freeplane ");
			outputStreamWriter.write(FreeplaneVersion.getVersion().toString());
			outputStreamWriter.write('\n');
			outputStreamWriter.flush();
			props.store(out, null);
		}
		catch (final Exception ex) {
		}
		finally {
			if (out != null) {
				try {
					out.close();
				}
				catch (final IOException e) {
				}
			}
		}
		FilterController.getCurrentFilterController().saveConditions();
	}

	/**
	 * @param pProperties
	 */
	private void setDefaultLocale(final Properties pProperties) {
		final String lang = pProperties.getProperty(ResourceBundles.RESOURCE_LANGUAGE);
		if (lang == null) {
			return;
		}
		Locale localeDef = null;
		switch (lang.length()) {
			case 2:
				localeDef = new Locale(lang);
				break;
			case 5:
				localeDef = new Locale(lang.substring(0, 1), lang.substring(3, 4));
				break;
			default:
				return;
		}
		Locale.setDefault(localeDef);
	}

	@Override
	public void setDefaultProperty(final String key, final String value) {
		defProps.setProperty(key, value);
	}

	@Override
	public void setProperty(final String key, final String value) {
		final String oldValue = getProperty(key);
		if (oldValue == value) {
			return;
		}
		if (oldValue != null && oldValue.equals(value)) {
			return;
		}
		props.setProperty(key, value);
		firePropertyChanged(key, value, oldValue);
	}
}
