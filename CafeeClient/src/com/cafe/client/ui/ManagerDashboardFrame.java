package com.cafe.client.ui;

import com.cafe.common.entity.Employee;

import javax.swing.*;
import java.awt.*;

public class ManagerDashboardFrame extends JFrame {
	private static final long serialVersionUID = 1L;

	public ManagerDashboardFrame(Employee user) {
		setTitle("Hệ Thống Quản Trị Cafe - Quản Lý: " + user.getFullName());
		setSize(1000, 680);
		setMinimumSize(new Dimension(900, 600));
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setLocationRelativeTo(null);

		setLayout(new BorderLayout());

		// 1. Top Header Bar
		JPanel headerBar = UIUtils.createHeaderBar(
				"HỆ THỐNG QUẢN TRỊ CAFE",
				"Phân hệ Quản lý & Báo cáo doanh thu",
				user.getFullName(),
				"Quản lý",
				() -> UIUtils.confirmLogout(this, "Bạn có chắc chắn muốn đăng xuất khỏi tài khoản Quản lý?"),
				() -> UIUtils.showChangePasswordDialog(this, "Đổi Mật Khẩu Quản Lý"));
		add(headerBar, BorderLayout.NORTH);

		// 2. Tabbed Pane
		JTabbedPane tabbedPane = new JTabbedPane();
		tabbedPane.setFont(UIUtils.FONT_TITLE);
		tabbedPane.setBackground(Color.WHITE);
		tabbedPane.setForeground(UIUtils.COLOR_TEXT_DARK);

		ProductPanel productPanel = new ProductPanel();
		BillPanel billPanel = new BillPanel(false);
		EmployeePanel employeePanel = new EmployeePanel(false);

		tabbedPane.addTab("Quản Lý Thực Đơn", productPanel);
		tabbedPane.addTab("Lịch Sử Hóa Đơn", billPanel);
		tabbedPane.addTab("Quản Lý Nhân Viên", employeePanel);

		// Tự động làm mới dữ liệu khi chuyển tab để đồng bộ thông tin mới nhất từ Server
		tabbedPane.addChangeListener(e -> {
			Component selected = tabbedPane.getSelectedComponent();
			if (selected instanceof UIUtils.Refreshable refreshable) {
				refreshable.refreshData();
			}
		});

		com.cafe.client.socket.SocketClient.getInstance().setSyncListener(topic ->
				SwingUtilities.invokeLater(() -> {
					if (!isDisplayable()) {
						return;
					}
					Component selected = tabbedPane.getSelectedComponent();
					switch (topic) {
					case "MENU_CHANGED":
						productPanel.reloadSilently();
						break;
					case "ORDER_CHANGED":
						if (selected == billPanel) {
							billPanel.reloadSilently();
						}
						break;
					case "EMPLOYEE_CHANGED":
						if (selected == employeePanel) {
							employeePanel.reloadSilently();
						}
						break;
					case "ALL_REFRESH":
						productPanel.reloadSilently();
						if (selected == billPanel) {
							billPanel.reloadSilently();
						} else if (selected == employeePanel) {
							employeePanel.reloadSilently();
						}
						break;
					default:
						break;
					}
				}));

		add(tabbedPane, BorderLayout.CENTER);
	}
}
