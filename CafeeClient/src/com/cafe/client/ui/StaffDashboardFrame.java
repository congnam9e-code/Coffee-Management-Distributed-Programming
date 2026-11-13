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
import java.util.ArrayList;
import java.util.List;

public class StaffDashboardFrame extends JFrame {
	private static final long serialVersionUID = 1L;
	private final Employee currentUser;
	private JPanel tablePanel;
	private JLabel lblTableSummary;
	private JLabel lblOrderTitle;
	private DefaultTableModel orderTableModel;
	private JTable orderTable;
	private JLabel lblTotal;
	private JButton btnRefreshTables;
	private JButton btnThemMon;
	private JButton btnThanhToan;
	private JButton btnRefreshOrder;

	private List<CoffeeTable> currentTables = new ArrayList<>();
	private int selectedTableId = -1;
	private String selectedTableName = "";
	private int activeBillId = -1;

	public StaffDashboardFrame(Employee user) {
		this.currentUser = user;
		setTitle("Hệ Thống Bán Cafe - Nhân Viên: " + user.getFullName());
		setSize(1150, 700);
		setMinimumSize(new Dimension(1000, 650));
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setLocationRelativeTo(null);

		setLayout(new BorderLayout());

		// 1. Top Header Bar
		JPanel headerBar = UIUtils.createHeaderBar(
				"HỆ THỐNG BÁN CAFE",
				"Nhân viên phục vụ tại bàn | Kết nối máy chủ TCP",
				user.getFullName(),
				"Nhân viên",
				() -> UIUtils.confirmLogout(this, "Bạn có chắc chắn muốn đăng xuất khỏi hệ thống?"),
				() -> UIUtils.showChangePasswordDialog(this, "Đổi Mật Khẩu Cá Nhân"));
		add(headerBar, BorderLayout.NORTH);

		// 2. Main Content Split Pane
		JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
		mainPanel.setBackground(UIUtils.COLOR_BG);
		mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		// 2.1 Left Panel: Sơ đồ bàn Cafe
		JPanel leftPanel = new JPanel(new BorderLayout(0, 8));
		leftPanel.setBackground(UIUtils.COLOR_BG);

		JPanel leftTopBar = new JPanel(new BorderLayout());
		leftTopBar.setBackground(UIUtils.COLOR_CARD);
		leftTopBar.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(UIUtils.COLOR_BORDER, 1),
				BorderFactory.createEmptyBorder(8, 12, 8, 12)));

		lblTableSummary = new JLabel("Đang tải sơ đồ bàn...");
		lblTableSummary.setFont(UIUtils.FONT_BOLD);
		lblTableSummary.setForeground(UIUtils.COLOR_TEXT_DARK);
		leftTopBar.add(lblTableSummary, BorderLayout.WEST);

		btnRefreshTables = UIUtils.createButton("Tải lại bàn", UIUtils.COLOR_SECONDARY, Color.WHITE,
				UIUtils.FONT_SMALL);
		btnRefreshTables.addActionListener(e -> loadTables());
		leftTopBar.add(btnRefreshTables, BorderLayout.EAST);

		leftPanel.add(leftTopBar, BorderLayout.NORTH);

		tablePanel = new JPanel(new GridLayout(0, 4, 12, 12));
		tablePanel.setBackground(UIUtils.COLOR_BG);
		tablePanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		JScrollPane scrollTables = new JScrollPane(tablePanel);
		scrollTables.setBorder(BorderFactory.createLineBorder(UIUtils.COLOR_BORDER, 1));
		scrollTables.getViewport().setBackground(UIUtils.COLOR_BG);
		scrollTables.getVerticalScrollBar().setUnitIncrement(16);
		leftPanel.add(scrollTables, BorderLayout.CENTER);

		mainPanel.add(leftPanel, BorderLayout.CENTER);

		// 2.2 Right Panel: Chi tiết gọi món & thanh toán
		JPanel rightPanel = new JPanel(new BorderLayout(0, 8));
		rightPanel.setPreferredSize(new Dimension(460, 0));
		rightPanel.setBackground(UIUtils.COLOR_BG);

		JPanel orderHeader = new JPanel(new BorderLayout());
		orderHeader.setBackground(UIUtils.COLOR_CARD);
		orderHeader.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(UIUtils.COLOR_BORDER, 1),
				BorderFactory.createEmptyBorder(10, 14, 10, 14)));

		lblOrderTitle = new JLabel("Vui lòng chọn bàn trên sơ đồ");
		lblOrderTitle.setFont(UIUtils.FONT_TITLE);
		lblOrderTitle.setForeground(UIUtils.COLOR_PRIMARY);
		orderHeader.add(lblOrderTitle, BorderLayout.CENTER);

		rightPanel.add(orderHeader, BorderLayout.NORTH);

		orderTableModel = new DefaultTableModel(
				new String[] { "STT", "Tên món", "Số lượng", "Đơn giá (VNĐ)", "Thành tiền (VNĐ)" }, 0) {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
		orderTable = new JTable(orderTableModel);
		UIUtils.formatTable(orderTable);
		orderTable.getColumnModel().getColumn(0).setPreferredWidth(45);
		orderTable.getColumnModel().getColumn(1).setPreferredWidth(160);
		orderTable.getColumnModel().getColumn(2).setPreferredWidth(65);
		orderTable.getColumnModel().getColumn(3).setPreferredWidth(95);
		orderTable.getColumnModel().getColumn(4).setPreferredWidth(105);
		UIUtils.alignTableColumns(orderTable, new int[] { 0, 2 }, new int[] { 3, 4 });

		JScrollPane scrollOrder = new JScrollPane(orderTable);
		scrollOrder.setBorder(BorderFactory.createLineBorder(UIUtils.COLOR_BORDER, 1));
		scrollOrder.getViewport().setBackground(Color.WHITE);
		rightPanel.add(scrollOrder, BorderLayout.CENTER);

		JPanel rightBottom = new JPanel(new BorderLayout(0, 10));
		rightBottom.setBackground(UIUtils.COLOR_BG);

		JPanel totalCard = new JPanel(new BorderLayout());
		totalCard.setBackground(UIUtils.COLOR_CARD);
		totalCard.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(UIUtils.COLOR_BORDER, 1),
				BorderFactory.createEmptyBorder(12, 16, 12, 16)));

		JLabel lblTotalText = new JLabel("TỔNG THANH TOÁN:");
		lblTotalText.setFont(UIUtils.FONT_TITLE);
		lblTotalText.setForeground(UIUtils.COLOR_TEXT_DARK);
		totalCard.add(lblTotalText, BorderLayout.WEST);

		lblTotal = new JLabel("0 VNĐ");
		lblTotal.setFont(UIUtils.FONT_MONEY);
		lblTotal.setForeground(UIUtils.COLOR_SUCCESS);
		totalCard.add(lblTotal, BorderLayout.EAST);

		rightBottom.add(totalCard, BorderLayout.NORTH);

		JPanel actionBox = new JPanel(new GridLayout(2, 1, 0, 8));
		actionBox.setOpaque(false);

		JPanel actionRow1 = new JPanel(new GridLayout(1, 2, 8, 0));
		actionRow1.setOpaque(false);

		btnThemMon = UIUtils.createButton("Thêm Món", UIUtils.COLOR_PRIMARY, Color.WHITE, UIUtils.FONT_BOLD);
		btnRefreshOrder = UIUtils.createButton("Làm Mới Order", UIUtils.COLOR_SECONDARY, Color.WHITE,
				UIUtils.FONT_BOLD);
		actionRow1.add(btnThemMon);
		actionRow1.add(btnRefreshOrder);
		actionBox.add(actionRow1);

		btnThanhToan = UIUtils.createButton("THANH TOÁN HÓA ĐƠN", UIUtils.COLOR_SUCCESS, Color.WHITE,
				UIUtils.FONT_TITLE);
		btnThanhToan.setPreferredSize(new Dimension(0, 42));
		actionBox.add(btnThanhToan);

		rightBottom.add(actionBox, BorderLayout.SOUTH);
		rightPanel.add(rightBottom, BorderLayout.SOUTH);

		mainPanel.add(rightPanel, BorderLayout.EAST);
		add(mainPanel, BorderLayout.CENTER);

		// 3. Sự kiện các nút
		btnThemMon.addActionListener(e -> showAddProductDialog());
		btnThanhToan.addActionListener(e -> checkout());
		btnRefreshOrder.addActionListener(e -> {
			loadTables();
			loadActiveBill();
		});

		SocketClient.getInstance().setSyncListener(topic -> SwingUtilities.invokeLater(() -> {
			if (!isDisplayable()) {
				return;
			}
			if ("ORDER_CHANGED".equals(topic) || "ALL_REFRESH".equals(topic)) {
				loadTables(true);
				if (selectedTableId > 0) {
					loadActiveBill(true);
				}
			}
		}));

		loadTables();
	}

	private void loadTables() {
		loadTables(false);
	}

	private void loadTables(boolean silent) {
		java.util.function.Supplier<Response> task = () -> SocketClient.getInstance()
				.sendRequest(new Request(ActionType.GET_TABLES, null));
		java.util.function.Consumer<Response> onComplete = res -> {
			if (res != null && res.isSuccess() && res.getData() instanceof List<?> rawList) {
				List<CoffeeTable> tables = new ArrayList<>();
				for (Object item : rawList) {
					if (item instanceof CoffeeTable t) {
						tables.add(t);
					}
				}
				this.currentTables = tables;
				renderTables();
			} else if (!silent) {
				String msg = res != null && res.getMessage() != null
						? res.getMessage()
						: "Không thể tải sơ đồ bàn từ máy chủ!";
				JOptionPane.showMessageDialog(this, msg, "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
			}
		};

		if (silent) {
			UIUtils.runSilentAsync(task, onComplete);
		} else {
			UIUtils.runAsync(this, btnRefreshTables, task, onComplete);
		}
	}

	private void renderTables() {
		tablePanel.removeAll();

		int availableCount = 0;
		int occupiedCount = 0;

		for (CoffeeTable t : currentTables) {
			boolean isOccupied = "OCCUPIED".equalsIgnoreCase(t.getStatus());
			if (isOccupied) {
				occupiedCount++;
			} else {
				availableCount++;
			}

			boolean isSelected = (t.getId() == selectedTableId);
			String statusText = isOccupied ? "Có khách" : "Trống";
			String statusColor = isOccupied ? "#DC2626" : "#059669";
			String nameColor = isOccupied ? "#991B1B" : "#065F46";
			String selectBadge = isSelected
					? "<br><span style='font-size:10px; background-color:#2563EB; color:white; padding:1px 5px; border-radius:3px;'>[ĐANG CHỌN]</span>"
					: "";

			String htmlText = "<html><center>"
					+ "<span style='font-size:13px; font-weight:bold; color:" + nameColor + ";'>" + t.getName()
					+ "</span><br>"
					+ "<span style='font-size:11px; font-weight:bold; color:" + statusColor + ";'>" + statusText
					+ "</span>"
					+ selectBadge
					+ "</center></html>";

			JButton btnTable = new JButton(htmlText);
			btnTable.setPreferredSize(new Dimension(105, 90));
			btnTable.setFocusPainted(false);
			btnTable.setContentAreaFilled(false);
			btnTable.setOpaque(true);
			btnTable.setCursor(new Cursor(Cursor.HAND_CURSOR));

			if (isOccupied) {
				btnTable.setBackground(UIUtils.BG_TABLE_OCCUPIED);
				btnTable.setBorder(BorderFactory.createLineBorder(
						isSelected ? UIUtils.BORDER_TABLE_SELECTED : UIUtils.BORDER_TABLE_OCCUPIED,
						isSelected ? 3 : 2));
			} else {
				btnTable.setBackground(UIUtils.BG_TABLE_AVAILABLE);
				btnTable.setBorder(BorderFactory.createLineBorder(
						isSelected ? UIUtils.BORDER_TABLE_SELECTED : UIUtils.BORDER_TABLE_AVAILABLE,
						isSelected ? 3 : 2));
			}

			btnTable.addActionListener(e -> {
				selectedTableId = t.getId();
				selectedTableName = t.getName();
				// Chỉ vẽ lại trạng thái viền chọn trên UI, không gọi lại GET_TABLES qua mạng
				renderTables();
				loadActiveBill();
			});
			tablePanel.add(btnTable);
		}

		lblTableSummary.setText(String.format("Tổng số: %d bàn  |  Trống: %d  |  Có khách: %d",
				currentTables.size(), availableCount, occupiedCount));

		tablePanel.revalidate();
		tablePanel.repaint();
	}

	private void loadActiveBill() {
		loadActiveBill(false);
	}

	private void loadActiveBill(boolean silent) {
		if (selectedTableId == -1) {
			this.activeBillId = -1;
			lblOrderTitle.setText("Vui lòng chọn bàn trên sơ đồ");
			orderTableModel.setRowCount(0);
			lblTotal.setText("0 VNĐ");
			return;
		}

		int targetTableId = selectedTableId;
		String targetTableName = selectedTableName;

		java.util.function.Supplier<Response> task = () -> SocketClient.getInstance()
				.sendRequest(new Request(ActionType.GET_ACTIVE_BILL, targetTableId));
		java.util.function.Consumer<Response> onComplete = res -> {
			if (targetTableId != this.selectedTableId) {
				return;
			}
			if (res == null || !res.isSuccess()) {
				if (!silent) {
					String msg = res != null && res.getMessage() != null
							? res.getMessage()
							: "Không thể tải hóa đơn của bàn!";
					JOptionPane.showMessageDialog(this, msg, "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
				}
				return;
			}

			orderTableModel.setRowCount(0);
			if (res.getData() instanceof Bill activeBill) {
				this.activeBillId = activeBill.getId();
				lblOrderTitle.setText("HÓA ĐƠN: " + targetTableName.toUpperCase() + " (Đang phục vụ)");

				int stt = 1;
				if (activeBill.getDetails() != null) {
					for (BillDetail bd : activeBill.getDetails()) {
						double subTotal = (double) bd.getQuantity() * bd.getPrice();
						String prodName = (bd.getProduct() != null)
								? bd.getProduct().getName()
								: "Không xác định";
						orderTableModel.addRow(new Object[] {
								stt++,
								prodName,
								bd.getQuantity(),
								UIUtils.formatCurrency(bd.getPrice()),
								UIUtils.formatCurrency(subTotal)
						});
					}
				}
				lblTotal.setText(UIUtils.formatCurrency(activeBill.getTotalAmount()));
			} else {
				this.activeBillId = -1;
				lblOrderTitle.setText("BÀN: " + targetTableName.toUpperCase() + " (Chưa có món nào)");
				lblTotal.setText("0 VNĐ");
			}
		};

		if (silent) {
			UIUtils.runSilentAsync(task, onComplete);
		} else {
			UIUtils.runAsync(this, btnRefreshOrder, task, onComplete);
		}
	}

	private void showAddProductDialog() {
		if (selectedTableId == -1) {
			JOptionPane.showMessageDialog(this, "Vui lòng chọn bàn trên sơ đồ trước khi thêm món!", "Thông báo",
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		UIUtils.runAsync(this, btnThemMon,
				() -> SocketClient.getInstance().sendRequest(new Request(ActionType.GET_PRODUCTS, null)),
				prodRes -> {
					if (prodRes == null || !prodRes.isSuccess()) {
						JOptionPane.showMessageDialog(this,
								prodRes != null && prodRes.getMessage() != null
										? prodRes.getMessage()
										: "Không thể tải danh sách món từ máy chủ!",
								"Lỗi", JOptionPane.ERROR_MESSAGE);
						return;
					}

					@SuppressWarnings("unchecked")
					List<Product> products = (List<Product>) prodRes.getData();
					if (products == null || products.isEmpty()) {
						JOptionPane.showMessageDialog(this,
								"Thực đơn hiện đang trống! Hãy liên hệ Quản lý để thêm món.",
								"Thông báo", JOptionPane.INFORMATION_MESSAGE);
						return;
					}

					JComboBox<Product> cbProducts = new JComboBox<>(products.toArray(new Product[0]));
					cbProducts.setFont(UIUtils.FONT_REGULAR);
					cbProducts.setBackground(Color.WHITE);
					cbProducts.setForeground(UIUtils.COLOR_TEXT_DARK);
					JTextField txtQty = new JTextField("1");
					UIUtils.styleTextField(txtQty);

					Object[] message = {
							"Bàn được chọn: " + selectedTableName,
							"Chọn món đồ uống:", cbProducts,
							"Số lượng gọi:", txtQty
					};

					int option = JOptionPane.showConfirmDialog(this, message, "Thêm Món Vào " + selectedTableName,
							JOptionPane.OK_CANCEL_OPTION);
					if (option != JOptionPane.OK_OPTION) {
						return;
					}

					try {
						Product selected = (Product) cbProducts.getSelectedItem();
						if (selected == null) {
							return;
						}
						int qty = Integer.parseInt(txtQty.getText().trim());
						if (qty <= 0) {
							JOptionPane.showMessageDialog(this, "Số lượng món phải lớn hơn 0!", "Cảnh báo",
									JOptionPane.WARNING_MESSAGE);
							return;
						}

						OrderDto dto = new OrderDto(selectedTableId, currentUser.getId(), selected.getId(), qty);
						UIUtils.runAsync(this, btnThemMon,
								() -> SocketClient.getInstance().sendRequest(new Request(ActionType.ADD_TO_BILL, dto)),
								res -> {
									if (res != null && res.isSuccess()) {
										loadTables();
										loadActiveBill();
									} else {
										JOptionPane.showMessageDialog(this,
												res != null && res.getMessage() != null
														? res.getMessage()
														: "Lỗi khi thêm món!",
												"Lỗi", JOptionPane.ERROR_MESSAGE);
									}
								});
					} catch (NumberFormatException ex) {
						JOptionPane.showMessageDialog(this, "Số lượng phải là một số nguyên hợp lệ!", "Cảnh báo",
								JOptionPane.WARNING_MESSAGE);
					}
				});
	}

	private void checkout() {
		if (selectedTableId == -1) {
			JOptionPane.showMessageDialog(this, "Vui lòng chọn bàn cần thanh toán!", "Thông báo",
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		if (activeBillId == -1 || orderTableModel.getRowCount() == 0) {
			JOptionPane.showMessageDialog(this, "Bàn này chưa có món nào để thanh toán!", "Thông báo",
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		String confirmMsg = String.format("Xác nhận thanh toán cho %s?\nTổng tiền: %s",
				selectedTableName, lblTotal.getText());

		int confirm = JOptionPane.showConfirmDialog(this, confirmMsg, "Xác nhận thanh toán",
				JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
		if (confirm != JOptionPane.YES_OPTION) {
			return;
		}

		int billToCheckout = activeBillId;
		String paidTableName = selectedTableName;

		UIUtils.runAsync(this, btnThanhToan,
				() -> SocketClient.getInstance()
						.sendRequest(new Request(ActionType.CHECKOUT_BILL, billToCheckout)),
				res -> {
					if (res != null && res.isSuccess()) {
						JOptionPane.showMessageDialog(this,
								"Thanh toán thành công cho " + paidTableName + "!\nCảm ơn quý khách.",
								"Thanh toán thành công", JOptionPane.INFORMATION_MESSAGE);
						selectedTableId = -1;
						selectedTableName = "";
						activeBillId = -1;
						orderTableModel.setRowCount(0);
						lblTotal.setText("0 VNĐ");
						lblOrderTitle.setText("Vui lòng chọn bàn trên sơ đồ");
						loadTables();
					} else {
						JOptionPane.showMessageDialog(this,
								res != null && res.getMessage() != null
										? res.getMessage()
										: "Lỗi khi thực hiện thanh toán!",
								"Lỗi", JOptionPane.ERROR_MESSAGE);
						loadTables();
						loadActiveBill();
					}
				});
	}
}
