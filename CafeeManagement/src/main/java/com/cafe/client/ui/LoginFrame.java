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
	private JTextField txtServerIp;
	private JTextField txtUsername;
	private JPasswordField txtPassword;
	private JButton btnLogin;

	public LoginFrame() {
		setTitle("Cafe Management - Đăng Nhập");
		setSize(380, 260);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setLocationRelativeTo(null);

		// Layout
		JPanel panel = new JPanel(new GridLayout(4, 2, 8, 8));
		panel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

		panel.add(new JLabel("Máy chủ (Server IP):"));
		txtServerIp = new JTextField(Config.SERVER_IP);
		panel.add(txtServerIp);

		panel.add(new JLabel("Tài khoản:"));
		txtUsername = new JTextField("admin");
		panel.add(txtUsername);

		panel.add(new JLabel("Mật khẩu:"));
		txtPassword = new JPasswordField("123456");
		panel.add(txtPassword);

		btnLogin = new JButton("Đăng Nhập");
		panel.add(new JLabel("")); // spacer
		panel.add(btnLogin);

		add(panel);

		btnLogin.addActionListener(this::handleLogin);
	}

	private void handleLogin(ActionEvent e) {
		String serverIp = txtServerIp.getText().trim();
		String username = txtUsername.getText().trim();
		String password = new String(txtPassword.getPassword());

		if (serverIp.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Vui lòng nhập IP máy chủ!", "Lỗi", JOptionPane.WARNING_MESSAGE);
			return;
		}

		if (username.isEmpty() || password.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Vui lòng nhập tài khoản và mật khẩu!", "Lỗi", JOptionPane.WARNING_MESSAGE);
			return;
		}

		// Kết nối đến Server theo IP người dùng chỉ định
		SocketClient client = SocketClient.getInstance();
		if (!client.isConnected() || !serverIp.equalsIgnoreCase(Config.SERVER_IP)) {
			boolean connected = client.connect(serverIp, Config.SERVER_PORT);
			if (!connected) {
				JOptionPane.showMessageDialog(this,
						"Không thể kết nối đến máy chủ tại: " + serverIp + ":" + Config.SERVER_PORT + "\nVui lòng kiểm tra lại IP hoặc chắc chắn Server đã được khởi chạy!",
						"Lỗi kết nối Server", JOptionPane.ERROR_MESSAGE);
				return;
			}
		}

		Employee creds = new Employee();
		creds.setUsername(username);
		creds.setPassword(password);

		Request req = new Request(ActionType.LOGIN, creds);
		Response res = client.sendRequest(req);

		if (res != null && res.isSuccess()) {
			Employee user = (Employee) res.getData();
			JOptionPane.showMessageDialog(this,
					"Đăng nhập thành công! Xin chào " + user.getFullName() + " (" + user.getRole() + ")");

			this.dispose(); // Đóng form đăng nhập
			if ("MANAGER".equals(user.getRole())) {
				new ManagerDashboardFrame(user).setVisible(true);
			} else {
				new StaffDashboardFrame(user).setVisible(true);
			}
		} else {
			String msg = (res != null) ? res.getMessage() : "Không thể liên lạc với máy chủ!";
			JOptionPane.showMessageDialog(this, msg, "Lỗi đăng nhập", JOptionPane.ERROR_MESSAGE);
		}
	}
}
