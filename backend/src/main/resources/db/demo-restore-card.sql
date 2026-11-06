-- 本地演示：快速恢复 MBR-DEMO 的 8 次演示卡，并解除手机号绑定。
-- 用于从“路人/体验课”身份切回“已开卡”身份；不恢复历史记录，历史记录用 demo-reset.sql。

UPDATE volt_member
SET phone = NULL,
    phone_bound = 0,
    credits = 8,
    credits_total = 10,
    plan_code = 'PLAN-03',
    plan_name = '10 次充电卡',
    expires_at = DATE_ADD(CURDATE(), INTERVAL 180 DAY)
WHERE member_id = 'MBR-DEMO';

SELECT member_id, nickname, phone, phone_bound, credits, credits_total, plan_code
FROM volt_member
WHERE member_id = 'MBR-DEMO';
