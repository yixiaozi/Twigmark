package org.docear.plugin.mermaid;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;

/**
 * Off-screen Mermaid.js rendering via a shared JavaFX WebView, with memory + disk cache
 * and async node refresh when a diagram finishes.
 * <p>
 * Never blocks the JavaFX thread waiting for JS — the render queue is fully asynchronous.
 */
public final class MermaidRenderService {

	private static final MermaidRenderService INSTANCE = new MermaidRenderService();
	private static final int MEMORY_CACHE_MAX = 64;

	private final ConcurrentHashMap<String, RichPreviewIcon> memoryCache = new ConcurrentHashMap<String, RichPreviewIcon>();
	private final ConcurrentHashMap<String, Boolean> inFlight = new ConcurrentHashMap<String, Boolean>();
	private final ConcurrentHashMap<String, List<WeakReference<NodeModel>>> waiters =
			new ConcurrentHashMap<String, List<WeakReference<NodeModel>>>();
	private final ConcurrentLinkedQueue<RenderRequest> queue = new ConcurrentLinkedQueue<RenderRequest>();
	private final AtomicBoolean shellReady = new AtomicBoolean(false);
	private final AtomicBoolean busy = new AtomicBoolean(false);
	private final Object shellLock = new Object();

	private enum Backend {
		NONE, JAVAFX, CLI
	}

	private volatile File shellDir;
	private volatile Object webView;
	private volatile Object webEngine;
	private volatile Backend backend = Backend.NONE;
	private volatile boolean unavailable;
	private volatile String unavailableReason = "Mermaid: no renderer";
	private volatile RenderRequest currentRequest;

	private MermaidRenderService() {
	}

	public static MermaidRenderService getInstance() {
		return INSTANCE;
	}

	public void ensureStarted() {
		if (backend != Backend.NONE || unavailable) {
			return;
		}
		if (MermaidJavaFxSupport.ensureAvailable()) {
			try {
				prepareShellResources();
				initFxToolkit();
				backend = Backend.JAVAFX;
				LogUtils.info("Mermaid: using JavaFX WebView renderer");
				return;
			}
			catch (Throwable t) {
				LogUtils.warn("Mermaid: JavaFX init failed, trying CLI", t);
			}
		}
		else {
			LogUtils.warn("Mermaid: JavaFX unavailable — " + MermaidJavaFxSupport.getLastError());
		}
		if (MermaidCliRenderer.ensureAvailable()) {
			backend = Backend.CLI;
			LogUtils.info("Mermaid: using mermaid-cli (npx) renderer");
			return;
		}
		unavailable = true;
		unavailableReason = "Mermaid: no renderer (JavaFX: " + MermaidJavaFxSupport.getLastError()
				+ "; CLI: " + MermaidCliRenderer.getLastError() + ")";
		LogUtils.warn(unavailableReason);
	}

	public static void shutdown() {
		INSTANCE.memoryCache.clear();
		INSTANCE.waiters.clear();
		INSTANCE.queue.clear();
	}

	public RichPreviewIcon getIcon(final String source, final NodeModel node) {
		final String key = RichCache.hash("mermaid", source);
		final RichPreviewIcon cached = memoryCache.get(key);
		if (cached != null) {
			return cached;
		}
		final RichPreviewIcon fromDisk = loadDiskCache(key);
		if (fromDisk != null) {
			putMemory(key, fromDisk);
			return fromDisk;
		}
		ensureStarted();
		if (unavailable || backend == Backend.NONE) {
			return RichPreviewIcon.error("mermaid", unavailableReason);
		}
		registerWaiter(key, node);
		if (inFlight.putIfAbsent(key, Boolean.TRUE) == null) {
			queue.offer(new RenderRequest(key, source));
			schedulePump();
		}
		return RichPreviewIcon.placeholder("mermaid", "Mermaid…");
	}

	private void registerWaiter(final String key, final NodeModel node) {
		if (node == null) {
			return;
		}
		List<WeakReference<NodeModel>> list = waiters.get(key);
		if (list == null) {
			list = new ArrayList<WeakReference<NodeModel>>();
			final List<WeakReference<NodeModel>> prev = waiters.putIfAbsent(key, list);
			if (prev != null) {
				list = prev;
			}
		}
		synchronized (list) {
			list.add(new WeakReference<NodeModel>(node));
		}
	}

	private void schedulePump() {
		if (backend == Backend.CLI) {
			if (!busy.compareAndSet(false, true)) {
				return;
			}
			final Thread t = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						pumpCli();
					}
					finally {
						busy.set(false);
						if (!queue.isEmpty()) {
							schedulePump();
						}
					}
				}
			}, "mermaid-cli-render");
			t.setDaemon(true);
			t.start();
			return;
		}
		runOnFx(new Runnable() {
			@Override
			public void run() {
				pumpOnFx();
			}
		});
	}

	private void pumpCli() {
		RenderRequest req;
		while ((req = queue.poll()) != null) {
			currentRequest = req;
			try {
				final BufferedImage img = MermaidCliRenderer.render(req.source);
				completeCurrent(RichPreviewIcon.fromImage("mermaid", img), img);
			}
			catch (Throwable t) {
				LogUtils.warn("Mermaid CLI render failed", t);
				completeCurrent(RichPreviewIcon.error("mermaid",
						t.getMessage() != null ? t.getMessage() : "CLI render failed"), null);
			}
		}
		currentRequest = null;
	}

	private void pumpOnFx() {
		if (busy.get()) {
			return;
		}
		if (webView != null && !shellReady.get()) {
			return;
		}
		try {
			if (webView == null) {
				createEngineOnFx();
				if (!shellReady.get()) {
					return;
				}
			}
		}
		catch (Throwable t) {
			LogUtils.warn("Mermaid: engine init failed", t);
			unavailable = true;
			unavailableReason = "Mermaid: " + (t.getMessage() != null ? t.getMessage() : "engine init failed");
			failAllInFlight(unavailableReason);
			return;
		}
		final RenderRequest req = queue.poll();
		if (req == null) {
			return;
		}
		busy.set(true);
		currentRequest = req;
		try {
			startRenderOnFx(req);
		}
		catch (Throwable t) {
			LogUtils.warn("Mermaid: render pump failed", t);
			finishRequest(req.key, RichPreviewIcon.error("mermaid",
					t.getMessage() != null ? t.getMessage() : "render failed"));
			busy.set(false);
			currentRequest = null;
			schedulePump();
		}
	}

	private void createEngineOnFx() throws Exception {
		synchronized (shellLock) {
			if (webView != null) {
				return;
			}
			final Class<?> webViewClass = Class.forName("javafx.scene.web.WebView");
			webView = webViewClass.getConstructor().newInstance();
			webViewClass.getMethod("setPrefSize", double.class, double.class).invoke(webView, 800.0, 600.0);
			webEngine = webViewClass.getMethod("getEngine").invoke(webView);
			Class.forName("javafx.scene.Scene").getConstructor(Class.forName("javafx.scene.Parent"))
					.newInstance(webView);

			final Object loadWorker = webEngine.getClass().getMethod("getLoadWorker").invoke(webEngine);
			final Object stateProperty = loadWorker.getClass().getMethod("stateProperty").invoke(loadWorker);
			attachLoadListener(stateProperty);

			final File shell = new File(shellDir, "mermaid-shell.html");
			webEngine.getClass().getMethod("load", String.class).invoke(webEngine, shell.toURI().toString());
		}
	}

	private void failAllInFlight(final String reason) {
		for (final String key : new ArrayList<String>(inFlight.keySet())) {
			finishRequest(key, RichPreviewIcon.error("mermaid", reason));
		}
		queue.clear();
		busy.set(false);
		currentRequest = null;
	}

	private void attachLoadListener(final Object stateProperty) throws Exception {
		final Class<?> changeListenerClass = Class.forName("javafx.beans.value.ChangeListener");
		final Object listener = Proxy.newProxyInstance(changeListenerClass.getClassLoader(),
				new Class<?>[] { changeListenerClass }, new InvocationHandler() {
					@Override
					public Object invoke(final Object proxy, final Method method, final Object[] args)
							throws Throwable {
						if ("changed".equals(method.getName()) && args != null && args.length >= 3) {
							final Object newState = args[2];
							final String name = String.valueOf(newState);
							if ("SUCCEEDED".equals(name)) {
								shellReady.set(true);
								schedulePump();
							}
							else if ("FAILED".equals(name)) {
								unavailable = true;
								unavailableReason = "Mermaid: shell failed to load";
								shellReady.set(false);
								failAllInFlight(unavailableReason);
							}
						}
						return null;
					}
				});
		stateProperty.getClass().getMethod("addListener", changeListenerClass).invoke(stateProperty, listener);
	}

	private void startRenderOnFx(final RenderRequest req) throws Exception {
		final Class<?> jsObjectClass = Class.forName("netscape.javascript.JSObject");
		final Object window = webEngine.getClass().getMethod("executeScript", String.class).invoke(webEngine,
				"window");
		final Bridge bridge = new Bridge(req);
		jsObjectClass.getMethod("setMember", String.class, Object.class).invoke(window, "javaBridge", bridge);
		webEngine.getClass().getMethod("executeScript", String.class).invoke(webEngine,
				"renderMermaid(String(javaBridge.getSource()))");
		// Completion via Bridge.onSuccess / onError (FX thread) — do not wait here.
	}

	private BufferedImage snapshotRaw(final int contentW, final int contentH) throws Exception {
		final int w = Math.max(40, Math.min(RichPreviewIcon.MAX_WIDTH + 16, contentW + 16));
		final int h = Math.max(30, Math.min(RichPreviewIcon.MAX_HEIGHT + 16, contentH + 16));
		webView.getClass().getMethod("setPrefSize", double.class, double.class).invoke(webView, (double) w,
				(double) h);
		final Class<?> writableImageClass = Class.forName("javafx.scene.image.WritableImage");
		final Object writable = writableImageClass.getConstructor(int.class, int.class).newInstance(w, h);
		final Object snapshot = webView.getClass()
				.getMethod("snapshot", Class.forName("javafx.scene.SnapshotParameters"), writableImageClass)
				.invoke(webView, null, writable);
		final Class<?> swingFxUtils = Class.forName("javafx.embed.swing.SwingFXUtils");
		return (BufferedImage) swingFxUtils
				.getMethod("fromFXImage", Class.forName("javafx.scene.image.Image"), BufferedImage.class)
				.invoke(null, snapshot, null);
	}

	private void finishRequest(final String key, final RichPreviewIcon icon) {
		// Do not permanently cache hard failures — allow retry after env fixes.
		if (icon != null) {
			putMemory(key, icon);
		}
		inFlight.remove(key);
		refreshWaiters(key);
	}

	private void completeCurrent(final RichPreviewIcon icon, final BufferedImage png) {
		final RenderRequest req = currentRequest;
		if (req == null) {
			if (backend != Backend.CLI) {
				busy.set(false);
				schedulePump();
			}
			return;
		}
		if (png != null) {
			savePngCache(req.key, png);
		}
		finishRequest(req.key, icon);
		currentRequest = null;
		if (backend != Backend.CLI) {
			busy.set(false);
			schedulePump();
		}
	}

	private void refreshWaiters(final String key) {
		final List<WeakReference<NodeModel>> list = waiters.remove(key);
		if (list == null) {
			return;
		}
		final List<NodeModel> nodes = new ArrayList<NodeModel>();
		synchronized (list) {
			for (final WeakReference<NodeModel> ref : list) {
				final NodeModel n = ref.get();
				if (n != null) {
					nodes.add(n);
				}
			}
		}
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				for (final NodeModel node : nodes) {
					try {
						final ModeController mc = Controller.getCurrentModeController();
						if (mc != null) {
							mc.getMapController().nodeRefresh(node);
						}
					}
					catch (Throwable t) {
						LogUtils.warn("Mermaid: node refresh failed", t);
					}
				}
			}
		});
	}

	private void putMemory(final String key, final RichPreviewIcon icon) {
		memoryCache.put(key, icon);
		if (memoryCache.size() <= MEMORY_CACHE_MAX) {
			return;
		}
		int removed = 0;
		final Iterator<String> it = memoryCache.keySet().iterator();
		while (it.hasNext() && removed < 8 && memoryCache.size() > MEMORY_CACHE_MAX) {
			final String k = it.next();
			if (!k.equals(key)) {
				it.remove();
				removed++;
			}
		}
	}

	private File cacheDir() {
		final File dir = new File(System.getProperty("user.home"), ".docear/mermaid-cache");
		if (!dir.isDirectory()) {
			dir.mkdirs();
		}
		return dir;
	}

	private RichPreviewIcon loadDiskCache(final String key) {
		try {
			final File png = RichCache.pngFile(key);
			if (png.isFile()) {
				final BufferedImage img = ImageIO.read(png);
				if (img != null) {
					return RichPreviewIcon.fromImage("mermaid", img);
				}
			}
		}
		catch (Throwable t) {
			LogUtils.warn("Mermaid: disk cache read failed", t);
		}
		return null;
	}

	private void savePngCache(final String key, final BufferedImage image) {
		try {
			if (image == null) {
				return;
			}
			ImageIO.write(image, "png", RichCache.pngFile(key));
		}
		catch (Throwable t) {
			LogUtils.warn("Mermaid: disk cache write failed", t);
		}
	}

	private void prepareShellResources() throws Exception {
		if (shellDir != null && new File(shellDir, "mermaid-shell.html").isFile()
				&& new File(shellDir, "mermaid.min.js").isFile()) {
			return;
		}
		final File dir = new File(cacheDir(), "shell-10.9.1");
		dir.mkdirs();
		copyResource("mermaid-shell.html", new File(dir, "mermaid-shell.html"));
		copyResource("mermaid.min.js", new File(dir, "mermaid.min.js"));
		shellDir = dir;
	}

	private static void copyResource(final String name, final File target) throws Exception {
		if (target.isFile() && target.length() > 0) {
			return;
		}
		final InputStream in = MermaidRenderService.class.getClassLoader().getResourceAsStream(name);
		if (in == null) {
			throw new IllegalStateException("Missing resource: " + name);
		}
		try {
			final OutputStream out = new FileOutputStream(target);
			try {
				final byte[] buf = new byte[8192];
				int n;
				while ((n = in.read(buf)) >= 0) {
					out.write(buf, 0, n);
				}
			}
			finally {
				out.close();
			}
		}
		finally {
			in.close();
		}
	}

	private void initFxToolkit() throws Exception {
		Class.forName("javafx.embed.swing.JFXPanel").getConstructor().newInstance();
	}

	private void runOnFx(final Runnable r) {
		try {
			final Class<?> platform = Class.forName("javafx.application.Platform");
			final Boolean fxThread = (Boolean) platform.getMethod("isFxApplicationThread").invoke(null);
			if (Boolean.TRUE.equals(fxThread)) {
				r.run();
			}
			else {
				platform.getMethod("runLater", Runnable.class).invoke(null, r);
			}
		}
		catch (Throwable t) {
			LogUtils.warn("Mermaid: Platform.runLater failed", t);
			unavailable = true;
			unavailableReason = "Mermaid: " + t.getMessage();
		}
	}

	private static final class RenderRequest {
		final String key;
		final String source;

		RenderRequest(final String key, final String source) {
			this.key = key;
			this.source = source;
		}
	}

	/** Called from JavaScript inside the WebView. Methods must be public. */
	public final class Bridge {
		private final RenderRequest request;

		Bridge(final RenderRequest request) {
			this.request = request;
		}

		public String getSource() {
			return request.source;
		}

		public void onSuccess(final Object w, final Object h) {
			try {
				final int width = toInt(w, 400);
				final int height = toInt(h, 300);
				final BufferedImage png = snapshotRaw(width, height);
				completeCurrent(RichPreviewIcon.fromImage("mermaid", png), png);
			}
			catch (Throwable t) {
				LogUtils.warn("Mermaid: snapshot failed", t);
				completeCurrent(RichPreviewIcon.error("mermaid", t.getMessage()), null);
			}
		}

		public void onError(final Object message) {
			completeCurrent(
					RichPreviewIcon.error("mermaid", message != null ? String.valueOf(message) : "render error"),
					null);
		}

		private int toInt(final Object o, final int fallback) {
			if (o instanceof Number) {
				return ((Number) o).intValue();
			}
			try {
				return Integer.parseInt(String.valueOf(o));
			}
			catch (Exception e) {
				return fallback;
			}
		}
	}
}
