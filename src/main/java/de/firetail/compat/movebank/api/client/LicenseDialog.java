package de.firetail.compat.movebank.api.client;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;

public class LicenseDialog extends JDialog {

	boolean accepted = false;

	private LicenseDialog(Frame owner, String html) {
		super(owner, true);
		setTitle("Accept License Terms");
		setSize(400, 400);
		JPanel mainPanel = new JPanel(new BorderLayout());
		getContentPane().add(mainPanel);
		JTextPane htmlPanel = new JTextPane();
		htmlPanel.setContentType("text/html");
		htmlPanel.setText(html);
		JScrollPane scrollPane = new JScrollPane(htmlPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		mainPanel.add(scrollPane, BorderLayout.CENTER);
		JPanel buttonPanel = new JPanel(new FlowLayout());
		mainPanel.add(buttonPanel, BorderLayout.SOUTH);
		JButton acceptButton = new JButton("Accept");
		acceptButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				accepted = true;
				dispose();
			}
		});
		buttonPanel.add(acceptButton);
		JButton declineButton = new JButton("Cancel");
		declineButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				accepted = false;
				dispose();
			}
		});
		buttonPanel.add(declineButton);
		if (owner != null)
			setLocationRelativeTo(owner);
	}

	public static boolean acceptLicense(Frame owner, String html) {
		LicenseDialog dialog = new LicenseDialog(owner, html);
		dialog.setVisible(true);
		return dialog.accepted;
	}

	public static void main(String[] args) {
		JFrame f = new JFrame();
		f.setBounds(300, 200, 0, 0);
		acceptLicense(f, "<html>xxx</html>");
	}

}
