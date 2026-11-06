package com.gym.gymmanagementsystem.mapper;

import com.gym.gymmanagementsystem.pojo.VoltMember;
import com.gym.gymmanagementsystem.pojo.VoltOtp;
import com.gym.gymmanagementsystem.pojo.VoltToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface VoltMemberMapper {

    VoltMember findById(@Param("memberId") String memberId);

    VoltMember findByPhone(@Param("phone") String phone);

    VoltMember findByOpenid(@Param("openid") String openid);

    List<VoltMember> findMembersByOpenid(@Param("openid") String openid);

    String findActiveMemberId(@Param("openid") String openid);

    String findOpenidByMember(@Param("memberId") String memberId);

    int countWechatMember(@Param("openid") String openid, @Param("memberId") String memberId);

    int insertWechatMember(@Param("openid") String openid, @Param("memberId") String memberId,
                           @Param("active") boolean active, @Param("createdAt") LocalDateTime createdAt);

    int clearWechatActive(@Param("openid") String openid);

    int setWechatActive(@Param("openid") String openid, @Param("memberId") String memberId);

    int deleteWechatMember(@Param("openid") String openid, @Param("memberId") String memberId);

    int insert(VoltMember member);

    int updateProfile(VoltMember member);

    int updatePhone(@Param("memberId") String memberId, @Param("phone") String phone);

    int insertToken(VoltToken token);

    VoltToken findToken(@Param("token") String token);

    int insertOtp(VoltOtp otp);

    VoltOtp findLatestOtp(@Param("phone") String phone, @Param("purpose") String purpose);

    /** 原子消费：并发校验同一个验证码时只有一次能成功。 */
    int consumeOtpIfUnused(@Param("id") Long id);

    int consumeOtp(@Param("id") Long id);

    int increaseOtpAttempts(@Param("id") Long id);
}
