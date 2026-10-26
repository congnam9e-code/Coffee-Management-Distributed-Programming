package com.cafe.client.ui;

import com.cafe.common.entity.Employee;

import javax.swing.*;
import java.awt.*;

public class ManagerDashboardFrame extends JFrame {
	private static final long serialVersionUID = 1L;

	public ManagerDashboardFrame(Employee user) {
		setTitle("Quản Lý Quán Cafe - Quyền: " + user.getRole());
		setSize(900, 600);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setLocationRelativeTo(null);

		JTabbedPane tabbedPane = new JTabbedPane();

		// Thêm các Tab quản lý
		tabbedPane.addTab("Quản Lý Món", new ProductPanel());
		tabbedPane.addTab("Quản Lý Hóa Đơn", new BillPanel());
		tabbedPane.addTab("Quản Lý Nhân Viên", new EmployeePanel());

		add(tabbedPane, BorderLayout.CENTER);

		JPanel bottomPanel = new JPanel(new BorderLayout());
		bottomPanel.add(new JLabel("Xin chào, " + user.getFullName() + " (" + user.getRole() + ")"), BorderLayout.WEST);

		JPanel rightBtnGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		JButton btnProfile = new JButton("Thông Tin Cá Nhân");
		btnProfile.addActionListener(e -> {
			String info = "=== THÔNG TIN QUẢN LÝ ===\n\n"
					+ "• Mã quản lý: " + user.getId() + "\n"
					+ "• Tên đăng nhập: " + user.getUsername() + "\n"
					+ "• Họ và tên: " + user.getFullName() + "\n"
					+ "• Quyền hạn: " + user.getRole() + "\n"
					+ "• Trạng thái tài khoản: Đang hoạt động (Active)";
			JOptionPane.showMessageDialog(this, info, "Thông Tin Cá Nhân", JOptionPane.INFORMATION_MESSAGE);
		});
		rightBtnGroup.add(btnProfile);

		JButton btnLogout = new JButton("Đăng Xuất");
		btnLogout.addActionListener(e -> {
			this.dispose();
			new LoginFrame().setVisible(true);
		});
		rightBtnGroup.add(btnLogout);
		bottomPanel.add(rightBtnGroup, BorderLayout.EAST);
		bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		add(bottomPanel, BorderLayout.SOUTH);
	}
}
