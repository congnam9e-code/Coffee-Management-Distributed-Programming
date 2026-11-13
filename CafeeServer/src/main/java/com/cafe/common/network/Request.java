package com.cafe.common.network;

import java.io.Serializable;
import java.util.UUID;

public class Request implements Serializable {
	private static final long serialVersionUID = 1L;
	private ActionType action;
	private Object payload;
	private String sessionToken;
	private String requestId;

	public Request() {
		this.requestId = UUID.randomUUID().toString();
	}

	public Request(ActionType action, Object payload) {
		this.action = action;
		this.payload = payload;
		this.requestId = UUID.randomUUID().toString();
	}

	public ActionType getAction() {
		return action;
	}

	public void setAction(ActionType action) {
		this.action = action;
	}

	public Object getPayload() {
		return payload;
	}

	public void setPayload(Object payload) {
		this.payload = payload;
	}

	public String getSessionToken() {
		return sessionToken;
	}

	public void setSessionToken(String sessionToken) {
		this.sessionToken = sessionToken;
	}

	public String getRequestId() {
		return requestId;
	}

	public void setRequestId(String requestId) {
		this.requestId = requestId;
	}
}
