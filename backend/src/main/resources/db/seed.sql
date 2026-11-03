-- VOLT 静态种子数据（门店 / 课程 / 价格方案 / 教练 / 社区活动）
-- 与 website/src/data/*.js、miniprogram/data/*.js 同源；改编码前先改官网数据。
-- 场次、挑战周期、会员等随日期变化的数据由 Java 侧 VoltDataSeeder 生成。

INSERT IGNORE INTO volt_venue
(venue_code, venue_name, venue_en, address, metro, hours, image_url, latitude, longitude, sort_order)
VALUES
('V-01', '天河工场', 'TIANHE STATION', '体育西路 103 号 3F', '地铁 1/3 号线体育西路站', '07:00 — 23:00',
 '/assets/images/station-tianhe.webp', 23.131900, 113.321400, 1),
('V-02', '海珠工场', 'HAIZHU STATION', '江南大道中 288 号 2F', '地铁 2 号线江南西站', '07:00 — 22:30',
 '/assets/images/station-haizhu.webp', 23.095400, 113.271200, 2),
('V-03', '番禺工场', 'PANYU STATION', '万博二路 76 号 B1', '地铁 7 号线南村万博站', '08:00 — 22:00',
 '/assets/images/station-panyu.webp', 23.006200, 113.348900, 3);

INSERT IGNORE INTO volt_program
(program_no, program_name, program_en, program_desc, intensity, duration_min, kcal, image_url, sort_order)
VALUES
('P01', '光影搏击', 'SHADOW BOXING', '出拳、躲闪、踢腿，把一天的压力打在空气里。', 'HIGH', 60, 620,
 '/assets/images/program-boxing.webp', 1),
('P02', '高压燃脂', 'HIGH VOLTAGE HIIT', '短时高强度间歇，心率拉满，结束后还在持续烧。', 'HIGH', 45, 550,
 '/assets/images/program-conditioning.webp', 2),
('P03', '力量搬运', 'STRENGTH WORKS', '杠铃、壶铃、自重，把骨架和肌肉一点点练扎实。', 'MID', 60, 430,
 '/assets/images/program-strength.webp', 3),
('P04', '战绳风暴', 'ROPE STORM', '双绳甩动、波比和冲刺，把心率直接拽到红线。', 'MID', 50, 580,
 '/assets/images/program-rope.webp', 4),
('P05', '静流瑜伽', 'QUIET FLOW', '呼吸、拉伸、平衡，给充过头的身体放一次电。', 'LOW', 60, 220,
 '/assets/images/program-mobility.webp', 5),
('P06', '深度恢复', 'DEEP RECOVERY', '泡沫轴、拉伸和呼吸，把紧绷的身体一点点放开。', 'LOW', 55, 180,
 '/assets/images/program-recovery.webp', 6);

INSERT IGNORE INTO volt_plan
(plan_code, plan_name, plan_en, price, unit_text, featured, points_json, cta_text, sort_order)
VALUES
('PLAN-01', '首次接入包', 'FIRST ACCESS', 49, '一次性', 1,
 '["团课 1 节，任选课程","InBody 体测 1 次","教练 1 对 1 训练建议","三店通用，到期自动失效"]',
 'RUN > 立即接入', 1),
('PLAN-02', '按次充电', 'PAY PER SESSION', 99, '每节起', 0,
 '["上一次付一次，不绑定年卡","小程序自助选场次","开课前 2 小时可退","新人首节自动按 49 元结算"]',
 '查看本周场次', 2),
('PLAN-03', '10 次充电卡', '10-PACK CARD', 899, '10 节 / 180 天', 0,
 '["折合 89.9 元一节","三店全部课程通用","180 天长期有效","可与朋友共享同一张卡"]',
 '购买次卡', 3);

INSERT IGNORE INTO volt_coach
(coach_id, coach_name, title_text, avatar_url, bio, program_no, venue_codes)
VALUES
('C01', '阿K', '搏击主教练', '/assets/images/coach-01.webp', '拳击与体能打底，强调站位和呼吸。', 'P01', 'V-01,V-02,V-03'),
('C02', '赵彻', '体能教练', '/assets/images/coach-02.webp', '高强度间歇与心率控制。', 'P02', 'V-01,V-02,V-03'),
('C03', '老魏', '力量教练', '/assets/images/coach-03.webp', '杠铃、壶铃与基础力量，动作先做稳。', 'P03', 'V-01,V-02,V-03'),
('C04', 'Mia', '体能教练', '/assets/images/coach-02.webp', '战绳、波比和冲刺，把心率推到红线。', 'P04', 'V-01,V-02,V-03'),
('C05', '林岚', '瑜伽教练', '/assets/images/coach-01.webp', '呼吸、拉伸与平衡，练完能真正停下来。', 'P05', 'V-01,V-02,V-03'),
('C06', '苏晓', '恢复教练', '/assets/images/coach-03.webp', '泡沫轴、拉伸与恢复，把身体一点点放开。', 'P06', 'V-01,V-02,V-03');

INSERT IGNORE INTO volt_event
(event_id, title, day_text, month_text, tag_text, desc_text, time_text, event_date, venue_code, venue_name,
 cap_count, signed_count, sort_order)
VALUES
('EV-1104', '燃脂电流营', '04', 'NOV · WED', 'WEEKDAY / 周三',
 'HIIT 循环 + 心率带冲刺，一节课烧掉一周奶茶热量。', '周三 19:30', '2026-11-04', 'V-01', '天河工场', 24, 18, 1),
('EV-1107', '周末对决赛', '07', 'NOV · SAT', 'WEEKEND / 周六',
 '两队 6v6 战绳接力，输的队请全场喝水。', '周六 14:00', '2026-11-07', 'V-02', '海珠工场', 18, 18, 2),
('EV-1108', '新手破冰课', '08', 'NOV · SUN', 'FRESH / 周日',
 '零基础专场，教练 1 对 1 过器械，社恐友好。', '周日 10:00', '2026-11-08', 'V-03', '番禺工场', 16, 9, 3),
('EV-1227', '季度总冲榜', '27', 'DEC · SEASON', 'SEASON / 季度',
 '季度最后一周，训练时长翻倍计点，榜首登门店墙。', '12 月 27–31', '2026-12-27', 'V-01', '三店同开', 120, 84, 4);
