package com.gym.gymmanagementsystem.mapper;

import com.gym.gymmanagementsystem.pojo.CardOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VoltCardOrderMapper {

    int insert(CardOrder order);

    CardOrder findByOrderNo(@Param("orderNo") String orderNo);

    List<CardOrder> findByPhone(@Param("phone") String phone);

    List<CardOrder> findForStaff(@Param("phone") String phone, @Param("status") String status);

    List<CardOrder> findUnappliedForPhone(@Param("phone") String phone);

    int countPending(@Param("phone") String phone, @Param("planCode") String planCode);

    int countByOrderNo(@Param("orderNo") String orderNo);

    int activate(@Param("orderNo") String orderNo, @Param("activatedBy") String activatedBy);

    int markApplied(@Param("cardOrderId") String cardOrderId, @Param("memberId") String memberId);

    int attachMember(@Param("memberId") String memberId, @Param("phone") String phone);
}
