-- 本地演示：重置当前真实微信会员的预约与消息，并解除手机号绑定。
-- 保留会员本身、openid 关联和已有次卡；下次冷启动重新走登录 → 绑定手机号流程。
-- 只影响 active 的真实 openid，不动 demo-openid / MBR-DEMO；可重复执行。
-- 注意：会删除当前真实微信会员的所有预约记录并回退场次占位。

UPDATE volt_session s
JOIN volt_booking b ON b.session_id = s.session_id
JOIN volt_wechat_member w ON w.member_id = b.member_id
SET s.booked = GREATEST(s.booked - 1, 0)
WHERE w.openid <> 'demo-openid'
  AND w.active = 1
  AND b.status IN ('locked', 'confirmed');

DELETE b FROM volt_booking b
JOIN volt_wechat_member w ON w.member_id = b.member_id
WHERE w.openid <> 'demo-openid'
  AND w.active = 1;

DELETE msg FROM volt_message msg
JOIN volt_wechat_member w ON w.member_id = msg.member_id
WHERE w.openid <> 'demo-openid'
  AND w.active = 1;

UPDATE volt_member m
JOIN volt_wechat_member w ON w.member_id = m.member_id
SET m.phone = NULL,
    m.phone_bound = 0
WHERE w.openid <> 'demo-openid'
  AND w.active = 1;

SELECT m.member_id, m.nickname, m.phone, m.phone_bound, m.credits, m.plan_code
FROM volt_member m
JOIN volt_wechat_member w ON w.member_id = m.member_id
WHERE w.openid <> 'demo-openid'
  AND w.active = 1;

SELECT b.booking_id, b.plan_code, b.status, b.member_phone
FROM volt_booking b
JOIN volt_wechat_member w ON w.member_id = b.member_id
WHERE w.openid <> 'demo-openid'
  AND w.active = 1;
