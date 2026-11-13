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

public class EmployeePanel extends JPanel implements UIUtils.Refreshable {
	private static final long serialVersionUID = 1L;
	private JTable table;
	private DefaultTableModel tableModel;
	private JTextField txtUsername, txtFullName;
	private JPasswordField txtPassword;
	private JComboBox<String> cbRole;
	private JButton btnAdd, btnUpdate, btnDelete, btnRefresh;

	public EmployeePanel() {
		this(true);
	}

	public EmployeePanel(boolean autoLoad) {
		setLayout(new BorderLayout(10, 10));
		setBackground(UIUtils.COLOR_BG);
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		// 1. Table (Center)
		tableModel = new DefaultTableModel(
				new String[] { "Mã NV", "Tên tài khoản", "Họ và tên", "Vai trò", "RawRole" }, 0) {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
		table = new JTable(tableModel);
		UIUtils.formatTable(table);

		// Ẩn cột RawRole
		table.getColumnModel().getColumn(4).setMinWidth(0);
		table.getColumnModel().getColumn(4).setMaxWidth(0);
		table.getColumnModel().getColumn(4).setWidth(0);

		table.getColumnModel().getColumn(0).setPreferredWidth(70);
		table.getColumnModel().getColumn(1).setPreferredWidth(140);
		table.getColumnModel().getColumn(2).setPreferredWidth(220);
		table.getColumnModel().getColumn(3).setPreferredWidth(120);
		UIUtils.alignTableColumns(table, new int[] { 0, 3 }, null);

		JScrollPane scrollTable = new JScrollPane(table);
		scrollTable.setBorder(BorderFactory.createLineBorder(UIUtils.COLOR_BORDER, 1));
		scrollTable.getViewport().setBackground(Color.WHITE);
		add(scrollTable, BorderLayout.CENTER);

		// 2. Input Form (East)
		JPanel formPanel = new JPanel(new BorderLayout(0, 10));
		formPanel.setPreferredSize(new Dimension(340, 0));
		formPanel.setBackground(UIUtils.COLOR_CARD);
		formPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(UIUtils.COLOR_BORDER, 1),
				BorderFactory.createEmptyBorder(15, 15, 15, 15)));

		JLabel lblFormTitle = new JLabel("THÔNG TIN NHÂN VIÊN");
		lblFormTitle.setFont(UIUtils.FONT_TITLE);
		lblFormTitle.setForeground(UIUtils.COLOR_TEXT_DARK);
		formPanel.add(lblFormTitle, BorderLayout.NORTH);

		JPanel fieldGrid = new JPanel(new GridLayout(4, 2, 8, 12));
		fieldGrid.setOpaque(false);

		JLabel lblUser = new JLabel("Tên tài khoản:");
		lblUser.setFont(UIUtils.FONT_BOLD);
		lblUser.setForeground(UIUtils.COLOR_TEXT_DARK);
		fieldGrid.add(lblUser);
		txtUsername = new JTextField();
		UIUtils.styleTextField(txtUsername);
		fieldGrid.add(txtUsername);

		JLabel lblPass = new JLabel("Mật khẩu:");
		lblPass.setFont(UIUtils.FONT_BOLD);
		lblPass.setForeground(UIUtils.COLOR_TEXT_DARK);
		fieldGrid.add(lblPass);
		txtPassword = new JPasswordField();
		UIUtils.styleTextField(txtPassword);
		txtPassword.setToolTipText("Để trống khi cập nhật nếu muốn giữ mật khẩu cũ");
		fieldGrid.add(txtPassword);

		JLabel lblName = new JLabel("Họ và tên:");
		lblName.setFont(UIUtils.FONT_BOLD);
		lblName.setForeground(UIUtils.COLOR_TEXT_DARK);
		fieldGrid.add(lblName);
		txtFullName = new JTextField();
		UIUtils.styleTextField(txtFullName);
		fieldGrid.add(txtFullName);

		JLabel lblRole = new JLabel("Vai trò / Quyền:");
		lblRole.setFont(UIUtils.FONT_BOLD);
		lblRole.setForeground(UIUtils.COLOR_TEXT_DARK);
		fieldGrid.add(lblRole);
		cbRole = new JComboBox<>(new String[] { "Nhân viên", "Quản lý" });
		cbRole.setFont(UIUtils.FONT_REGULAR);
		cbRole.setBackground(Color.WHITE);
		cbRole.setForeground(UIUtils.COLOR_TEXT_DARK);
		fieldGrid.add(cbRole);

		formPanel.add(fieldGrid, BorderLayout.CENTER);

		// Buttons
		JPanel btnGrid = new JPanel(new GridLayout(2, 2, 8, 8));
		btnGrid.setOpaque(false);

		btnAdd = UIUtils.createButton("Thêm mới", UIUtils.COLOR_SUCCESS, Color.WHITE, UIUtils.FONT_BOLD);
		btnUpdate = UIUtils.createButton("Cập nhật", UIUtils.COLOR_PRIMARY, Color.WHITE, UIUtils.FONT_BOLD);
		btnDelete = UIUtils.createButton("Xóa NV", UIUtils.COLOR_DANGER, Color.WHITE, UIUtils.FONT_BOLD);
		btnRefresh = UIUtils.createButton("Nhập lại", UIUtils.COLOR_SECONDARY, Color.WHITE, UIUtils.FONT_BOLD);

		btnGrid.add(btnAdd);
		btnGrid.add(btnUpdate);
		btnGrid.add(btnDelete);
		btnGrid.add(btnRefresh);

		formPanel.add(btnGrid, BorderLayout.SOUTH);
		add(formPanel, BorderLayout.EAST);

		// 3. Listeners
		table.getSelectionModel().addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting() && table.getSelectedRow() != -1) {
				int modelRow = table.convertRowIndexToModel(table.getSelectedRow());
				txtUsername.setText(tableModel.getValueAt(modelRow, 1).toString());
				txtPassword.setText("");
				txtFullName.setText(tableModel.getValueAt(modelRow, 2).toString());
				String displayRole = tableModel.getValueAt(modelRow, 3).toString();
				cbRole.setSelectedItem("Quản lý".equals(displayRole) ? "Quản lý" : "Nhân viên");
			}
		});

		btnRefresh.addActionListener(e -> refreshData());
		btnAdd.addActionListener(e -> addEmployee());
		btnUpdate.addActionListener(e -> updateEmployee());
		btnDelete.addActionListener(e -> deleteEmployee());

		if (autoLoad) {
			loadData();
		}
	}

	@Override
	public void refreshData() {
		clearForm();
		loadData(false);
	}

	public void reloadSilently() {
		loadData(true);
	}

	private void clearForm() {
		table.clearSelection();
		txtUsername.setText("");
		txtPassword.setText("");
		txtFullName.setText("");
		cbRole.setSelectedIndex(0);
	}

	private void loadData() {
		loadData(false);
	}

	private void loadData(boolean silent) {
		java.util.function.Supplier<Response> task = () -> SocketClient.getInstance()
				.sendRequest(new Request(ActionType.GET_EMPLOYEES, null));
		java.util.function.Consumer<Response> onComplete = res -> {
			if (res != null && res.isSuccess() && res.getData() instanceof List<?> rawList) {
				int selectedRow = table.getSelectedRow();
				Object selectedEmpId = selectedRow >= 0 ? tableModel.getValueAt(selectedRow, 0) : null;

				tableModel.setRowCount(0);
				int restoredRow = -1;
				for (Object item : rawList) {
					if (item instanceof Employee emp) {
						String displayRole = "MANAGER".equalsIgnoreCase(emp.getRole())
								? "Quản lý"
								: "Nhân viên";
						tableModel.addRow(new Object[] {
								emp.getId(),
								emp.getUsername(),
								emp.getFullName(),
								displayRole,
								emp.getRole()
						});
						if (selectedEmpId != null && selectedEmpId.equals(emp.getId())) {
							restoredRow = tableModel.getRowCount() - 1;
						}
					}
				}
				if (restoredRow >= 0) {
					table.setRowSelectionInterval(restoredRow, restoredRow);
				}
			} else if (!silent) {
				String msg = res != null && res.getMessage() != null
						? res.getMessage()
						: "Không thể tải danh sách nhân viên!";
				JOptionPane.showMessageDialog(this, msg, "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
			}
		};

		if (silent) {
			UIUtils.runSilentAsync(task, onComplete);
		} else {
			UIUtils.runAsync(this, btnRefresh, task, onComplete);
		}
	}

	private void addEmployee() {
		String username = txtUsername.getText().trim();
		String password = new String(txtPassword.getPassword()).trim();
		String fullName = txtFullName.getText().trim();
		String selectedRole = (String) cbRole.getSelectedItem();
		String serverRole = "Quản lý".equals(selectedRole) ? "MANAGER" : "STAFF";

		if (username.isEmpty() || username.contains(" ") || password.isEmpty() || fullName.isEmpty()) {
			JOptionPane.showMessageDialog(this,
					"Vui lòng nhập đầy đủ thông tin (tên tài khoản không chứa khoảng trắng), mật khẩu và họ tên!",
					"Cảnh báo", JOptionPane.WARNING_MESSAGE);
			return;
		}

		Employee emp = new Employee();
		emp.setUsername(username);
		emp.setPassword(password);
		emp.setFullName(fullName);
		emp.setRole(serverRole);

		UIUtils.runAsync(this, btnAdd,
				() -> SocketClient.getInstance().sendRequest(new Request(ActionType.ADD_EMPLOYEE, emp)),
				res -> {
					if (res != null && res.isSuccess()) {
						JOptionPane.showMessageDialog(this, "Đã thêm tài khoản nhân viên mới thành công!", "Thông báo",
								JOptionPane.INFORMATION_MESSAGE);
						clearForm();
						loadData();
					} else {
						JOptionPane.showMessageDialog(this,
								res != null && res.getMessage() != null ? res.getMessage() : "Lỗi kết nối máy chủ!",
								"Lỗi", JOptionPane.ERROR_MESSAGE);
					}
				});
	}

	private void updateEmployee() {
		if (table.getSelectedRow() == -1) {
			JOptionPane.showMessageDialog(this, "Vui lòng chọn nhân viên trong bảng cần cập nhật!", "Thông báo",
					JOptionPane.WARNING_MESSAGE);
			return;
		}
		int modelRow = table.convertRowIndexToModel(table.getSelectedRow());
		int id = (int) tableModel.getValueAt(modelRow, 0);

		String username = txtUsername.getText().trim();
		String password = new String(txtPassword.getPassword()).trim();
		String fullName = txtFullName.getText().trim();
		String selectedRole = (String) cbRole.getSelectedItem();
		String serverRole = "Quản lý".equals(selectedRole) ? "MANAGER" : "STAFF";

		if (username.isEmpty() || username.contains(" ") || fullName.isEmpty()) {
			JOptionPane.showMessageDialog(this,
					"Tên tài khoản (không khoảng trắng) và họ tên không được để trống!", "Cảnh báo",
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		Employee emp = new Employee();
		emp.setId(id);
		emp.setUsername(username);
		emp.setPassword(password);
		emp.setFullName(fullName);
		emp.setRole(serverRole);

		UIUtils.runAsync(this, btnUpdate,
				() -> SocketClient.getInstance().sendRequest(new Request(ActionType.UPDATE_EMPLOYEE, emp)),
				res -> {
					if (res != null && res.isSuccess()) {
						JOptionPane.showMessageDialog(this, "Cập nhật thông tin nhân viên thành công!", "Thông báo",
								JOptionPane.INFORMATION_MESSAGE);
						clearForm();
						loadData();
					} else {
						JOptionPane.showMessageDialog(this,
								"Lỗi: " + (res != null && res.getMessage() != null
										? res.getMessage()
										: "Mất kết nối máy chủ"),
								"Lỗi", JOptionPane.ERROR_MESSAGE);
					}
				});
	}

	private void deleteEmployee() {
		if (table.getSelectedRow() == -1) {
			JOptionPane.showMessageDialog(this, "Vui lòng chọn nhân viên trong bảng cần xóa!", "Thông báo",
					JOptionPane.WARNING_MESSAGE);
			return;
		}
		int modelRow = table.convertRowIndexToModel(table.getSelectedRow());
		int id = (int) tableModel.getValueAt(modelRow, 0);
		String empName = (String) tableModel.getValueAt(modelRow, 2);

		int choice = JOptionPane.showConfirmDialog(this,
				"Bạn có chắc chắn muốn xóa nhân viên \"" + empName + "\"?",
				"Xác nhận xóa nhân viên",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.QUESTION_MESSAGE);
		if (choice != JOptionPane.YES_OPTION) {
			return;
		}

		UIUtils.runAsync(this, btnDelete,
				() -> SocketClient.getInstance().sendRequest(new Request(ActionType.DELETE_EMPLOYEE, id)),
				res -> {
					if (res != null && res.isSuccess()) {
						JOptionPane.showMessageDialog(this, "Đã xóa tài khoản nhân viên thành công!", "Thông báo",
								JOptionPane.INFORMATION_MESSAGE);
						clearForm();
						loadData();
					} else {
						JOptionPane.showMessageDialog(this,
								"Lỗi: " + (res != null && res.getMessage() != null
										? res.getMessage()
										: "Mất kết nối máy chủ"),
								"Lỗi", JOptionPane.ERROR_MESSAGE);
					}
				});
	}
}
