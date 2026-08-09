package org.docear.plugin.core.eagle;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileFilter;

import org.freeplane.core.ui.theme.DocearUiTheme;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.url.UrlManager;
import org.freeplane.view.swing.features.filepreview.ExternalImageSelection;
import org.freeplane.view.swing.features.filepreview.ImagePreview;

/**
 * Chooser for Add/Change ExternalObject: Eagle list + preview, clipboard import,
 * local file import into Eagle (stores {@code eagle://}).
 */
public final class EagleImagePicker implements ExternalImageSelection.Chooser {
	private static final int PREVIEW_MAX = 280;

	private EagleImagePicker() {
	}

	public static void install() {
		ExternalImageSelection.setChooser(new EagleImagePicker());
	}

	public URI chooseStoredUri(final NodeModel node) {
		final Frame owner = Controller.getCurrentController().getViewController().getFrame();
		if (EagleConfig.existingLibraryRoots().isEmpty()) {
			final int option = JOptionPane.showOptionDialog(owner,
					TextUtils.getText("eagle.picker.no_library"), TextUtils.getText("eagle.picker.title"),
					JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null,
					new Object[] {
							TextUtils.getText("eagle.picker.open_settings"),
							TextUtils.getText("eagle.picker.browse_local"),
							TextUtils.getText("cancel")
					},
					TextUtils.getText("eagle.picker.open_settings"));
			if (option == 0) {
				EagleSettingsAction.showSettingsDialog();
				if (EagleConfig.existingLibraryRoots().isEmpty()) {
					return null;
				}
			}
			else if (option == 1) {
				return importLocalFileToEagle(owner, node);
			}
			else {
				return null;
			}
		}
		EagleItemIndex.getInstance().ensureLoaded(false);
		final PickerDialog dialog = new PickerDialog(owner, node);
		dialog.setVisible(true);
		return dialog.result;
	}

	/** Browse local file → import into Eagle → return eagle:// (or null). */
	static URI importLocalFileToEagle(final Component parent, final NodeModel node) {
		final File input = browseLocalFile(parent);
		if (input == null) {
			return null;
		}
		return importFileAndMakeUri(parent, input);
	}

	static File browseLocalFile(final Component parent) {
		final UrlManager urlManager = (UrlManager) Controller.getCurrentModeController().getExtension(UrlManager.class);
		final JFileChooser chooser = urlManager.getFileChooser(null, false);
		chooser.setAcceptAllFileFilterUsed(false);
		chooser.setFileFilter(new FileFilter() {
			public boolean accept(File f) {
				if (f.isDirectory()) {
					return true;
				}
				final String n = f.getName().toLowerCase();
				return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".gif")
						|| n.endsWith(".bmp") || n.endsWith(".webp") || n.endsWith(".svg");
			}

			public String getDescription() {
				return TextUtils.getText("eagle.picker.local_filter");
			}
		});
		chooser.setAccessory(new ImagePreview(chooser));
		final int returnVal = chooser.showOpenDialog(parent);
		if (returnVal != JFileChooser.APPROVE_OPTION) {
			return null;
		}
		final File input = chooser.getSelectedFile();
		if (input == null || !input.isFile()) {
			return null;
		}
		return input;
	}

	static URI importFileAndMakeUri(final Component parent, final File file) {
		try {
			ensurePrimaryLibraryOrPrompt(parent);
			final EagleItem item = EagleItemIndex.getInstance().importFile(file);
			return EagleUri.create(item.getId(), item.getExt());
		}
		catch (Exception e) {
			JOptionPane.showMessageDialog(parent,
					TextUtils.format("eagle.picker.import_failed", String.valueOf(e.getMessage())),
					TextUtils.getText("eagle.picker.title"), JOptionPane.ERROR_MESSAGE);
			return null;
		}
	}

	static URI importClipboardImage(final Component parent) {
		final BufferedImage image = readClipboardImage();
		if (image == null) {
			JOptionPane.showMessageDialog(parent, TextUtils.getText("eagle.picker.clipboard_empty"),
					TextUtils.getText("eagle.picker.title"), JOptionPane.INFORMATION_MESSAGE);
			return null;
		}
		File temp = null;
		try {
			ensurePrimaryLibraryOrPrompt(parent);
			temp = File.createTempFile("docear-clipboard-", ".png");
			ImageIO.write(toArgb(image), "png", temp);
			final EagleItem item = EagleItemIndex.getInstance().importFile(temp);
			return EagleUri.create(item.getId(), item.getExt());
		}
		catch (Exception e) {
			JOptionPane.showMessageDialog(parent,
					TextUtils.format("eagle.picker.import_failed", String.valueOf(e.getMessage())),
					TextUtils.getText("eagle.picker.title"), JOptionPane.ERROR_MESSAGE);
			return null;
		}
		finally {
			if (temp != null && temp.exists()) {
				temp.delete();
			}
		}
	}

	static boolean clipboardHasImage() {
		return readClipboardImage() != null;
	}

	static BufferedImage readClipboardImage() {
		try {
			final Transferable t = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
			if (t == null || !t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
				return null;
			}
			final Object data = t.getTransferData(DataFlavor.imageFlavor);
			if (data instanceof BufferedImage) {
				return (BufferedImage) data;
			}
			if (data instanceof Image) {
				return toArgb((Image) data);
			}
		}
		catch (Exception e) {
			LogUtils.info("Eagle clipboard image read: " + e.getMessage());
		}
		return null;
	}

	private static void ensurePrimaryLibraryOrPrompt(final Component parent) throws IOException {
		File library = EagleConfig.getPrimaryLibrary();
		if (library != null && library.isDirectory()) {
			return;
		}
		EagleSettingsAction.showSettingsDialog();
		library = EagleConfig.getPrimaryLibrary();
		if (library == null || !library.isDirectory()) {
			throw new IOException(TextUtils.getText("eagle.picker.no_primary"));
		}
	}

	private static BufferedImage toArgb(final Image src) {
		if (src instanceof BufferedImage) {
			final BufferedImage bi = (BufferedImage) src;
			if (bi.getType() == BufferedImage.TYPE_INT_ARGB) {
				return bi;
			}
		}
		final int w = Math.max(1, src.getWidth(null));
		final int h = Math.max(1, src.getHeight(null));
		final BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = out.createGraphics();
		g.drawImage(src, 0, 0, null);
		g.dispose();
		return out;
	}

	static ImageIcon loadPreviewIcon(final File file, final int max) {
		if (file == null || !file.isFile()) {
			return null;
		}
		try {
			BufferedImage img = ImageIO.read(file);
			if (img == null) {
				return null;
			}
			final int w = img.getWidth();
			final int h = img.getHeight();
			if (w <= 0 || h <= 0) {
				return null;
			}
			final double scale = Math.min(1.0, Math.min((double) max / w, (double) max / h));
			final int nw = Math.max(1, (int) Math.round(w * scale));
			final int nh = Math.max(1, (int) Math.round(h * scale));
			if (nw != w || nh != h) {
				final Image scaled = img.getScaledInstance(nw, nh, Image.SCALE_SMOOTH);
				final BufferedImage canvas = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
				final Graphics2D g = canvas.createGraphics();
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				g.drawImage(scaled, 0, 0, null);
				g.dispose();
				img = canvas;
			}
			return new ImageIcon(img);
		}
		catch (Exception e) {
			return null;
		}
	}

	private static final class PickerDialog extends JDialog {
		private static final long serialVersionUID = 1L;
		private URI result;
		private final DefaultListModel listModel = new DefaultListModel();
		private final JList list = new JList(listModel);
		private final JTextField search = new JTextField();
		private final JLabel previewLabel = new JLabel(" ", SwingConstants.CENTER);
		private final JLabel previewMeta = DocearUiTheme.mutedLabel(" ");
		private final JButton clipboardBtn;
		private List<EagleItem> allItems = Collections.emptyList();

		PickerDialog(final Frame owner, final NodeModel node) {
			super(owner, TextUtils.getText("eagle.picker.title"), true);
			setLayout(new BorderLayout(8, 8));
			((JPanel) getContentPane()).setBorder(new EmptyBorder(10, 12, 10, 12));
			DocearUiTheme.styleSurface((JPanel) getContentPane());

			final JLabel hint = DocearUiTheme.mutedLabel(TextUtils.getText("eagle.picker.hint"));
			add(hint, BorderLayout.NORTH);

			final JPanel left = new JPanel(new BorderLayout(6, 6));
			left.setOpaque(false);
			search.setFont(DocearUiTheme.font(13f));
			left.add(search, BorderLayout.NORTH);
			list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			list.setCellRenderer(new DefaultListCellRenderer() {
				private static final long serialVersionUID = 1L;

				public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected,
						boolean cellHasFocus) {
					super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
					if (value instanceof EagleItem) {
						final EagleItem item = (EagleItem) value;
						setText(item.getName() + "." + item.getExt() + "  [" + item.getId() + "]");
					}
					return this;
				}
			});
			list.addMouseListener(new MouseAdapter() {
				public void mouseClicked(MouseEvent e) {
					if (e.getClickCount() >= 2) {
						acceptSelected();
					}
				}
			});
			list.addListSelectionListener(new ListSelectionListener() {
				public void valueChanged(ListSelectionEvent e) {
					if (!e.getValueIsAdjusting()) {
						updatePreview();
					}
				}
			});
			final JScrollPane scroll = new JScrollPane(list);
			scroll.setPreferredSize(new Dimension(340, 320));
			left.add(scroll, BorderLayout.CENTER);

			final JPanel right = new JPanel(new BorderLayout(6, 6));
			right.setOpaque(false);
			previewLabel.setPreferredSize(new Dimension(PREVIEW_MAX + 24, PREVIEW_MAX + 24));
			previewLabel.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(new Color(0xD0, 0xD4, 0xDA)),
					new EmptyBorder(8, 8, 8, 8)));
			previewLabel.setOpaque(true);
			previewLabel.setBackground(Color.WHITE);
			right.add(previewLabel, BorderLayout.CENTER);
			right.add(previewMeta, BorderLayout.SOUTH);

			final JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
			split.setResizeWeight(0.55);
			split.setBorder(null);
			add(split, BorderLayout.CENTER);

			final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
			buttons.setOpaque(false);
			final JButton settings = DocearUiTheme.softButton(TextUtils.getText("eagle.picker.open_settings"));
			clipboardBtn = DocearUiTheme.softButton(TextUtils.getText("eagle.picker.clipboard"));
			final JButton local = DocearUiTheme.softButton(TextUtils.getText("eagle.picker.browse_import"));
			final JButton cancel = DocearUiTheme.softButton(TextUtils.getText("cancel"));
			final JButton ok = DocearUiTheme.primaryButton(TextUtils.getText("ok"));
			settings.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					EagleSettingsAction.showSettingsDialog();
					reload();
					refreshClipboardButton();
				}
			});
			clipboardBtn.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					final URI uri = importClipboardImage(PickerDialog.this);
					if (uri != null) {
						result = uri;
						dispose();
					}
				}
			});
			local.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					final URI uri = importLocalFileToEagle(PickerDialog.this, node);
					if (uri != null) {
						result = uri;
						dispose();
					}
				}
			});
			cancel.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					result = null;
					dispose();
				}
			});
			ok.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					acceptSelected();
				}
			});
			buttons.add(settings);
			buttons.add(clipboardBtn);
			buttons.add(local);
			buttons.add(cancel);
			buttons.add(ok);
			add(buttons, BorderLayout.SOUTH);

			search.getDocument().addDocumentListener(new DocumentListener() {
				public void insertUpdate(DocumentEvent e) {
					filter();
				}

				public void removeUpdate(DocumentEvent e) {
					filter();
				}

				public void changedUpdate(DocumentEvent e) {
					filter();
				}
			});

			reload();
			refreshClipboardButton();
			// If clipboard has an image, show it in preview as a hint until list selection
			final BufferedImage clip = readClipboardImage();
			if (clip != null && listModel.isEmpty()) {
				showImagePreview(clip, TextUtils.getText("eagle.picker.clipboard_preview_hint"));
			}
			pack();
			setMinimumSize(new Dimension(720, 480));
			setLocationRelativeTo(owner);
		}

		private void refreshClipboardButton() {
			final boolean has = clipboardHasImage();
			clipboardBtn.setEnabled(has);
			clipboardBtn.setToolTipText(has ? TextUtils.getText("eagle.picker.clipboard_tip")
					: TextUtils.getText("eagle.picker.clipboard_empty"));
		}

		private void reload() {
			if (!EagleConfig.existingLibraryRoots().isEmpty()) {
				EagleItemIndex.getInstance().rebuild(false, null);
			}
			allItems = EagleItemIndex.getInstance().listAllItems();
			Collections.sort(allItems, new Comparator<EagleItem>() {
				public int compare(EagleItem a, EagleItem b) {
					return a.fileNameLower().compareTo(b.fileNameLower());
				}
			});
			filter();
		}

		private void filter() {
			final String q = search.getText() == null ? "" : search.getText().trim().toLowerCase();
			listModel.clear();
			for (EagleItem item : allItems) {
				if (q.length() == 0 || item.fileNameLower().indexOf(q) >= 0
						|| item.getId().toLowerCase().indexOf(q) >= 0
						|| (item.getName() != null && item.getName().toLowerCase().indexOf(q) >= 0)) {
					listModel.addElement(item);
				}
			}
			if (listModel.size() > 0) {
				list.setSelectedIndex(0);
			}
			else {
				updatePreview();
			}
		}

		private void updatePreview() {
			final Object sel = list.getSelectedValue();
			if (!(sel instanceof EagleItem)) {
				previewLabel.setIcon(null);
				previewLabel.setText(TextUtils.getText("eagle.picker.preview_empty"));
				previewMeta.setText(" ");
				return;
			}
			final EagleItem item = (EagleItem) sel;
			File previewFile = item.getFile();
			// Prefer Eagle thumbnail if present next to the media file
			if (previewFile != null && previewFile.getParentFile() != null) {
				final File thumb = new File(previewFile.getParentFile(),
						stripExt(previewFile.getName()) + "_thumbnail.png");
				if (thumb.isFile()) {
					previewFile = thumb;
				}
			}
			final ImageIcon icon = loadPreviewIcon(previewFile, PREVIEW_MAX);
			if (icon != null) {
				previewLabel.setText(null);
				previewLabel.setIcon(icon);
			}
			else {
				previewLabel.setIcon(null);
				previewLabel.setText(TextUtils.getText("eagle.picker.preview_failed"));
			}
			previewMeta.setText(item.getName() + "." + item.getExt() + "  ·  " + item.getId()
					+ (item.getFile() != null ? "  ·  " + formatSize(item.getSize()) : ""));
		}

		private void showImagePreview(final BufferedImage image, final String meta) {
			final ImageIcon icon = loadPreviewIconFromImage(image, PREVIEW_MAX);
			previewLabel.setText(null);
			previewLabel.setIcon(icon);
			previewMeta.setText(meta == null ? " " : meta);
		}

		private void acceptSelected() {
			final Object sel = list.getSelectedValue();
			if (!(sel instanceof EagleItem)) {
				JOptionPane.showMessageDialog(this, TextUtils.getText("eagle.picker.select_one"),
						TextUtils.getText("eagle.picker.title"), JOptionPane.WARNING_MESSAGE);
				return;
			}
			final EagleItem item = (EagleItem) sel;
			result = EagleUri.create(item.getId(), item.getExt());
			dispose();
		}

		private static String stripExt(final String name) {
			final int dot = name.lastIndexOf('.');
			return dot > 0 ? name.substring(0, dot) : name;
		}

		private static String formatSize(final long bytes) {
			if (bytes < 1024) {
				return bytes + " B";
			}
			if (bytes < 1024 * 1024) {
				return (bytes / 1024) + " KB";
			}
			return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
		}

		private static ImageIcon loadPreviewIconFromImage(final BufferedImage img, final int max) {
			if (img == null) {
				return null;
			}
			final int w = img.getWidth();
			final int h = img.getHeight();
			final double scale = Math.min(1.0, Math.min((double) max / w, (double) max / h));
			final int nw = Math.max(1, (int) Math.round(w * scale));
			final int nh = Math.max(1, (int) Math.round(h * scale));
			final Image scaled = img.getScaledInstance(nw, nh, Image.SCALE_SMOOTH);
			return new ImageIcon(scaled);
		}
	}
}
