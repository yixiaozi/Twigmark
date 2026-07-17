package org.docear.plugin.core.calendar;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

/**
 * Scheduling hub: MonthView + timed Week/Day (DocearReminder calendar frameworks).
 */
final class CalendarViewportPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	private static final String CARD_MONTH = "month";
	private static final String CARD_TIMED = "timed";
	private static final int MODE_MONTH = 0;
	private static final int MODE_WEEK = 1;
	private static final int MODE_DAY = 2;

	private final MonthViewPanel monthView = new MonthViewPanel();
	private final DayViewPanel dayView = new DayViewPanel();
	private final MiniMonthPanel miniMonth = new MiniMonthPanel();
	private final CardLayout cards = new CardLayout();
	private final JPanel cardHost = new JPanel(cards);
	private final JScrollPane timedScroll;
	private final JLabel titleLabel = new JLabel("安排中心");
	private final JLabel subtitleLabel = new JLabel(" ");
	private final JLabel statusLabel = new JLabel("拖拽可选分钟级时间 · 双击日期进入日视图 · 任务数据后续接入");
	private final SimpleDateFormat monthTitle = new SimpleDateFormat("yyyy年M月", Locale.CHINA);
	private final SimpleDateFormat dayTitle = new SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINA);
	private final SimpleDateFormat rangeTitle = new SimpleDateFormat("M月d日", Locale.CHINA);

	private int mode = MODE_WEEK;
	private List appointments = Collections.EMPTY_LIST;
	private final JToggleButton monthBtn = segment("月");
	private final JToggleButton weekBtn = segment("周");
	private final JToggleButton dayBtn = segment("日");
	private final JToggleButton scale5 = scale("5分");
	private final JToggleButton scale15 = scale("15分");
	private final JToggleButton scale30 = scale("30分");
	private final JToggleButton scale60 = scale("60分");

	CalendarViewportPanel() {
		super(new BorderLayout(0, 0));
		setOpaque(true);
		setBackground(CalendarTheme.CANVAS);

		add(buildHero(), BorderLayout.NORTH);

		final JPanel body = new JPanel(new BorderLayout(10, 0));
		body.setOpaque(false);
		body.setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));

		final JPanel side = new JPanel(new BorderLayout(0, 8));
		side.setOpaque(false);
		side.setPreferredSize(new Dimension(232, 0));
		final JPanel miniNav = new JPanel(new BorderLayout(4, 0));
		miniNav.setOpaque(false);
		final JButton miniPrev = ghost("◀");
		final JButton miniNext = ghost("▶");
		miniNav.add(miniPrev, BorderLayout.WEST);
		miniNav.add(miniNext, BorderLayout.EAST);
		side.add(miniNav, BorderLayout.NORTH);
		side.add(miniMonth, BorderLayout.CENTER);
		final JPanel legend = new JPanel(new GridLayout(0, 1, 0, 4));
		legend.setOpaque(false);
		legend.setBorder(BorderFactory.createEmptyBorder(8, 4, 0, 4));
		legend.add(legendLine(CalendarTheme.EVENT_A, "安排 / 任务"));
		legend.add(legendLine(CalendarTheme.EVENT_D, "周期提醒"));
		legend.add(legendLine(CalendarTheme.NOW, "当前时间"));
		side.add(legend, BorderLayout.SOUTH);

		timedScroll = new JScrollPane(dayView);
		timedScroll.setBorder(BorderFactory.createLineBorder(CalendarTheme.HAIRLINE));
		timedScroll.getVerticalScrollBar().setUnitIncrement(22);
		timedScroll.getViewport().setBackground(CalendarTheme.CANVAS);

		monthView.setBorder(BorderFactory.createLineBorder(CalendarTheme.HAIRLINE));
		cardHost.setOpaque(false);
		cardHost.add(monthView, CARD_MONTH);
		cardHost.add(timedScroll, CARD_TIMED);

		body.add(side, BorderLayout.WEST);
		body.add(cardHost, BorderLayout.CENTER);
		add(body, BorderLayout.CENTER);

		statusLabel.setFont(CalendarTheme.font(11f));
		statusLabel.setForeground(CalendarTheme.TEXT_MUTED);
		statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 14, 10, 14));
		add(statusLabel, BorderLayout.SOUTH);

		final ButtonGroup viewGroup = new ButtonGroup();
		viewGroup.add(monthBtn);
		viewGroup.add(weekBtn);
		viewGroup.add(dayBtn);
		weekBtn.setSelected(true);
		final ButtonGroup scaleGroup = new ButtonGroup();
		scaleGroup.add(scale5);
		scaleGroup.add(scale15);
		scaleGroup.add(scale30);
		scaleGroup.add(scale60);
		scale30.setSelected(true);

		monthBtn.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				setMode(MODE_MONTH);
			}
		});
		weekBtn.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				setMode(MODE_WEEK);
			}
		});
		dayBtn.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				setMode(MODE_DAY);
			}
		});
		scale5.addActionListener(scaleAction(5));
		scale15.addActionListener(scaleAction(15));
		scale30.addActionListener(scaleAction(30));
		scale60.addActionListener(scaleAction(60));

		miniPrev.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				miniMonth.shiftMonth(-1);
			}
		});
		miniNext.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				miniMonth.shiftMonth(1);
			}
		});
		miniMonth.setListener(new MiniMonthPanel.Listener() {
			public void onDayChosen(final Date day) {
				jumpToDay(day, mode == MODE_MONTH ? MODE_MONTH : MODE_DAY);
			}
		});
		monthView.setDayHandler(new MonthViewPanel.DayHandler() {
			public void onDaySelected(final Date dayStart) {
				miniMonth.setSelectedDay(dayStart);
				refreshChrome();
			}

			public void onDayActivated(final Date dayStart) {
				jumpToDay(dayStart, MODE_DAY);
			}
		});
		dayView.setDayHeaderListener(new DayViewPanel.DayHeaderListener() {
			public void onDayHeaderClicked(final Date dayStart) {
				jumpToDay(dayStart, MODE_DAY);
			}
		});

		dayView.setDaysToShow(7);
		dayView.setTimeScaleMinutes(30);
		dayView.setStartDate(DayViewPanel.startOfWeekMonday(new Date()));
		monthView.setMonthStart(MonthViewPanel.firstOfMonth(new Date()));
		monthView.setSelectedDay(new Date());
		miniMonth.setSelectedDay(new Date());
		setMode(MODE_WEEK);
		scrollToWorkHours();
	}

	void setAppointments(final List list) {
		appointments = list == null ? Collections.EMPTY_LIST : list;
		monthView.setAppointments(appointments);
		dayView.setAppointments(appointments);
	}

	void refreshChrome() {
		if (mode == MODE_MONTH) {
			subtitleLabel.setText(monthTitle.format(monthView.getMonthStart()) + "  ·  月视图");
			cards.show(cardHost, CARD_MONTH);
		}
		else if (mode == MODE_WEEK) {
			final Date start = dayView.getStartDate();
			final Calendar end = Calendar.getInstance();
			end.setTime(start);
			end.add(Calendar.DAY_OF_MONTH, 6);
			subtitleLabel.setText(rangeTitle.format(start) + " — " + rangeTitle.format(end.getTime()) + "  ·  周视图 · "
			        + dayView.getTimeScaleMinutes() + " 分钟刻度");
			cards.show(cardHost, CARD_TIMED);
		}
		else {
			subtitleLabel.setText(dayTitle.format(dayView.getStartDate()) + "  ·  日视图 · "
			        + dayView.getTimeScaleMinutes() + " 分钟刻度");
			cards.show(cardHost, CARD_TIMED);
		}
		setScaleEnabled(mode != MODE_MONTH);
	}

	private void setMode(final int newMode) {
		mode = newMode;
		monthBtn.setSelected(mode == MODE_MONTH);
		weekBtn.setSelected(mode == MODE_WEEK);
		dayBtn.setSelected(mode == MODE_DAY);
		if (mode == MODE_MONTH) {
			monthView.setMonthStart(MonthViewPanel.firstOfMonth(dayView.getStartDate()));
			monthView.setSelectedDay(dayView.getStartDate());
		}
		else if (mode == MODE_WEEK) {
			dayView.setDaysToShow(7);
			dayView.setStartDate(DayViewPanel.startOfWeekMonday(dayView.getStartDate()));
			scrollToWorkHours();
		}
		else {
			dayView.setDaysToShow(1);
			scrollToWorkHours();
		}
		refreshChrome();
		revalidate();
		repaint();
	}

	private void jumpToDay(final Date day, final int targetMode) {
		final Date d = DayViewPanel.startOfDay(day);
		dayView.setStartDate(targetMode == MODE_WEEK ? DayViewPanel.startOfWeekMonday(d) : d);
		monthView.setSelectedDay(d);
		monthView.setMonthStart(MonthViewPanel.firstOfMonth(d));
		miniMonth.setSelectedDay(d);
		setMode(targetMode);
	}

	private void shift(final int delta) {
		if (mode == MODE_MONTH) {
			final Calendar cal = Calendar.getInstance();
			cal.setTime(monthView.getMonthStart());
			cal.add(Calendar.MONTH, delta);
			monthView.setMonthStart(cal.getTime());
			miniMonth.setSelectedDay(cal.getTime());
		}
		else if (mode == MODE_WEEK) {
			final Calendar cal = Calendar.getInstance();
			cal.setTime(dayView.getStartDate());
			cal.add(Calendar.DAY_OF_MONTH, delta * 7);
			dayView.setStartDate(cal.getTime());
			miniMonth.setSelectedDay(cal.getTime());
		}
		else {
			final Calendar cal = Calendar.getInstance();
			cal.setTime(dayView.getStartDate());
			cal.add(Calendar.DAY_OF_MONTH, delta);
			dayView.setStartDate(cal.getTime());
			miniMonth.setSelectedDay(cal.getTime());
		}
		refreshChrome();
	}

	private void goToday() {
		final Date today = new Date();
		if (mode == MODE_MONTH) {
			monthView.setMonthStart(MonthViewPanel.firstOfMonth(today));
			monthView.setSelectedDay(today);
		}
		else if (mode == MODE_WEEK) {
			dayView.setStartDate(DayViewPanel.startOfWeekMonday(today));
		}
		else {
			dayView.setStartDate(DayViewPanel.startOfDay(today));
		}
		miniMonth.setSelectedDay(today);
		refreshChrome();
		scrollToWorkHours();
	}

	private JPanel buildHero() {
		final JPanel hero = new JPanel(new BorderLayout()) {
			private static final long serialVersionUID = 1L;

			protected void paintComponent(final Graphics g) {
				final Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				CalendarTheme.paintHeaderBand(g2, new Rectangle(0, 0, getWidth(), getHeight()));
				g2.dispose();
				super.paintComponent(g);
			}
		};
		hero.setOpaque(false);
		hero.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

		final JPanel left = new JPanel(new BorderLayout(0, 2));
		left.setOpaque(false);
		titleLabel.setFont(CalendarTheme.font(20f, Font.BOLD));
		titleLabel.setForeground(Color.WHITE);
		subtitleLabel.setFont(CalendarTheme.font(12f));
		subtitleLabel.setForeground(new Color(255, 255, 255, 210));
		left.add(titleLabel, BorderLayout.NORTH);
		left.add(subtitleLabel, BorderLayout.SOUTH);

		final JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		controls.setOpaque(false);
		final JButton prev = heroBtn("◀");
		final JButton next = heroBtn("▶");
		final JButton today = heroBtn("今天");
		final JButton close = heroBtn("返回导图");
		controls.add(prev);
		controls.add(next);
		controls.add(today);
		controls.add(spacer());
		controls.add(monthBtn);
		controls.add(weekBtn);
		controls.add(dayBtn);
		controls.add(spacer());
		controls.add(scale5);
		controls.add(scale15);
		controls.add(scale30);
		controls.add(scale60);
		controls.add(spacer());
		controls.add(close);

		hero.add(left, BorderLayout.WEST);
		hero.add(controls, BorderLayout.EAST);

		prev.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				shift(-1);
			}
		});
		next.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				shift(1);
			}
		});
		today.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				goToday();
			}
		});
		close.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				CalendarViewportService.hide();
			}
		});
		return hero;
	}

	private ActionListener scaleAction(final int minutes) {
		return new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				dayView.setTimeScaleMinutes(minutes);
				timedScroll.getVerticalScrollBar().setUnitIncrement(dayView.getSlotHeight());
				refreshChrome();
				scrollToWorkHours();
			}
		};
	}

	private void setScaleEnabled(final boolean enabled) {
		scale5.setEnabled(enabled);
		scale15.setEnabled(enabled);
		scale30.setEnabled(enabled);
		scale60.setEnabled(enabled);
	}

	private void scrollToWorkHours() {
		if (mode == MODE_MONTH) {
			return;
		}
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				final int y = 36 + (8 * 60 / dayView.getTimeScaleMinutes()) * dayView.getSlotHeight();
				timedScroll.getVerticalScrollBar().setValue(Math.max(0, y - 48));
			}
		});
	}

	private static JLabel spacer() {
		final JLabel label = new JLabel(" ");
		label.setPreferredSize(new Dimension(8, 8));
		return label;
	}

	private static JLabel legendLine(final Color color, final String text) {
		final JLabel label = new JLabel("●  " + text);
		label.setFont(CalendarTheme.font(11f));
		label.setForeground(CalendarTheme.TEXT_MUTED);
		label.setForeground(color.darker());
		return label;
	}

	private static JToggleButton segment(final String text) {
		final JToggleButton button = new JToggleButton(text);
		button.setFont(CalendarTheme.font(12f, Font.BOLD));
		button.setFocusable(false);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.setForeground(Color.WHITE);
		button.setOpaque(false);
		button.setContentAreaFilled(false);
		button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 120)),
		        BorderFactory.createEmptyBorder(4, 10, 4, 10)));
		return button;
	}

	private static JToggleButton scale(final String text) {
		final JToggleButton button = new JToggleButton(text);
		button.setFont(CalendarTheme.font(11f));
		button.setFocusable(false);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.setForeground(new Color(255, 255, 255, 220));
		button.setOpaque(false);
		button.setContentAreaFilled(false);
		button.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		return button;
	}

	private static JButton heroBtn(final String text) {
		final JButton button = new JButton(text);
		button.setFont(CalendarTheme.font(12f));
		button.setFocusable(false);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.setForeground(Color.WHITE);
		button.setOpaque(false);
		button.setContentAreaFilled(false);
		button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 140)),
		        BorderFactory.createEmptyBorder(4, 10, 4, 10)));
		return button;
	}

	private static JButton ghost(final String text) {
		final JButton button = new JButton(text);
		button.setFont(CalendarTheme.font(12f));
		button.setFocusable(false);
		button.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
		return button;
	}

	public Dimension getPreferredSize() {
		return new Dimension(980, 680);
	}
}
