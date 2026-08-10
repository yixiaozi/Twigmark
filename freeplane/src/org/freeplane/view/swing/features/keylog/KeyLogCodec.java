package org.freeplane.view.swing.features.keylog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Compact keystroke chunk: zlib( [u16 keyId][u16 deltaMs]* ).
 */
final class KeyLogCodec {
	private KeyLogCodec() {
	}

	static final class Event {
		final int keyId;
		final int deltaMs;

		Event(final int keyId, final int deltaMs) {
			this.keyId = keyId;
			this.deltaMs = deltaMs < 0 ? 0 : (deltaMs > 65535 ? 65535 : deltaMs);
		}
	}

	static byte[] encode(final List events) throws Exception {
		final ByteArrayOutputStream raw = new ByteArrayOutputStream(events.size() * 4 + 16);
		final DataOutputStream out = new DataOutputStream(raw);
		for (int i = 0; i < events.size(); i++) {
			final Event e = (Event) events.get(i);
			out.writeShort(e.keyId & 0xffff);
			out.writeShort(e.deltaMs & 0xffff);
		}
		out.flush();
		final ByteArrayOutputStream zipped = new ByteArrayOutputStream(Math.max(32, raw.size() / 2));
		final DeflaterOutputStream def = new DeflaterOutputStream(zipped, new Deflater(Deflater.BEST_SPEED));
		try {
			def.write(raw.toByteArray());
		}
		finally {
			def.close();
		}
		return zipped.toByteArray();
	}

	static List decode(final byte[] blob) throws Exception {
		final List events = new ArrayList();
		if (blob == null || blob.length == 0) {
			return events;
		}
		final InflaterInputStream inf = new InflaterInputStream(new ByteArrayInputStream(blob));
		try {
			final DataInputStream in = new DataInputStream(inf);
			while (true) {
				final int keyId;
				final int deltaMs;
				try {
					keyId = in.readUnsignedShort();
					deltaMs = in.readUnsignedShort();
				}
				catch (java.io.EOFException eof) {
					break;
				}
				events.add(new Event(keyId, deltaMs));
			}
		}
		finally {
			inf.close();
		}
		return events;
	}

	/** Reconstruct absolute timestamps from session start. */
	static long[] timesOf(final long startTs, final List events) {
		final long[] times = new long[events.size()];
		long t = startTs;
		for (int i = 0; i < events.size(); i++) {
			final Event e = (Event) events.get(i);
			t += e.deltaMs;
			times[i] = t;
		}
		return times;
	}
}
