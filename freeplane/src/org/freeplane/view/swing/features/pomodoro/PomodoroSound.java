package org.freeplane.view.swing.features.pomodoro;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.LogUtils;

/**
 * Classic kitchen-timer style tomato ring (synthesized two-tone chime).
 */
final class PomodoroSound {
	private static final String PROP_ENABLED = "pomodoro_ring_enabled";

	private PomodoroSound() {
	}

	static boolean isEnabled() {
		return Boolean.parseBoolean(ResourceController.getResourceController().getProperty(PROP_ENABLED, "true"));
	}

	static void setEnabled(final boolean enabled) {
		ResourceController.getResourceController().setProperty(PROP_ENABLED, Boolean.toString(enabled));
	}

	/** Play ring asynchronously (stop / classic complete). */
	static void playRing() {
		if (!isEnabled()) {
			return;
		}
		final Thread t = new Thread(new Runnable() {
			public void run() {
				try {
					playChime();
				}
				catch (Exception e) {
					try {
						java.awt.Toolkit.getDefaultToolkit().beep();
					}
					catch (Exception ignored) {
					}
					LogUtils.info("Pomodoro ring fallback to Toolkit.beep()");
				}
			}
		}, "pomodoro-ring");
		t.setDaemon(true);
		t.start();
	}

	private static void playChime() throws Exception {
		final float sampleRate = 22050f;
		final AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
		final SourceDataLine line = AudioSystem.getSourceDataLine(format);
		line.open(format);
		line.start();
		// Classic tomato: bright ding → lower ding → soft echo
		writeTone(line, sampleRate, 880.0, 160, 0.55);
		Thread.sleep(40);
		writeTone(line, sampleRate, 660.0, 220, 0.45);
		Thread.sleep(60);
		writeTone(line, sampleRate, 990.0, 120, 0.25);
		line.drain();
		line.stop();
		line.close();
	}

	private static void writeTone(final SourceDataLine line, final float sampleRate, final double freqHz,
			final int durationMs, final double volume) {
		final int samples = (int) (sampleRate * durationMs / 1000.0);
		final byte[] buf = new byte[samples * 2];
		for (int i = 0; i < samples; i++) {
			final double t = i / sampleRate;
			final double env = Math.min(1.0, Math.min(i / (sampleRate * 0.01), (samples - i) / (sampleRate * 0.04)));
			final double sample = Math.sin(2.0 * Math.PI * freqHz * t) * volume * env;
			final short val = (short) (sample * Short.MAX_VALUE);
			buf[i * 2] = (byte) (val & 0xff);
			buf[i * 2 + 1] = (byte) ((val >> 8) & 0xff);
		}
		line.write(buf, 0, buf.length);
	}
}
