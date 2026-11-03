package com.gym.gymmanagementsystem.common;

/**
 * 幂等存储里的一条记录：重放时连同 HTTP 状态一起返回。
 */
public class StoredResponse {

    private int status;
    private Object body;

    public StoredResponse() {
    }

    public StoredResponse(int status, Object body) {
        this.status = status;
        this.body = body;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public Object getBody() {
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
    }
}
