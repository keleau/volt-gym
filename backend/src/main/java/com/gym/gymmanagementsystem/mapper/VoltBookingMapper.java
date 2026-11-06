package com.gym.gymmanagementsystem.mapper;

import com.gym.gymmanagementsystem.pojo.Booking;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VoltBookingMapper {

    Booking findById(@Param("bookingId") String bookingId);

    int countByOrderNo(@Param("orderNo") String orderNo);

    Booking findActiveByPhoneAndSession(@Param("phone") String phone, @Param("sessionId") String sessionId);

    Booking findActiveByMemberAndSession(@Param("memberId") String memberId, @Param("sessionId") String sessionId);

    int countTrialsByPhone(@Param("phone") String phone);

    List<Booking> findByMember(@Param("memberId") String memberId);

    List<Booking> findByPhone(@Param("phone") String phone);

    int insert(Booking booking);

    int updateStatus(@Param("bookingId") String bookingId, @Param("status") String status);

    int confirm(@Param("bookingId") String bookingId);

    /** 只有仍处于 locked 时才能确认，返回 0 表示状态已被其他请求改变。 */
    int confirmIfLocked(@Param("bookingId") String bookingId);

    int cancel(@Param("bookingId") String bookingId);

    /** 只有 locked/confirmed 时才能取消，避免并发取消重复释放座位。 */
    int cancelIfActive(@Param("bookingId") String bookingId);

    int markCheckedIn(@Param("bookingId") String bookingId);

    int markCheckedInIfConfirmed(@Param("bookingId") String bookingId);
}
