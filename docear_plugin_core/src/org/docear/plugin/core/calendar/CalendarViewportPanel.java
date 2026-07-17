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
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

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
	private final JLabel dayTaskHeader = new JLabel("当日安排");
	private final JButton newTaskBtn = ghost("新建");
	private final CardLayout cards = new CardLayout();
	private final JPanel cardHost = new JPanel(cards);
	private final JScrollPane timedScroll;
	private final JLabel titleLabel = new JLabel("安排中心");
	private final JLabel subtitleLabel = new JLabel(" ");
	private final JLabel statusLabel = new JLabel("加载任务中…");
	private final SimpleDateFormat monthTitle = new SimpleDateFormat("yyyy年M月", Locale.CHINA);
	private final SimpleDateFormat dayTitle = new SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINA);
	private final SimpleDateFormat rangeTitle = new SimpleDateFormat("M月d日", Locale.CHINA);
	private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.CHINA);

	private int mode = MODE_WEEK;
	private List appointments = Collections.EMPTY_LIST;
	private Date pendingCreateStart;
	private Date pendingCreateEnd;
	private int loadGeneration;
	private boolean showPomodoro = true;
	private final JToggleButton monthBtn = segment("月");
	private final JToggleButton weekBtn = segment("周");
	private final JToggleButton dayBtn = segment("日");
	private final JToggleButton scale5 = scale("5分");
	private final JToggleButton scale15 = scale("15分");
	private final JToggleButton scale30 = scale("30分");
	private final JToggleButton scale60 = scale("60分");
	private final JToggleButton pomodoroToggle = segment("番茄");

	CalendarViewportPanel() {
		super(new BorderLayout(0, 0));
		setOpaque(true);
		setBackground(CalendarTheme.CANVAS);

		add(buildHero(), BorderLayout.NORTH);

		final JPanel body = new JPanel(new BorderLayout(10, 0));
		body.setOpaque(false);
		body.setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));

		final JPanel side = new JPanel();
		side.setLayout(new javax.swing.BoxLayout(side, javax.swing.BoxLayout.Y_AXIS));
		side.setOpaque(false);
		side.setPreferredSize(new Dimension(220, 0));
		final JPanel miniNav = new JPanel(new BorderLayout(4, 0));
		miniNav.setOpaque(false);
		miniNav.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		final JButton miniPrev = ghost("<");
		final JButton miniNext = ghost(">");
		miniNav.add(miniPrev, BorderLayout.WEST);
		miniNav.add(miniNext, BorderLayout.EAST);
		side.add(miniNav);
		side.add(miniMonth);
		final JPanel legend = new JPanel(new GridLayout(0, 1, 0, 2));
		legend.setOpaque(false);
		legend.setBorder(BorderFactory.createEmptyBorder(6, 4, 4, 4));
		legend.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
		legend.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
		legend.add(legendLine(CalendarTheme.EVENT_A, "安排"));
		legend.add(legendLine(CalendarTheme.EVENT_D, "周期"));
		legend.add(legendLine(CalendarTaskService.POMODORO_COLOR, "番茄钟"));
		side.add(legend);

		final JPanel taskHead = new JPanel(new BorderLayout(4, 0));
		taskHead.setOpaque(false);
		taskHead.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
		taskHead.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		taskHead.setBorder(BorderFactory.createEmptyBorder(8, 2, 4, 2));
		dayTaskHeader.setFont(CalendarTheme.font(12f, Font.BOLD));
		dayTaskHeader.setForeground(CalendarTheme.TEXT);
		taskHead.add(dayTaskHeader, BorderLayout.WEST);
		taskHead.add(newTaskBtn, BorderLayout.EAST);
		side.add(taskHead);

		dayTaskList.setFont(CalendarTheme.font(11f));
		dayTaskList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		dayTaskList.setFixedCellHeight(22);
		dayTaskList.setBackground(CalendarTheme.SURFACE);
		final JScrollPane taskScroll = new JScrollPane(dayTaskList);
		taskScroll.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
		taskScroll.setBorder(BorderFactory.createLineBorder(CalendarTheme.HAIRLINE));
		taskScroll.setPreferredSize(new Dimension(214, 180));
		side.add(taskScroll);

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
				refreshDayTaskList();
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
				statusLabel.setText("已打开节点：" + appt.title);
			}

			public void onAppointmentActivated(final CalendarAppointment appt) {
				if (CalendarTaskService.isPomodoro(appt)) {
					CalendarTaskService.open(appt);
					return;
				}
				final boolean ok = CalendarTaskService.checkInOrComplete(appt);
				statusLabel.setText(ok ? "已打卡/完成：" + appt.title : "打卡已取消");
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
				statusLabel.setText("已打开节点：" + appt.title);
			}

			public void onAppointmentActivated(final CalendarAppointment appt) {
				if (CalendarTaskService.isPomodoro(appt)) {
					CalendarTaskService.open(appt);
					return;
				}
				final boolean ok = CalendarTaskService.checkInOrComplete(appt);
				statusLabel.setText(ok ? "已打卡/完成：" + appt.title : "打卡已取消");
				if (ok) {
					reloadTasksAsync();
				}
			}

			public void onAppointmentMoved(final CalendarAppointment appt, final long newStartMillis) {
				if (CalendarTaskService.isPomodoro(appt)) {
					statusLabel.setText("番茄钟时段不可拖拽改期");
					reloadTasksAsync();
					return;
				}
				final boolean ok = CalendarTaskService.reschedule(appt, newStartMillis);
				if (ok) {
					statusLabel.setText("已改期到 " + timeFmt.format(new Date(newStartMillis)) + "（记得保存导图）· "
					        + appt.title);
					reloadTasksAsync();
				}
				else {
					statusLabel.setText("改期失败：" + appt.title);
					reloadTasksAsync();
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
				statusLabel.setText("选中 " + timeFmt.format(start) + " – " + timeFmt.format(end)
				        + " · 点左侧「新建」或双击空白处创建任务");
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
		dayView.setTimeScaleMinutes(30);
		dayView.setStartDate(DayViewPanel.startOfWeekMonday(new Date()));
		monthView.setMonthStart(MonthViewPanel.firstOfMonth(new Date()));
		monthView.setSelectedDay(new Date());
		miniMonth.setSelectedDay(new Date());
		setMode(MODE_WEEK);
		scrollToWorkHours();
		ReminderCalendarBridge.warmEntriesAsync();
		reloadTasksAsync();
	}

	void setAppointments(final List list, final Map dayCounts) {
		appointments = list == null ? Collections.EMPTY_LIST : list;
		monthView.setAppointments(appointments);
		dayView.setAppointments(appointments);
		miniMonth.setDayCounts(dayCounts);
		refreshDayTaskList();
	}

	void reloadTasksAsync() {
		reloadTasksAsync(false);
	}

	void reloadTasksAsync(final boolean forceRescan) {
		final int gen = ++loadGeneration;
		if (forceRescan) {
			ReminderCalendarBridge.invalidateReminderCache();
		}
		statusLabel.setText(forceRescan ? "正在重新扫描导图提醒…" : "正在加载提醒…");
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
							statusLabel.setText("当前视图暂无安排 · 拖选时间后点「新建」· 点「刷新」重扫（"
							        + result.elapsedMs + "ms）");
						}
						else {
							statusLabel.setText("已加载 " + result.appointments.size()
							        + " 条 · 单击打开 · 拖拽改期 · 双击打卡 · 拖选空白新建（" + result.elapsedMs + "ms）");
						}
						refreshChrome();
					}
				});
			}
		}, "Calendar-LoadReminders");
		thread.setDaemon(true);
		thread.start();
	}

	private void refreshDayTaskList() {
		dayTaskModel.clear();
		Date day;
		if (mode == MODE_MONTH) {
			day = monthView.getSelectedDay();
		}
		else if (mode == MODE_DAY) {
			day = DayViewPanel.startOfDay(dayView.getStartDate());
		}
		else {
			day = miniMonth.getSelectedDay();
		}
		final List dayTasks = CalendarTaskService.dayTaskLines(appointments, day);
		dayTaskHeader.setText("当日安排 · " + dayTasks.size());
		if (dayTasks.isEmpty()) {
			dayTaskModel.addElement("（无）");
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
					setText(timeFmt.format(appt.start) + "  " + appt.title);
					setForeground(appt.color != null ? appt.color.darker() : CalendarTheme.TEXT);
				}
				setFont(CalendarTheme.font(11f));
				return this;
			}
		});
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
		final String title = JOptionPane.showInputDialog(this,
		        "在选中节点下新建子节点，并设置提醒：\n" + timeFmt.format(start) + " – " + timeFmt.format(end), "新安排");
		if (title == null) {
			return;
		}
		final boolean ok = CalendarTaskService.createTask(title, start.getTime(), end.getTime());
		if (ok) {
			statusLabel.setText("已新建：" + title + "（记得保存导图）");
			dayView.clearSelection();
			pendingCreateStart = null;
			pendingCreateEnd = null;
			reloadTasksAsync(true);
		}
		else {
			statusLabel.setText("新建失败：请先选中一个导图节点作为父节点");
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
		final JMenuItem open = new JMenuItem("打开节点");
		open.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				CalendarTaskService.open(appt);
			}
		});
		menu.add(open);
		if (!CalendarTaskService.isPomodoro(appt)) {
			final JMenuItem checkIn = new JMenuItem("打卡 / 完成");
			checkIn.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					if (CalendarTaskService.checkInOrComplete(appt)) {
						reloadTasksAsync();
					}
				}
			});
			menu.add(checkIn);
		}
		final JMenuItem refresh = new JMenuItem("刷新任务");
		refresh.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				reloadTasksAsync(true);
			}
		});
		menu.addSeparator();
		menu.add(refresh);
		menu.show(invoker, x, y);
	}

	void refreshChrome() {
		if (mode == MODE_MONTH) {
			subtitleLabel.setText(monthTitle.format(monthView.getMonthStart()) + "  ·  月视图  ·  "
			        + appointments.size() + " 条");
			cards.show(cardHost, CARD_MONTH);
		}
		else if (mode == MODE_WEEK) {
			final Date start = dayView.getStartDate();
			final Calendar end = Calendar.getInstance();
			end.setTime(start);
			end.add(Calendar.DAY_OF_MONTH, 6);
			subtitleLabel.setText(rangeTitle.format(start) + " — " + rangeTitle.format(end.getTime()) + "  ·  周视图 · "
			        + dayView.getTimeScaleMinutes() + " 分钟 · " + appointments.size() + " 条");
			cards.show(cardHost, CARD_TIMED);
		}
		else {
			subtitleLabel.setText(dayTitle.format(dayView.getStartDate()) + "  ·  日视图 · "
			        + dayView.getTimeScaleMinutes() + " 分钟 · " + appointments.size() + " 条");
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
		scrollToWorkHours();
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
		final JButton today = heroBtn("今天");
		final JButton refresh = heroBtn("刷新");
		final JButton close = heroBtn("返回导图");
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
