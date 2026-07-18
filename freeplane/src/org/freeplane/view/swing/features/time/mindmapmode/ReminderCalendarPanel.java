package org.freeplane.view.swing.features.time.mindmapmode;

import org.freeplane.core.ui.theme.DocearUiTheme;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;

import org.freeplane.core.util.HtmlUtils;

/**
 * Month, week and day calendar for reminder tasks.
 */
final class ReminderCalendarPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private static final String VIEW_MONTH = "month";
	private static final String VIEW_WEEK = "week";
	private static final String VIEW_DAY = "day";
	private static final int MODE_MONTH = 0;
	private static final int MODE_WEEK = 1;
	private static final int MODE_DAY = 2;
	private static final String[] WEEK_HEADERS = { "\u5468\u4e00", "\u5468\u4e8c", "\u5468\u4e09", "\u5468\u56db", "\u5468\u4e94", "\u5468\u516d", "\u5468\u65e5" };

	interface NavigationHandler {
		void openEntry(ReminderCalendarEntry entry);
	}

	interface CheckInHandler {
		void checkInEntry(ReminderCalendarEntry entry, long occurrenceAt);
	}

	private final SimpleDateFormat monthTitleFormat = new SimpleDateFormat("yyyy\u5e74M\u6708", Locale.CHINA);
	private final SimpleDateFormat dayTitleFormat = new SimpleDateFormat("M\u6708d\u65e5 EEEE", Locale.CHINA);
	private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.CHINA);

	private final JLabel titleLabel = new JLabel(" ");
	private final JLabel detailLabel = new JLabel("\u9009\u4e2d\u65e5\u671f\u7684\u4efb\u52a1\uff1a\u5355\u51fb\u8df3\u8f6c\u8282\u70b9\uff0c\u53cc\u51fb\u6253\u5361");
	private final DefaultListModel detailModel = new DefaultListModel();
	private final JList detailList = new JList(detailModel);
	private final JPanel monthGrid = new JPanel(new GridLayout(0, 7, 2, 2));
	private final JPanel weekGrid = new JPanel(new GridLayout(1, 7, 4, 0));
	private final JPanel dayPanel = new JPanel(new BorderLayout(4, 4));
	private final CardLayout cardLayout = new CardLayout();
	private final JPanel calendarCard = new JPanel(cardLayout);
	private final JPanel detailPanel = new JPanel(new BorderLayout(2, 2));
	private final List entries = new ArrayList();
	private long visibleRangeStart;
	private long selectedDayStart = ReminderCycleScheduler.startOfDay(System.currentTimeMillis());
	private int viewMode = MODE_MONTH;
	private NavigationHandler navigationHandler;
	private CheckInHandler checkInHandler;
	private boolean checkInEnabled = true;

	ReminderCalendarPanel() {
		this("\u4efb\u52a1\u65e5\u5386");
	}

	ReminderCalendarPanel(final String borderTitle) {
		super(new BorderLayout(4, 4));
		DocearUiTheme.styleCanvas(this);
		setBorder(DocearUiTheme.pageBorder());
		setToolTipText(borderTitle);

		final JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		final JButton prevButton = new JButton("\u25C0");
		final JButton nextButton = new JButton("\u25B6");
		final JButton todayButton = new JButton("\u4eca\u5929");
		final JToggleButton monthButton = new JToggleButton("\u6708\u89c6\u56fe", true);
		final JToggleButton weekButton = new JToggleButton("\u5468\u89c6\u56fe");
		final JToggleButton dayButton = new JToggleButton("\u65e5\u89c6\u56fe");
		final ButtonGroup viewGroup = new ButtonGroup();
		viewGroup.add(monthButton);
		viewGroup.add(weekButton);
		viewGroup.add(dayButton);
		toolbar.add(prevButton);
		toolbar.add(nextButton);
		toolbar.add(todayButton);
		toolbar.add(titleLabel);
		toolbar.add(monthButton);
		toolbar.add(weekButton);
		toolbar.add(dayButton);
		add(toolbar, BorderLayout.NORTH);

		for (int i = 0; i < 7; i++) {
			monthGrid.add(createHeaderLabel(WEEK_HEADERS[i]));
		}
		calendarCard.add(new JScrollPane(monthGrid), VIEW_MONTH);
		calendarCard.add(new JScrollPane(weekGrid), VIEW_WEEK);
		calendarCard.add(dayPanel, VIEW_DAY);
		add(calendarCard, BorderLayout.CENTER);

		detailList.setVisibleRowCount(4);
		detailList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		detailList.setCellRenderer(createOccurrenceRenderer());
		installOpenOnClick(detailList);
		detailPanel.add(detailLabel, BorderLayout.NORTH);
		detailPanel.add(new JScrollPane(detailList), BorderLayout.CENTER);
		detailPanel.setPreferredSize(new Dimension(0, 110));
		add(detailPanel, BorderLayout.SOUTH);

		prevButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				navigatePrevious();
			}
		});
		nextButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				navigateNext();
			}
		});
		todayButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				selectedDayStart = ReminderCycleScheduler.startOfDay(System.currentTimeMillis());
				syncVisibleRangeToSelection();
				rebuildCalendar();
			}
		});
		monthButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				switchView(MODE_MONTH, VIEW_MONTH, monthButton, weekButton, dayButton);
			}
		});
		weekButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				switchView(MODE_WEEK, VIEW_WEEK, weekButton, monthButton, dayButton);
			}
		});
		dayButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				switchView(MODE_DAY, VIEW_DAY, dayButton, monthButton, weekButton);
			}
		});

		visibleRangeStart = startOfMonth(System.currentTimeMillis());
		rebuildCalendar();
	}

	void setCheckInEnabled(final boolean checkInEnabled) {
		this.checkInEnabled = checkInEnabled;
	}

	void setEntries(final List newEntries) {
		entries.clear();
		if (newEntries != null) {
			for (int i = 0; i < newEntries.size(); i++) {
				final Object item = newEntries.get(i);
				if (item instanceof ReminderCalendarEntry) {
					entries.add(item);
				}
				else if (item instanceof RecurringReminderEntry) {
					entries.add(ReminderCalendarEntry.fromRecurring((RecurringReminderEntry) item));
				}
			}
		}
		rebuildCalendar();
	}

	void setNavigationHandler(final NavigationHandler navigationHandler) {
		this.navigationHandler = navigationHandler;
	}

	void setCheckInHandler(final CheckInHandler checkInHandler) {
		this.checkInHandler = checkInHandler;
	}

	private void switchView(final int mode, final String card, final JToggleButton selected,
			final JToggleButton other1, final JToggleButton other2) {
		viewMode = mode;
		selected.setSelected(true);
		other1.setSelected(false);
		other2.setSelected(false);
		syncVisibleRangeToSelection();
		cardLayout.show(calendarCard, card);
		detailPanel.setVisible(mode != MODE_DAY);
		rebuildCalendar();
	}

	private void navigatePrevious() {
		if (viewMode == MODE_WEEK) {
			visibleRangeStart = ReminderCycleScheduler.addDays(visibleRangeStart, -7);
		}
		else if (viewMode == MODE_DAY) {
			selectedDayStart = ReminderCycleScheduler.addDays(selectedDayStart, -1);
			visibleRangeStart = selectedDayStart;
		}
		else {
			visibleRangeStart = ReminderCycleScheduler.addMonths(visibleRangeStart, -1);
		}
		rebuildCalendar();
	}

	private void navigateNext() {
		if (viewMode == MODE_WEEK) {
			visibleRangeStart = ReminderCycleScheduler.addDays(visibleRangeStart, 7);
		}
		else if (viewMode == MODE_DAY) {
			selectedDayStart = ReminderCycleScheduler.addDays(selectedDayStart, 1);
			visibleRangeStart = selectedDayStart;
		}
		else {
			visibleRangeStart = ReminderCycleScheduler.addMonths(visibleRangeStart, 1);
		}
		rebuildCalendar();
	}

	private void syncVisibleRangeToSelection() {
		if (viewMode == MODE_WEEK) {
			visibleRangeStart = ReminderCycleScheduler.startOfWeek(selectedDayStart);
		}
		else if (viewMode == MODE_DAY) {
			visibleRangeStart = selectedDayStart;
		}
		else {
			visibleRangeStart = startOfMonth(selectedDayStart);
		}
	}

	private DefaultListCellRenderer createOccurrenceRenderer() {
		return new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			public Component getListCellRendererComponent(final JList list, final Object value, final int index,
					final boolean isSelected, final boolean cellHasFocus) {
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof OccurrenceItem) {
					setText(formatOccurrence((OccurrenceItem) value));
				}
				else if (value instanceof String) {
					setText((String) value);
				}
				return this;
			}
		};
	}

	private void installOpenOnClick(final JList list) {
		list.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(final MouseEvent e) {
				final int index = list.locationToIndex(e.getPoint());
				if (index < 0) {
					return;
				}
				final Object value = list.getModel().getElementAt(index);
				if (!(value instanceof OccurrenceItem)) {
					return;
				}
				list.setSelectedIndex(index);
				final OccurrenceItem item = (OccurrenceItem) value;
				if (e.getClickCount() >= 2 && checkInEnabled && item.entry.recurring) {
					checkInEntry(item.entry, item.occurrenceAt);
				}
				else if (e.getClickCount() == 1) {
					openEntry(item.entry);
				}
			}
		});
	}

	private void checkInEntry(final ReminderCalendarEntry entry, final long occurrenceAt) {
		if (checkInHandler != null && entry != null) {
			checkInHandler.checkInEntry(entry, occurrenceAt);
		}
	}

	private void openEntry(final ReminderCalendarEntry entry) {
		if (navigationHandler != null && entry != null) {
			navigationHandler.openEntry(entry);
		}
	}

	private void selectDay(final long dayStart) {
		selectedDayStart = dayStart;
		if (viewMode == MODE_DAY) {
			visibleRangeStart = dayStart;
		}
		rebuildCalendar();
	}

	private void rebuildCalendar() {
		if (viewMode == MODE_WEEK) {
			rebuildWeekView();
		}
		else if (viewMode == MODE_DAY) {
			rebuildDayView();
		}
		else {
			rebuildMonthView();
		}
		if (viewMode != MODE_DAY) {
			updateDetailList();
		}
	}

	private void rebuildMonthView() {
		titleLabel.setText(monthTitleFormat.format(new Date(visibleRangeStart)));
		monthGrid.removeAll();
		for (int i = 0; i < 7; i++) {
			monthGrid.add(createHeaderLabel(WEEK_HEADERS[i]));
		}
		final long monthEnd = ReminderCycleScheduler.addMonths(visibleRangeStart, 1);
		final Map dayMap = buildOccurrenceMap(visibleRangeStart, monthEnd);
		final Calendar cal = Calendar.getInstance(Locale.CHINA);
		cal.setTimeInMillis(visibleRangeStart);
		cal.setFirstDayOfWeek(Calendar.MONDAY);
		int leading = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7;
		for (int i = 0; i < leading; i++) {
			monthGrid.add(createEmptyCell());
		}
		cal.setTimeInMillis(visibleRangeStart);
		while (cal.getTimeInMillis() < monthEnd) {
			final long dayStart = ReminderCycleScheduler.startOfDay(cal.getTimeInMillis());
			final List dayEntries = (List) dayMap.get(Long.valueOf(dayStart));
			monthGrid.add(createDayCell(cal.get(Calendar.DAY_OF_MONTH), dayStart, dayEntries));
			cal.add(Calendar.DAY_OF_MONTH, 1);
		}
		while (monthGrid.getComponentCount() % 7 != 0) {
			monthGrid.add(createEmptyCell());
		}
		monthGrid.revalidate();
		monthGrid.repaint();
	}

	private void rebuildWeekView() {
		final long weekEnd = ReminderCycleScheduler.addDays(visibleRangeStart, 7);
		titleLabel.setText(dayTitleFormat.format(new Date(visibleRangeStart)) + " - "
				+ dayTitleFormat.format(new Date(ReminderCycleScheduler.addDays(visibleRangeStart, 6))));
		weekGrid.removeAll();
		final Map dayMap = buildOccurrenceMap(visibleRangeStart, weekEnd);
		for (int i = 0; i < 7; i++) {
			final long dayStart = ReminderCycleScheduler.addDays(visibleRangeStart, i);
			final List dayEntries = (List) dayMap.get(Long.valueOf(dayStart));
			weekGrid.add(createDayColumn(dayStart, dayEntries));
		}
		weekGrid.revalidate();
		weekGrid.repaint();
	}

	private void rebuildDayView() {
		titleLabel.setText(dayTitleFormat.format(new Date(selectedDayStart)));
		dayPanel.removeAll();
		final long dayEnd = ReminderCycleScheduler.addDays(selectedDayStart, 1);
		final Map dayMap = buildOccurrenceMap(selectedDayStart, dayEnd);
		final List dayEntries = (List) dayMap.get(Long.valueOf(selectedDayStart));
		dayPanel.add(createDayColumn(selectedDayStart, dayEntries), BorderLayout.CENTER);
		dayPanel.revalidate();
		dayPanel.repaint();
	}

	private JPanel createDayColumn(final long dayStart, final List dayEntries) {
		final JPanel column = new JPanel(new BorderLayout(2, 2));
		column.setBorder(DocearUiTheme.hairlineBorder());
		final Calendar cal = Calendar.getInstance(Locale.CHINA);
		cal.setTimeInMillis(dayStart);
		final JLabel header = new JLabel(WEEK_HEADERS[(cal.get(Calendar.DAY_OF_WEEK) + 5) % 7] + " "
				+ cal.get(Calendar.DAY_OF_MONTH), JLabel.CENTER);
		header.setFont(header.getFont().deriveFont(Font.BOLD));
		if (viewMode != MODE_DAY) {
			header.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			header.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(final MouseEvent e) {
					selectDay(dayStart);
				}
			});
		}
		column.add(header, BorderLayout.NORTH);
		final DefaultListModel model = new DefaultListModel();
		if (dayEntries != null) {
			for (int i = 0; i < dayEntries.size(); i++) {
				model.addElement(dayEntries.get(i));
			}
		}
		final JList list = new JList(model);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(createOccurrenceRenderer());
		installOpenOnClick(list);
		column.add(new JScrollPane(list), BorderLayout.CENTER);
		if (viewMode != MODE_DAY) {
			column.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(final MouseEvent e) {
					if (e.getSource() == column) {
						selectDay(dayStart);
					}
				}
			});
		}
		if (dayStart == selectedDayStart && viewMode != MODE_DAY) {
			column.setBackground(DocearUiTheme.ACCENT_WASH);
		}
		if (dayStart == ReminderCycleScheduler.startOfDay(System.currentTimeMillis())) {
			column.setBorder(BorderFactory.createLineBorder(DocearUiTheme.ACCENT, 2));
		}
		return column;
	}

	private JButton createDayCell(final int dayOfMonth, final long dayStart, final List dayEntries) {
		final int count = dayEntries == null ? 0 : dayEntries.size();
		final String text = count > 0 ? dayOfMonth + "\n(" + count + ")" : String.valueOf(dayOfMonth);
		final JButton button = new JButton(text);
		button.setMargin(new java.awt.Insets(2, 2, 2, 2));
		if (dayStart == selectedDayStart) {
			button.setBackground(DocearUiTheme.SELECTION);
		}
		else if (count > 0) {
			button.setForeground(DocearUiTheme.ACCENT_DEEP);
		}
		if (dayStart == ReminderCycleScheduler.startOfDay(System.currentTimeMillis())) {
			button.setBorder(BorderFactory.createLineBorder(DocearUiTheme.ACCENT, 2));
		}
		button.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				selectDay(dayStart);
			}
		});
		return button;
	}

	private void updateDetailList() {
		final long dayEnd = ReminderCycleScheduler.addDays(selectedDayStart, 1);
		final Map dayMap = buildOccurrenceMap(selectedDayStart, dayEnd);
		final List dayEntries = (List) dayMap.get(Long.valueOf(selectedDayStart));
		detailModel.clear();
		detailLabel.setText(dayTitleFormat.format(new Date(selectedDayStart)) + "\u7684\u4efb\u52a1");
		if (dayEntries == null || dayEntries.isEmpty()) {
			detailModel.addElement("\u6682\u65e0\u4efb\u52a1");
			return;
		}
		for (int i = 0; i < dayEntries.size(); i++) {
			detailModel.addElement(dayEntries.get(i));
		}
	}

	private String formatOccurrence(final OccurrenceItem item) {
		final String duration = ReminderTaskFormatter.formatDurationPadding(item.entry.taskTime, 4);
		return timeFormat.format(new Date(item.occurrenceAt)) + duration + "  "
				+ normalizeTaskText(item.entry.nodeText) + "  [" + item.entry.file.getName() + "]";
	}

	private Map buildOccurrenceMap(final long rangeStart, final long rangeEnd) {
		final Map map = new HashMap();
		for (int i = 0; i < entries.size(); i++) {
			final ReminderCalendarEntry entry = (ReminderCalendarEntry) entries.get(i);
			final List occurrences = enumerateOccurrences(entry, rangeStart, rangeEnd);
			for (int j = 0; j < occurrences.size(); j++) {
				final long occurrenceAt = ((Long) occurrences.get(j)).longValue();
				final long dayStart = ReminderCycleScheduler.startOfDay(occurrenceAt);
				List dayList = (List) map.get(Long.valueOf(dayStart));
				if (dayList == null) {
					dayList = new ArrayList();
					map.put(Long.valueOf(dayStart), dayList);
				}
				dayList.add(new OccurrenceItem(entry, occurrenceAt));
			}
		}
		for (final Iterator it = map.values().iterator(); it.hasNext();) {
			final List dayList = (List) it.next();
			Collections.sort(dayList, new Comparator() {
				public int compare(final Object o1, final Object o2) {
					final OccurrenceItem a = (OccurrenceItem) o1;
					final OccurrenceItem b = (OccurrenceItem) o2;
					return Long.compare(a.occurrenceAt, b.occurrenceAt);
				}
			});
		}
		return map;
	}

	static List enumerateOccurrences(final ReminderCalendarEntry entry, final long rangeStart, final long rangeEnd) {
		if (entry == null || rangeEnd <= rangeStart) {
			return Collections.emptyList();
		}
		if (entry.recurring && entry.cycleConfig != null && entry.cycleConfig.isRecurring()) {
			return ReminderCycleScheduler.enumerateOccurrencesInRange(entry.remindAt, entry.cycleConfig, rangeStart,
					rangeEnd);
		}
		if (entry.remindAt >= rangeStart && entry.remindAt < rangeEnd) {
			final List single = new ArrayList(1);
			single.add(Long.valueOf(entry.remindAt));
			return single;
		}
		return Collections.emptyList();
	}

	private static JLabel createHeaderLabel(final String text) {
		final JLabel label = new JLabel(text, JLabel.CENTER);
		label.setFont(label.getFont().deriveFont(Font.BOLD));
		return label;
	}

	private static JLabel createEmptyCell() {
		final JLabel label = new JLabel(" ");
		label.setEnabled(false);
		return label;
	}

	private static long startOfMonth(final long millis) {
		final Calendar cal = Calendar.getInstance(Locale.CHINA);
		cal.setTimeInMillis(millis);
		cal.set(Calendar.DAY_OF_MONTH, 1);
		return ReminderCycleScheduler.startOfDay(cal.getTimeInMillis());
	}

	private static String normalizeTaskText(final String text) {
		if (text == null) {
			return "";
		}
		return HtmlUtils.removeHtmlTagsFromString(text).replaceAll("\\s+", " ").trim();
	}

	private static final class OccurrenceItem {
		private final ReminderCalendarEntry entry;
		private final long occurrenceAt;

		private OccurrenceItem(final ReminderCalendarEntry entry, final long occurrenceAt) {
			this.entry = entry;
			this.occurrenceAt = occurrenceAt;
		}
	}
}
