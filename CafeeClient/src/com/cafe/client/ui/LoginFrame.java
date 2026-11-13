package com.cafe.client.ui;

import com.cafe.client.socket.SocketClient;
import com.cafe.common.entity.Employee;
import com.cafe.common.network.ActionType;
import com.cafe.common.network.Config;
import com.cafe.common.network.Request;
import com.cafe.common.network.Response;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class LoginFrame extends JFrame {
	private static final long serialVersionUID = 1L;
	private JTextField txtUsername;
	private JPasswordField txtPassword;
	private JButton btnLogin;

	public LoginFrame() {
		setTitle("Đăng Nhập - Hệ Thống Quản Lý Bán Cafe");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setResizable(false);

		JPanel root = new JPanel(new BorderLayout());
		root.setBackground(UIUtils.COLOR_BG);
		root.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));

		// Top Header
		JPanel topHeader = new JPanel(new GridLayout(2, 1, 0, 4));
		topHeader.setOpaque(false);
		topHeader.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));

		JLabel lblTitle = new JLabel("HỆ THỐNG BÁN CAFE", SwingConstants.CENTER);
		lblTitle.setFont(UIUtils.FONT_HEADER_TITLE);
		lblTitle.setForeground(UIUtils.COLOR_DARK_HEADER);
		topHeader.add(lblTitle);

		JLabel lblSub = new JLabel("Đăng nhập hệ thống quản lý quán cà phê", SwingConstants.CENTER);
		lblSub.setFont(UIUtils.FONT_REGULAR);
		lblSub.setForeground(UIUtils.COLOR_TEXT_MUTED);
		topHeader.add(lblSub);

		root.add(topHeader, BorderLayout.NORTH);

		// Center Form Card
		JPanel formCard = new JPanel(new GridBagLayout());
		formCard.setBackground(UIUtils.COLOR_CARD);
		formCard.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(UIUtils.COLOR_BORDER, 1),
				BorderFactory.createEmptyBorder(18, 22, 20, 22)));

		JLabel lblUser = new JLabel("Tên tài khoản:");
		lblUser.setFont(UIUtils.FONT_BOLD);
		lblUser.setForeground(UIUtils.COLOR_TEXT_DARK);

		txtUsername = new JTextField();
		UIUtils.styleTextField(txtUsername);
		txtUsername.setPreferredSize(new Dimension(340, 38));
		txtUsername.setMinimumSize(new Dimension(280, 38));
		txtUsername.setToolTipText("Nhập tên đăng nhập (VD: admin hoặc staff)");

		JLabel lblPass = new JLabel("Mật khẩu:");
		lblPass.setFont(UIUtils.FONT_BOLD);
		lblPass.setForeground(UIUtils.COLOR_TEXT_DARK);

		txtPassword = new JPasswordField();
		UIUtils.styleTextField(txtPassword);
		txtPassword.setPreferredSize(new Dimension(340, 38));
		txtPassword.setMinimumSize(new Dimension(280, 38));
		txtPassword.setToolTipText("Nhập mật khẩu tài khoản");

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.weightx = 1.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.WEST;

		gbc.gridy = 0;
		gbc.insets = new Insets(0, 0, 6, 0);
		formCard.add(lblUser, gbc);

		gbc.gridy = 1;
		gbc.insets = new Insets(0, 0, 14, 0);
		formCard.add(txtUsername, gbc);

		gbc.gridy = 2;
		gbc.insets = new Insets(0, 0, 6, 0);
		formCard.add(lblPass, gbc);

		gbc.gridy = 3;
		gbc.insets = new Insets(0, 0, 4, 0);
		formCard.add(txtPassword, gbc);

		root.add(formCard, BorderLayout.CENTER);

		// Bottom Button
		JPanel bottomPanel = new JPanel(new BorderLayout(0, 10));
		bottomPanel.setOpaque(false);
		bottomPanel.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));

		btnLogin = UIUtils.createButton("ĐĂNG NHẬP", UIUtils.COLOR_PRIMARY, Color.WHITE, UIUtils.FONT_TITLE);
		btnLogin.setPreferredSize(new Dimension(0, 42));
		bottomPanel.add(btnLogin, BorderLayout.NORTH);

		JLabel lblFooter = new JLabel("Đồ án Lập trình phân tán - JPA & TCP Socket (FIT IUH)", SwingConstants.CENTER);
		lblFooter.setFont(UIUtils.FONT_SMALL);
		lblFooter.setForeground(UIUtils.COLOR_TEXT_MUTED);
		bottomPanel.add(lblFooter, BorderLayout.SOUTH);

		root.add(bottomPanel, BorderLayout.SOUTH);

		add(root);
		pack();
		setMinimumSize(getSize());
		setSize(Math.max(getWidth(), 460), Math.max(getHeight(), 390));
		setLocationRelativeTo(null);

		btnLogin.addActionListener(this::handleLogin);
		txtPassword.addActionListener(this::handleLogin);
		txtUsername.addActionListener(e -> txtPassword.requestFocusInWindow());

		SwingUtilities.invokeLater(() -> txtUsername.requestFocusInWindow());
	}

	private void handleLogin(ActionEvent e) {
		String username = txtUsername.getText().trim();
		String password = new String(txtPassword.getPassword());

		if (username.isEmpty() || password.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ tên tài khoản và mật khẩu!", "Thông báo",
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		btnLogin.setText("ĐANG KẾT NỐI...");
		Employee creds = new Employee();
		creds.setUsername(username);
		creds.setPassword(password);

		UIUtils.runAsync(this, btnLogin, () -> {
			SocketClient client = SocketClient.getInstance();
			if (!client.connect(Config.SERVER_IP, Config.SERVER_PORT)) {
				return new Response(false,
						"Không thể kết nối đến máy chủ tại: " + Config.SERVER_IP + ":" + Config.SERVER_PORT
								+ "\nVui lòng chắc chắn ServerMain đã khởi chạy!",
						null);
			}
			return client.sendRequest(new Request(ActionType.LOGIN, creds));
		}, res -> {
			btnLogin.setText("ĐĂNG NHẬP");
			if (res != null && res.isSuccess() && res.getData() instanceof Employee user) {
				boolean isManager = "MANAGER".equalsIgnoreCase(user.getRole());

				this.dispose();
				if (isManager) {
					new ManagerDashboardFrame(user).setVisible(true);
				} else {
					new StaffDashboardFrame(user).setVisible(true);
				}
			} else {
				SocketClient.getInstance().close();
				String msg = (res != null && res.getMessage() != null)
						? res.getMessage()
						: "Không thể liên lạc với máy chủ!";
				JOptionPane.showMessageDialog(this, msg, "Lỗi đăng nhập", JOptionPane.ERROR_MESSAGE);
			}
		});
	}
}
