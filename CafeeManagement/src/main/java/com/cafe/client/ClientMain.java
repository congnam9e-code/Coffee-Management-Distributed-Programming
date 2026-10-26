package com.cafe.client;

import com.cafe.client.ui.LoginFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class ClientMain {
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			try {
				// Set native Look and Feel for Windows
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch (Exception e) {
				e.printStackTrace();
			}
			new LoginFrame().setVisible(true);
		});
	}
}
