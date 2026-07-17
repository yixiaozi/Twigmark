package org.docear.plugin.core.calendar;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

/**
 * Host chrome for the DayView calendar shown in the main map viewport.
 */
final class CalendarViewportPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	private static final Color PANEL = new Color(0xF8, 0xFA, 0xFC);
	private static final Color TEXT = new Color(0x11, 0x18, 0x27);
	private static final Color MUTED = new Color(0x6B, 0x72, 0x80);
	private static final Color ACCENT = new Color(0x0F, 0x76, 0x6E);

	private final DayViewPanel dayView = new DayViewPanel();
	private final JLabel titleLabel = new JLabel(" ");
	private final JLabel hintLabel = new JLabel("拖拽可选中时间（精确到分钟）· 任务数据后续接入");
	private final SimpleDateFormat rangeFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
	private final JScrollPane scroll;

	CalendarViewportPanel() {
		super(new BorderLayout(0, 0));
		setBackground(PANEL);
		setOpaque(true);

		final JPanel toolbar = new JPanel(new BorderLayout());
		toolbar.setOpaque(true);
		toolbar.setBackground(PANEL);
		toolbar.setBorder(BorderFactory.createCompoundBorder(
		        BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xE5, 0xE7, 0xEB)),
		        BorderFactory.createEmptyBorder(8, 10, 8, 10)));

		final JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		left.setOpaque(false);
		final JButton prev = button("◀");
		final JButton next = button("▶");
		final JButton today = button("今天");
		final JToggleButton day1 = toggle("1天");
		final JToggleButton day3 = toggle("3天");
		final JToggleButton day7 = toggle("7天");
		day7.setSelected(true);
		titleLabel.setFont(preferFont(14f));
		titleLabel.setForeground(TEXT);
		hintLabel.setFont(preferFont(11f));
		hintLabel.setForeground(MUTED);
		left.add(prev);
		left.add(next);
		left.add(today);
		left.add(titleLabel);
		left.add(day1);
		left.add(day3);
		left.add(day7);

		final JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		right.setOpaque(false);
		final JButton close = button("返回导图");
		right.add(hintLabel);
		right.add(close);

		toolbar.add(left, BorderLayout.WEST);
		toolbar.add(right, BorderLayout.EAST);
		add(toolbar, BorderLayout.NORTH);

		scroll = new JScrollPane(dayView);
		scroll.setBorder(null);
		scroll.getVerticalScrollBar().setUnitIncrement(DayViewPanel.HALF_HOUR_HEIGHT);
		scroll.getViewport().setBackground(PANEL);
		add(scroll, BorderLayout.CENTER);

		prev.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				shiftDays(-dayView.getDaysToShow());
			}
		});
		next.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				shiftDays(dayView.getDaysToShow());
			}
		});
		today.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				goToday();
			}
		});
		day1.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				setDays(1, day1, day3, day7);
			}
		});
		day3.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				setDays(3, day3, day1, day7);
			}
		});
		day7.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				setDays(7, day7, day1, day3);
			}
		});
		close.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				CalendarViewportService.hide();
			}
		});

		dayView.setDaysToShow(7);
		goToday();
		scrollToWorkHours();
	}

	void setAppointments(final List appointments) {
		dayView.setAppointments(appointments);
	}

	void refreshChrome() {
		final Date start = dayView.getStartDate();
		final Calendar end = Calendar.getInstance();
		end.setTime(start);
		end.add(Calendar.DAY_OF_MONTH, dayView.getDaysToShow() - 1);
		titleLabel.setText("日历  " + rangeFormat.format(start) + "  →  " + rangeFormat.format(end.getTime()));
	}

	private void setDays(final int days, final JToggleButton on, final JToggleButton a, final JToggleButton b) {
		on.setSelected(true);
		a.setSelected(false);
		b.setSelected(false);
		dayView.setDaysToShow(days);
		if (days == 7) {
			dayView.setStartDate(DayViewPanel.startOfWeekMonday(dayView.getStartDate()));
		}
		refreshChrome();
	}

	private void shiftDays(final int delta) {
		final Calendar cal = Calendar.getInstance();
		cal.setTime(dayView.getStartDate());
		cal.add(Calendar.DAY_OF_MONTH, delta);
		dayView.setStartDate(cal.getTime());
		refreshChrome();
	}

	private void goToday() {
		if (dayView.getDaysToShow() == 7) {
			dayView.setStartDate(DayViewPanel.startOfWeekMonday(new Date()));
		}
		else {
			dayView.setStartDate(DayViewPanel.startOfDay(new Date()));
		}
		refreshChrome();
		scrollToWorkHours();
	}

	private void scrollToWorkHours() {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				// ~8:00
				final int y = 28 + (8 * 2 * 18);
				scroll.getVerticalScrollBar().setValue(Math.max(0, y - 40));
			}
		});
	}

	private static JButton button(final String text) {
		final JButton button = new JButton(text);
		button.setFont(preferFont(12f));
		button.setFocusable(false);
		return button;
	}

	private static JToggleButton toggle(final String text) {
		final JToggleButton button = new JToggleButton(text);
		button.setFont(preferFont(12f));
		button.setFocusable(false);
		button.setForeground(ACCENT);
		return button;
	}

	private static Font preferFont(final float size) {
		final String[] prefer = new String[] { "Microsoft YaHei UI", "Microsoft YaHei", "PingFang SC",
		        "Noto Sans CJK SC", "SansSerif" };
		for (int i = 0; i < prefer.length; i++) {
			final Font font = new Font(prefer[i], Font.PLAIN, Math.round(size));
			if (font.canDisplay('历')) {
				return font.deriveFont(size);
			}
		}
		return new Font("SansSerif", Font.PLAIN, Math.round(size));
	}

	public Dimension getPreferredSize() {
		return new Dimension(900, 640);
	}
}
