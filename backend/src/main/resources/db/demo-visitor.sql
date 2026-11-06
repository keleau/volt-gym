-- 本地演示：把 MBR-DEMO 变成“路人”档案（无次卡、无预约、无训练记录），并解除手机号绑定。
-- 用于演示：微信登录 → 手机号验证 → 路人引导弹窗 → 预约体验课 / 购买会员卡。
-- 恢复已开卡演示可执行 demo-restore-card.sql；完整恢复演示基线执行 demo-reset.sql 并重启后端。

UPDATE volt_session s
JOIN volt_booking b ON b.session_id = s.session_id
SET s.booked = GREATEST(s.booked - 1, 0)
WHERE b.member_id = 'MBR-DEMO'
  AND b.status IN ('locked', 'confirmed');

DELETE FROM volt_booking
WHERE member_id = 'MBR-DEMO';

DELETE FROM volt_record
WHERE member_id = 'MBR-DEMO';

DELETE FROM volt_body_log
WHERE member_id = 'MBR-DEMO';

DELETE FROM volt_message
WHERE member_id = 'MBR-DEMO';

DELETE FROM volt_waitlist
WHERE member_id = 'MBR-DEMO';

DELETE FROM volt_subscription
WHERE member_id = 'MBR-DEMO';

DELETE FROM volt_card_order
WHERE member_id = 'MBR-DEMO' OR member_phone = '13800006688';

DELETE FROM volt_credit_ledger
WHERE member_id = 'MBR-DEMO';

UPDATE volt_member
SET phone = NULL,
    phone_bound = 0,
    credits = 0,
    credits_total = 0,
    plan_code = '',
    plan_name = '',
    expires_at = NULL
WHERE member_id = 'MBR-DEMO';

SELECT member_id, nickname, phone, phone_bound, credits, credits_total, plan_code
FROM volt_member
WHERE member_id = 'MBR-DEMO';
