package org.docear.plugin.core.quickcapture;

import org.freeplane.core.util.Compat;
import org.freeplane.core.util.LogUtils;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinUser;

/**
 * Registers system-wide hotkeys on Windows (Docear must be running):
 * Ctrl+Shift+Space → quick capture, Ctrl+Space → show/hide, Shift+Space → command palette.
 * RegisterHotKey and the message loop run on the same dedicated thread (required by Win32).
 */
final class QuickCaptureHotkey {
	private static final int HOTKEY_ID_CAPTURE = 0x7CE0;
	private static final int HOTKEY_ID_TOGGLE = 0x7CE1;
	private static final int HOTKEY_ID_COMMAND = 0x7CE2;
	private static final int MOD_NOREPEAT = 0x4000;
	private static final int MODIFIERS_CAPTURE = WinUser.MOD_CONTROL | WinUser.MOD_SHIFT | MOD_NOREPEAT;
	private static final int MODIFIERS_TOGGLE = WinUser.MOD_CONTROL | MOD_NOREPEAT;
	private static final int MODIFIERS_COMMAND = WinUser.MOD_SHIFT | MOD_NOREPEAT;
	private static final int VK_SPACE = 0x20;
	private static final int PM_REMOVE = 0x0001;

	private static volatile boolean running;
	private static volatile boolean registeredCapture;
	private static volatile boolean registeredToggle;
	private static volatile boolean registeredCommand;
	private static volatile boolean threadStarted;
	private static volatile boolean shutdownHookAdded;

	private QuickCaptureHotkey() {
	}

	static synchronized void registerWhenReady() {
		if (!Compat.isWindowsOS() || threadStarted) {
			return;
		}
		threadStarted = true;
		addShutdownHook();
		final Thread thread = new Thread(new Runnable() {
			public void run() {
				registerOnThread();
			}
		}, "QuickCapture-Hotkey");
		thread.setDaemon(true);
		thread.start();
	}

	private static void addShutdownHook() {
		if (shutdownHookAdded) {
			return;
		}
		shutdownHookAdded = true;
		try {
			Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
				public void run() {
					shutdown();
				}
			}, "QuickCapture-Hotkey-Shutdown"));
		}
		catch (Exception e) {
			LogUtils.warn("QuickCapture: could not add shutdown hook.", e);
		}
	}

	private static void registerOnThread() {
		try {
			if (!User32.INSTANCE.RegisterHotKey(null, HOTKEY_ID_CAPTURE, MODIFIERS_CAPTURE, VK_SPACE)) {
				final int err = Kernel32.INSTANCE.GetLastError();
				LogUtils.warn("QuickCapture: RegisterHotKey failed for Ctrl+Shift+Space (Win32 error " + err
				        + "). May be used by another program.");
			}
			else {
				registeredCapture = true;
				LogUtils.info("QuickCapture: global hotkey Ctrl+Shift+Space registered.");
			}

			if (!User32.INSTANCE.RegisterHotKey(null, HOTKEY_ID_TOGGLE, MODIFIERS_TOGGLE, VK_SPACE)) {
				final int err = Kernel32.INSTANCE.GetLastError();
				LogUtils.warn("QuickCapture: RegisterHotKey failed for Ctrl+Space (Win32 error " + err
				        + "). May be used by another program.");
			}
			else {
				registeredToggle = true;
				LogUtils.info("QuickCapture: global hotkey Ctrl+Space registered.");
			}

			if (!User32.INSTANCE.RegisterHotKey(null, HOTKEY_ID_COMMAND, MODIFIERS_COMMAND, VK_SPACE)) {
				final int err = Kernel32.INSTANCE.GetLastError();
				LogUtils.warn("QuickCapture: RegisterHotKey failed for Shift+Space (Win32 error " + err
				        + "). May be used by another program.");
			}
			else {
				registeredCommand = true;
				LogUtils.info("QuickCapture: global hotkey Shift+Space registered.");
			}

			if (!registeredCapture && !registeredToggle && !registeredCommand) {
				return;
			}

			running = true;
			final WinUser.MSG msg = new WinUser.MSG();
			while (running) {
				while (User32.INSTANCE.PeekMessage(msg, null, 0, 0, PM_REMOVE)) {
					if (msg.message == WinUser.WM_QUIT) {
						running = false;
						break;
					}
					if (msg.message == WinUser.WM_HOTKEY) {
						if (msg.wParam.intValue() == HOTKEY_ID_CAPTURE) {
							QuickCaptureService.showCaptureDialog();
						}
						else if (msg.wParam.intValue() == HOTKEY_ID_TOGGLE) {
							toggleDocearVisibility();
						}
						else if (msg.wParam.intValue() == HOTKEY_ID_COMMAND) {
							org.docear.plugin.core.quickcommand.QuickCommandService.showDialog();
						}
					}
					User32.INSTANCE.TranslateMessage(msg);
					User32.INSTANCE.DispatchMessage(msg);
				}
				try {
					Thread.sleep(50);
				}
				catch (InterruptedException e) {
					running = false;
					Thread.currentThread().interrupt();
				}
			}
		}
		catch (Throwable t) {
			LogUtils.warn("QuickCapture: could not register global hotkey.", t);
		}
	}

	private static void toggleDocearVisibility() {
		java.awt.EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					final org.freeplane.features.mode.Controller controller = org.freeplane.features.mode.Controller.getCurrentController();
					if (controller == null || controller.getViewController() == null) {
						return;
					}
					final java.awt.Frame frame = controller.getViewController().getFrame();
					if (frame == null) {
						return;
					}
					final boolean isActive = frame.isActive() || (frame.getExtendedState() & java.awt.Frame.ICONIFIED) != 0;
					if (frame.isVisible() && isActive) {
						frame.setVisible(false);
					}
					else {
						moveFrameToCurrentScreen(frame);
						frame.setVisible(true);
						frame.setState(java.awt.Frame.NORMAL);
						frame.toFront();
						frame.requestFocus();
						final org.freeplane.features.ui.IMapViewManager mapViewManager = controller.getMapViewManager();
						if (mapViewManager != null) {
							mapViewManager.obtainFocusForSelected();
						}
					}
				}
				catch (Exception e) {
					LogUtils.warn("QuickCapture: could not toggle Docear visibility.", e);
				}
			}
		});
	}

	private static void moveFrameToCurrentScreen(final java.awt.Frame frame) {
		final java.awt.Point mouseLocation = java.awt.MouseInfo.getPointerInfo().getLocation();
		final java.awt.GraphicsEnvironment ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment();
		final java.awt.GraphicsDevice[] screens = ge.getScreenDevices();
		
		for (java.awt.GraphicsDevice screen : screens) {
			java.awt.Rectangle screenRect = screen.getDefaultConfiguration().getBounds();
			if (screenRect.contains(mouseLocation)) {
				java.awt.Rectangle frameBounds = frame.getBounds();
				int newX = screenRect.x + (screenRect.width - frameBounds.width) / 2;
				int newY = screenRect.y + (screenRect.height - frameBounds.height) / 2;
				frame.setLocation(newX, newY);
				break;
			}
		}
	}

	static synchronized void shutdown() {
		running = false;
		unregister();
		threadStarted = false;
	}

	private static void unregister() {
		boolean any = false;
		if (registeredCapture) {
			User32.INSTANCE.UnregisterHotKey(null, HOTKEY_ID_CAPTURE);
			registeredCapture = false;
			any = true;
		}
		if (registeredToggle) {
			User32.INSTANCE.UnregisterHotKey(null, HOTKEY_ID_TOGGLE);
			registeredToggle = false;
			any = true;
		}
		if (registeredCommand) {
			User32.INSTANCE.UnregisterHotKey(null, HOTKEY_ID_COMMAND);
			registeredCommand = false;
			any = true;
		}
		if (any) {
			LogUtils.info("QuickCapture: global hotkeys unregistered.");
		}
	}
}
