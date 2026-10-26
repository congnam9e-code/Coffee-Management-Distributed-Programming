package com.cafe.common.network;

import java.io.Serializable;

public class Response implements Serializable {
	private static final long serialVersionUID = 1L;
	private boolean success;
	private String message;
	private Object data;

	public Response() {
	}

	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public Object getData() {
		return data;
	}

	public void setData(Object data) {
		this.data = data;
	}
}
