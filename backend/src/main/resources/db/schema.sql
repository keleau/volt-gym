-- VOLT 前后端共用：库表结构
-- 约定：表字段 snake_case，接口字段 camelCase；金额整数元，次数整数。
-- 首次启动时由 seed/VoltDataSeeder 自动执行本脚本（幂等，可重复执行）。

-- ---------------------------------------------------------------- VOLT 目录
CREATE TABLE IF NOT EXISTS volt_venue (
    venue_code VARCHAR(16)   NOT NULL PRIMARY KEY,
    venue_name VARCHAR(64)   NOT NULL,
    venue_en   VARCHAR(64)   NOT NULL DEFAULT '',
    address    VARCHAR(128)  NOT NULL DEFAULT '',
    metro      VARCHAR(128)  NOT NULL DEFAULT '',
    hours      VARCHAR(64)   NOT NULL DEFAULT '',
    image_url  VARCHAR(255)  NOT NULL DEFAULT '',
    latitude   DECIMAL(10, 6) NOT NULL DEFAULT 0,
    longitude  DECIMAL(10, 6) NOT NULL DEFAULT 0,
    sort_order INT           NOT NULL DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS volt_program (
    program_no   VARCHAR(16)  NOT NULL PRIMARY KEY,
    program_name VARCHAR(64)  NOT NULL,
    program_en   VARCHAR(64)  NOT NULL DEFAULT '',
    program_desc VARCHAR(255) NOT NULL DEFAULT '',
    intensity    VARCHAR(16)  NOT NULL DEFAULT 'MID',
    duration_min INT          NOT NULL DEFAULT 60,
    kcal         INT          NOT NULL DEFAULT 0,
    image_url    VARCHAR(255) NOT NULL DEFAULT '',
    sort_order   INT          NOT NULL DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS volt_plan (
    plan_code    VARCHAR(16)   NOT NULL PRIMARY KEY,
    plan_name    VARCHAR(64)   NOT NULL,
    plan_en      VARCHAR(64)   NOT NULL DEFAULT '',
    price        INT           NOT NULL DEFAULT 0,
    unit_text    VARCHAR(64)   NOT NULL DEFAULT '',
    featured     TINYINT       NOT NULL DEFAULT 0,
    points_json  VARCHAR(1024) NOT NULL DEFAULT '[]',
    cta_text     VARCHAR(64)   NOT NULL DEFAULT '',
    sort_order   INT           NOT NULL DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS volt_coach (
    coach_id    VARCHAR(8)   NOT NULL PRIMARY KEY,
    coach_name  VARCHAR(32)  NOT NULL,
    title_text  VARCHAR(32)  NOT NULL DEFAULT '',
    avatar_url  VARCHAR(255) NOT NULL DEFAULT '',
    bio         VARCHAR(255) NOT NULL DEFAULT '',
    program_no  VARCHAR(16)  NOT NULL DEFAULT '',
    venue_codes VARCHAR(128) NOT NULL DEFAULT ''
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------- 场次（库存唯一单位）
CREATE TABLE IF NOT EXISTS volt_session (
    session_id   VARCHAR(32) NOT NULL PRIMARY KEY,
    session_code VARCHAR(16) NOT NULL,
    session_date DATE        NOT NULL,
    start_time   TIME        NOT NULL,
    end_time     TIME        NOT NULL,
    venue_code   VARCHAR(16) NOT NULL,
    program_no   VARCHAR(16) NOT NULL,
    coach_id     VARCHAR(8)  NOT NULL,
    capacity     INT         NOT NULL DEFAULT 16,
    booked       INT         NOT NULL DEFAULT 0,
    status       VARCHAR(16) NOT NULL DEFAULT 'open',
    KEY idx_session_date (session_date),
    KEY idx_session_lookup (session_date, venue_code, program_no),
    KEY idx_session_venue (session_date, venue_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------- 会员与登录态
CREATE TABLE IF NOT EXISTS volt_member (
    member_id     VARCHAR(32) NOT NULL PRIMARY KEY,
    member_no     VARCHAR(32) NOT NULL,
    nickname      VARCHAR(32) NOT NULL DEFAULT '',
    phone         VARCHAR(20) NULL,
    phone_bound   TINYINT     NOT NULL DEFAULT 1,
    avatar_url    VARCHAR(255) NOT NULL DEFAULT '',
    openid        VARCHAR(64) NULL,
    unionid       VARCHAR(64) NULL,
    goal          VARCHAR(64) NOT NULL DEFAULT '',
    height_cm     INT         NOT NULL DEFAULT 0,
    weight_kg     DECIMAL(5, 1) NOT NULL DEFAULT 0,
    credits       INT         NOT NULL DEFAULT 0,
    credits_total INT         NOT NULL DEFAULT 0,
    plan_code     VARCHAR(16) NOT NULL DEFAULT '',
    plan_name     VARCHAR(32) NOT NULL DEFAULT '',
    expires_at    DATE        NULL,
    tier          VARCHAR(32) NOT NULL DEFAULT 'VOLT MEMBER',
    referral_code VARCHAR(16) NOT NULL DEFAULT '',
    joined_at     DATE        NOT NULL,
    UNIQUE KEY uk_member_phone (phone),
    UNIQUE KEY uk_member_openid (openid),
    UNIQUE KEY uk_member_referral (referral_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS volt_token (
    token      VARCHAR(64) NOT NULL PRIMARY KEY,
    member_id  VARCHAR(32) NOT NULL,
    expires_at BIGINT      NOT NULL,
    created_at DATETIME    NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS volt_otp (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    phone      VARCHAR(20) NOT NULL,
    code       VARCHAR(8)  NOT NULL,
    purpose    VARCHAR(16) NOT NULL DEFAULT 'login',
    expires_at BIGINT      NOT NULL,
    consumed   TINYINT     NOT NULL DEFAULT 0,
    attempts   INT         NOT NULL DEFAULT 0,
    created_at DATETIME    NOT NULL,
    KEY idx_otp_phone (phone, purpose)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------- 预约
CREATE TABLE IF NOT EXISTS volt_booking (
    booking_id      VARCHAR(32) NOT NULL PRIMARY KEY,
    order_no        VARCHAR(32) NOT NULL,
    entry_code      VARCHAR(32) NOT NULL,
    member_id       VARCHAR(32) NULL,
    session_id      VARCHAR(32) NOT NULL,
    status          VARCHAR(16) NOT NULL,
    seat_no         INT         NOT NULL DEFAULT 0,
    lock_expires_at BIGINT      NULL,
    reminder_opt_in TINYINT     NOT NULL DEFAULT 1,
    source          VARCHAR(8)  NOT NULL DEFAULT 'web',
    plan_code       VARCHAR(16) NOT NULL DEFAULT '',
    plan_name       VARCHAR(32) NOT NULL DEFAULT '',
    amount          INT         NOT NULL DEFAULT 0,
    member_name     VARCHAR(32) NOT NULL DEFAULT '',
    member_phone    VARCHAR(20) NOT NULL,
    created_at      DATETIME    NOT NULL,
    UNIQUE KEY uk_booking_order (order_no),
    KEY idx_booking_session (session_id),
    KEY idx_booking_phone (member_phone),
    KEY idx_booking_member (member_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------- 社区
CREATE TABLE IF NOT EXISTS volt_event (
    event_id     VARCHAR(32)  NOT NULL PRIMARY KEY,
    title        VARCHAR(64)  NOT NULL,
    day_text     VARCHAR(8)   NOT NULL DEFAULT '',
    month_text   VARCHAR(32)  NOT NULL DEFAULT '',
    tag_text     VARCHAR(32)  NOT NULL DEFAULT '',
    desc_text    VARCHAR(255) NOT NULL DEFAULT '',
    time_text    VARCHAR(32)  NOT NULL DEFAULT '',
    event_date   DATE         NULL,
    venue_code   VARCHAR(16)  NOT NULL DEFAULT '',
    venue_name   VARCHAR(64)  NOT NULL DEFAULT '',
    cap_count    INT          NOT NULL DEFAULT 0,
    signed_count INT          NOT NULL DEFAULT 0,
    sort_order   INT          NOT NULL DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS volt_event_signup (
    signup_id   VARCHAR(32) NOT NULL PRIMARY KEY,
    event_id    VARCHAR(32) NOT NULL,
    member_id   VARCHAR(32) NULL,
    member_name VARCHAR(32) NOT NULL DEFAULT '',
    member_phone VARCHAR(20) NOT NULL,
    signup_no   VARCHAR(32) NOT NULL,
    created_at  DATETIME    NOT NULL,
    UNIQUE KEY uk_signup_event_phone (event_id, member_phone)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS volt_challenge (
    challenge_id    VARCHAR(32)  NOT NULL PRIMARY KEY,
    title           VARCHAR(64)  NOT NULL,
    desc_text       VARCHAR(255) NOT NULL DEFAULT '',
    target_sessions INT          NOT NULL DEFAULT 3,
    days            INT          NOT NULL DEFAULT 7,
    joined_count    INT          NOT NULL DEFAULT 0,
    percent         INT          NOT NULL DEFAULT 0,
    week_start      DATE         NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'active'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS volt_challenge_join (
    join_id      VARCHAR(32) NOT NULL PRIMARY KEY,
    challenge_id VARCHAR(32) NOT NULL,
    member_id    VARCHAR(32) NULL,
    member_phone VARCHAR(20) NOT NULL,
    member_no    VARCHAR(32) NOT NULL DEFAULT '',
    created_at   DATETIME    NOT NULL,
    UNIQUE KEY uk_challenge_join (challenge_id, member_phone)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------- 训练档案 / 进场记录
CREATE TABLE IF NOT EXISTS volt_record (
    record_id    VARCHAR(32) NOT NULL PRIMARY KEY,
    member_id    VARCHAR(32) NOT NULL,
    session_id   VARCHAR(32) NOT NULL DEFAULT '',
    booking_id   VARCHAR(32) NOT NULL DEFAULT '',
    mode         VARCHAR(8)  NOT NULL DEFAULT 'class',
    source       VARCHAR(8)  NOT NULL DEFAULT 'scan',
    record_date  DATE        NOT NULL,
    record_time  TIME        NOT NULL,
    start_time   TIME        NULL,
    program_no   VARCHAR(16) NOT NULL DEFAULT '',
    program_name VARCHAR(64) NOT NULL DEFAULT '',
    program_en   VARCHAR(64) NOT NULL DEFAULT '',
    coach_id     VARCHAR(8)  NOT NULL DEFAULT '',
    coach_name   VARCHAR(32) NOT NULL DEFAULT '',
    venue_code   VARCHAR(16) NOT NULL DEFAULT '',
    venue_name   VARCHAR(64) NOT NULL DEFAULT '',
    duration_min INT         NOT NULL DEFAULT 0,
    kcal         INT         NOT NULL DEFAULT 0,
    checked_at   BIGINT      NOT NULL,
    created_at   DATETIME    NOT NULL,
    KEY idx_record_member_date (member_id, record_date),
    KEY idx_record_member_checked (member_id, checked_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS volt_body_log (
    body_log_id VARCHAR(32) NOT NULL PRIMARY KEY,
    member_id   VARCHAR(32) NOT NULL,
    log_date    DATE        NOT NULL,
    height_cm   INT         NOT NULL DEFAULT 0,
    weight_kg   DECIMAL(5, 1) NOT NULL DEFAULT 0,
    created_at  DATETIME    NOT NULL,
    UNIQUE KEY uk_body_member_date (member_id, log_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------- 消息 / 订阅偏好
CREATE TABLE IF NOT EXISTS volt_message (
    message_id VARCHAR(32) NOT NULL PRIMARY KEY,
    member_id  VARCHAR(32) NOT NULL,
    msg_type   VARCHAR(16) NOT NULL DEFAULT 'system',
    title      VARCHAR(128) NOT NULL DEFAULT '',
    body       VARCHAR(512) NOT NULL DEFAULT '',
    action     VARCHAR(128) NOT NULL DEFAULT '',
    read_flag  TINYINT     NOT NULL DEFAULT 0,
    created_at BIGINT      NOT NULL,
    KEY idx_message_member (member_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS volt_subscription (
    member_id      VARCHAR(32) NOT NULL PRIMARY KEY,
    class_reminder TINYINT     NOT NULL DEFAULT 1,
    class_change   TINYINT     NOT NULL DEFAULT 1,
    waitlist       TINYINT     NOT NULL DEFAULT 1
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------- 候补
CREATE TABLE IF NOT EXISTS volt_waitlist (
    waitlist_id VARCHAR(32) NOT NULL PRIMARY KEY,
    member_id   VARCHAR(32) NOT NULL,
    session_id  VARCHAR(32) NOT NULL,
    position    INT         NOT NULL DEFAULT 1,
    status      VARCHAR(16) NOT NULL DEFAULT 'waiting',
    created_at  DATETIME    NOT NULL,
    updated_at  DATETIME    NULL,
    UNIQUE KEY uk_waitlist_member_session (member_id, session_id),
    KEY idx_waitlist_session (session_id, status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------- 次卡流水
-- 负数=扣次，正数=返还。预约/确认不扣次，只有扫码核销成功才写一条 CHECKIN(-1)；
-- 唯一键保证同一预约最多扣一次。返还写 REVOKE(+1)，当前无自助撤销入口，留给门店后台。
CREATE TABLE IF NOT EXISTS volt_credit_ledger (
    ledger_id     VARCHAR(32) NOT NULL PRIMARY KEY,
    member_id     VARCHAR(32) NOT NULL,
    booking_id    VARCHAR(32) NULL,
    record_id     VARCHAR(32) NULL,
    change_amount INT         NOT NULL,
    balance_after INT         NOT NULL,
    reason        VARCHAR(16) NOT NULL,
    note          VARCHAR(255) NOT NULL DEFAULT '',
    created_at    DATETIME    NOT NULL,
    UNIQUE KEY uk_ledger_booking_reason (booking_id, reason),
    KEY idx_ledger_member_created (member_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------- 邀请关系
CREATE TABLE IF NOT EXISTS volt_invite (
    invite_id         VARCHAR(32) NOT NULL PRIMARY KEY,
    ref_code          VARCHAR(16) NOT NULL,
    inviter_member_id VARCHAR(32) NOT NULL,
    invitee_member_id VARCHAR(32) NOT NULL,
    status            VARCHAR(16) NOT NULL DEFAULT 'registered',
    created_at        DATETIME    NOT NULL,
    UNIQUE KEY uk_invite_invitee (invitee_member_id),
    KEY idx_invite_inviter (inviter_member_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------- 写请求幂等
CREATE TABLE IF NOT EXISTS volt_idempotency (
    idempotency_key VARCHAR(96) NOT NULL PRIMARY KEY,
    endpoint        VARCHAR(64) NOT NULL,
    response_json   TEXT        NOT NULL,
    created_at      DATETIME   NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
