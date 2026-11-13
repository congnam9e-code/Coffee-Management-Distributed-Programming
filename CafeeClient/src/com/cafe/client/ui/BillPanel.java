package com.cafe.client.ui;

import com.cafe.client.socket.SocketClient;
import com.cafe.common.entity.Bill;
import com.cafe.common.network.ActionType;
import com.cafe.common.network.Request;
import com.cafe.common.network.Response;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.List;

public class BillPanel extends JPanel implements UIUtils.Refreshable {
	private static final long serialVersionUID = 1L;
	private JTable table;
	private DefaultTableModel tableModel;
	private JLabel lblSummary;
	private JButton btnRefresh;

	public BillPanel() {
		this(true);
	}

	public BillPanel(boolean autoLoad) {
		setLayout(new BorderLayout(10, 10));
		setBackground(UIUtils.COLOR_BG);
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		// 1. Top Panel (Summary & Refresh)
		JPanel topPanel = new JPanel(new BorderLayout());
		topPanel.setBackground(UIUtils.COLOR_CARD);
		topPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(UIUtils.COLOR_BORDER, 1),
				BorderFactory.createEmptyBorder(8, 14, 8, 14)));

		lblSummary = new JLabel(autoLoad ? "Đang tải dữ liệu hóa đơn..." : "Sẵn sàng tải dữ liệu hóa đơn");
		lblSummary.setFont(UIUtils.FONT_TITLE);
		lblSummary.setForeground(UIUtils.COLOR_TEXT_DARK);
		topPanel.add(lblSummary, BorderLayout.WEST);

		btnRefresh = UIUtils.createButton("Tải lại dữ liệu", UIUtils.COLOR_SECONDARY, Color.WHITE,
				UIUtils.FONT_SMALL);
		btnRefresh.addActionListener(e -> loadData());
		topPanel.add(btnRefresh, BorderLayout.EAST);

		add(topPanel, BorderLayout.NORTH);

		// 2. Table (Center)
		tableModel = new DefaultTableModel(
				new String[] { "Mã HĐ", "Bàn phục vụ", "Nhân viên", "Giờ vào", "Giờ thanh toán", "Tổng tiền (VNĐ)",
						"Trạng thái" },
				0) {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
		table = new JTable(tableModel);
		UIUtils.formatTable(table);

		table.getColumnModel().getColumn(0).setPreferredWidth(65);
		table.getColumnModel().getColumn(1).setPreferredWidth(100);
		table.getColumnModel().getColumn(2).setPreferredWidth(160);
		table.getColumnModel().getColumn(3).setPreferredWidth(140);
		table.getColumnModel().getColumn(4).setPreferredWidth(140);
		table.getColumnModel().getColumn(5).setPreferredWidth(130);
		table.getColumnModel().getColumn(6).setPreferredWidth(120);

		UIUtils.alignTableColumns(table, new int[] { 0, 1, 3, 4 }, new int[] { 5 });

		table.getColumnModel().getColumn(6).setCellRenderer(new DefaultTableCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public Component getTableCellRendererComponent(JTable tbl, Object val, boolean isSel, boolean hasFoc,
					int row, int col) {
				Component c = super.getTableCellRendererComponent(tbl, val, isSel, hasFoc, row, col);
				setOpaque(true);
				setHorizontalAlignment(SwingConstants.CENTER);
				setFont(UIUtils.FONT_BOLD);
				setBackground(isSel ? tbl.getSelectionBackground() : Color.WHITE);
				String text = val != null ? val.toString() : "";
				if (isSel) {
					setForeground(tbl.getSelectionForeground());
				} else if ("Đã thanh toán".equals(text)) {
					setForeground(new Color(5, 150, 105));
				} else {
					setForeground(new Color(220, 38, 38));
				}
				return c;
			}
		});

		JScrollPane scrollTable = new JScrollPane(table);
		scrollTable.setBorder(BorderFactory.createLineBorder(UIUtils.COLOR_BORDER, 1));
		scrollTable.getViewport().setBackground(Color.WHITE);
		add(scrollTable, BorderLayout.CENTER);

		if (autoLoad) {
			loadData();
		}
	}

	@Override
	public void refreshData() {
		loadData(false);
	}

	public void reloadSilently() {
		loadData(true);
	}

	private void loadData() {
		loadData(false);
	}

	private void loadData(boolean silent) {
		java.util.function.Supplier<Response> task = () -> SocketClient.getInstance()
				.sendRequest(new Request(ActionType.GET_ALL_BILLS, null));
		java.util.function.Consumer<Response> onComplete = res -> applyBillsResponse(res, silent);
		if (silent) {
			UIUtils.runSilentAsync(task, onComplete);
		} else {
			UIUtils.runAsync(this, btnRefresh, task, onComplete);
		}
	}

	private void applyBillsResponse(Response res, boolean silent) {
		if (res != null && res.isSuccess() && res.getData() instanceof List<?> rawList) {
			int selectedRow = table.getSelectedRow();
			Object selectedBillId = selectedRow >= 0 ? tableModel.getValueAt(selectedRow, 0) : null;

			tableModel.setRowCount(0);
			SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");

			BigDecimal totalRevenue = BigDecimal.ZERO;
			int paidCount = 0;
			int restoredRow = -1;

			for (Object item : rawList) {
				if (!(item instanceof Bill b)) {
					continue;
				}
				String checkIn = b.getCheckInTime() != null ? sdf.format(b.getCheckInTime()) : "";
				String checkOut = b.getCheckOutTime() != null ? sdf.format(b.getCheckOutTime()) : "Chưa ra";
				String tableName = b.getTable() != null ? b.getTable().getName() : "";
				String empName = b.getEmployee() != null ? b.getEmployee().getFullName() : "";

				boolean isPaid = "PAID".equalsIgnoreCase(b.getStatus());
				String statusDisplay = isPaid ? "Đã thanh toán" : "Chưa thanh toán";

				if (isPaid) {
					totalRevenue = totalRevenue.add(BigDecimal.valueOf(b.getTotalAmount()));
					paidCount++;
				}

				tableModel.addRow(new Object[] {
						b.getId(),
						tableName,
						empName,
						checkIn,
						checkOut,
						UIUtils.formatCurrency(b.getTotalAmount()),
						statusDisplay
				});
				if (selectedBillId != null && selectedBillId.equals(b.getId())) {
					restoredRow = tableModel.getRowCount() - 1;
				}
			}

			lblSummary.setText(String.format(
					"Tổng số hóa đơn: %d  |  Đã thanh toán: %d  |  Tổng doanh thu: %s",
					rawList.size(), paidCount, UIUtils.formatCurrency(totalRevenue.doubleValue())));
			if (restoredRow >= 0) {
				table.setRowSelectionInterval(restoredRow, restoredRow);
			}
		} else if (!silent) {
			String msg = res != null && res.getMessage() != null
					? res.getMessage()
					: "Không thể tải danh sách hóa đơn!";
			lblSummary.setText("Không thể tải dữ liệu hóa đơn");
			JOptionPane.showMessageDialog(this, msg, "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
		}
	}
}
