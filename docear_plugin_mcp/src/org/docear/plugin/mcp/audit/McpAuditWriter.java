package org.docear.plugin.mcp.audit;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.docear.plugin.mcp.DocearMcpConfig;
import org.docear.plugin.mcp.json.JsonParser;
import org.docear.plugin.mcp.json.JsonValue;
import org.docear.plugin.mcp.json.JsonWriter;
import org.freeplane.core.util.LogUtils;

final class McpAuditWriter implements Runnable {

	private static final Charset UTF8 = Charset.forName("UTF-8");

	private final LinkedBlockingQueue<McpAuditEvent> queue;
	private final McpAuditDatabase database;
	private final File overflowFile;
	private final int batchSize;
	private final Thread worker;
	private volatile boolean running = true;

	McpAuditWriter(final McpAuditDatabase database, final File overflowFile) {
		this.database = database;
		this.overflowFile = overflowFile;
		this.batchSize = DocearMcpConfig.getAuditBatchSize();
		this.queue = new LinkedBlockingQueue<McpAuditEvent>(DocearMcpConfig.getAuditQueueSize());
		this.worker = new Thread(this, "docear-mcp-audit-writer");
		this.worker.setDaemon(true);
	}

	void start() {
		worker.start();
	}

	void shutdown() {
		running = false;
		worker.interrupt();
		try {
			worker.join(5000L);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		flushRemaining();
	}

	boolean enqueue(final McpAuditEvent event) {
		if (event == null) {
			return false;
		}
		if (queue.offer(event)) {
			return true;
		}
		appendOverflow(event);
		return false;
	}

	int pendingCount() {
		return queue.size();
	}

	public void run() {
		final List<McpAuditEvent> batch = new ArrayList<McpAuditEvent>(batchSize);
		while (running || !queue.isEmpty() || overflowFile.exists()) {
			try {
				drainOverflowIntoQueue();
				queue.drainTo(batch, batchSize);
				if (batch.isEmpty()) {
					final McpAuditEvent one = queue.poll(200L, TimeUnit.MILLISECONDS);
					if (one != null) {
						batch.add(one);
					}
				}
				if (batch.isEmpty()) {
					continue;
				}
				database.insertBatch(batch);
				batch.clear();
			}
			catch (InterruptedException e) {
				if (!running) {
					break;
				}
			}
			catch (Exception e) {
				LogUtils.warn("Docear MCP audit batch write failed: " + e.getMessage(), e);
				replayBatchToOverflow(batch);
				batch.clear();
				sleepQuietly(500L);
			}
		}
	}

	private void flushRemaining() {
		final List<McpAuditEvent> batch = new ArrayList<McpAuditEvent>();
		queue.drainTo(batch);
		drainOverflowIntoQueue();
		queue.drainTo(batch);
		if (batch.isEmpty()) {
			return;
		}
		try {
			database.insertBatch(batch);
		}
		catch (Exception e) {
			LogUtils.warn("Docear MCP audit final flush failed: " + e.getMessage(), e);
			replayBatchToOverflow(batch);
		}
	}

	private void drainOverflowIntoQueue() {
		if (!overflowFile.exists()) {
			return;
		}
		BufferedReader reader = null;
		final File temp = new File(overflowFile.getAbsolutePath() + ".tmp");
		BufferedWriter kept = null;
		try {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(overflowFile), UTF8));
			kept = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(temp), UTF8));
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.trim().length() == 0) {
					continue;
				}
				final McpAuditEvent event = decodeOverflowLine(line);
				if (event != null && queue.offer(event)) {
					continue;
				}
				if (event != null) {
					kept.write(line);
					kept.newLine();
				}
			}
		}
		catch (Exception e) {
			LogUtils.warn("Docear MCP audit overflow replay failed: " + e.getMessage(), e);
		}
		finally {
			closeQuietly(reader);
			closeQuietly(kept);
		}
		if (!overflowFile.delete()) {
			// keep temp if original cannot be removed
		}
		if (temp.exists() && temp.length() > 0L) {
			temp.renameTo(overflowFile);
		}
		else if (temp.exists()) {
			temp.delete();
		}
	}

	private void appendOverflow(final McpAuditEvent event) {
		BufferedWriter writer = null;
		try {
			final File parent = overflowFile.getParentFile();
			if (parent != null && !parent.exists()) {
				parent.mkdirs();
			}
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(overflowFile, true), UTF8));
			writer.write(encodeOverflowLine(event));
			writer.newLine();
		}
		catch (Exception e) {
			LogUtils.warn("Docear MCP audit overflow write failed: " + e.getMessage(), e);
		}
		finally {
			closeQuietly(writer);
		}
	}

	private void replayBatchToOverflow(final List<McpAuditEvent> batch) {
		for (int i = 0; i < batch.size(); i++) {
			appendOverflow(batch.get(i));
		}
	}

	private static String encodeOverflowLine(final McpAuditEvent event) {
		final JsonValue value = JsonValue.ofMap(McpAuditService.eventToMap(event));
		return JsonWriter.write(value);
	}

	private static McpAuditEvent decodeOverflowLine(final String line) {
		try {
			final JsonValue value = JsonParser.parse(line);
			return McpAuditService.eventFromMap(value.asMap());
		}
		catch (Exception e) {
			LogUtils.warn("Docear MCP audit overflow line skipped: " + e.getMessage(), e);
			return null;
		}
	}

	private static void sleepQuietly(final long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static void closeQuietly(final java.io.Closeable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
			}
			catch (Exception e) {
				// ignore
			}
		}
	}
}
