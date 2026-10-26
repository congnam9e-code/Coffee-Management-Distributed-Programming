package com.cafe.client.ui;

import com.cafe.client.socket.SocketClient;
import com.cafe.common.entity.Bill;
import com.cafe.common.entity.BillDetail;
import com.cafe.common.entity.CoffeeTable;
import com.cafe.common.entity.Employee;
import com.cafe.common.entity.Product;
import com.cafe.common.network.ActionType;
import com.cafe.common.network.OrderDto;
import com.cafe.common.network.Request;
import com.cafe.common.network.Response;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class StaffDashboardFrame extends JFrame {
	private static final long serialVersionUID = 1L;
	private Employee currentUser;
	private JPanel tablePanel;
	private DefaultTableModel orderTableModel;
	private JTable orderTable;
	private JLabel lblTotal;

	private int selectedTableId = -1;
	private int activeBillId = -1;

	public StaffDashboardFrame(Employee user) {
		this.currentUser = user;
		setTitle("Nhân Viên Bán Hàng - " + user.getFullName());
		setSize(1100, 650);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setLocationRelativeTo(null);

		setLayout(new BorderLayout());

		// Left Panel
		tablePanel = new JPanel(new GridLayout(0, 4, 10, 10));
		tablePanel.setBorder(BorderFactory.createTitledBorder("Sơ đồ bàn Cafe"));
		add(new JScrollPane(tablePanel), BorderLayout.CENTER);

		// Right Panel
		JPanel rightPanel = new JPanel(new BorderLayout());
		rightPanel.setPreferredSize(new Dimension(450, 650));
		rightPanel.setBorder(BorderFactory.createTitledBorder("Chi tiết Order"));

		orderTableModel = new DefaultTableModel(new String[] { "Tên món", "SL", "Giá", "Thành tiền" }, 0);
		orderTable = new JTable(orderTableModel);
		rightPanel.add(new JScrollPane(orderTable), BorderLayout.CENTER);

		JPanel bottomArea = new JPanel(new GridLayout(2, 1));

		lblTotal = new JLabel("Tổng tiền: 0 VNĐ", SwingConstants.RIGHT);
		lblTotal.setFont(new Font("Arial", Font.BOLD, 18));
		bottomArea.add(lblTotal);

		JPanel btnPanel = new JPanel();
		JButton btnThemMon = new JButton("Thêm Món");
		JButton btnThanhToan = new JButton("Thanh Toán");
		JButton btnProfile = new JButton("Thông Tin Cá Nhân");
		JButton btnDoiPass = new JButton("Đổi Mật Khẩu");
		JButton btnLogout = new JButton("Đăng Xuất");

		btnPanel.add(btnThemMon);
		btnPanel.add(btnThanhToan);
		btnPanel.add(btnProfile);
		btnPanel.add(btnDoiPass);
		btnPanel.add(btnLogout);
		bottomArea.add(btnPanel);

		rightPanel.add(bottomArea, BorderLayout.SOUTH);
		add(rightPanel, BorderLayout.EAST);

		// Actions
		btnThemMon.addActionListener(e -> showAddProductDialog());
		btnThanhToan.addActionListener(e -> checkout());
		btnProfile.addActionListener(e -> showProfileDialog());
		btnDoiPass.addActionListener(e -> changePassword());
		btnLogout.addActionListener(e -> {
			this.dispose();
			new LoginFrame().setVisible(true);
		});

		loadTables();
	}

	private void showProfileDialog() {
		String info = "=== THÔNG TIN NHÂN VIÊN ===\n\n"
				+ "• Mã nhân viên: " + currentUser.getId() + "\n"
				+ "• Tên đăng nhập: " + currentUser.getUsername() + "\n"
				+ "• Họ và tên: " + currentUser.getFullName() + "\n"
				+ "• Quyền hạn: " + currentUser.getRole() + "\n"
				+ "• Trạng thái tài khoản: Đang hoạt động (Active)";
		JOptionPane.showMessageDialog(this, info, "Thông Tin Cá Nhân", JOptionPane.INFORMATION_MESSAGE);
	}

	private void changePassword() {
		JPasswordField txtOldPass = new JPasswordField();
		JPasswordField txtNewPass = new JPasswordField();
		JPasswordField txtConfirmPass = new JPasswordField();

		Object[] fields = {
			"Mật khẩu hiện tại:", txtOldPass,
			"Mật khẩu mới:", txtNewPass,
			"Xác nhận mật khẩu mới:", txtConfirmPass
		};

		int option = JOptionPane.showConfirmDialog(this, fields, "Đổi Mật Khẩu", JOptionPane.OK_CANCEL_OPTION);
		if (option == JOptionPane.OK_OPTION) {
			String oldPass = new String(txtOldPass.getPassword());
			String newPass = new String(txtNewPass.getPassword()).trim();
			String confirmPass = new String(txtConfirmPass.getPassword()).trim();

			if (!oldPass.equals(currentUser.getPassword())) {
				JOptionPane.showMessageDialog(this, "Mật khẩu hiện tại không chính xác!", "Lỗi", JOptionPane.ERROR_MESSAGE);
				return;
			}
			if (newPass.isEmpty()) {
				JOptionPane.showMessageDialog(this, "Mật khẩu mới không được để trống!", "Lỗi", JOptionPane.WARNING_MESSAGE);
				return;
			}
			if (!newPass.equals(confirmPass)) {
				JOptionPane.showMessageDialog(this, "Xác nhận mật khẩu mới không trùng khớp!", "Lỗi", JOptionPane.WARNING_MESSAGE);
				return;
			}

			Response res = SocketClient.getInstance().sendRequest(
					new Request(ActionType.CHANGE_PASSWORD, new Object[] { currentUser.getId(), newPass }));
			if (res != null && res.isSuccess()) {
				JOptionPane.showMessageDialog(this, "Đổi mật khẩu thành công!");
				currentUser.setPassword(newPass);
			} else {
				JOptionPane.showMessageDialog(this, "Lỗi khi đổi mật khẩu!", "Lỗi", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private void loadTables() {
		Response res = SocketClient.getInstance().sendRequest(new Request(ActionType.GET_TABLES, null));
		if (res != null && res.isSuccess()) {
			@SuppressWarnings("unchecked")
			List<CoffeeTable> tables = (List<CoffeeTable>) res.getData();
			tablePanel.removeAll();
			for (CoffeeTable t : tables) {
				JButton btnTable = new JButton(t.getName() + " (" + t.getStatus() + ")");
				btnTable.setPreferredSize(new Dimension(100, 100));

				btnTable.setContentAreaFilled(false);
				btnTable.setOpaque(true);

				if ("OCCUPIED".equals(t.getStatus())) {
					btnTable.setBackground(new Color(255, 100, 100));
					btnTable.setForeground(Color.BLACK);
				} else {
					btnTable.setBackground(new Color(150, 255, 150));
					btnTable.setForeground(Color.BLACK);
				}

				btnTable.addActionListener(e -> {
					selectedTableId = t.getId();
					loadActiveBill();
				});
				tablePanel.add(btnTable);
			}
			tablePanel.revalidate();
			tablePanel.repaint();
		}
	}

	private void loadActiveBill() {
		if (selectedTableId == -1)
			return;

		Response res = SocketClient.getInstance().sendRequest(new Request(ActionType.GET_ACTIVE_BILL, selectedTableId));
		orderTableModel.setRowCount(0);

		if (res != null && res.isSuccess() && res.getData() != null) {
			Bill activeBill = (Bill) res.getData();
			this.activeBillId = activeBill.getId();

			float total = 0;
			if (activeBill.getDetails() != null) {
				for (BillDetail bd : activeBill.getDetails()) {
					float subTotal = bd.getQuantity() * bd.getPrice();
					total += subTotal;
					orderTableModel
							.addRow(new Object[] { bd.getProduct() != null ? bd.getProduct().getName() : "Unknown",
									bd.getQuantity(), bd.getPrice(), subTotal });
				}
			}
			lblTotal.setText("Tổng tiền: " + total + " VNĐ");
		} else {
			this.activeBillId = -1;
			lblTotal.setText("Tổng tiền: 0 VNĐ");
		}
	}

	private void showAddProductDialog() {
		if (selectedTableId == -1) {
			JOptionPane.showMessageDialog(this, "Vui lòng chọn bàn trước!");
			return;
		}

		Response prodRes = SocketClient.getInstance().sendRequest(new Request(ActionType.GET_PRODUCTS, null));
		if (prodRes == null || !prodRes.isSuccess())
			return;

		@SuppressWarnings("unchecked")
		List<Product> products = (List<Product>) prodRes.getData();
		if (products.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Menu trống! Hãy báo Quản lý thêm món mới.");
			return;
		}

		JComboBox<Product> cbProducts = new JComboBox<>(products.toArray(new Product[0]));
		JTextField txtQty = new JTextField("1");

		Object[] message = { "Chọn đồ uống:", cbProducts, "Số lượng:", txtQty };

		int option = JOptionPane.showConfirmDialog(this, message, "Thêm Món Vào Bàn", JOptionPane.OK_CANCEL_OPTION);
		if (option == JOptionPane.OK_OPTION) {
			try {
				Product selected = (Product) cbProducts.getSelectedItem();
				int qty = Integer.parseInt(txtQty.getText());

				OrderDto dto = new OrderDto(selectedTableId, currentUser.getId(), selected.getId(), qty);
				Response res = SocketClient.getInstance().sendRequest(new Request(ActionType.ADD_TO_BILL, dto));

				if (res != null && res.isSuccess()) {
					loadTables();
					loadActiveBill();
				} else {
					JOptionPane.showMessageDialog(this, "Lỗi khi thêm món!");
				}
			} catch (Exception e) {
				JOptionPane.showMessageDialog(this, "Số lượng không hợp lệ!");
			}
		}
	}

	private void checkout() {
		if (activeBillId == -1) {
			JOptionPane.showMessageDialog(this, "Bàn này chưa có hóa đơn!");
			return;
		}

		int confirm = JOptionPane.showConfirmDialog(this, "Thanh toán hóa đơn này?", "Xác nhận",
				JOptionPane.YES_NO_OPTION);
		if (confirm == JOptionPane.YES_OPTION) {
			Response res = SocketClient.getInstance().sendRequest(new Request(ActionType.CHECKOUT_BILL, activeBillId));
			if (res != null && res.isSuccess()) {
				JOptionPane.showMessageDialog(this, "Thanh toán thành công!");
				selectedTableId = -1;
				activeBillId = -1;
				orderTableModel.setRowCount(0);
				lblTotal.setText("Tổng tiền: 0 VNĐ");
				loadTables();
			} else {
				JOptionPane.showMessageDialog(this, "Lỗi khi thanh toán!");
			}
		}
	}
}
