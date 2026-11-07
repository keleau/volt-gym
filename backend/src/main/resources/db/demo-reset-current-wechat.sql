-- 本地演示：重置当前真实微信会员的预约、消息、手机号绑定，并清掉购卡记录与次卡余额。
-- 保留会员本身和 openid 关联；没有会员归属的孤儿购卡登记也会被清掉。
-- 只影响 active 的真实 openid，不动 demo-openid / MBR-DEMO；可重复执行。
-- 开启 VOLT_RESET_ON_START=true 后，后端每次启动都会执行本脚本。
-- 注意：会删除当前真实微信会员的预约记录并回退场次占位。

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

-- 当前真实微信会员的购卡登记：官网提交的单可能没有 member_id，所以按会员和手机号各删一次。
DELETE co FROM volt_card_order co
JOIN volt_wechat_member w ON w.member_id = co.member_id
WHERE w.openid <> 'demo-openid'
  AND w.active = 1;

DELETE co FROM volt_card_order co
JOIN volt_member m ON m.phone = co.member_phone
JOIN volt_wechat_member w ON w.member_id = m.member_id
WHERE w.openid <> 'demo-openid'
  AND w.active = 1;

-- 没有对应会员手机号的孤儿购卡登记，例如重置后只剩 member_phone 的旧单。
DELETE co FROM volt_card_order co
LEFT JOIN volt_member m ON m.phone = co.member_phone
WHERE m.member_id IS NULL;

DELETE l FROM volt_credit_ledger l
JOIN volt_wechat_member w ON w.member_id = l.member_id
WHERE w.openid <> 'demo-openid'
  AND w.active = 1;

UPDATE volt_member m
JOIN volt_wechat_member w ON w.member_id = m.member_id
SET m.credits = 0,
    m.credits_total = 0,
    m.plan_code = '',
    m.plan_name = '',
    m.expires_at = NULL
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

SELECT COUNT(*) AS remaining_card_orders FROM volt_card_order;
