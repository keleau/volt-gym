package com.gym.gymmanagementsystem.service;

import com.gym.gymmanagementsystem.pojo.VoltMember;

import java.util.Map;

public interface VoltAuthService {

    Map<String, Object> sendOtp(String phone);

    /** 微信登录后绑定手机号专用验证码。 */
    Map<String, Object> sendBindOtp(VoltMember member, String phone);

    Map<String, Object> verifyOtp(String phone, String code);

    /**
     * 小程序 wx.login：配置 WX_APP_ID/WX_APP_SECRET 时走真实 jscode2session；
     * 未配置时按 volt.wx.demo-fallback 落到演示会员。
     */
    Map<String, Object> loginWithWechat(Map<String, Object> input);

    /** 校验绑定验证码后，把手机号绑定/合并到当前微信会员。 */
    Map<String, Object> linkPhone(VoltMember member, String phone, String code);

    /** 当前微信名下可切换的会员档案（同一手机号始终是同一个会员）。 */
    Map<String, Object> members(VoltMember member);

    /** 通过手机号 OTP 给当前微信添加一个会员档案，并切换过去。 */
    Map<String, Object> addMember(VoltMember member, String phone, String code);

    /** 在当前微信名下的会员档案之间切换，返回新 token。 */
    Map<String, Object> switchMember(VoltMember member, String memberId);

    /**
     * 解析 Bearer token：
     * 没有带 token 时返回 null（按游客处理）；
     * 带了但无效或已过期时抛 401，让客户端清掉本地会话。
     */
    VoltMember currentMember(String authorization);

    /** 同上，但必须拿到会员，否则抛 401。 */
    VoltMember requireMember(String authorization);
}
