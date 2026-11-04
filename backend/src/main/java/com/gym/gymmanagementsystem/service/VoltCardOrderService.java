package com.gym.gymmanagementsystem.service;

import com.gym.gymmanagementsystem.pojo.VoltMember;

import java.util.List;
import java.util.Map;

public interface VoltCardOrderService {

    Map<String, Object> create(Map<String, Object> input, VoltMember member);

    List<Map<String, Object>> memberOrders(VoltMember member);

    List<Map<String, Object>> staffOrders(String phone, String status);

    Map<String, Object> activate(String orderNo, String staffName);

    /** 演示支付：仅本地演示开关打开时可用，不调用微信支付。 */
    Map<String, Object> demoPay(String orderNo, VoltMember member);

    /** 把该手机号已开卡但未领取的权益并入会员，返回最新会员。 */
    VoltMember claimForMember(VoltMember member);
}
