package com.cafe.server.dao;

/**
 * Kết quả thao tác tầng DAO dùng nội bộ trên Server để truyền thông điệp lỗi rõ ràng tới ClientHandler.
 */
public class DaoResult<T> {
	private final boolean success;
	private final String message;
	private final T data;

	private DaoResult(boolean success, String message, T data) {
		this.success = success;
		this.message = message;
		this.data = data;
	}

	public static <T> DaoResult<T> ok(String message) {
		return new DaoResult<>(true, message, null);
	}

	public static <T> DaoResult<T> ok(String message, T data) {
		return new DaoResult<>(true, message, data);
	}

	public static <T> DaoResult<T> fail(String message) {
		return new DaoResult<>(false, message, null);
	}

	public boolean isSuccess() {
		return success;
	}

	public String getMessage() {
		return message;
	}

	public T getData() {
		return data;
	}
}
