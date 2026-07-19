package org.docear.plugin.drawio.browser;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.docear.plugin.drawio.DrawioEmbedServer;
import org.docear.plugin.drawio.DrawioJson;
import org.docear.plugin.drawio.ui.DrawioEditorListener;
import org.freeplane.core.util.LogUtils;

/**
 * Embeds Draw.io via JavaFX WebView (loaded reflectively when JavaFX is available).
 */
public final class DrawioBrowserPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	private static final int BRIDGE_MAX_ATTEMPTS = 200;
	private static final int BRIDGE_RETRY_MS = 100;
	private static final int EDITOR_READY_TIMEOUT_MS = 45000;

	private final DrawioEditorListener listener;
	private final JPanel browserHost = new JPanel(new BorderLayout());
	private Object webEngine;
	private Object jfxPanel;
	private boolean loaded;
	private boolean disposed;
	private boolean editorInitReceived;
	private String pendingLoadMessage;
	private File diagramFile;
	private javax.swing.Timer readyWatchdog;

	public DrawioBrowserPanel(final DrawioEditorListener listener) {
		this.listener = listener;
		setLayout(new BorderLayout());
		add(browserHost, BorderLayout.CENTER);
	}

	public void setDiagramFile(final File file) {
		this.diagramFile = file;
	}

	public void ensureLoaded() {
		if (loaded || disposed) {
			return;
		}
		if (!DrawioJavaFxSupport.ensureAvailable()) {
			showJavaFxMissingHelp();
			loaded = true;
			return;
		}
		if (SwingUtilities.isEventDispatchThread()) {
			initBrowserAsync();
		}
		else {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					initBrowserAsync();
				}
			});
		}
	}

	public void loadDiagram(final String xml, final String title) {
		final String message = DrawioJson.buildLoadMessage(xml, title);
		if (webEngine == null || !editorInitReceived) {
			pendingLoadMessage = message;
			return;
		}
		postToEditor(message);
	}

	public void disposeBrowser() {
		disposed = true;
		stopReadyWatchdog();
		webEngine = null;
		jfxPanel = null;
		browserHost.removeAll();
	}

	private void initBrowserAsync() {
		if (loaded || disposed) {
			return;
		}
		try {
			showLoading("正在启动 Draw.io 编辑器…");
			final Class<?> jfxPanelClass = Class.forName("javafx.embed.swing.JFXPanel");
			jfxPanel = jfxPanelClass.newInstance();
			browserHost.removeAll();
			browserHost.add((Component) jfxPanel, BorderLayout.CENTER);
			revalidate();
			repaint();
			loaded = true;

			runOnFxThread(new Runnable() {
				public void run() {
					try {
						disableImplicitJavaFxExit();
						createWebView();
					}
					catch (Exception e) {
						LogUtils.warn("Draw.io WebView init failed", e);
						final String msg = e.getMessage();
						SwingUtilities.invokeLater(new Runnable() {
							public void run() {
								showError("无法初始化 Draw.io 浏览器：\n" + msg);
							}
						});
					}
				}
			});
		}
		catch (Exception e) {
			LogUtils.warn("Draw.io JavaFX init failed", e);
			showJavaFxMissingHelp();
			loaded = true;
		}
	}

	private void createWebView() throws Exception {
		final Class<?> webViewClass = Class.forName("javafx.scene.web.WebView");
		final Object webView = webViewClass.newInstance();
		webEngine = invokeAccessible(webView, "getEngine");

		final Object bridge = new DrawioJsBridge(listener, this);
		invokeAccessible(webEngine, "setJavaScriptEnabled", new Class[] { boolean.class }, Boolean.TRUE);
		attachLoadWorkerListener(bridge);

		final String shellUrl = DrawioEmbedServer.getShellUrl();
		LogUtils.info("Draw.io loading shell: " + shellUrl);
		final Method loadMethod = accessibleMethod(webEngine.getClass(), "load", String.class);
		loadMethod.invoke(webEngine, shellUrl);

		final Class<?> sceneClass = Class.forName("javafx.scene.Scene");
		final Constructor<?> sceneCtor = sceneClass.getConstructor(Class.forName("javafx.scene.Parent"));
		final Object scene = sceneCtor.newInstance(webView);

		final Method setScene = accessibleMethod(jfxPanel.getClass(), "setScene", sceneClass);
		setScene.invoke(jfxPanel, scene);

		scheduleBridgeInstallAttempt(bridge, 0);
		startReadyWatchdog();
	}

	private void attachLoadWorkerListener(final Object bridge) {
		try {
			final Object loadWorker = invokeAccessible(webEngine, "getLoadWorker");
			final Object stateProperty = invokeAccessible(loadWorker, "stateProperty");
			final Class<?> changeListenerClass = Class.forName("javafx.beans.value.ChangeListener");
			final Object listenerProxy = Proxy.newProxyInstance(changeListenerClass.getClassLoader(),
			        new Class[] { changeListenerClass }, new InvocationHandler() {
				        public Object invoke(final Object proxy, final Method method, final Object[] args)
				                throws Throwable {
					        if ("changed".equals(method.getName()) && args != null && args.length >= 3) {
						        final Object newValue = args[2];
						        if (newValue != null && "SUCCEEDED".equals(String.valueOf(newValue))) {
							        scheduleBridgeInstallAttempt(bridge, 0);
						        }
						        else if (newValue != null && "FAILED".equals(String.valueOf(newValue))) {
							        Object ex = null;
							        try {
								        ex = invokeAccessible(loadWorker, "getException");
							        }
							        catch (Exception ignore) {
							        }
							        final String detail = ex != null ? String.valueOf(ex) : "unknown error";
							        LogUtils.warn("Draw.io shell page failed to load: " + detail);
							        SwingUtilities.invokeLater(new Runnable() {
								        public void run() {
									        showError("无法加载 Draw.io 外壳页：\n" + detail
									                + "\n\n请检查本机回环地址 127.0.0.1 是否可用。");
								        }
							        });
						        }
					        }
					        return null;
				        }
			        });
			final Method addListener = accessibleMethod(stateProperty.getClass(), "addListener",
			        changeListenerClass);
			addListener.invoke(stateProperty, listenerProxy);
		}
		catch (Exception e) {
			LogUtils.warn("Draw.io LoadWorker listener attach failed; falling back to bridge polling", e);
		}
	}

	private void startReadyWatchdog() {
		stopReadyWatchdog();
		readyWatchdog = new javax.swing.Timer(EDITOR_READY_TIMEOUT_MS, new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				if (disposed || editorInitReceived) {
					return;
				}
				LogUtils.warn("Draw.io editor init timed out after " + EDITOR_READY_TIMEOUT_MS + "ms");
				showError("Draw.io 编辑器未能在限定时间内就绪。\n\n常见原因：\n"
				        + "1. 无法访问 embed.diagrams.net（需联网，或改用本地 embed URL）\n"
				        + "2. 当前 Java 无 JavaFX（请用发行包内 jre 启动）\n"
				        + "3. 公司代理/防火墙拦截了 iframe\n\n"
				        + "可在偏好中设置 drawio.embed.url，或用系统程序打开 .drawio 文件。");
			}
		});
		readyWatchdog.setRepeats(false);
		readyWatchdog.start();
	}

	private void stopReadyWatchdog() {
		if (readyWatchdog != null) {
			readyWatchdog.stop();
			readyWatchdog = null;
		}
	}

	private void scheduleBridgeInstallAttempt(final Object bridge, final int attempt) throws Exception {
		if (disposed || editorInitReceived) {
			return;
		}
		if (attempt >= BRIDGE_MAX_ATTEMPTS) {
			LogUtils.warn("Draw.io bridge install timed out after " + BRIDGE_MAX_ATTEMPTS + " attempts");
			return;
		}
		runOnFxThread(new Runnable() {
			public void run() {
				try {
					if (disposed) {
						return;
					}
					if (tryInstallBridge(bridge)) {
						return;
					}
				}
				catch (Exception e) {
					LogUtils.warn("Draw.io bridge install attempt failed", e);
				}
				final javax.swing.Timer timer = new javax.swing.Timer(BRIDGE_RETRY_MS, new ActionListener() {
					public void actionPerformed(final ActionEvent e) {
						try {
							scheduleBridgeInstallAttempt(bridge, attempt + 1);
						}
						catch (Exception ex) {
							LogUtils.warn("Draw.io bridge install scheduling failed", ex);
						}
					}
				});
				timer.setRepeats(false);
				timer.start();
			}
		});
	}

	private boolean tryInstallBridge(final Object bridge) throws Exception {
		if (webEngine == null) {
			return false;
		}
		final Method executeScript = accessibleMethod(webEngine.getClass(), "executeScript", String.class);
		final Object readyState = executeScript.invoke(webEngine, "document.readyState");
		if (readyState == null) {
			return false;
		}
		final String state = readyState.toString();
		if (!"complete".equals(state) && !"interactive".equals(state)) {
			return false;
		}
		installBridge(bridge);
		return true;
	}

	private static Method accessibleMethod(final Class<?> clazz, final String name, final Class<?>... parameterTypes)
	        throws NoSuchMethodException {
		Class<?> search = clazz;
		while (search != null) {
			try {
				final Method method = search.getDeclaredMethod(name, parameterTypes);
				method.setAccessible(true);
				return method;
			}
			catch (NoSuchMethodException e) {
				// try public API next
			}
			try {
				final Method method = search.getMethod(name, parameterTypes);
				method.setAccessible(true);
				return method;
			}
			catch (NoSuchMethodException e) {
				search = search.getSuperclass();
			}
		}
		throw new NoSuchMethodException(clazz.getName() + "." + name);
	}

	private static Object invokeAccessible(final Object target, final String name, final Class<?>[] parameterTypes,
	        final Object... args) throws Exception {
		final Method method = accessibleMethod(target.getClass(), name, parameterTypes);
		return method.invoke(target, args);
	}

	private static Object invokeAccessible(final Object target, final String name) throws Exception {
		return invokeAccessible(target, name, new Class[0]);
	}

	private void installBridge(final Object bridge) throws Exception {
		final Class<?> JSObjectClass = Class.forName("netscape.javascript.JSObject");
		final Object window = invokeAccessible(webEngine, "executeScript", new Class[] { String.class }, "window");
		if (window != null) {
			final Method setMember = accessibleMethod(JSObjectClass, "setMember", String.class, Object.class);
			setMember.invoke(window, "javaBridge", bridge);
			try {
				invokeAccessible(webEngine, "executeScript", new Class[] { String.class },
				        "window.__docearBridgeReady=true;");
			}
			catch (Exception ignore) {
			}
		}
	}

	void handleEditorMessage(final String json) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				handleEditorMessageOnEdt(json);
			}
		});
	}

	private void handleEditorMessageOnEdt(final String json) {
		final String event = DrawioJson.getEvent(json);
		if (event == null) {
			return;
		}
		if ("init".equals(event)) {
			editorInitReceived = true;
			stopReadyWatchdog();
			if (pendingLoadMessage != null) {
				postToEditor(pendingLoadMessage);
				pendingLoadMessage = null;
			}
			listener.onEditorReady();
		}
		else if ("save".equals(event) || "autosave".equals(event)) {
			final String xml = DrawioJson.getXml(json);
			listener.onDiagramSaved(xml, "autosave".equals(event));
		}
		else if ("exit".equals(event)) {
			final String xml = DrawioJson.getXml(json);
			listener.onEditorExit(xml, true);
		}
	}

	private void postToEditor(final String jsonMessage) {
		if (webEngine == null || disposed) {
			return;
		}
		final String script = "if(window.postToEditor){window.postToEditor(" + jsonMessage + ");}";
		try {
			runOnFxThread(new Runnable() {
				public void run() {
					try {
						invokeAccessible(webEngine, "executeScript", new Class[] { String.class }, script);
					}
					catch (Exception e) {
						LogUtils.warn("Draw.io postToEditor failed", e);
					}
				}
			});
		}
		catch (Exception e) {
			LogUtils.warn("Draw.io postToEditor scheduling failed", e);
		}
	}

	private static void disableImplicitJavaFxExit() throws Exception {
		final Class<?> platformClass = Class.forName("javafx.application.Platform");
		final Method setImplicitExit = accessibleMethod(platformClass, "setImplicitExit", boolean.class);
		setImplicitExit.invoke(null, Boolean.FALSE);
	}

	private static void runOnFxThread(final Runnable runnable) throws Exception {
		final Class<?> platformClass = Class.forName("javafx.application.Platform");
		final Method isFxThread = accessibleMethod(platformClass, "isFxApplicationThread");
		final Boolean onFx = (Boolean) isFxThread.invoke(null);
		if (Boolean.TRUE.equals(onFx)) {
			runnable.run();
			return;
		}
		final Method runLater = accessibleMethod(platformClass, "runLater", Runnable.class);
		runLater.invoke(null, runnable);
	}

	private void showLoading(final String message) {
		browserHost.removeAll();
		browserHost.add(new JLabel("<html><div style='padding:16px'>" + message + "</div></html>"),
		        BorderLayout.CENTER);
		revalidate();
		repaint();
	}

	private void showJavaFxMissingHelp() {
		final String javaHome = System.getProperty("java.home", "");
		final String vendor = System.getProperty("java.vendor", "");
		final String detail = DrawioJavaFxSupport.getLastError();
		final StringBuilder candidates = new StringBuilder();
		final java.util.List<String> hints = DrawioJavaFxSupport.candidateHints();
		final int max = Math.min(6, hints.size());
		for (int i = 0; i < max; i++) {
			candidates.append("· ").append(hints.get(i)).append("<br>");
		}
		final JPanel panel = new JPanel(new BorderLayout(8, 8));
		panel.add(new JLabel("<html><b>Draw.io 内嵌编辑器需要 JavaFX</b><br><br>"
		        + "当前 JVM：<code>" + escapeHtml(javaHome) + "</code><br>"
		        + "厂商： " + escapeHtml(vendor) + "<br>"
		        + (detail.length() > 0 ? ("探测： " + escapeHtml(detail) + "<br><br>") : "<br>")
		        + "解决方法（任选其一）：<br>"
		        + "1. 用发行包里的 <code>docear-javafx.bat</code>（或含 <code>jre\\lib\\ext\\jfxrt.jar</code> 的捆绑 JRE）启动<br>"
		        + "2. 项目根目录运行 <code>scripts\\setup-drawio-javafx.ps1</code> 后重新构建/部署<br>"
		        + "3. 安装 BellSoft Liberica JDK 8 <i>Full</i>，并用该 JDK 启动 Docear<br>"
		        + "4. 设置环境变量 <code>DOCEAR_JAVAFX_HOME</code> 指向含 JavaFX 的 JRE 根目录<br><br>"
		        + (candidates.length() > 0 ? ("已扫描：<br>" + candidates) : "")
		        + "</html>"), BorderLayout.CENTER);
		final JButton openExternal = new JButton("用系统默认程序打开 .drawio");
		openExternal.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				openDiagramExternally();
			}
		});
		openExternal.setEnabled(diagramFile != null && diagramFile.exists());
		panel.add(openExternal, BorderLayout.SOUTH);
		browserHost.removeAll();
		browserHost.add(panel, BorderLayout.CENTER);
		revalidate();
		repaint();
	}

	private void openDiagramExternally() {
		if (diagramFile == null || !diagramFile.exists()) {
			return;
		}
		try {
			if (Desktop.isDesktopSupported()) {
				Desktop.getDesktop().open(diagramFile);
			}
		}
		catch (Exception e) {
			LogUtils.warn("Could not open .drawio externally: " + diagramFile, e);
			showError("无法用系统程序打开：\n" + e.getMessage());
		}
	}

	private static String escapeHtml(final String text) {
		if (text == null) {
			return "";
		}
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private void showError(final String message) {
		final JPanel panel = new JPanel(new BorderLayout(8, 8));
		panel.add(new JLabel("<html><div style='padding:12px'>" + message.replace("\n", "<br>")
		        + "</div></html>"), BorderLayout.CENTER);
		final JButton openExternal = new JButton("用系统默认程序打开 .drawio");
		openExternal.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				openDiagramExternally();
			}
		});
		openExternal.setEnabled(diagramFile != null && diagramFile.exists());
		panel.add(openExternal, BorderLayout.SOUTH);
		browserHost.removeAll();
		browserHost.add(panel, BorderLayout.CENTER);
		revalidate();
		repaint();
	}

	/** Called from JavaScript through JavaFX bridge. */
	public static final class DrawioJsBridge {
		private final DrawioEditorListener listener;
		private final DrawioBrowserPanel panel;

		public DrawioJsBridge(final DrawioEditorListener listener, final DrawioBrowserPanel panel) {
			this.listener = listener;
			this.panel = panel;
		}

		public void onEditorMessage(final String json) {
			panel.handleEditorMessage(json);
		}
	}
}
