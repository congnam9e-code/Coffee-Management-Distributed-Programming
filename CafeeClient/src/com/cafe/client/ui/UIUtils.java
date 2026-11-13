package com.cafe.client.ui;

import com.cafe.client.socket.SocketClient;
import com.cafe.common.network.ActionType;
import com.cafe.common.network.ChangePasswordDto;
import com.cafe.common.network.Request;
import com.cafe.common.network.Response;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class UIUtils {
	public interface Refreshable {
		void refreshData();
	}

	// Typography
	public static final Font FONT_HEADER_TITLE = new Font("Segoe UI", Font.BOLD, 17);
	public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 15);
	public static final Font FONT_SUBTITLE = new Font("Segoe UI", Font.BOLD, 13);
	public static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 13);
	public static final Font FONT_REGULAR = new Font("Segoe UI", Font.PLAIN, 13);
	public static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 12);
	public static final Font FONT_MONEY = new Font("Segoe UI", Font.BOLD, 22);

	// Color Palette
	public static final Color COLOR_PRIMARY = new Color(37, 99, 235);       // Royal Blue (#2563EB)
	public static final Color COLOR_PRIMARY_HOVER = new Color(29, 78, 216);
	public static final Color COLOR_SUCCESS = new Color(16, 185, 129);      // Emerald Green (#10B981)
	public static final Color COLOR_SUCCESS_HOVER = new Color(5, 150, 105);
	public static final Color COLOR_DANGER = new Color(220, 38, 38);        // Crimson Red (#DC2626)
	public static final Color COLOR_DANGER_HOVER = new Color(185, 28, 28);
	public static final Color COLOR_WARNING = new Color(245, 158, 11);      // Amber (#F59E0B)
	public static final Color COLOR_SECONDARY = new Color(100, 116, 139);   // Slate Gray (#64748B)
	public static final Color COLOR_SECONDARY_HOVER = new Color(71, 85, 105);

	public static final Color COLOR_DARK_HEADER = new Color(30, 41, 59);    // Deep Slate (#1E293B)
	public static final Color COLOR_BG = new Color(241, 245, 249);          // Cool Light Gray (#F1F5F9)
	public static final Color COLOR_CARD = Color.WHITE;
	public static final Color COLOR_BORDER = new Color(203, 213, 225);      // Slate Border (#CBD5E1)
	public static final Color COLOR_TEXT_DARK = new Color(15, 23, 42);      // Slate Dark Text
	public static final Color COLOR_TEXT_MUTED = new Color(100, 116, 139);

	// Table status colors (Staff POS)
	public static final Color BG_TABLE_AVAILABLE = new Color(236, 253, 245);  // Mint (#ECFDF5)
	public static final Color BORDER_TABLE_AVAILABLE = new Color(16, 185, 129);
	public static final Color TEXT_TABLE_AVAILABLE = new Color(6, 95, 70);

	public static final Color BG_TABLE_OCCUPIED = new Color(254, 242, 242);   // Soft Red (#FEF2F2)
	public static final Color BORDER_TABLE_OCCUPIED = new Color(239, 68, 68);
	public static final Color TEXT_TABLE_OCCUPIED = new Color(153, 27, 27);

	public static final Color BORDER_TABLE_SELECTED = new Color(37, 99, 235); // Blue (#2563EB)

	private static final DecimalFormat CURRENCY_FORMAT;

	static {
		DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.forLanguageTag("vi-VN"));
		symbols.setGroupingSeparator('.');
		symbols.setDecimalSeparator(',');
		CURRENCY_FORMAT = new DecimalFormat("#,##0 VNĐ", symbols);
	}

	public static String formatCurrency(double amount) {
		return CURRENCY_FORMAT.format(amount);
	}

	public static void runAsync(Component owner, JButton triggerBtn, Supplier<Response> backgroundTask,
			Consumer<Response> onComplete) {
		Window window = owner instanceof Window w ? w : (owner != null ? SwingUtilities.getWindowAncestor(owner) : null);
		if (triggerBtn != null) {
			triggerBtn.setEnabled(false);
		}
		if (window != null) {
			window.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		}

		new SwingWorker<Response, Void>() {
			@Override
			protected Response doInBackground() {
				return backgroundTask.get();
			}

			@Override
			protected void done() {
				if (window != null) {
					window.setCursor(Cursor.getDefaultCursor());
				}
				if (triggerBtn != null) {
					triggerBtn.setEnabled(true);
				}
				try {
					Response res = get();
					if (onComplete != null) {
						onComplete.accept(res);
					}
				} catch (Exception ex) {
					if (onComplete != null) {
						onComplete.accept(new Response(false, "Lỗi xử lý yêu cầu: " + ex.getMessage(), null));
					}
				}
			}
		}.execute();
	}

	/**
	 * Chạy tác vụ nền đồng bộ âm thầm: không đổi con trỏ chờ, không khóa nút bấm hay gián đoạn người dùng.
	 */
	public static void runSilentAsync(Supplier<Response> backgroundTask, Consumer<Response> onComplete) {
		new SwingWorker<Response, Void>() {
			@Override
			protected Response doInBackground() {
				return backgroundTask.get();
			}

			@Override
			protected void done() {
				try {
					Response res = get();
					if (onComplete != null) {
						onComplete.accept(res);
					}
				} catch (Exception ignored) {
				}
			}
		}.execute();
	}

	public static void confirmLogout(JFrame frame, String message) {
		int choice = JOptionPane.showConfirmDialog(
				frame,
				message,
				"Xác nhận đăng xuất",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.QUESTION_MESSAGE);
		if (choice == JOptionPane.YES_OPTION) {
			SocketClient.getInstance().setSyncListener(null);
			runAsync(frame, null, () -> {
				SocketClient client = SocketClient.getInstance();
				Response res = client.sendRequest(new Request(ActionType.LOGOUT, null));
				client.close();
				return res;
			}, res -> {
				frame.dispose();
				new LoginFrame().setVisible(true);
			});
		}
	}

	public static void showChangePasswordDialog(JFrame parent, String title) {
		JPasswordField txtOldPass = new JPasswordField();
		JPasswordField txtNewPass = new JPasswordField();
		JPasswordField txtConfirmPass = new JPasswordField();
		styleTextField(txtOldPass);
		styleTextField(txtNewPass);
		styleTextField(txtConfirmPass);

		Object[] fields = {
				"Mật khẩu hiện tại:", txtOldPass,
				"Mật khẩu mới:", txtNewPass,
				"Xác nhận mật khẩu mới:", txtConfirmPass
		};

		int option = JOptionPane.showConfirmDialog(parent, fields, title, JOptionPane.OK_CANCEL_OPTION);
		if (option != JOptionPane.OK_OPTION) {
			return;
		}

		String oldPass = new String(txtOldPass.getPassword());
		String newPass = new String(txtNewPass.getPassword()).trim();
		String confirmPass = new String(txtConfirmPass.getPassword()).trim();

		if (oldPass.isEmpty()) {
			JOptionPane.showMessageDialog(parent, "Vui lòng nhập mật khẩu hiện tại!", "Cảnh báo",
					JOptionPane.WARNING_MESSAGE);
			return;
		}
		if (newPass.isEmpty()) {
			JOptionPane.showMessageDialog(parent, "Mật khẩu mới không được để trống!", "Cảnh báo",
					JOptionPane.WARNING_MESSAGE);
			return;
		}
		if (!newPass.equals(confirmPass)) {
			JOptionPane.showMessageDialog(parent, "Xác nhận mật khẩu mới không trùng khớp!", "Cảnh báo",
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		ChangePasswordDto dto = new ChangePasswordDto(oldPass, newPass);
		runAsync(parent, null,
				() -> SocketClient.getInstance().sendRequest(new Request(ActionType.CHANGE_PASSWORD, dto)),
				res -> {
					if (res != null && res.isSuccess()) {
						JOptionPane.showMessageDialog(parent,
								res.getMessage() != null ? res.getMessage() : "Đổi mật khẩu thành công!",
								"Thông báo", JOptionPane.INFORMATION_MESSAGE);
					} else {
						JOptionPane.showMessageDialog(parent,
								res != null && res.getMessage() != null ? res.getMessage()
										: "Lỗi khi đổi mật khẩu trên máy chủ!",
								"Lỗi", JOptionPane.ERROR_MESSAGE);
					}
				});
	}

	public static JButton createButton(String text, Color bg, Color fg, Font font) {
		JButton btn = new JButton(text);
		btn.setFont(font != null ? font : FONT_BOLD);
		btn.setBackground(bg);
		btn.setForeground(fg);
		btn.setFocusPainted(false);
		btn.setContentAreaFilled(false);
		btn.setOpaque(true);
		btn.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(bg.darker(), 1, true),
				BorderFactory.createEmptyBorder(8, 14, 8, 14)));
		btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

		btn.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				if (btn.isEnabled()) {
					btn.setBackground(bg.brighter());
				}
			}

			@Override
			public void mouseExited(MouseEvent e) {
				if (btn.isEnabled()) {
					btn.setBackground(bg);
				}
			}
		});
		btn.addPropertyChangeListener("enabled", evt -> {
			if (btn.isEnabled()) {
				btn.setBackground(bg);
				btn.setForeground(fg);
			} else {
				btn.setBackground(COLOR_BG);
				btn.setForeground(COLOR_TEXT_DARK);
			}
		});
		return btn;
	}

	public static void formatTable(JTable table) {
		table.setFont(FONT_REGULAR);
		table.setBackground(Color.WHITE);
		table.setForeground(COLOR_TEXT_DARK);
		table.setRowHeight(32);
		table.setShowGrid(true);
		table.setGridColor(new Color(226, 232, 240));
		table.setSelectionBackground(new Color(219, 234, 254));
		table.setSelectionForeground(COLOR_TEXT_DARK);
		table.setFillsViewportHeight(true);
		table.setDefaultRenderer(Object.class, createAlignedRenderer(SwingConstants.LEFT));

		JTableHeader header = table.getTableHeader();
		header.setFont(FONT_BOLD);
		header.setBackground(COLOR_DARK_HEADER);
		header.setForeground(Color.WHITE);
		header.setOpaque(true);
		header.setPreferredSize(new Dimension(0, 36));
		header.setReorderingAllowed(false);
		header.setDefaultRenderer(new DefaultTableCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected,
					boolean hasFocus, int row, int column) {
				super.getTableCellRendererComponent(tbl, value, false, false, row, column);
				setOpaque(true);
				setBackground(COLOR_DARK_HEADER);
				setForeground(Color.WHITE);
				setFont(FONT_BOLD);
				setHorizontalAlignment(SwingConstants.CENTER);
				setBorder(BorderFactory.createCompoundBorder(
						BorderFactory.createMatteBorder(0, 0, 1, 1, new Color(51, 65, 85)),
						BorderFactory.createEmptyBorder(0, 8, 0, 8)));
				return this;
			}
		});
	}

	private static DefaultTableCellRenderer createAlignedRenderer(int alignment) {
		return new DefaultTableCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected,
					boolean hasFocus, int row, int column) {
				super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);
				setOpaque(true);
				setHorizontalAlignment(alignment);
				setBackground(isSelected ? tbl.getSelectionBackground() : Color.WHITE);
				setForeground(isSelected ? tbl.getSelectionForeground() : COLOR_TEXT_DARK);
				return this;
			}
		};
	}

	public static void alignTableColumns(JTable table, int[] centerCols, int[] rightCols) {
		DefaultTableCellRenderer centerRenderer = createAlignedRenderer(SwingConstants.CENTER);
		DefaultTableCellRenderer rightRenderer = createAlignedRenderer(SwingConstants.RIGHT);

		if (centerCols != null) {
			for (int col : centerCols) {
				if (col < table.getColumnCount()) {
					table.getColumnModel().getColumn(col).setCellRenderer(centerRenderer);
				}
			}
		}
		if (rightCols != null) {
			for (int col : rightCols) {
				if (col < table.getColumnCount()) {
					table.getColumnModel().getColumn(col).setCellRenderer(rightRenderer);
				}
			}
		}
	}

	public static void styleTextField(JTextField tf) {
		tf.setFont(FONT_REGULAR);
		tf.setBackground(Color.WHITE);
		tf.setForeground(COLOR_TEXT_DARK);
		tf.setCaretColor(COLOR_TEXT_DARK);
		int width = Math.max(tf.getPreferredSize().width, 160);
		tf.setPreferredSize(new Dimension(width, 38));
		tf.setMinimumSize(new Dimension(120, 38));
		tf.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(COLOR_BORDER, 1),
				BorderFactory.createEmptyBorder(6, 10, 6, 10)));
	}

	public static JPanel createHeaderBar(String title, String subtitle, String userName, String roleText,
			Runnable onLogout, Runnable onChangePass) {
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(COLOR_DARK_HEADER);
		header.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

		// Left: Title & Subtitle
		JPanel leftBox = new JPanel(new GridLayout(2, 1, 0, 2));
		leftBox.setOpaque(false);

		JLabel lblTitle = new JLabel(title);
		lblTitle.setFont(FONT_HEADER_TITLE);
		lblTitle.setForeground(Color.WHITE);
		leftBox.add(lblTitle);

		JLabel lblSubtitle = new JLabel(subtitle);
		lblSubtitle.setFont(FONT_SMALL);
		lblSubtitle.setForeground(new Color(148, 163, 184));
		leftBox.add(lblSubtitle);

		header.add(leftBox, BorderLayout.WEST);

		// Right: User Info + Buttons
		JPanel rightBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
		rightBox.setOpaque(false);

		JLabel lblUser = new JLabel(userName + " (" + roleText + ")");
		lblUser.setFont(FONT_BOLD);
		lblUser.setForeground(new Color(241, 245, 249));
		rightBox.add(lblUser);

		if (onChangePass != null) {
			JButton btnChangePass = createButton("Đổi Mật Khẩu", COLOR_SECONDARY, Color.WHITE, FONT_SMALL);
			btnChangePass.addActionListener(e -> onChangePass.run());
			rightBox.add(btnChangePass);
		}

		if (onLogout != null) {
			JButton btnLogout = createButton("Đăng Xuất", COLOR_DANGER, Color.WHITE, FONT_BOLD);
			btnLogout.addActionListener(e -> onLogout.run());
			rightBox.add(btnLogout);
		}

		header.add(rightBox, BorderLayout.EAST);
		return header;
	}
}
