package com.gym.gymmanagementsystem.dto;

import lombok.Data;

import java.util.List;

/**
 * 次卡流水响应：entries 为流水明细，credits 为当前剩余次数。
 */
@Data
public class CreditLedgerView {
    private List<CreditLedgerEntry> entries;
    private Integer credits;
}
