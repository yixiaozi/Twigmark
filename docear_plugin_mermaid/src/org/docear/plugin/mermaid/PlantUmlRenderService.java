package org.docear.plugin.mermaid;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.ref.WeakReference;
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

/** Async PlantUML rendering with cache (CLI only). */
final class PlantUmlRenderService {

	private static final PlantUmlRenderService INSTANCE = new PlantUmlRenderService();
	private static final int MEMORY_CACHE_MAX = 32;

	private final ConcurrentHashMap<String, RichPreviewIcon> memoryCache =
			new ConcurrentHashMap<String, RichPreviewIcon>();
	private final ConcurrentHashMap<String, Boolean> inFlight = new ConcurrentHashMap<String, Boolean>();
	private final ConcurrentHashMap<String, List<WeakReference<NodeModel>>> waiters =
			new ConcurrentHashMap<String, List<WeakReference<NodeModel>>>();
	private final ConcurrentLinkedQueue<RenderRequest> queue = new ConcurrentLinkedQueue<RenderRequest>();
	private final AtomicBoolean busy = new AtomicBoolean(false);

	private PlantUmlRenderService() {
	}

	static PlantUmlRenderService getInstance() {
		return INSTANCE;
	}

	RichPreviewIcon getIcon(final String source, final NodeModel node) {
		final String key = RichCache.hash("plantuml", source);
		final RichPreviewIcon cached = memoryCache.get(key);
		if (cached != null) {
			return cached;
		}
		final RichPreviewIcon fromDisk = loadDisk(key);
		if (fromDisk != null) {
			memoryCache.put(key, fromDisk);
			return fromDisk;
		}
		if (!PlantUmlCliRenderer.ensureAvailable()) {
			return RichPreviewIcon.error("plantuml", PlantUmlCliRenderer.getLastError());
		}
		registerWaiter(key, node);
		if (inFlight.putIfAbsent(key, Boolean.TRUE) == null) {
			queue.offer(new RenderRequest(key, source));
			schedulePump();
		}
		return RichPreviewIcon.placeholder("plantuml", "PlantUML…");
	}

	private void schedulePump() {
		if (!busy.compareAndSet(false, true)) {
			return;
		}
		final Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					RenderRequest req;
					while ((req = queue.poll()) != null) {
						try {
							final BufferedImage img = PlantUmlCliRenderer.render(req.source);
							saveDisk(req.key, img);
							finish(req.key, RichPreviewIcon.fromImage("plantuml", img));
						}
						catch (Throwable t) {
							LogUtils.warn("PlantUML render failed", t);
							finish(req.key, RichPreviewIcon.error("plantuml", t.getMessage()));
						}
					}
				}
				finally {
					busy.set(false);
					if (!queue.isEmpty()) {
						schedulePump();
					}
				}
			}
		}, "plantuml-render");
		t.setDaemon(true);
		t.start();
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

	private void finish(final String key, final RichPreviewIcon icon) {
		if (icon != null && icon.getFullImage() != null) {
			memoryCache.put(key, icon);
		}
		inFlight.remove(key);
		refreshWaiters(key);
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
						LogUtils.warn("PlantUML: node refresh failed", t);
					}
				}
			}
		});
	}

	private RichPreviewIcon loadDisk(final String key) {
		try {
			final File png = RichCache.pngFile(key);
			if (png.isFile()) {
				final BufferedImage img = ImageIO.read(png);
				if (img != null) {
					return RichPreviewIcon.fromImage("plantuml", img);
				}
			}
		}
		catch (Throwable t) {
			LogUtils.warn("PlantUML: disk cache read failed", t);
		}
		return null;
	}

	private void saveDisk(final String key, final BufferedImage image) {
		try {
			ImageIO.write(image, "png", RichCache.pngFile(key));
		}
		catch (Throwable t) {
			LogUtils.warn("PlantUML: disk cache write failed", t);
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
}
