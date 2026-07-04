package org.docear.plugin.drawio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.Charset;

import org.freeplane.core.util.LogUtils;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Serves the Draw.io HTML shell on localhost for JavaFX WebView embedding.
 */
public final class DrawioEmbedServer {

	private static final Charset UTF8 = Charset.forName("UTF-8");
	private static HttpServer server;
	private static int port = -1;
	private static String shellHtml;

	private DrawioEmbedServer() {
	}

	public static synchronized String getShellUrl() throws IOException {
		ensureStarted();
		final String embedBase = URLDecoder.decode(DrawioConfig.getEmbedUrl(), "UTF-8");
		return "http://127.0.0.1:" + port + "/shell?embedBase="
		        + java.net.URLEncoder.encode(embedBase, "UTF-8");
	}

	private static void ensureStarted() throws IOException {
		if (server != null) {
			return;
		}
		shellHtml = loadShellHtml();
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/shell", new ShellHandler());
		server.start();
		port = server.getAddress().getPort();
		LogUtils.info("Draw.io embed server listening on http://127.0.0.1:" + port + "/shell");
	}

	public static synchronized void stopServer() {
		if (server != null) {
			server.stop(0);
			server = null;
			port = -1;
		}
	}

	private static String loadShellHtml() throws IOException {
		final InputStream in = DrawioEmbedServer.class.getResourceAsStream("/drawio-shell.html");
		if (in == null) {
			throw new IOException("drawio-shell.html not found in plugin resources");
		}
		try {
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			final byte[] buffer = new byte[4096];
			int read;
			while ((read = in.read(buffer)) >= 0) {
				out.write(buffer, 0, read);
			}
			return out.toString("UTF-8");
		}
		finally {
			in.close();
		}
	}

	private static final class ShellHandler implements HttpHandler {
		public void handle(final HttpExchange exchange) throws IOException {
			final byte[] bytes = shellHtml.getBytes(UTF8);
			exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
			exchange.sendResponseHeaders(200, bytes.length);
			final OutputStream out = exchange.getResponseBody();
			out.write(bytes);
			out.close();
		}
	}
}
