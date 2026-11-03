package com.gym.gymmanagementsystem.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.gymmanagementsystem.common.StoredResponse;
import com.gym.gymmanagementsystem.mapper.VoltIdempotencyMapper;
import com.gym.gymmanagementsystem.service.IdempotencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

@Service
public class IdempotencyServiceImpl implements IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyServiceImpl.class);

    private final VoltIdempotencyMapper idempotencyMapper;
    private final ObjectMapper objectMapper;

    public IdempotencyServiceImpl(VoltIdempotencyMapper idempotencyMapper, ObjectMapper objectMapper) {
        this.idempotencyMapper = idempotencyMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public StoredResponse find(String scope, String idempotencyKey, String endpoint) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String json = idempotencyMapper.findResponse(storageKey(scope, idempotencyKey, endpoint), endpoint);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, StoredResponse.class);
        } catch (Exception ex) {
            log.warn("幂等记录解析失败 scope={} endpoint={}", scope, endpoint, ex);
            return null;
        }
    }

    @Override
    public void save(String scope, String idempotencyKey, String endpoint, int status, Object body) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(new StoredResponse(status, body));
            idempotencyMapper.insert(storageKey(scope, idempotencyKey, endpoint), endpoint, json,
                    LocalDateTime.now());
        } catch (DuplicateKeyException ignored) {
            // 并发下另一个请求已写入同一键，保留先到的结果。
        } catch (Exception ex) {
            log.warn("幂等记录写入失败 scope={} endpoint={}", scope, endpoint, ex);
        }
    }

    /** 存储键只保留哈希，长度稳定且不暴露会员 ID / 手机号。 */
    private String storageKey(String scope, String idempotencyKey, String endpoint) {
        String raw = (scope == null ? "anon" : scope) + "|" + endpoint + "|" + idempotencyKey;
        return sha256(raw);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
