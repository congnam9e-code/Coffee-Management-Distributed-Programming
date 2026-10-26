package com.cafe.client.ui;

import com.cafe.client.socket.SocketClient;
import com.cafe.common.entity.Employee;
import com.cafe.common.network.ActionType;
import com.cafe.common.network.Request;
import com.cafe.common.network.Response;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class EmployeePanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private JTable table;
	private DefaultTableModel tableModel;
	private JTextField txtUsername, txtPassword, txtFullName;
	private JComboBox<String> cbRole;
	private JButton btnAdd, btnUpdate, btnDelete, btnRefresh;

	public EmployeePanel() {
		setLayout(new BorderLayout());

		// Table
		tableModel = new DefaultTableModel(new String[] { "ID", "Tài khoản", "Họ Tên", "Vai trò" }, 0);
		table = new JTable(tableModel);
		add(new JScrollPane(table), BorderLayout.CENTER);

		// Input Form
		JPanel formPanel = new JPanel(new GridLayout(5, 2, 5, 5));
		formPanel.setBorder(BorderFactory.createTitledBorder("Thông tin Nhân viên"));

		formPanel.add(new JLabel("Tài khoản:"));
		txtUsername = new JTextField();
		formPanel.add(txtUsername);

		formPanel.add(new JLabel("Mật khẩu:"));
		txtPassword = new JPasswordField();
		formPanel.add(txtPassword);

		formPanel.add(new JLabel("Họ tên:"));
		txtFullName = new JTextField();
		formPanel.add(txtFullName);

		formPanel.add(new JLabel("Quyền hạn:"));
		cbRole = new JComboBox<>(new String[] { "STAFF", "MANAGER" });
		formPanel.add(cbRole);

		btnAdd = new JButton("Thêm");
		btnUpdate = new JButton("Cập nhật");
		btnDelete = new JButton("Xóa");
		btnRefresh = new JButton("Làm mới");

		JPanel btnGroup = new JPanel(new GridLayout(2, 2, 5, 5));
		btnGroup.add(btnAdd);
		btnGroup.add(btnUpdate);
		btnGroup.add(btnDelete);
		btnGroup.add(btnRefresh);

		formPanel.add(new JLabel("Thao tác:"));
		formPanel.add(btnGroup);

		add(formPanel, BorderLayout.EAST);

		// Selection Listener
		table.getSelectionModel().addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting() && table.getSelectedRow() != -1) {
				int row = table.getSelectedRow();
				txtUsername.setText(tableModel.getValueAt(row, 1).toString());
				txtPassword.setText(""); // Hide password for safety
				txtFullName.setText(tableModel.getValueAt(row, 2).toString());
				cbRole.setSelectedItem(tableModel.getValueAt(row, 3).toString());
			}
		});

		// Events
		btnRefresh.addActionListener(e -> loadData());
		btnAdd.addActionListener(e -> addEmployee());
		btnUpdate.addActionListener(e -> updateEmployee());
		btnDelete.addActionListener(e -> deleteEmployee());

		loadData();
	}

	private void loadData() {
		Response res = SocketClient.getInstance().sendRequest(new Request(ActionType.GET_EMPLOYEES, null));
		if (res != null && res.isSuccess()) {
			@SuppressWarnings("unchecked")
			List<Employee> employees = (List<Employee>) res.getData();
			tableModel.setRowCount(0);
			for (Employee emp : employees) {
				tableModel.addRow(new Object[] { emp.getId(), emp.getUsername(), emp.getFullName(), emp.getRole() });
			}
		}
	}

	private void addEmployee() {
		String username = txtUsername.getText().trim();
		String password = txtPassword.getText().trim();
		String fullName = txtFullName.getText().trim();
		String role = (String) cbRole.getSelectedItem();

		if (username.isEmpty() || password.isEmpty() || fullName.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ thông tin!");
			return;
		}

		Employee emp = new Employee();
		emp.setUsername(username);
		emp.setPassword(password);
		emp.setFullName(fullName);
		emp.setRole(role);

		Response res = SocketClient.getInstance().sendRequest(new Request(ActionType.ADD_EMPLOYEE, emp));
		if (res != null && res.isSuccess()) {
			JOptionPane.showMessageDialog(this, res.getMessage());
			txtUsername.setText("");
			txtPassword.setText("");
			txtFullName.setText("");
			loadData();
		} else {
			JOptionPane.showMessageDialog(this, res != null ? res.getMessage() : "Lỗi kết nối", "Lỗi",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private void updateEmployee() {
		if (table.getSelectedRow() == -1) {
			JOptionPane.showMessageDialog(this, "Chọn nhân viên cần sửa!");
			return;
		}
		int id = (int) tableModel.getValueAt(table.getSelectedRow(), 0);
		Employee emp = new Employee();
		emp.setId(id);
		emp.setUsername(txtUsername.getText().trim());
		emp.setPassword(txtPassword.getText().trim());
		emp.setFullName(txtFullName.getText().trim());
		emp.setRole((String) cbRole.getSelectedItem());

		Response res = SocketClient.getInstance().sendRequest(new Request(ActionType.UPDATE_EMPLOYEE, emp));
		if (res != null && res.isSuccess()) {
			JOptionPane.showMessageDialog(this, "Cập nhật thành công!");
			loadData();
		} else {
			JOptionPane.showMessageDialog(this, "Lỗi: " + (res != null ? res.getMessage() : "Mất kết nối"));
		}
	}

	private void deleteEmployee() {
		if (table.getSelectedRow() == -1) {
			JOptionPane.showMessageDialog(this, "Chọn nhân viên cần xóa!");
			return;
		}
		int id = (int) tableModel.getValueAt(table.getSelectedRow(), 0);
		if (JOptionPane.showConfirmDialog(this, "Xóa nhân viên này?", "Xác nhận",
				JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
			Response res = SocketClient.getInstance().sendRequest(new Request(ActionType.DELETE_EMPLOYEE, id));
			if (res != null && res.isSuccess()) {
				JOptionPane.showMessageDialog(this, "Xóa thành công!");
				loadData();
			} else {
				JOptionPane.showMessageDialog(this, "Lỗi: " + (res != null ? res.getMessage() : "Mất kết nối"));
			}
		}
	}
}
