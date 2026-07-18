package org.freeplane.view.swing.features.time.mindmapmode;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import org.freeplane.core.ui.components.calendar.JCalendar;
import org.freeplane.core.ui.theme.DocearUiTheme;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.ui.FrameController;

/**
 * Reminder datetime editor: editable date/time field with calendar picker.
 */
class ReminderDateTimeEditorPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private static final String[] PARSE_PATTERNS = { "yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm", "yyyy-M-d H:m",
			"yyyyMMdd HHmm", "yyyy-MM-dd", "yyyy\u5e74M\u6708d\u65e5  EEEE  HH:mm", "yyyy\u5e74M\u6708d\u65e5 HH:mm",
			"M\u6708d\u65e5 HH:mm" };

	private final JTextField dateTimeField;
	private final JCalendar calendarComponent;
	private final JPopupMenu calendarPopup;
	private Calendar calendar;
	private Runnable changeListener;
	private boolean updatingUi;

	ReminderDateTimeEditorPanel() {
		super(new BorderLayout(0, 8));
		setBorder(BorderFactory.createTitledBorder(DocearUiTheme.hairlineBorder(),
				TextUtils.getText("plugins/TimeManagement.xml_reminderDateTimeLabel")));
		setOpaque(false);

		dateTimeField = new JTextField(28);
		dateTimeField.setToolTipText(TextUtils.getText("plugins/TimeManagement.xml_reminderDateTimeHint"));
		dateTimeField.addFocusListener(new FocusAdapter() {
			@Override
			public void focusLost(final FocusEvent e) {
				commitInputField();
			}
		});
		dateTimeField.addKeyListener(new KeyAdapter() {
			public void keyPressed(final KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					commitInputField();
					e.consume();
					return;
				}
				if (handleArrowKey(e)) {
					e.consume();
				}
			}
		});

		calendarComponent = new JCalendar(new Date(), Locale.CHINA, true, true, true);
		calendarComponent.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(final MouseEvent e) {
				calendarPopup.setVisible(false);
			}
		});
		calendarPopup = calendarComponent.createPopupMenu();
		calendarPopup.addPopupMenuListener(new PopupMenuListener() {
			public void popupMenuWillBecomeVisible(final PopupMenuEvent e) {
				calendarComponent.setDate(getDate());
			}

			public void popupMenuWillBecomeInvisible(final PopupMenuEvent e) {
				calendar = ReminderDateTimeFormatter.toCalendar(calendarComponent.getDate());
				refreshUi();
			}

			public void popupMenuCanceled(final PopupMenuEvent e) {
			}
		});

		final JButton calendarButton = new JButton(FrameController.dateTimeIcon);
		calendarButton.setToolTipText(TextUtils.getText("plugins/TimeManagement.xml_openCalendarButton"));
		calendarButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				if (calendarButton.isShowing()) {
					calendarPopup.show(calendarButton, 0, calendarButton.getHeight());
				}
			}
		});

		final JPanel dateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		dateRow.add(dateTimeField);
		dateRow.add(calendarButton);
		add(dateRow, BorderLayout.NORTH);

		final JPanel shortcuts = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		shortcuts.add(createButton("plugins/TimeManagement.xml_datePrevDay", new Runnable() {
			public void run() {
				adjustDate(-1);
			}
		}));
		shortcuts.add(createButton("plugins/TimeManagement.xml_dateNextDay", new Runnable() {
			public void run() {
				adjustDate(1);
			}
		}));
		shortcuts.add(createButton("plugins/TimeManagement.xml_todayButton", new Runnable() {
			public void run() {
				setDate(Calendar.getInstance().getTime());
			}
		}));
		shortcuts.add(createButton("plugins/TimeManagement.xml_inOneHourButton", new Runnable() {
			public void run() {
				adjustTime(Calendar.HOUR_OF_DAY, 1);
			}
		}));
		shortcuts.add(createButton("plugins/TimeManagement.xml_tomorrowButton", new Runnable() {
			public void run() {
				adjustDate(1);
			}
		}));
		add(shortcuts, BorderLayout.SOUTH);

		setDate(defaultDate());
	}

	void setOnChangeListener(final Runnable changeListener) {
		this.changeListener = changeListener;
	}

	Date getDate() {
		return ReminderDateTimeFormatter.fromCalendar(calendar);
	}

	void setDate(final Date date) {
		calendar = ReminderDateTimeFormatter.toCalendar(date == null ? defaultDate() : date);
		refreshUi();
	}

	void focusInput() {
		dateTimeField.requestFocusInWindow();
		dateTimeField.selectAll();
	}

	boolean handleArrowKey(final KeyEvent e) {
		if (e.getKeyCode() != KeyEvent.VK_LEFT && e.getKeyCode() != KeyEvent.VK_RIGHT && e.getKeyCode() != KeyEvent.VK_UP
				&& e.getKeyCode() != KeyEvent.VK_DOWN) {
			return false;
		}
		if (e.getKeyCode() == KeyEvent.VK_LEFT) {
			adjustDate(-1);
		}
		else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
			adjustDate(1);
		}
		else if (e.getKeyCode() == KeyEvent.VK_UP) {
			if (e.isShiftDown()) {
				adjustTime(Calendar.HOUR_OF_DAY, 1);
			}
			else {
				adjustTime(Calendar.MINUTE, 15);
			}
		}
		else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
			if (e.isShiftDown()) {
				adjustTime(Calendar.HOUR_OF_DAY, -1);
			}
			else {
				adjustTime(Calendar.MINUTE, -15);
			}
		}
		return true;
	}

	private Date defaultDate() {
		final Calendar defaultCalendar = Calendar.getInstance();
		defaultCalendar.add(Calendar.HOUR_OF_DAY, 1);
		ReminderDateTimeFormatter.normalizeSeconds(defaultCalendar);
		return defaultCalendar.getTime();
	}

	private void adjustDate(final int days) {
		ensureCalendar();
		calendar.add(Calendar.DAY_OF_MONTH, days);
		refreshUi();
	}

	private void adjustTime(final int field, final int amount) {
		ensureCalendar();
		calendar.add(field, amount);
		refreshUi();
	}

	private void ensureCalendar() {
		if (calendar == null) {
			calendar = ReminderDateTimeFormatter.toCalendar(defaultDate());
		}
	}

	private void commitInputField() {
		final Date parsed = parseDateTime(dateTimeField.getText());
		if (parsed != null) {
			calendar = ReminderDateTimeFormatter.toCalendar(parsed);
			refreshUi();
		}
		else if (calendar != null) {
			updatingUi = true;
			dateTimeField.setText(ReminderDateTimeFormatter.formatMainDisplay(ReminderDateTimeFormatter
					.fromCalendar(calendar)));
			updatingUi = false;
		}
	}

	private void refreshUi() {
		updatingUi = true;
		final Date date = ReminderDateTimeFormatter.fromCalendar(calendar);
		dateTimeField.setText(ReminderDateTimeFormatter.formatMainDisplay(date));
		updatingUi = false;
		notifyChanged();
	}

	private void notifyChanged() {
		if (changeListener != null) {
			changeListener.run();
		}
	}

	private Date parseDateTime(final String value) {
		if (value == null) {
			return null;
		}
		final String trimmed = value.trim();
		if (trimmed.length() == 0) {
			return null;
		}
		for (int i = 0; i < PARSE_PATTERNS.length; i++) {
			try {
				final SimpleDateFormat format = new SimpleDateFormat(PARSE_PATTERNS[i], Locale.CHINA);
				format.setLenient(false);
				final Date parsed = format.parse(trimmed);
				if (PARSE_PATTERNS[i].equals("yyyy-MM-dd")) {
					final Calendar parsedCalendar = Calendar.getInstance();
					parsedCalendar.setTime(parsed);
					parsedCalendar.set(Calendar.HOUR_OF_DAY, 9);
					parsedCalendar.set(Calendar.MINUTE, 0);
					ReminderDateTimeFormatter.normalizeSeconds(parsedCalendar);
					return parsedCalendar.getTime();
				}
				return parsed;
			}
			catch (final ParseException e) {
				// try next pattern
			}
		}
		return null;
	}

	private JButton createButton(final String textKey, final Runnable action) {
		final JButton button = new JButton(TextUtils.getText(textKey));
		button.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				action.run();
			}
		});
		return button;
	}
}
