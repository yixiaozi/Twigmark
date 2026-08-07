package org.freeplane.core.ui.ribbon;

import java.awt.Event;
import java.awt.event.KeyEvent;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.freeplane.core.ui.PlatformHotKeyGuide;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.resources.components.GrabKeyDialog;
import org.freeplane.core.resources.components.IKeystrokeValidator;
import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.IAcceleratorChangeListener;
import org.freeplane.core.ui.IEditHandler.FirstAction;
import org.freeplane.core.ui.IKeyStrokeProcessor;
import org.freeplane.core.ui.components.FreeplaneMenuBar;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.Compat;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;

public class RibbonAcceleratorManager implements IKeyStrokeProcessor, IAcceleratorChangeListener {
	
	private static final String SHORTCUT_PROPERTY_PREFIX = "ribbon.acceleratorFor.";
	
	private final Map<KeyStroke, AFreeplaneAction> accelerators = new HashMap<KeyStroke, AFreeplaneAction>();
	private final Map<String, KeyStroke> actionMap = new HashMap<String, KeyStroke>();
	private final List<IAcceleratorChangeListener> changeListeners = new ArrayList<IAcceleratorChangeListener>();
	
	private final RibbonBuilder builder;
	private IAcceleratorChangeListener acceleratorChangeListener;
	private final Properties keysetProps = new Properties();
	private final Properties defaultProps = new Properties();

	
	/***********************************************************************************
	 * CONSTRUCTORS
	 **********************************************************************************/
	
 	public RibbonAcceleratorManager(RibbonBuilder ribbonBuilder) {
 		this.builder = ribbonBuilder;
 	}
 	
	/***********************************************************************************
	 * METHODS
	 **********************************************************************************/

 	public void setAccelerator(final AFreeplaneAction action, final KeyStroke keyStroke) {
 		setAccelerator(action, keyStroke, true);
 	}

 	private void setAccelerator(final AFreeplaneAction action, final KeyStroke keyStroke, final boolean showConflictDialog) {
 		if(action == null) {
 			return;
 		}
 		if(keyStroke != null) { 		
    		final AFreeplaneAction oldAction = accelerators.put(keyStroke, action);
    		if(action == oldAction) {
    			return;
    		}
    		if (keyStroke != null && oldAction != null) {
    			final String message = TextUtils.removeTranslateComment(TextUtils.format("action_keystroke_in_use_error", keyStroke, getActionTitle(action.getKey()), getActionTitle(oldAction.getKey())));
    			if (showConflictDialog) {
    				UITools.errorMessage(message);
    			}
    			else {
    				LogUtils.warn(message);
    			}
    			accelerators.put(keyStroke, oldAction);
    			final String shortcutKey = getPropertyKey(action.getKey());
    			
    			keysetProps.setProperty(shortcutKey, "");
    			return;
    		}
 		}
		final KeyStroke removedAccelerator = removeAccelerator(action);
		if(keyStroke != null) {
			actionMap.put(action.getKey(), keyStroke);
		}
		else {
			actionMap.remove(action.getKey());
		}
		if (acceleratorChangeListener != null && (removedAccelerator != null || keyStroke != null)) {
			acceleratorChangeListener.acceleratorChanged(action, removedAccelerator, keyStroke);
		}
		fireAcceleratorChanged(action, removedAccelerator, keyStroke);
	}
 	
 	private String getActionTitle(String key) {
 		String title = TextUtils.getText(key+".text");
		if(title == null || title.isEmpty()) {
			title = key;
		}
		return TextUtils.removeTranslateComment(title);
 	}
 	
 	public void setDefaultAccelerator(final String itemKey, final String accelerator) {
		final String shortcutKey = getPropertyKey(itemKey);
		// Always remember the factory/ribbon default for the hot-key editor,
		// even when the user already remapped the live binding.
		if (!defaultProps.containsKey(shortcutKey)) {
			defaultProps.setProperty(shortcutKey, accelerator);
		}
		// User presets are loaded into keysetProps before the ribbon is built —
		// do not clobber them with XML defaults.
		if (keysetProps.containsKey(shortcutKey)) {
			return;
		}
		final KeyStroke ks = KeyStroke.getKeyStroke(accelerator);
		final AFreeplaneAction action = builder.getMode().getAction(itemKey);
		setAccelerator(action, ks, false);
	}
 	
 	public KeyStroke removeAccelerator(final AFreeplaneAction action) {
 		if(action == null) {
 			return null;
 		}
		final KeyStroke oldAccelerator = actionMap.get(action.getKey());
		if (oldAccelerator != null) {
			final AFreeplaneAction mappedAction = accelerators.get(oldAccelerator);
			if (mappedAction != null && !action.equals(mappedAction)) {
				LogUtils.warn("Accelerator map inconsistency for " + oldAccelerator
				        + ": expected " + action.getKey() + ", found " + mappedAction.getKey()
				        + " - clearing stale mapping");
				actionMap.remove(mappedAction.getKey());
			}
			accelerators.remove(oldAccelerator);
			actionMap.remove(action.getKey());
		}
		return oldAccelerator;
	}
 	
 	public void setAcceleratorChangeListener(final IAcceleratorChangeListener acceleratorChangeListener) {
		this.acceleratorChangeListener = acceleratorChangeListener;
	}
 	
 	public String getPropertyKey(final String key) {
		return SHORTCUT_PROPERTY_PREFIX + builder.getMode().getModeName() + "/" + key;
	}
 	
 	public KeyStroke getAccelerator(String actionKey) {
 		KeyStroke ks = actionMap.get(actionKey);
 		return ks;
 	}

	/** Snapshot of actionKey → accelerator (for the shortcuts editor). */
	public Map<String, KeyStroke> getActionAccelerators() {
		return new HashMap<String, KeyStroke>(actionMap);
	}

	/**
	 * Factory / ribbon default stroke for an action (before user remaps).
	 * Used by the hot-key editor 「默认快捷键」column.
	 */
	public KeyStroke getDefaultAccelerator(final String actionKey) {
		if (actionKey == null) {
			return null;
		}
		final String shortcutKey = getPropertyKey(actionKey);
		final String raw = defaultProps.getProperty(shortcutKey);
		if (raw == null || raw.length() == 0) {
			return null;
		}
		return parseKeyStroke(raw);
	}

	/** Action keys that have a recorded default stroke (ribbon XML or default properties). */
	public java.util.Set getDefaultAcceleratorActionKeys() {
		final java.util.Set keys = new java.util.HashSet();
		final String prefix = SHORTCUT_PROPERTY_PREFIX + builder.getMode().getModeName() + "/";
		for (final Iterator it = defaultProps.keySet().iterator(); it.hasNext();) {
			final String propKey = String.valueOf(it.next());
			if (!propKey.startsWith(prefix)) {
				continue;
			}
			final String value = defaultProps.getProperty(propKey);
			if (value == null || value.length() == 0) {
				continue;
			}
			keys.add(propKey.substring(prefix.length()));
		}
		return keys;
	}

	/**
	 * Clear accelerator for an action and persist. Used by the shortcuts editor.
	 */
	public void clearAccelerator(final AFreeplaneAction action) {
		if (action == null) {
			return;
		}
		final String shortcutKey = getPropertyKey(action.getKey());
		setAccelerator(action, null);
		keysetProps.setProperty(shortcutKey, "");
		try {
			if (!getPresetsFile().exists()) {
				getPresetsFile().createNewFile();
			}
			storeAcceleratorPreset(new FileOutputStream(getPresetsFile()));
		}
		catch (IOException e) {
			LogUtils.warn("Could not persist cleared accelerator: " + e.getMessage());
		}
	}
 	
 	public void addAcceleratorChangeListener(IAcceleratorChangeListener changeListener) {
		synchronized (changeListeners) {
			if(!changeListeners.contains(changeListener)) {
				changeListeners.add(changeListener);
			}
		}
	}
 	
 	protected void fireAcceleratorChanged(AFreeplaneAction action, KeyStroke oldStroke, KeyStroke newStroke) {
 		synchronized (changeListeners) {
			for (IAcceleratorChangeListener listener : changeListeners) {
				listener.acceleratorChanged(action, oldStroke, newStroke);
			}
		}
	}
 	
 	private String getProperty(String key) {
 		return keysetProps.getProperty(key, defaultProps.getProperty(key, null));
 	}
 	
 	public void newAccelerator(final AFreeplaneAction action, final KeyStroke newAccelerator) {
		final String shortcutKey = getPropertyKey(action.getKey());
		final String oldShortcut = getProperty(shortcutKey);
		if (newAccelerator == null || !new KeystrokeValidator(action).isValid(newAccelerator, newAccelerator.getKeyChar())) {
			final GrabKeyDialog grabKeyDialog = new GrabKeyDialog(oldShortcut);
			final IKeystrokeValidator validator = new KeystrokeValidator(action);
			grabKeyDialog.setValidator(validator);
			grabKeyDialog.setVisible(true);
			if (grabKeyDialog.isOK()) {
				final String shortcut = grabKeyDialog.getShortcut();
				final KeyStroke accelerator = UITools.getKeyStroke(shortcut);
				setAccelerator(action, accelerator);
				keysetProps.setProperty(shortcutKey, shortcut);
				LogUtils.info("created shortcut '" + shortcut + "' for action '" + action.getKey() + "', shortcutKey '"
				+ shortcutKey + "' (" + RibbonActionContributorFactory.getActionTitle(action) + ")");
			}
		}
		else{
			if(oldShortcut != null){
				final int replace = JOptionPane.showConfirmDialog(UITools.getFrame(), oldShortcut, TextUtils.removeTranslateComment(TextUtils.getText("remove_shortcut_question")), JOptionPane.YES_NO_OPTION);
				if (replace != JOptionPane.YES_OPTION) {
					return;
				}
			}
			setAccelerator(action, newAccelerator);
			keysetProps.setProperty(shortcutKey, toString(newAccelerator));
			LogUtils.info("created shortcut '" + toString(newAccelerator) + "' for action '" + action+ "', shortcutKey '" + shortcutKey + "' (" + RibbonActionContributorFactory.getActionTitle(action) + ")");
		}
		try {
			if(!getPresetsFile().exists()) {
					getPresetsFile().createNewFile();	
			}
			storeAcceleratorPreset(new FileOutputStream(getPresetsFile()));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
 	
 	/**
	 * Per-OS preset file under {@code ribbons/} so Win / Mac / Linux can sync the same
	 * data folder without overwriting each other's shortcuts.
	 * Legacy {@code accelerator.properties} is copied once when the platform file is missing.
	 */
 	public File getPresetsFile() {
 		final File ribbonsDir = new File(ResourceController.getResourceController().getFreeplaneUserDirectory(),
 		        "ribbons");
		if (!ribbonsDir.exists()) {
			ribbonsDir.mkdirs();
		}
		final File platformFile = new File(ribbonsDir, PlatformHotKeyGuide.getAcceleratorFileName());
		if (!platformFile.exists()) {
			final File legacy = new File(ribbonsDir, "accelerator.properties");
			if (legacy.isFile()) {
				copyFileQuietly(legacy, platformFile);
				LogUtils.info("Migrated accelerator presets to " + platformFile.getName());
			}
		}
		return platformFile;
 	}

	private static void copyFileQuietly(final File from, final File to) {
		java.io.InputStream in = null;
		java.io.OutputStream out = null;
		try {
			in = new java.io.FileInputStream(from);
			out = new java.io.FileOutputStream(to);
			final byte[] buf = new byte[8192];
			int n;
			while ((n = in.read(buf)) >= 0) {
				out.write(buf, 0, n);
			}
		}
		catch (Exception e) {
			LogUtils.warn("Could not copy " + from + " -> " + to + ": " + e.getMessage());
		}
		finally {
			if (in != null) {
				try {
					in.close();
				}
				catch (Exception e) {
				}
			}
			if (out != null) {
				try {
					out.close();
				}
				catch (Exception e) {
				}
			}
		}
	}
 	
 	public void loadAcceleratorPresets(final InputStream in) {
		final Properties prop = new Properties();
		try {
			prop.load(in);
			for (final Entry<Object, Object> property : prop.entrySet()) {
				final String shortcutKey = (String) property.getKey();
				final String keystrokeString = (String) property.getValue();
				if (!shortcutKey.startsWith(SHORTCUT_PROPERTY_PREFIX)) {
					LogUtils.warn("wrong property key " + shortcutKey);
					continue;
				}
				final int pos = shortcutKey.indexOf("/", SHORTCUT_PROPERTY_PREFIX.length());
				if (pos <= 0) {
					LogUtils.warn("wrong property key " + shortcutKey);
					continue;
				}
				final String modeName = shortcutKey.substring(SHORTCUT_PROPERTY_PREFIX.length(), pos);
				final String itemKey = shortcutKey.substring(pos + 1);
				Controller controller = Controller.getCurrentController();
				final ModeController modeController = controller.getModeController(modeName);
				if (modeController == null) {
					LogUtils.warn("unknown mode name in " + shortcutKey);
					continue;
				}
				final AFreeplaneAction action = modeController.getAction(itemKey);
				if (action == null) {
					LogUtils.warn("wrong key in " + shortcutKey);
					continue;
				}
				final KeyStroke keyStroke;
				if (!keystrokeString.equals("")) {
					keyStroke = UITools.getKeyStroke(parseKeyStroke(keystrokeString).toString());
					final AFreeplaneAction oldAction = accelerators.get(keyStroke);
					if (oldAction != null) {
						setAccelerator(oldAction, null);
						final Object key = oldAction.getKey();
						final String oldShortcutKey = getPropertyKey(key.toString());
						keysetProps.setProperty(oldShortcutKey, "");
					}
				}
				else {
					keyStroke = null;
				}
				setAccelerator(action, keyStroke, false);
				keysetProps.setProperty(shortcutKey, keystrokeString);
			}
		}
		catch (final IOException e) {
			LogUtils.warn("shortcut presets not stored: "+e.getMessage());
		}
	}

	public void loadDefaultAcceleratorPresets() {
		loadAcceleratorPresetsResource("/accelerator.default.properties", "default");
		seedDefaultAcceleratorPresets();
		applyPlatformDefaultAccelerators();
	}

	/**
	 * Record factory defaults from {@code accelerator.default.properties} into
	 * {@link #defaultProps} without changing live bindings. Safe to call after
	 * the user preset file has been loaded.
	 */
	public void seedDefaultAcceleratorPresets() {
		InputStream in = null;
		try {
			in = RibbonAcceleratorManager.class.getResourceAsStream("/accelerator.default.properties");
			if (in == null) {
				return;
			}
			final Properties prop = new Properties();
			prop.load(in);
			for (final Entry<Object, Object> entry : prop.entrySet()) {
				final String shortcutKey = (String) entry.getKey();
				final String keystrokeString = (String) entry.getValue();
				if (!shortcutKey.startsWith(SHORTCUT_PROPERTY_PREFIX)) {
					continue;
				}
				if (keystrokeString == null || keystrokeString.length() == 0) {
					continue;
				}
				if (!defaultProps.containsKey(shortcutKey)) {
					defaultProps.setProperty(shortcutKey, keystrokeString);
				}
			}
		}
		catch (Exception e) {
			LogUtils.warn("Could not seed default accelerator presets: " + e.getMessage());
		}
		finally {
			if (in != null) {
				try {
					in.close();
				}
				catch (IOException e) {
				}
			}
		}
	}

	/**
	 * Mac-friendly defaults: fills unbound keys and migrates Insert / Alt+Space bindings
	 * that Mac keyboards or the OS cannot use comfortably.
	 */
	public void applyPlatformDefaultAccelerators() {
		if (!Compat.isMacOsX()) {
			return;
		}
		final Map alternatives = PlatformHotKeyGuide.getMacDefaultAlternatives();
		Properties fileProps = null;
		InputStream in = null;
		try {
			in = RibbonAcceleratorManager.class.getResourceAsStream("/accelerator.default.mac.properties");
			if (in != null) {
				fileProps = new Properties();
				fileProps.load(in);
			}
		}
		catch (Exception e) {
			LogUtils.warn("Could not read accelerator.default.mac.properties: " + e.getMessage());
		}
		finally {
			if (in != null) {
				try {
					in.close();
				}
				catch (IOException e) {
				}
			}
		}
		LogUtils.info("Applying Mac accelerator alternatives (" + alternatives.size() + " actions)");
		boolean dirty = false;
		for (final Iterator it = alternatives.entrySet().iterator(); it.hasNext();) {
			final Entry entry = (Entry) it.next();
			final String itemKey = (String) entry.getKey();
			String keystrokeString = (String) entry.getValue();
			if (fileProps != null) {
				final String fromFile = fileProps.getProperty(getPropertyKey(itemKey));
				if (fromFile != null && fromFile.length() > 0) {
					keystrokeString = fromFile;
				}
			}
			final AFreeplaneAction action = builder.getMode().getAction(itemKey);
			if (action == null) {
				continue;
			}
			final KeyStroke current = actionMap.get(itemKey);
			if (!PlatformHotKeyGuide.shouldApplyMacAlternative(itemKey, current)) {
				continue;
			}
			final KeyStroke keyStroke = parseKeyStroke(keystrokeString);
			if (keyStroke == null) {
				continue;
			}
			final String shortcutKey = getPropertyKey(itemKey);
			setAccelerator(action, keyStroke, false);
			defaultProps.setProperty(shortcutKey, keystrokeString);
			keysetProps.setProperty(shortcutKey, keystrokeString);
			dirty = true;
			if (current != null) {
				LogUtils.info("Migrated " + itemKey + " from " + current + " to " + keystrokeString + " on Mac");
			}
		}
		if (dirty) {
			try {
				if (!getPresetsFile().exists()) {
					getPresetsFile().createNewFile();
				}
				storeAcceleratorPreset(new FileOutputStream(getPresetsFile()));
			}
			catch (IOException e) {
				LogUtils.warn("Could not persist Mac accelerator alternatives: " + e.getMessage());
			}
		}
	}

	private void loadAcceleratorPresetsResource(final String resourcePath, final String label) {
		InputStream in = null;
		try {
			in = RibbonAcceleratorManager.class.getResourceAsStream(resourcePath);
			if (in != null) {
				LogUtils.info("Loading " + label + " accelerator presets from " + resourcePath);
				loadAcceleratorPresets(in);
			}
		}
		catch (final Exception e) {
			LogUtils.warn("Could not load " + label + " accelerator presets: " + e.getMessage());
		}
		finally {
			if (in != null) {
				try {
					in.close();
				}
				catch (IOException e) {
					LogUtils.warn(e);
				}
			}
		}
	}
 	
 	public void storeAcceleratorPreset(OutputStream out) {
 		try {
 			final OutputStream output = new BufferedOutputStream(out);
 			keysetProps.store(output, "");
 			output.close();
 		}
 		catch (final IOException e1) {
 			UITools.errorMessage(TextUtils.removeTranslateComment(TextUtils.getText("can_not_save_key_set")));
 		}
 	}
 	
	private static String toString(final KeyStroke newAccelerator) {
		return newAccelerator.toString().replaceFirst("pressed ", "");
	}
	
	private static boolean askForReplaceShortcutViaDialog(String oldMenuItemTitle) {
		final int replace = JOptionPane.showConfirmDialog(UITools.getFrame(), 
				TextUtils.removeTranslateComment(TextUtils.format("replace_shortcut_question", oldMenuItemTitle)),
				TextUtils.removeTranslateComment(TextUtils.format("replace_shortcut_title")), JOptionPane.YES_NO_OPTION);
		return replace == JOptionPane.YES_OPTION;
	}
	/***********************************************************************************
	 * REQUIRED METHODS FOR INTERFACES
	 **********************************************************************************/

	public boolean processKeyBinding(KeyStroke ks, KeyEvent event, int condition, boolean pressed, boolean consumed) {
		if (!consumed && condition == JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) {
			AFreeplaneAction action = accelerators.get(ks);
			if(action == null) {
				action = accelerators.get(FreeplaneMenuBar.derive(ks, event.getKeyChar()));
			}
			if(action != null && action.isEnabled()) {
				if(action != null && SwingUtilities.notifyAction(action, ks, event, event.getComponent(), event.getModifiers())) {
					return true;
				}
			}
		}
		return false;
	}

	public void acceleratorChanged(JMenuItem action, KeyStroke oldStroke, KeyStroke newStroke) {
		// TODO Auto-generated method stub
		
	}

	public void acceleratorChanged(AFreeplaneAction action, KeyStroke oldStroke, KeyStroke newStroke) {
		KeyStroke ks = actionMap.put(action.getKey(), newStroke);
		if(ks != null) {
			accelerators.remove(ks);
		}		
		accelerators.put(newStroke, action);
	}
	
	/***********************************************************************************
	 * NESTED TYPE DECLARATIONS
	 **********************************************************************************/
	private class KeystrokeValidator implements IKeystrokeValidator {
		private final AFreeplaneAction action;

		private KeystrokeValidator(AFreeplaneAction action) {
			this.action = action;
		}

		private boolean checkForOverwriteShortcut(final KeyStroke keystroke) {
			final AFreeplaneAction priorAssigned = accelerators.get(keystroke);
			if (priorAssigned == null || action.getKey().equals(priorAssigned.getKey())) {
				return true;
			}
			return replaceOrCancel(priorAssigned, RibbonActionContributorFactory.getActionTitle(priorAssigned));
		}

		private boolean replaceOrCancel(AFreeplaneAction action, String oldMenuItemTitle) {
			if (askForReplaceShortcutViaDialog(oldMenuItemTitle)) {
				setAccelerator(action, null);
				final String shortcutKey = getPropertyKey(action.getKey());
				keysetProps.setProperty(shortcutKey, "");
				return true;
			} else {
				return false;
			}
		}

		public boolean isValid(final KeyStroke keystroke, final Character keyChar) {
			if (keystroke == null) {
				return true;
			}
			if (actionMap.containsKey(action.getKey())) {
				return true;
			}
			if (keyChar != KeyEvent.CHAR_UNDEFINED && (keystroke.getModifiers() & (Event.ALT_MASK | Event.CTRL_MASK | Event.META_MASK)) == 0) {
				final String keyTypeActionString = ResourceController.getResourceController().getProperty("key_type_action",
						FirstAction.EDIT_CURRENT.toString());
				FirstAction keyTypeAction = FirstAction.valueOf(keyTypeActionString);
				return FirstAction.IGNORE.equals(keyTypeAction);
			}
			if (!checkForOverwriteShortcut(keystroke)) {
				return false;
			}
			final KeyStroke derivedKS = FreeplaneMenuBar.derive(keystroke, keyChar);
			if (derivedKS == keystroke) {
				return true;
			}
			return checkForOverwriteShortcut(derivedKS);
		}
	}

	public static KeyStroke parseKeyStroke(String accelerator) {
		if (accelerator != null) {				
			if (Compat.isMacOsX()) {
				accelerator = accelerator.replaceFirst("CONTROL", "META").replaceFirst("control", "meta");
			}
			else {
				accelerator = accelerator.replaceFirst("META", "CONTROL").replaceFirst("meta", "control");
			}
			return KeyStroke.getKeyStroke(accelerator);
		}
		return null;
	}
}
