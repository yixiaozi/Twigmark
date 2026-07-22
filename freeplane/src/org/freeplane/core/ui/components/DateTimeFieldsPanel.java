package org.freeplane.core.ui.components;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.beans.PropertyChangeListener;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import org.freeplane.core.ui.components.calendar.JCalendar;
import org.freeplane.features.ui.FrameController;

/**
 * Date (+ optional hour/minute) editor: calendar popup for the day, separate
 * editable hour/minute fields supporting typing, ↑↓ keys and mouse wheel.
 */
public class DateTimeFieldsPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);

	private final boolean withTime;
	private final JTextField dateField;
	private final JButton calendarButton;
	private final JTextField hourField;
	private final JTextField minuteField;
	private final JCalendar calendarComponent;
	private final JPopupMenu calendarPopup;
	private Calendar value;
	private boolean updating;
	private PropertyChangeListener changeListener;

	public DateTimeFieldsPanel(final boolean withTime) {
		this(withTime, new Date());
	}

	public DateTimeFieldsPanel(final boolean withTime, final Date initial) {
		super(new FlowLayout(FlowLayout.LEFT, 4, 0));
		this.withTime = withTime;
		setOpaque(false);
		value = Calendar.getInstance(Locale.CHINA);
		if (initial != null) {
			value.setTime(initial);
		}
		value.set(Calendar.SECOND, 0);
		value.set(Calendar.MILLISECOND, 0);

		dateField = new JTextField(10);
		dateField.setToolTipText("日期：可直接输入，或点右侧日历按钮选择");
		wireNumberLikeField(dateField, true);

		calendarComponent = new JCalendar(value.getTime(), Locale.CHINA, true, true, false);
		calendarPopup = calendarComponent.createPopupMenu();
		calendarComponent.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				calendarPopup.setVisible(false);
			}
		});
		calendarPopup.addPopupMenuListener(new PopupMenuListener() {
			public void popupMenuWillBecomeVisible(final PopupMenuEvent e) {
				calendarComponent.setDate(getDate());
			}

			public void popupMenuWillBecomeInvisible(final PopupMenuEvent e) {
				final Calendar picked = Calendar.getInstance(Locale.CHINA);
				picked.setTime(calendarComponent.getDate());
				value.set(Calendar.YEAR, picked.get(Calendar.YEAR));
				value.set(Calendar.MONTH, picked.get(Calendar.MONTH));
				value.set(Calendar.DAY_OF_MONTH, picked.get(Calendar.DAY_OF_MONTH));
				refreshFieldsFromValue();
				fireChanged();
			}

			public void popupMenuCanceled(final PopupMenuEvent e) {
			}
		});

		if (FrameController.dateTimeIcon != null) {
			calendarButton = new JButton(FrameController.dateTimeIcon);
		}
		else {
			calendarButton = new JButton("日历");
		}
		calendarButton.setToolTipText("打开日历选择日期");
		calendarButton.setFocusable(false);
		calendarButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				if (calendarButton.isShowing()) {
					calendarPopup.show(calendarButton, 0, calendarButton.getHeight());
				}
			}
		});

		add(dateField);
		add(calendarButton);

		if (withTime) {
			hourField = new JTextField(2);
			minuteField = new JTextField(2);
			hourField.setToolTipText("小时 0-23：键盘输入 / ↑↓ / 滚轮");
			minuteField.setToolTipText("分钟 0-59：键盘输入 / ↑↓ / 滚轮");
			wireNumberLikeField(hourField, false);
			wireNumberLikeField(minuteField, false);
			add(new JLabel(" "));
			add(hourField);
			add(new JLabel(":"));
			add(minuteField);
			attachWheelAndArrows(hourField, Calendar.HOUR_OF_DAY, 0, 23);
			attachWheelAndArrows(minuteField, Calendar.MINUTE, 0, 59);
		}
		else {
			hourField = null;
			minuteField = null;
			attachDayWheelAndArrows(dateField);
		}

		refreshFieldsFromValue();
	}

	public void setChangeListener(final PropertyChangeListener listener) {
		this.changeListener = listener;
	}

	public Date getDate() {
		return value.getTime();
	}

	public long getTimeMillis() {
		return value.getTimeInMillis();
	}

	public void setDate(final Date date) {
		if (date == null) {
			return;
		}
		value.setTime(date);
		value.set(Calendar.SECOND, 0);
		value.set(Calendar.MILLISECOND, 0);
		refreshFieldsFromValue();
	}

	private void wireNumberLikeField(final JTextField field, final boolean isDate) {
		field.addFocusListener(new FocusAdapter() {
			public void focusLost(final FocusEvent e) {
				commitFields();
			}
		});
		field.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				commitFields();
			}
		});
		if (!isDate) {
			field.getDocument().addDocumentListener(new DocumentListener() {
				public void insertUpdate(final DocumentEvent e) {
				}

				public void removeUpdate(final DocumentEvent e) {
				}

				public void changedUpdate(final DocumentEvent e) {
				}
			});
		}
	}

	private void attachWheelAndArrows(final JTextField field, final int calendarField, final int min,
			final int max) {
		field.addMouseWheelListener(new MouseWheelListener() {
			public void mouseWheelMoved(final MouseWheelEvent e) {
				adjustField(calendarField, e.getWheelRotation() < 0 ? 1 : -1, min, max);
				e.consume();
			}
		});
		field.addKeyListener(new KeyAdapter() {
			public void keyPressed(final KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_UP) {
					adjustField(calendarField, 1, min, max);
					e.consume();
				}
				else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
					adjustField(calendarField, -1, min, max);
					e.consume();
				}
			}
		});
	}

	private void attachDayWheelAndArrows(final JTextField field) {
		field.addMouseWheelListener(new MouseWheelListener() {
			public void mouseWheelMoved(final MouseWheelEvent e) {
				value.add(Calendar.DAY_OF_MONTH, e.getWheelRotation() < 0 ? 1 : -1);
				refreshFieldsFromValue();
				fireChanged();
				e.consume();
			}
		});
		field.addKeyListener(new KeyAdapter() {
			public void keyPressed(final KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_UP) {
					value.add(Calendar.DAY_OF_MONTH, 1);
					refreshFieldsFromValue();
					fireChanged();
					e.consume();
				}
				else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
					value.add(Calendar.DAY_OF_MONTH, -1);
					refreshFieldsFromValue();
					fireChanged();
					e.consume();
				}
			}
		});
	}

	private void adjustField(final int calendarField, final int delta, final int min, final int max) {
		int next = value.get(calendarField) + delta;
		if (next > max) {
			next = min;
		}
		if (next < min) {
			next = max;
		}
		value.set(calendarField, next);
		refreshFieldsFromValue();
		fireChanged();
	}

	private void commitFields() {
		if (updating) {
			return;
		}
		try {
			final Date parsed = DATE_FMT.parse(dateField.getText().trim());
			final Calendar day = Calendar.getInstance(Locale.CHINA);
			day.setTime(parsed);
			value.set(Calendar.YEAR, day.get(Calendar.YEAR));
			value.set(Calendar.MONTH, day.get(Calendar.MONTH));
			value.set(Calendar.DAY_OF_MONTH, day.get(Calendar.DAY_OF_MONTH));
			if (withTime) {
				value.set(Calendar.HOUR_OF_DAY, clamp(parseInt(hourField.getText(), value.get(Calendar.HOUR_OF_DAY)), 0, 23));
				value.set(Calendar.MINUTE, clamp(parseInt(minuteField.getText(), value.get(Calendar.MINUTE)), 0, 59));
			}
			else {
				value.set(Calendar.HOUR_OF_DAY, 0);
				value.set(Calendar.MINUTE, 0);
			}
			value.set(Calendar.SECOND, 0);
			value.set(Calendar.MILLISECOND, 0);
			refreshFieldsFromValue();
			fireChanged();
		}
		catch (ParseException e) {
			refreshFieldsFromValue();
		}
	}

	private void refreshFieldsFromValue() {
		updating = true;
		try {
			synchronized (DATE_FMT) {
				dateField.setText(DATE_FMT.format(value.getTime()));
			}
			if (withTime) {
				hourField.setText(pad2(value.get(Calendar.HOUR_OF_DAY)));
				minuteField.setText(pad2(value.get(Calendar.MINUTE)));
			}
		}
		finally {
			updating = false;
		}
	}

	private void fireChanged() {
		if (changeListener != null) {
			changeListener.propertyChange(new java.beans.PropertyChangeEvent(this, "date", null, getDate()));
		}
	}

	private static int parseInt(final String text, final int fallback) {
		try {
			return Integer.parseInt(text.trim());
		}
		catch (Exception e) {
			return fallback;
		}
	}

	private static int clamp(final int v, final int min, final int max) {
		return Math.max(min, Math.min(max, v));
	}

	private static String pad2(final int v) {
		return v < 10 ? "0" + v : Integer.toString(v);
	}
}
