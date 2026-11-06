-- 把演示数据恢复到种子基线（活动报名、挑战、预约、训练档案、场次占位）。
-- 用法：mysql ... < demo-reset.sql，然后重启后端让 VoltDataSeeder 重新生成场次与演示会员。
-- 注意：会清空所有预约、报名、档案与演示会员会话，只用于本地演示。
-- 如需演示「微信登录 → 绑定手机号」，请在后端重启并重建 MBR-DEMO 后再执行 demo-unbind-phone.sql。

DELETE FROM volt_event_signup;
DELETE FROM volt_challenge_join;
DELETE FROM volt_booking;
DELETE FROM volt_card_order;
DELETE FROM volt_idempotency;
DELETE FROM volt_credit_ledger;
DELETE FROM volt_invite;
DELETE FROM volt_record;
DELETE FROM volt_body_log;
DELETE FROM volt_message;
DELETE FROM volt_waitlist;
DELETE FROM volt_subscription;
DELETE FROM volt_token;
DELETE FROM volt_wechat_member;
DELETE FROM volt_otp;
DELETE FROM volt_member WHERE member_id = 'MBR-DEMO';
DELETE FROM volt_session;

UPDATE volt_event
SET signed_count = CASE event_id
    WHEN 'EV-1104' THEN 18
    WHEN 'EV-1107' THEN 18
    WHEN 'EV-1108' THEN 9
    WHEN 'EV-1227' THEN 84
    ELSE signed_count
END;

UPDATE volt_challenge SET joined_count = 128;
