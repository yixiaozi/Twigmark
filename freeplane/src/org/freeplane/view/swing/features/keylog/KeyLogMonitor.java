package org.freeplane.view.swing.features.keylog;

import org.freeplane.core.util.Compat;
import org.freeplane.core.util.LogUtils;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HMODULE;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.HHOOK;
import com.sun.jna.platform.win32.WinUser.KBDLLHOOKSTRUCT;
import com.sun.jna.platform.win32.WinUser.LowLevelKeyboardProc;
import com.sun.jna.platform.win32.WinUser.MSG;

/**
 * Windows WH_KEYBOARD_LL hook. Callback only enqueues; never touches SQLite.
 */
public final class KeyLogMonitor {
	private static final int PM_REMOVE = 0x0001;
	private static volatile KeyLogMonitor instance;
	private static volatile boolean shutdownHookAdded;

	private final KeyLogService service;
	private volatile boolean running;
	private volatile HHOOK hook;
	/** Keep a strong reference so the native callback is not GC'd. */
	private LowLevelKeyboardProc proc;
	private Thread hookThread;

	private KeyLogMonitor(final KeyLogService service) {
		this.service = service;
	}

	public static synchronized void start() {
		if (instance != null) {
			return;
		}
		if (!KeyLogConfig.isEnabled()) {
			LogUtils.info("Keylog monitor disabled by configuration.");
			return;
		}
		if (!Compat.isWindowsOS()) {
			LogUtils.info("Keylog monitor: global hook only supported on Windows.");
			return;
		}
		instance = new KeyLogMonitor(KeyLogService.getInstance());
		instance.service.start();
		instance.attach();
	}

	public static synchronized void stop() {
		if (instance == null) {
			return;
		}
		instance.detach();
		instance.service.shutdown();
		instance = null;
	}

	private void attach() {
		running = true;
		addShutdownHook();
		hookThread = new Thread(new Runnable() {
			public void run() {
				runHookLoop();
			}
		}, "docear-keylog-hook");
		hookThread.setDaemon(true);
		hookThread.start();
	}

	private static void addShutdownHook() {
		if (shutdownHookAdded) {
			return;
		}
		shutdownHookAdded = true;
		try {
			Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
				public void run() {
					try {
						stop();
					}
					catch (Throwable t) {
					}
				}
			}, "docear-keylog-shutdown"));
		}
		catch (Exception e) {
		}
	}

	private void detach() {
		running = false;
		final HHOOK h = hook;
		if (h != null) {
			try {
				User32.INSTANCE.UnhookWindowsHookEx(h);
			}
			catch (Throwable t) {
			}
			hook = null;
		}
		if (hookThread != null) {
			hookThread.interrupt();
			try {
				hookThread.join(3000L);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			hookThread = null;
		}
		proc = null;
	}

	private void runHookLoop() {
		try {
			proc = new LowLevelKeyboardProc() {
				public LRESULT callback(final int nCode, final WPARAM wParam, final KBDLLHOOKSTRUCT info) {
					try {
						if (nCode >= 0 && info != null && running) {
							final int msg = wParam.intValue();
							if (msg == WinUser.WM_KEYDOWN || msg == WinUser.WM_SYSKEYDOWN) {
								service.offer(KeyVkNames.nameOf(info.vkCode), System.currentTimeMillis());
							}
						}
					}
					catch (Throwable t) {
					}
					return User32.INSTANCE.CallNextHookEx(hook, nCode, wParam, info.getPointer());
				}
			};
			final HMODULE hMod = Kernel32.INSTANCE.GetModuleHandle(null);
			hook = User32.INSTANCE.SetWindowsHookEx(WinUser.WH_KEYBOARD_LL, proc, hMod, 0);
			if (hook == null) {
				LogUtils.warn("Keylog: SetWindowsHookEx failed (Win32 error "
				        + Kernel32.INSTANCE.GetLastError() + ")");
				return;
			}
			LogUtils.info("Keylog: global keyboard hook installed.");
			final MSG msg = new MSG();
			while (running) {
				while (User32.INSTANCE.PeekMessage(msg, null, 0, 0, PM_REMOVE)) {
					if (msg.message == WinUser.WM_QUIT) {
						running = false;
						break;
					}
					User32.INSTANCE.TranslateMessage(msg);
					User32.INSTANCE.DispatchMessage(msg);
				}
				try {
					Thread.sleep(15L);
				}
				catch (InterruptedException e) {
					break;
				}
			}
		}
		catch (Throwable t) {
			LogUtils.warn("Keylog hook loop failed: " + t.getMessage(), t);
		}
		finally {
			if (hook != null) {
				try {
					User32.INSTANCE.UnhookWindowsHookEx(hook);
				}
				catch (Throwable t) {
				}
				hook = null;
			}
		}
	}
}
