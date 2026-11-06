-- 本地演示：把演示微信会员改回未绑定手机号，用于重复演示「微信一键登录 → 绑定手机号」。
-- 用法：mysql ... < demo-unbind-phone.sql
-- 解绑后在小程序「我的」退出登录，再点微信一键登录，会重新进入绑定手机号页。
-- 绑定回 13800006688 可恢复 MBR-DEMO 的演示次卡与档案数据。

UPDATE volt_member
SET phone = NULL, phone_bound = 0
WHERE member_id = 'MBR-DEMO';

SELECT member_id, nickname, phone, phone_bound
FROM volt_member
WHERE member_id = 'MBR-DEMO';
