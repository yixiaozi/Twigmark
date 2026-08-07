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
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import org.freeplane.core.util.TextUtils;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderCalendarBridge;

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
	private final DefaultListModel dayTaskModel = new DefaultListModel();
	private final JList dayTaskList = new JList(dayTaskModel);
	private final JLabel dayTaskHeader = new JLabel(TextUtils.getText("CalendarViewport.dayTasks"));
	private final JButton newTaskBtn = ghost(TextUtils.getText("CalendarViewport.new"));
	private final CardLayout cards = new CardLayout();
	private final JPanel cardHost = new JPanel(cards);
	private final JScrollPane timedScroll;
	private final JLabel titleLabel = new JLabel(TextUtils.getText("CalendarViewport.title"));
	private final JLabel subtitleLabel = new JLabel(" ");
	private final JLabel statusLabel = new JLabel(TextUtils.getText("CalendarViewport.loading"));
	private final SimpleDateFormat monthTitle = new SimpleDateFormat(TextUtils.getText("CalendarViewport.date.monthTitle"), Locale.getDefault());
	private final SimpleDateFormat dayTitle = new SimpleDateFormat(TextUtils.getText("CalendarViewport.date.dayTitle"), Locale.getDefault());
	private final SimpleDateFormat rangeTitle = new SimpleDateFormat(TextUtils.getText("CalendarViewport.date.rangeTitle"), Locale.getDefault());
	private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());

	private int mode = MODE_WEEK;
	private List appointments = Collections.EMPTY_LIST;
	private Date pendingCreateStart;
	private Date pendingCreateEnd;
	private int loadGeneration;
	private boolean showPomodoro = true;
	private final JToggleButton monthBtn = segment(TextUtils.getText("CalendarViewport.mode.month"));
	private final JToggleButton weekBtn = segment(TextUtils.getText("CalendarViewport.mode.week"));
	private final JToggleButton dayBtn = segment(TextUtils.getText("CalendarViewport.mode.day"));
	private final JToggleButton rangeAllDay = scale(TextUtils.getText("CalendarViewport.range.allDay"));
	private final JToggleButton rangeWork = scale("4–22");
	private final JToggleButton pomodoroToggle = segment(TextUtils.getText("CalendarViewport.pomodoro"));

	CalendarViewportPanel() {
		super(new BorderLayout(0, 0));
		setOpaque(true);
		setBackground(CalendarTheme.CANVAS);

		add(buildHero(), BorderLayout.NORTH);

		final JPanel body = new JPanel(new BorderLayout(0, 0));
		body.setOpaque(false);
		body.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 8));

		final JPanel side = new JPanel(new BorderLayout(0, 0));
		side.setOpaque(false);
		side.setMinimumSize(new Dimension(160, 0));
		side.setPreferredSize(new Dimension(200, 0));

		final JPanel sideTop = new JPanel();
		sideTop.setLayout(new BoxLayout(sideTop, BoxLayout.Y_AXIS));
		sideTop.setOpaque(false);
		sideTop.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
		final JPanel miniNav = new JPanel(new BorderLayout(4, 0));
		miniNav.setOpaque(false);
		miniNav.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
		miniNav.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		final JButton miniPrev = ghost("<");
		final JButton miniNext = ghost(">");
		miniNav.add(miniPrev, BorderLayout.WEST);
		miniNav.add(miniNext, BorderLayout.EAST);
		sideTop.add(miniNav);
		miniMonth.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
		sideTop.add(miniMonth);
		final JPanel legend = new JPanel(new GridLayout(0, 1, 0, 2));
		legend.setOpaque(false);
		legend.setBorder(BorderFactory.createEmptyBorder(4, 2, 2, 2));
		legend.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
		legend.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
		legend.add(legendLine(CalendarTheme.EVENT_A, TextUtils.getText("CalendarViewport.legend.event")));
		legend.add(legendLine(CalendarTheme.EVENT_D, TextUtils.getText("CalendarViewport.legend.recurring")));
		legend.add(legendLine(CalendarTaskService.POMODORO_COLOR, TextUtils.getText("CalendarViewport.legend.pomodoro")));
		sideTop.add(legend);

		final JPanel taskHead = new JPanel(new BorderLayout(4, 0));
		taskHead.setOpaque(false);
		taskHead.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
		taskHead.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		taskHead.setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));
		dayTaskHeader.setFont(CalendarTheme.font(12f, Font.BOLD));
		dayTaskHeader.setForeground(CalendarTheme.TEXT);
		taskHead.add(dayTaskHeader, BorderLayout.WEST);
		taskHead.add(newTaskBtn, BorderLayout.EAST);
		sideTop.add(taskHead);
		side.add(sideTop, BorderLayout.NORTH);

		dayTaskList.setFont(CalendarTheme.font(11f));
		dayTaskList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		dayTaskList.setFixedCellHeight(22);
		dayTaskList.setBackground(CalendarTheme.SURFACE);
		final JScrollPane taskScroll = new JScrollPane(dayTaskList);
		taskScroll.setBorder(BorderFactory.createLineBorder(CalendarTheme.HAIRLINE));
		side.add(taskScroll, BorderLayout.CENTER);

		timedScroll = new JScrollPane(dayView);
		timedScroll.setBorder(BorderFactory.createLineBorder(CalendarTheme.HAIRLINE));
		timedScroll.getVerticalScrollBar().setUnitIncrement(22);
		timedScroll.getViewport().setBackground(CalendarTheme.CANVAS);

		monthView.setBorder(BorderFactory.createLineBorder(CalendarTheme.HAIRLINE));
		cardHost.setOpaque(false);
		cardHost.add(monthView, CARD_MONTH);
		cardHost.add(timedScroll, CARD_TIMED);

		final JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, side, cardHost);
		split.setOpaque(false);
		split.setBorder(null);
		split.setContinuousLayout(true);
		split.setOneTouchExpandable(false);
		split.setResizeWeight(0.0);
		split.setDividerSize(5);
		split.setDividerLocation(200);
		body.add(split, BorderLayout.CENTER);
		add(body, BorderLayout.CENTER);
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				split.setDividerLocation(200);
			}
		});

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
		scaleGroup.add(rangeAllDay);
		scaleGroup.add(rangeWork);
		rangeWork.setSelected(true);

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
		rangeAllDay.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				applyHourRange(0, 24);
			}
		});
		rangeWork.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				applyHourRange(4, 22);
			}
		});
		pomodoroToggle.setSelected(true);
		pomodoroToggle.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				showPomodoro = pomodoroToggle.isSelected();
				reloadTasksAsync(false);
			}
		});
		newTaskBtn.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				promptCreateTask();
			}
		});
		dayTaskList.addMouseListener(new java.awt.event.MouseAdapter() {
			public void mouseClicked(final java.awt.event.MouseEvent e) {
				final int idx = dayTaskList.locationToIndex(e.getPoint());
				if (idx < 0 || idx >= dayTaskModel.size()) {
					return;
				}
				final Object item = dayTaskModel.get(idx);
				if (item instanceof CalendarAppointment) {
					if (e.getClickCount() >= 2) {
						CalendarTaskService.open((CalendarAppointment) item);
					}
					else {
						statusLabel.setText(((CalendarAppointment) item).title);
					}
				}
			}
		});

		miniPrev.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				miniMonth.shiftMonth(-1);
				reloadDayCountsOnly();
			}
		});
		miniNext.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				miniMonth.shiftMonth(1);
				reloadDayCountsOnly();
			}
		});
		miniMonth.setListener(new MiniMonthPanel.Listener() {
			public void onDayChosen(final Date day) {
				if (mode == MODE_WEEK) {
					miniMonth.setSelectedDay(day);
					final Date weekStart = DayViewPanel.startOfWeekMonday(day);
					if (!weekStart.equals(DayViewPanel.startOfDay(dayView.getStartDate()))) {
						dayView.setStartDate(weekStart);
						reloadTasksAsync();
					}
					refreshDayTaskList();
					refreshChrome();
					scrollToSelectedDayTasks();
					return;
				}
				jumpToDay(day, mode == MODE_MONTH ? MODE_MONTH : MODE_DAY);
			}
		});
		monthView.setDayHandler(new MonthViewPanel.DayHandler() {
			public void onDaySelected(final Date dayStart) {
				miniMonth.setSelectedDay(dayStart);
				refreshDayTaskList();
				refreshChrome();
			}

			public void onDayActivated(final Date dayStart) {
				jumpToDay(dayStart, MODE_DAY);
			}
		});
		monthView.setAppointmentListener(new MonthViewPanel.AppointmentListener() {
			public void onAppointmentClicked(final CalendarAppointment appt) {
				CalendarTaskService.open(appt);
				statusLabel.setText(TextUtils.format("CalendarViewport.status.opened", appt.title));
			}

			public void onAppointmentActivated(final CalendarAppointment appt) {
				if (CalendarTaskService.isPomodoro(appt)) {
					CalendarTaskService.open(appt);
					return;
				}
				final boolean ok = CalendarTaskService.checkInOrComplete(appt);
				statusLabel.setText(ok ? TextUtils.format("CalendarViewport.status.checkedIn", appt.title) : TextUtils.getText("CalendarViewport.status.checkInCancelled"));
				if (ok) {
					reloadTasksAsync();
				}
			}

			public void onAppointmentPopup(final CalendarAppointment appt, final int x, final int y) {
				showAppointmentMenu(appt, monthView, x, y);
			}
		});
		dayView.setDayHeaderListener(new DayViewPanel.DayHeaderListener() {
			public void onDayHeaderClicked(final Date dayStart) {
				jumpToDay(dayStart, MODE_DAY);
			}
		});
		dayView.setAppointmentListener(new DayViewPanel.AppointmentListener() {
			public void onAppointmentClicked(final CalendarAppointment appt) {
				CalendarTaskService.open(appt);
				statusLabel.setText(TextUtils.format("CalendarViewport.status.opened", appt.title));
			}

			public void onAppointmentActivated(final CalendarAppointment appt) {
				if (CalendarTaskService.isPomodoro(appt)) {
					CalendarTaskService.open(appt);
					return;
				}
				final boolean ok = CalendarTaskService.checkInOrComplete(appt);
				statusLabel.setText(ok ? TextUtils.format("CalendarViewport.status.checkedIn", appt.title)
				        : TextUtils.getText("CalendarViewport.status.checkInCancelled"));
				if (ok) {
					reloadTasksAsync();
				}
			}

			public void onAppointmentMoved(final CalendarAppointment appt, final long newStartMillis) {
				if (CalendarTaskService.isPomodoro(appt)) {
					statusLabel.setText(TextUtils.getText("CalendarViewport.status.pomodoroNoMove"));
					reloadTasksAsync();
					return;
				}
				final boolean ok = CalendarTaskService.reschedule(appt, newStartMillis);
				if (ok) {
					statusLabel.setText(TextUtils.format("CalendarViewport.status.rescheduled", timeFmt.format(new Date(newStartMillis))));
					reloadTasksAsync(false);
				}
				else {
					statusLabel.setText(TextUtils.format("CalendarViewport.status.rescheduleFailed", appt.title));
					reloadTasksAsync(true);
				}
			}

			public void onAppointmentResized(final CalendarAppointment appt, final long newEndMillis) {
				if (CalendarTaskService.isPomodoro(appt)) {
					statusLabel.setText(TextUtils.getText("CalendarViewport.status.pomodoroNoResize"));
					reloadTasksAsync();
					return;
				}
				final boolean ok = CalendarTaskService.resizeDuration(appt, newEndMillis);
				if (ok) {
					final int minutes = (int) Math.max(5L, (newEndMillis - appt.startMillis()) / 60000L);
					statusLabel.setText(TextUtils.format("CalendarViewport.status.resized", Integer.valueOf(minutes)));
					reloadTasksAsync(false);
				}
				else {
					statusLabel.setText(TextUtils.format("CalendarViewport.status.resizeFailed", appt.title));
					reloadTasksAsync(true);
				}
			}

			public void onAppointmentPopup(final CalendarAppointment appt, final int x, final int y) {
				showAppointmentMenu(appt, dayView, x, y);
			}
		});
		dayView.setSelectionListener(new DayViewPanel.SelectionListener() {
			public void onTimeSelected(final Date start, final Date end) {
				pendingCreateStart = start;
				pendingCreateEnd = end;
				miniMonth.setSelectedDay(start);
				refreshDayTaskList();
				statusLabel.setText(TextUtils.format("CalendarViewport.status.selected", timeFmt.format(start),
				        timeFmt.format(end)));
			}
		});
		dayView.setEmptyPopupListener(new DayViewPanel.EmptyPopupListener() {
			public void onEmptyPopup(final Date start, final Date end, final int x, final int y) {
				pendingCreateStart = start;
				pendingCreateEnd = end;
				miniMonth.setSelectedDay(start);
				refreshDayTaskList();
				showCreateMenu(dayView, x, y, start, end);
			}
		});
		dayView.addMouseListener(new java.awt.event.MouseAdapter() {
			public void mouseClicked(final java.awt.event.MouseEvent e) {
				if (e.getClickCount() >= 2 && dayView.getAppointmentAt(e.getX(), e.getY()) == null
				        && pendingCreateStart != null && pendingCreateEnd != null) {
					promptCreateTask();
				}
			}
		});

		dayView.setDaysToShow(7);
		dayView.setHourRange(4, 22);
		dayView.setStartDate(DayViewPanel.startOfWeekMonday(new Date()));
		monthView.setMonthStart(MonthViewPanel.firstOfMonth(new Date()));
		monthView.setSelectedDay(new Date());
		miniMonth.setSelectedDay(new Date());
		setMode(MODE_WEEK);
		timedScroll.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
			public void componentResized(final java.awt.event.ComponentEvent e) {
				fitDayViewIfNeeded();
			}
		});
		ReminderCalendarBridge.warmEntriesAsync();
		reloadTasksAsync();
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				fitDayViewIfNeeded();
			}
		});
	}

	void setAppointments(final List list, final Map dayCounts) {
		appointments = list == null ? Collections.EMPTY_LIST : list;
		monthView.setAppointments(appointments);
		dayView.setAppointments(appointments);
		miniMonth.setDayCounts(dayCounts);
		refreshDayTaskList();
		scrollToSelectedDayTasks();
	}

	void reloadTasksAsync() {
		reloadTasksAsync(false);
	}

	void reloadTasksAsync(final boolean forceRescan) {
		final int gen = ++loadGeneration;
		if (forceRescan) {
			ReminderCalendarBridge.invalidateReminderCache();
		}
		statusLabel.setText(forceRescan ? TextUtils.getText("CalendarViewport.status.rescanning") : TextUtils.getText("CalendarViewport.status.loadingReminders"));
		final long[] range = visibleRange();
		final long[] monthRange = miniMonthVisibleRange();
		final boolean pomo = showPomodoro;
		final Thread thread = new Thread(new Runnable() {
			public void run() {
				final CalendarTaskService.LoadResult result = CalendarTaskService.loadBundle(range[0], range[1],
				        monthRange[0], monthRange[1], pomo);
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						if (gen != loadGeneration) {
							return;
						}
						setAppointments(result.appointments, result.dayCounts);
						if (result.appointments.isEmpty()) {
							statusLabel.setText(TextUtils.format("CalendarViewport.status.empty",
							        Long.valueOf(result.elapsedMs)));
						}
						else {
							statusLabel.setText(TextUtils.format("CalendarViewport.status.loaded",
							        Integer.valueOf(result.appointments.size()), Long.valueOf(result.elapsedMs)));
						}
						refreshChrome();
					}
				});
			}
		}, "Calendar-LoadReminders");
		thread.setDaemon(true);
		thread.start();
	}

	private Date selectedListDay() {
		if (mode == MODE_MONTH) {
			return monthView.getSelectedDay();
		}
		if (mode == MODE_DAY) {
			return DayViewPanel.startOfDay(dayView.getStartDate());
		}
		return miniMonth.getSelectedDay();
	}

	private void refreshDayTaskList() {
		dayTaskModel.clear();
		final Date day = selectedListDay();
		dayView.setSelectedDayHighlight(mode == MODE_WEEK ? day : null);
		final List dayTasks = CalendarTaskService.dayTaskLines(appointments, day);
		dayTaskHeader.setText(TextUtils.format("CalendarViewport.dayTasks.header", Integer.valueOf(dayTasks.size())));
		if (dayTasks.isEmpty()) {
			dayTaskModel.addElement(TextUtils.getText("CalendarViewport.dayTasks.empty"));
		}
		else {
			for (int i = 0; i < dayTasks.size(); i++) {
				dayTaskModel.addElement(dayTasks.get(i));
			}
		}
		dayTaskList.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			public java.awt.Component getListCellRendererComponent(final JList list, final Object value, final int index,
			        final boolean isSelected, final boolean cellHasFocus) {
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof CalendarAppointment) {
					final CalendarAppointment appt = (CalendarAppointment) value;
					final String start = timeFmt.format(appt.start);
					final String end = appt.end != null ? timeFmt.format(appt.end) : "";
					setText(end.length() > 0 ? start + "–" + end + "  " + appt.title : start + "  " + appt.title);
					setForeground(appt.color != null ? appt.color.darker() : CalendarTheme.TEXT);
				}
				else {
					setForeground(CalendarTheme.TEXT_FAINT);
				}
				setFont(CalendarTheme.font(11f));
				return this;
			}
		});
	}

	private void scrollToSelectedDayTasks() {
		if (mode == MODE_MONTH) {
			return;
		}
		final Date day = selectedListDay();
		final List dayTasks = CalendarTaskService.dayTaskLines(appointments, day);
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				Date focus = null;
				if (!dayTasks.isEmpty()) {
					focus = ((CalendarAppointment) dayTasks.get(0)).start;
				}
				else if (pendingCreateStart != null) {
					focus = pendingCreateStart;
				}
				if (focus == null) {
					return;
				}
				final int y = dayView.getYForTime(focus);
				if (y < 0) {
					return;
				}
				final int viewH = timedScroll.getViewport().getHeight();
				timedScroll.getVerticalScrollBar().setValue(Math.max(0, y - Math.max(48, viewH / 3)));
				dayView.repaint();
			}
		});
	}

	private void showCreateMenu(final java.awt.Component invoker, final int x, final int y, final Date start,
	        final Date end) {
		final JPopupMenu menu = new JPopupMenu();
		final JMenuItem create = new JMenuItem(
		        TextUtils.format("CalendarViewport.menu.createTask", timeFmt.format(start), timeFmt.format(end)));
		create.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				promptCreateTask();
			}
		});
		menu.add(create);
		menu.show(invoker, x, y);
	}

	private void promptCreateTask() {
		Date start = pendingCreateStart;
		Date end = pendingCreateEnd;
		if (start == null || end == null) {
			start = dayView.getSelectionStart();
			end = dayView.getSelectionEnd();
		}
		if (start == null || end == null) {
			final Calendar cal = Calendar.getInstance();
			cal.set(Calendar.SECOND, 0);
			cal.set(Calendar.MILLISECOND, 0);
			cal.set(Calendar.MINUTE, (cal.get(Calendar.MINUTE) / 30) * 30);
			start = cal.getTime();
			end = new Date(start.getTime() + 30L * 60L * 1000L);
		}
		final Boolean ok = CalendarTaskService.promptCreateTask(this, start.getTime(), end.getTime());
		if (Boolean.TRUE.equals(ok)) {
			statusLabel.setText(TextUtils.getText("CalendarViewport.status.created"));
			miniMonth.setSelectedDay(start);
			dayView.clearSelection();
			pendingCreateStart = start;
			pendingCreateEnd = end;
			reloadTasksAsync(true);
		}
		else if (Boolean.FALSE.equals(ok)) {
			statusLabel.setText(TextUtils.getText("CalendarViewport.status.createFailed"));
		}
	}

	private void reloadDayCountsOnly() {
		final long[] monthRange = miniMonthVisibleRange();
		final Thread thread = new Thread(new Runnable() {
			public void run() {
				final Map counts = ReminderCalendarBridge.loadDayCounts(monthRange[0], monthRange[1]);
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						miniMonth.setDayCounts(counts);
					}
				});
			}
		}, "Calendar-DayCounts");
		thread.setDaemon(true);
		thread.start();
	}

	private long[] miniMonthVisibleRange() {
		final Calendar start = Calendar.getInstance();
		start.setTime(MonthViewPanel.firstOfMonth(miniMonth.getMonthStart()));
		start.add(Calendar.DAY_OF_MONTH, -7);
		final Calendar end = Calendar.getInstance();
		end.setTime(MonthViewPanel.firstOfMonth(miniMonth.getMonthStart()));
		end.add(Calendar.MONTH, 1);
		end.add(Calendar.DAY_OF_MONTH, 7);
		return new long[] { start.getTimeInMillis(), end.getTimeInMillis() };
	}

	private long[] visibleRange() {
		final Calendar start = Calendar.getInstance();
		final Calendar end = Calendar.getInstance();
		if (mode == MODE_MONTH) {
			start.setTime(MonthViewPanel.firstOfMonth(monthView.getMonthStart()));
			start.add(Calendar.DAY_OF_MONTH, -7);
			end.setTime(MonthViewPanel.firstOfMonth(monthView.getMonthStart()));
			end.add(Calendar.MONTH, 1);
			end.add(Calendar.DAY_OF_MONTH, 7);
		}
		else if (mode == MODE_WEEK) {
			start.setTime(dayView.getStartDate());
			end.setTime(dayView.getStartDate());
			end.add(Calendar.DAY_OF_MONTH, 7);
		}
		else {
			start.setTime(DayViewPanel.startOfDay(dayView.getStartDate()));
			end.setTime(start.getTime());
			end.add(Calendar.DAY_OF_MONTH, 1);
		}
		return new long[] { start.getTimeInMillis(), end.getTimeInMillis() };
	}

	private void showAppointmentMenu(final CalendarAppointment appt, final java.awt.Component invoker, final int x,
	        final int y) {
		final JPopupMenu menu = new JPopupMenu();
		final JMenuItem open = new JMenuItem(TextUtils.getText("CalendarViewport.menu.openNode"));
		open.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				CalendarTaskService.open(appt);
			}
		});
		menu.add(open);
		if (!CalendarTaskService.isPomodoro(appt)) {
			final JMenuItem edit = new JMenuItem(TextUtils.getText("CalendarViewport.menu.edit"));
			edit.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					promptEditTask(appt);
				}
			});
			menu.add(edit);
			final JMenuItem checkIn = new JMenuItem(TextUtils.getText("CalendarViewport.menu.checkIn"));
			checkIn.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					if (CalendarTaskService.checkInOrComplete(appt)) {
						reloadTasksAsync();
					}
				}
			});
			menu.add(checkIn);
		}
		final JMenuItem refresh = new JMenuItem(TextUtils.getText("CalendarViewport.menu.refresh"));
		refresh.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				reloadTasksAsync(true);
			}
		});
		menu.addSeparator();
		menu.add(refresh);
		menu.show(invoker, x, y);
	}

	private void promptEditTask(final CalendarAppointment appt) {
		final Boolean ok = CalendarTaskService.promptEditTask(this, appt);
		if (Boolean.TRUE.equals(ok)) {
			statusLabel.setText(TextUtils.getText("CalendarViewport.status.updated"));
			reloadTasksAsync(true);
		}
		else if (Boolean.FALSE.equals(ok)) {
			statusLabel.setText(TextUtils.getText("CalendarViewport.status.editFailed"));
		}
	}

	void refreshChrome() {
		if (mode == MODE_MONTH) {
			subtitleLabel.setText(TextUtils.format("CalendarViewport.subtitle.month",
			        monthTitle.format(monthView.getMonthStart()), Integer.valueOf(appointments.size())));
			cards.show(cardHost, CARD_MONTH);
		}
		else if (mode == MODE_WEEK) {
			final Date start = dayView.getStartDate();
			final Calendar end = Calendar.getInstance();
			end.setTime(start);
			end.add(Calendar.DAY_OF_MONTH, 6);
			subtitleLabel.setText(TextUtils.format("CalendarViewport.subtitle.week",
			        rangeTitle.format(start), rangeTitle.format(end.getTime()), dayView.getRangeLabel(),
			        Integer.valueOf(appointments.size())));
			cards.show(cardHost, CARD_TIMED);
		}
		else {
			subtitleLabel.setText(TextUtils.format("CalendarViewport.subtitle.day",
			        dayTitle.format(dayView.getStartDate()), dayView.getRangeLabel(),
			        Integer.valueOf(appointments.size())));
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
			fitDayViewIfNeeded();
		}
		else {
			dayView.setDaysToShow(1);
			fitDayViewIfNeeded();
		}
		refreshChrome();
		reloadTasksAsync();
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
		reloadTasksAsync();
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
		fitDayViewIfNeeded();
		reloadTasksAsync();
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
		final JButton prev = heroBtn("<");
		final JButton next = heroBtn(">");
		final JButton today = heroBtn(TextUtils.getText("CalendarViewport.today"));
		final JButton refresh = heroBtn(TextUtils.getText("CalendarViewport.refresh"));
		final JButton close = heroBtn(TextUtils.getText("CalendarViewport.backToMap"));
		controls.add(prev);
		controls.add(next);
		controls.add(today);
		controls.add(refresh);
		controls.add(spacer());
		controls.add(monthBtn);
		controls.add(weekBtn);
		controls.add(dayBtn);
		controls.add(spacer());
		controls.add(pomodoroToggle);
		controls.add(spacer());
		controls.add(rangeAllDay);
		controls.add(rangeWork);
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
		refresh.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				reloadTasksAsync(true);
			}
		});
		close.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				CalendarViewportService.hide();
			}
		});
		return hero;
	}

	private void applyHourRange(final int startHour, final int endHour) {
		dayView.setHourRange(startHour, endHour);
		fitDayViewIfNeeded();
		refreshChrome();
		statusLabel.setText(TextUtils.format("CalendarViewport.status.hourRange", Integer.valueOf(startHour), Integer.valueOf(endHour)));
	}

	private void fitDayViewIfNeeded() {
		if (mode == MODE_MONTH) {
			return;
		}
		final int h = timedScroll.getViewport().getHeight();
		if (h < 80) {
			return;
		}
		timedScroll.getVerticalScrollBar().setUnitIncrement(dayView.getSlotHeight());
		if (dayView.isUserZoomed()) {
			return;
		}
		dayView.fitToViewportHeight(h);
		timedScroll.getVerticalScrollBar().setValue(0);
	}

	private void setScaleEnabled(final boolean enabled) {
		rangeAllDay.setEnabled(enabled);
		rangeWork.setEnabled(enabled);
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
		button.setMargin(new java.awt.Insets(0, 0, 0, 0));
		button.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
		return button;
	}

	public Dimension getPreferredSize() {
		return new Dimension(980, 680);
	}
}
