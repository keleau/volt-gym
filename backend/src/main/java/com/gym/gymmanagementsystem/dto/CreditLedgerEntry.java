package com.gym.gymmanagementsystem.dto;

import lombok.Data;

/**
 * 次卡扣减/返还流水。正数表示返还，负数表示扣次。
 */
@Data
public class CreditLedgerEntry {
    private String id;
    private String bookingId;
    private String recordId;
    private Integer changeAmount;
    private Integer balanceAfter;
    private String reason;
    private String note;
    private String createdAt;
}
