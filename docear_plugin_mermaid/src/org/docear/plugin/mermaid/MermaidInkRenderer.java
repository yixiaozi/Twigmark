package org.docear.plugin.mermaid;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

/**
 * Online fallback: https://mermaid.ink/img/&lt;base64url(source)&gt;
 */
final class MermaidInkRenderer {

	private MermaidInkRenderer() {
	}

	static BufferedImage render(final String source) throws Exception {
		final String encoded = base64Url(source.getBytes(StandardCharsets.UTF_8));
		final URL url = new URL("https://mermaid.ink/img/" + encoded);
		final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setConnectTimeout(15000);
		conn.setReadTimeout(30000);
		conn.setRequestProperty("User-Agent", "Twigmark-Mermaid/1.0");
		conn.connect();
		final int code = conn.getResponseCode();
		if (code < 200 || code >= 300) {
			throw new IllegalStateException("mermaid.ink HTTP " + code);
		}
		final InputStream in = conn.getInputStream();
		try {
			final BufferedImage img = ImageIO.read(in);
			if (img == null) {
				throw new IllegalStateException("mermaid.ink returned non-image");
			}
			return img;
		}
		finally {
			in.close();
			conn.disconnect();
		}
	}

	private static String base64Url(final byte[] data) {
		return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(data);
	}
}
