package com.cafe.client.ui;

import com.cafe.client.socket.SocketClient;
import com.cafe.common.entity.Category;
import com.cafe.common.entity.Product;
import com.cafe.common.network.ActionType;
import com.cafe.common.network.Request;
import com.cafe.common.network.Response;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.regex.Pattern;

public class ProductPanel extends JPanel implements UIUtils.Refreshable {
	private static final long serialVersionUID = 1L;
	private JTable table;
	private DefaultTableModel tableModel;
	private TableRowSorter<DefaultTableModel> sorter;
	private JTextField txtSearch;
	private JTextField txtName, txtPrice;
	private JComboBox<Category> cbCategory;
	private JButton btnAdd, btnUpdate, btnDelete, btnRefresh;

	public ProductPanel() {
		setLayout(new BorderLayout(10, 10));
		setBackground(UIUtils.COLOR_BG);
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		// 1. Search Panel (North)
		JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
		searchPanel.setBackground(UIUtils.COLOR_CARD);
		searchPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(UIUtils.COLOR_BORDER, 1),
				BorderFactory.createEmptyBorder(4, 10, 4, 10)));

		JLabel lblSearch = new JLabel("Tìm kiếm món:");
		lblSearch.setFont(UIUtils.FONT_BOLD);
		lblSearch.setForeground(UIUtils.COLOR_TEXT_DARK);
		searchPanel.add(lblSearch);

		txtSearch = new JTextField(22);
		UIUtils.styleTextField(txtSearch);
		txtSearch.setToolTipText("Gõ tên món hoặc danh mục để lọc nhanh...");
		searchPanel.add(txtSearch);

		btnRefresh = UIUtils.createButton("Tải lại dữ liệu", UIUtils.COLOR_SECONDARY, Color.WHITE,
				UIUtils.FONT_SMALL);
		searchPanel.add(btnRefresh);

		add(searchPanel, BorderLayout.NORTH);

		// 2. Table (Center)
		tableModel = new DefaultTableModel(
				new String[] { "Mã món", "Tên món", "Đơn giá (VNĐ)", "Danh mục", "RawPrice" }, 0) {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
		table = new JTable(tableModel);
		UIUtils.formatTable(table);

		// Ẩn cột RawPrice (dùng để lấy số thực khi sửa)
		table.getColumnModel().getColumn(4).setMinWidth(0);
		table.getColumnModel().getColumn(4).setMaxWidth(0);
		table.getColumnModel().getColumn(4).setWidth(0);

		table.getColumnModel().getColumn(0).setPreferredWidth(70);
		table.getColumnModel().getColumn(1).setPreferredWidth(220);
		table.getColumnModel().getColumn(2).setPreferredWidth(130);
		table.getColumnModel().getColumn(3).setPreferredWidth(150);
		UIUtils.alignTableColumns(table, new int[] { 0, 3 }, new int[] { 2 });

		sorter = new TableRowSorter<>(tableModel);
		table.setRowSorter(sorter);

		JScrollPane scrollTable = new JScrollPane(table);
		scrollTable.setBorder(BorderFactory.createLineBorder(UIUtils.COLOR_BORDER, 1));
		scrollTable.getViewport().setBackground(Color.WHITE);
		add(scrollTable, BorderLayout.CENTER);

		// 3. Input Form (East)
		JPanel formPanel = new JPanel(new BorderLayout(0, 10));
		formPanel.setPreferredSize(new Dimension(340, 0));
		formPanel.setBackground(UIUtils.COLOR_CARD);
		formPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(UIUtils.COLOR_BORDER, 1),
				BorderFactory.createEmptyBorder(15, 15, 15, 15)));

		JLabel lblFormTitle = new JLabel("THÔNG TIN MÓN");
		lblFormTitle.setFont(UIUtils.FONT_TITLE);
		lblFormTitle.setForeground(UIUtils.COLOR_TEXT_DARK);
		formPanel.add(lblFormTitle, BorderLayout.NORTH);

		JPanel fieldGrid = new JPanel(new GridLayout(3, 2, 8, 12));
		fieldGrid.setOpaque(false);

		JLabel lblName = new JLabel("Tên món:");
		lblName.setFont(UIUtils.FONT_BOLD);
		lblName.setForeground(UIUtils.COLOR_TEXT_DARK);
		fieldGrid.add(lblName);
		txtName = new JTextField();
		UIUtils.styleTextField(txtName);
		fieldGrid.add(txtName);

		JLabel lblPrice = new JLabel("Đơn giá (VNĐ):");
		lblPrice.setFont(UIUtils.FONT_BOLD);
		lblPrice.setForeground(UIUtils.COLOR_TEXT_DARK);
		fieldGrid.add(lblPrice);
		txtPrice = new JTextField();
		UIUtils.styleTextField(txtPrice);
		fieldGrid.add(txtPrice);

		JLabel lblCat = new JLabel("Danh mục:");
		lblCat.setFont(UIUtils.FONT_BOLD);
		lblCat.setForeground(UIUtils.COLOR_TEXT_DARK);
		fieldGrid.add(lblCat);
		cbCategory = new JComboBox<>();
		cbCategory.setFont(UIUtils.FONT_REGULAR);
		cbCategory.setBackground(Color.WHITE);
		cbCategory.setForeground(UIUtils.COLOR_TEXT_DARK);
		fieldGrid.add(cbCategory);

		formPanel.add(fieldGrid, BorderLayout.CENTER);

		// Action Buttons
		JPanel btnGrid = new JPanel(new GridLayout(2, 2, 8, 8));
		btnGrid.setOpaque(false);

		btnAdd = UIUtils.createButton("Thêm mới", UIUtils.COLOR_SUCCESS, Color.WHITE, UIUtils.FONT_BOLD);
		btnUpdate = UIUtils.createButton("Cập nhật", UIUtils.COLOR_PRIMARY, Color.WHITE, UIUtils.FONT_BOLD);
		btnDelete = UIUtils.createButton("Xóa món", UIUtils.COLOR_DANGER, Color.WHITE, UIUtils.FONT_BOLD);
		JButton btnClear = UIUtils.createButton("Nhập lại", UIUtils.COLOR_SECONDARY, Color.WHITE,
				UIUtils.FONT_BOLD);

		btnGrid.add(btnAdd);
		btnGrid.add(btnUpdate);
		btnGrid.add(btnDelete);
		btnGrid.add(btnClear);

		formPanel.add(btnGrid, BorderLayout.SOUTH);
		add(formPanel, BorderLayout.EAST);

		// 4. Listeners
		table.getSelectionModel().addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting() && table.getSelectedRow() != -1) {
				int viewRow = table.getSelectedRow();
				int modelRow = table.convertRowIndexToModel(viewRow);
				txtName.setText(tableModel.getValueAt(modelRow, 1).toString());
				txtPrice.setText(tableModel.getValueAt(modelRow, 4).toString());
				String catName = tableModel.getValueAt(modelRow, 3).toString();
				for (int i = 0; i < cbCategory.getItemCount(); i++) {
					if (cbCategory.getItemAt(i).getName().equals(catName)) {
						cbCategory.setSelectedIndex(i);
						break;
					}
				}
			}
		});

		txtSearch.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				String text = txtSearch.getText().trim();
				if (text.isEmpty()) {
					sorter.setRowFilter(null);
				} else {
					sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
				}
			}
		});

		btnRefresh.addActionListener(e -> refreshData());
		btnClear.addActionListener(e -> clearForm());

		btnAdd.addActionListener(e -> addProduct());
		btnUpdate.addActionListener(e -> updateProduct());
		btnDelete.addActionListener(e -> deleteProduct());

		loadData();
	}

	@Override
	public void refreshData() {
		txtSearch.setText("");
		sorter.setRowFilter(null);
		loadData(false);
	}

	public void reloadSilently() {
		loadData(true);
	}

	private void clearForm() {
		table.clearSelection();
		txtName.setText("");
		txtPrice.setText("");
		if (cbCategory.getItemCount() > 0) {
			cbCategory.setSelectedIndex(0);
		}
	}

	private void loadData() {
		loadData(false);
	}

	@SuppressWarnings("unchecked")
	private void loadData(boolean silent) {
		java.util.function.Supplier<Response> task = () -> {
			SocketClient client = SocketClient.getInstance();
			Response catRes = client.sendRequest(new Request(ActionType.GET_CATEGORIES, null));
			if (catRes == null || !catRes.isSuccess()) {
				return catRes != null ? catRes : new Response(false, "Không thể tải danh mục!", null);
			}
			Response prodRes = client.sendRequest(new Request(ActionType.GET_PRODUCTS, null));
			if (prodRes == null || !prodRes.isSuccess()) {
				return prodRes != null ? prodRes : new Response(false, "Không thể tải danh sách món!", null);
			}
			return new Response(true, "OK", new Object[] { catRes.getData(), prodRes.getData() });
		};

		java.util.function.Consumer<Response> onComplete = res -> {
			if (res != null && res.isSuccess() && res.getData() instanceof Object[] bundle) {
				Category prevCat = (Category) cbCategory.getSelectedItem();
				int prevCatId = prevCat != null ? prevCat.getId() : -1;

				int prevViewRow = table.getSelectedRow();
				Object prevProductId = null;
				if (prevViewRow >= 0) {
					int prevModelRow = table.convertRowIndexToModel(prevViewRow);
					prevProductId = tableModel.getValueAt(prevModelRow, 0);
				}

				List<Category> categories = (List<Category>) bundle[0];
				cbCategory.removeAllItems();
				int restoredCatIdx = -1;
				if (categories != null) {
					for (int i = 0; i < categories.size(); i++) {
						Category c = categories.get(i);
						cbCategory.addItem(c);
						if (c.getId() == prevCatId) {
							restoredCatIdx = i;
						}
					}
				}
				if (restoredCatIdx >= 0) {
					cbCategory.setSelectedIndex(restoredCatIdx);
				}

				List<Product> products = (List<Product>) bundle[1];
				tableModel.setRowCount(0);
				int restoredModelRow = -1;
				if (products != null) {
					for (Product p : products) {
						String catName = p.getCategory() != null ? p.getCategory().getName() : "";
						tableModel.addRow(new Object[] {
								p.getId(),
								p.getName(),
								UIUtils.formatCurrency(p.getPrice()),
								catName,
								String.format("%.0f", p.getPrice())
						});
						if (prevProductId != null && prevProductId.equals(p.getId())) {
							restoredModelRow = tableModel.getRowCount() - 1;
						}
					}
				}
				if (restoredModelRow >= 0) {
					int viewRow = table.convertRowIndexToView(restoredModelRow);
					if (viewRow >= 0) {
						table.setRowSelectionInterval(viewRow, viewRow);
					}
				}
			} else if (!silent) {
				String msg = res != null && res.getMessage() != null
						? res.getMessage()
						: "Không thể tải dữ liệu thực đơn từ máy chủ!";
				JOptionPane.showMessageDialog(this, msg, "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
			}
		};

		if (silent) {
			UIUtils.runSilentAsync(task, onComplete);
		} else {
			UIUtils.runAsync(this, btnRefresh, task, onComplete);
		}
	}

	private Product buildValidatedProduct(Integer id) {
		String name = txtName.getText().trim();
		String priceStr = txtPrice.getText().trim();
		Category selectedCat = (Category) cbCategory.getSelectedItem();

		if (name.isEmpty() || priceStr.isEmpty() || selectedCat == null) {
			JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ tên món, giá bán và danh mục!", "Cảnh báo",
					JOptionPane.WARNING_MESSAGE);
			return null;
		}

		float price;
		try {
			price = Float.parseFloat(priceStr);
		} catch (NumberFormatException ex) {
			JOptionPane.showMessageDialog(this, "Đơn giá bán phải là một số hợp lệ!", "Cảnh báo",
					JOptionPane.WARNING_MESSAGE);
			return null;
		}

		if (Float.isNaN(price) || Float.isInfinite(price) || price < 0) {
			JOptionPane.showMessageDialog(this, "Đơn giá không được âm hoặc không hợp lệ!", "Cảnh báo",
					JOptionPane.WARNING_MESSAGE);
			return null;
		}

		Product p = new Product();
		if (id != null) {
			p.setId(id);
		}
		p.setName(name);
		p.setPrice(price);
		p.setCategory(selectedCat);
		return p;
	}

	private void addProduct() {
		Product p = buildValidatedProduct(null);
		if (p == null) {
			return;
		}

		UIUtils.runAsync(this, btnAdd,
				() -> SocketClient.getInstance().sendRequest(new Request(ActionType.ADD_PRODUCT, p)),
				res -> {
					if (res != null && res.isSuccess()) {
						JOptionPane.showMessageDialog(this, "Đã thêm món mới thành công!", "Thông báo",
								JOptionPane.INFORMATION_MESSAGE);
						clearForm();
						loadData();
					} else {
						JOptionPane.showMessageDialog(this,
								res != null && res.getMessage() != null ? res.getMessage() : "Lỗi khi thêm món!",
								"Lỗi", JOptionPane.ERROR_MESSAGE);
					}
				});
	}

	private void updateProduct() {
		if (table.getSelectedRow() == -1) {
			JOptionPane.showMessageDialog(this, "Vui lòng chọn món trong bảng cần sửa!", "Thông báo",
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		int modelRow = table.convertRowIndexToModel(table.getSelectedRow());
		int id = (int) tableModel.getValueAt(modelRow, 0);
		Product p = buildValidatedProduct(id);
		if (p == null) {
			return;
		}

		UIUtils.runAsync(this, btnUpdate,
				() -> SocketClient.getInstance().sendRequest(new Request(ActionType.UPDATE_PRODUCT, p)),
				res -> {
					if (res != null && res.isSuccess()) {
						JOptionPane.showMessageDialog(this, "Cập nhật thông tin món thành công!", "Thông báo",
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

	private void deleteProduct() {
		if (table.getSelectedRow() == -1) {
			JOptionPane.showMessageDialog(this, "Vui lòng chọn món trong bảng cần xóa!", "Thông báo",
					JOptionPane.WARNING_MESSAGE);
			return;
		}
		int modelRow = table.convertRowIndexToModel(table.getSelectedRow());
		int id = (int) tableModel.getValueAt(modelRow, 0);
		String name = (String) tableModel.getValueAt(modelRow, 1);

		int choice = JOptionPane.showConfirmDialog(this,
				"Bạn có chắc chắn muốn xóa món \"" + name + "\" khỏi thực đơn?",
				"Xác nhận xóa món",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.QUESTION_MESSAGE);
		if (choice != JOptionPane.YES_OPTION) {
			return;
		}

		UIUtils.runAsync(this, btnDelete,
				() -> SocketClient.getInstance().sendRequest(new Request(ActionType.DELETE_PRODUCT, id)),
				res -> {
					if (res != null && res.isSuccess()) {
						JOptionPane.showMessageDialog(this, "Đã xóa món thành công!", "Thông báo",
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
