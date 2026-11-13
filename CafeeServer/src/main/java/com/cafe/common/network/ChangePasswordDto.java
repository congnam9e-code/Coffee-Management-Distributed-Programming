package com.cafe.common.network;

import java.io.Serializable;

/**
 * Payload dùng để đổi mật khẩu của tài khoản đang đăng nhập.
 * Không mang theo empId để tránh client giả mạo đổi mật khẩu của người khác.
 */
public class ChangePasswordDto implements Serializable {
	private static final long serialVersionUID = 1L;

	private String oldPassword;
	private String newPassword;

	public ChangePasswordDto() {
	}

	public ChangePasswordDto(String oldPassword, String newPassword) {
		this.oldPassword = oldPassword;
		this.newPassword = newPassword;
	}

	public String getOldPassword() {
		return oldPassword;
	}

	public void setOldPassword(String oldPassword) {
		this.oldPassword = oldPassword;
	}

	public String getNewPassword() {
		return newPassword;
	}

	public void setNewPassword(String newPassword) {
		this.newPassword = newPassword;
	}
}
