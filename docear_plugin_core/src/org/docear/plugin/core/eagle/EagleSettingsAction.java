package org.docear.plugin.core.eagle;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.theme.DocearUiTheme;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.mode.Controller;

public class EagleSettingsAction extends AFreeplaneAction {
	private static final long serialVersionUID = 1L;
	public static final String KEY = "EagleSettingsAction";

	public EagleSettingsAction() {
		super(KEY);
	}

	public void actionPerformed(final ActionEvent e) {
		showSettingsDialog();
	}

	static void showSettingsDialog() {
		final JTextArea librariesArea = new JTextArea(EagleConfig.getLibraryPathsText(), 6, 40);
		librariesArea.setLineWrap(true);
		librariesArea.setWrapStyleWord(true);
		librariesArea.setFont(DocearUiTheme.font(12f));
		final JScrollPane librariesScroll = new JScrollPane(librariesArea);
		librariesScroll.setPreferredSize(new Dimension(480, 120));

		final File primary = EagleConfig.getPrimaryLibrary();
		final JTextField primaryField = new JTextField(primary == null ? "" : primary.getAbsolutePath(), 40);
		primaryField.setFont(DocearUiTheme.font(12f));

		final JCheckBox autoImport = new JCheckBox(TextUtils.getText("eagle.settings.auto_import"),
				EagleConfig.isAutoImportEnabled());
		autoImport.setOpaque(false);
		autoImport.setFont(DocearUiTheme.font(12f));

		final JPanel panel = new JPanel(new GridBagLayout());
		DocearUiTheme.styleSurface(panel);
		panel.setBorder(new EmptyBorder(8, 8, 4, 8));
		final GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.insets = new Insets(4, 4, 4, 4);
		panel.add(label(TextUtils.getText("eagle.settings.libraries")), c);
		c.gridy = 1;
		c.weightx = 1.0;
		c.weighty = 1.0;
		c.fill = GridBagConstraints.BOTH;
		panel.add(librariesScroll, c);
		c.gridy = 2;
		c.weighty = 0;
		c.fill = GridBagConstraints.HORIZONTAL;
		panel.add(label(TextUtils.getText("eagle.settings.primary")), c);
		c.gridy = 3;
		panel.add(primaryField, c);
		c.gridy = 4;
		panel.add(autoImport, c);
		c.gridy = 5;
		panel.add(DocearUiTheme.mutedLabel(TextUtils.getText("eagle.settings.hint")), c);

		final int option = JOptionPane.showConfirmDialog(
				Controller.getCurrentController().getViewController().getFrame(), panel,
				TextUtils.getText("eagle.settings.title"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (option != JOptionPane.OK_OPTION) {
			return;
		}
		EagleConfig.setLibraryPathsText(librariesArea.getText());
		final String primaryText = primaryField.getText().trim();
		EagleConfig.setPrimaryLibrary(primaryText.length() == 0 ? null : new File(primaryText));
		EagleConfig.setAutoImportEnabled(autoImport.isSelected());
		EagleItemIndex.getInstance().rebuild(true, null);
		final List<File> roots = EagleConfig.existingLibraryRoots();
		JOptionPane.showMessageDialog(Controller.getCurrentController().getViewController().getFrame(),
				TextUtils.format("eagle.settings.rebuilt", Integer.valueOf(EagleItemIndex.getInstance().size()),
						Integer.valueOf(roots.size())),
				TextUtils.getText("eagle.settings.title"), JOptionPane.INFORMATION_MESSAGE);
	}

	private static JLabel label(final String text) {
		final JLabel label = new JLabel(text);
		label.setFont(DocearUiTheme.font(12f));
		label.setForeground(DocearUiTheme.TEXT);
		return label;
	}
}
