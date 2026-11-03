package com.gym.gymmanagementsystem.service;

import com.gym.gymmanagementsystem.common.StoredResponse;

public interface IdempotencyService {

    /**
     * 命中同一个成员/访客范围内的 Idempotency-Key 时返回首次结果；未命中返回 null。
     * scope 必须包含调用者身份与本次操作对象，避免不同会员之间串用同一个键。
     */
    StoredResponse find(String scope, String idempotencyKey, String endpoint);

    void save(String scope, String idempotencyKey, String endpoint, int status, Object body);
}
