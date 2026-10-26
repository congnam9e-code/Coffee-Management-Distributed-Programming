package com.cafe.client.ui;

import com.cafe.client.socket.SocketClient;
import com.cafe.common.entity.Bill;
import com.cafe.common.network.ActionType;
import com.cafe.common.network.Request;
import com.cafe.common.network.Response;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.List;

public class BillPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private JTable table;
	private DefaultTableModel tableModel;
	private JButton btnRefresh;

	public BillPanel() {
		setLayout(new BorderLayout());

		tableModel = new DefaultTableModel(
				new String[] { "ID Hóa đơn", "Bàn", "Nhân viên", "Giờ vào", "Giờ ra", "Tổng tiền", "Trạng thái" }, 0);
		table = new JTable(tableModel);
		add(new JScrollPane(table), BorderLayout.CENTER);

		JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		btnRefresh = new JButton("Làm mới danh sách");
		topPanel.add(btnRefresh);
		add(topPanel, BorderLayout.NORTH);

		btnRefresh.addActionListener(e -> loadData());

		loadData();
	}

	private void loadData() {
		Response res = SocketClient.getInstance().sendRequest(new Request(ActionType.GET_ALL_BILLS, null));
		if (res != null && res.isSuccess()) {
			@SuppressWarnings("unchecked")
			List<Bill> bills = (List<Bill>) res.getData();
			tableModel.setRowCount(0);
			SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
			for (Bill b : bills) {
				String checkIn = b.getCheckInTime() != null ? sdf.format(b.getCheckInTime()) : "";
				String checkOut = b.getCheckOutTime() != null ? sdf.format(b.getCheckOutTime()) : "";
				String tableName = b.getTable() != null ? b.getTable().getName() : "";
				String empName = b.getEmployee() != null ? b.getEmployee().getFullName() : "";

				tableModel.addRow(new Object[] { b.getId(), tableName, empName, checkIn, checkOut, b.getTotalAmount(),
						b.getStatus() });
			}
		}
	}
}
