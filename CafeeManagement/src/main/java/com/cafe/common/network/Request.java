package com.cafe.common.network;

import java.io.Serializable;

public class Request implements Serializable {
    private static final long serialVersionUID = 1L;
    private ActionType action;
    private Object payload;

    public Request() {}
    
    public Request(ActionType action, Object payload) {
        this.action = action;
        this.payload = payload;
	}

	public ActionType getAction() { return action; }
    public void setAction(ActionType action) { this.action = action; }
    
    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }
}
