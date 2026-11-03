package com.gym.gymmanagementsystem.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * 契约里的固定阈值与展示格式，两端共用。
 */
public final class VoltContract {

    public static final int BOOKING_WINDOW_DAYS = 60;
    public static final int LOCK_MINUTES = 10;
    public static final int CANCEL_WINDOW_HOURS = 2;
    public static final int CHECKIN_WINDOW_MINUTES = 180;
    public static final int TOKEN_TTL_DAYS = 30;
    public static final int OTP_TTL_SECONDS = 300;
    public static final int OTP_RESEND_COOLDOWN_SECONDS = 60;
    public static final int OTP_MAX_ATTEMPTS = 5;
    public static final int HEIGHT_MIN_CM = 120;
    public static final int HEIGHT_MAX_CM = 230;
    public static final int WEIGHT_MIN_KG = 30;
    public static final int WEIGHT_MAX_KG = 250;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private VoltContract() {
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    public static String dateShort(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("MM/dd"));
    }

    public static String dayCn(LocalDate date) {
        String[] names = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        return names[date.getDayOfWeek().getValue() - 1];
    }

    public static String compactDate(LocalDate date) {
        return date.format(DAY);
    }

    /** 运行时实体 ID：前缀 + 日期 + 8 位随机十六进制，避免 4 位随机数在连续操作下撞主键。 */
    public static String uniqueId(String prefix) {
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        return prefix + "-" + compactDate(LocalDate.now()) + "-" + random;
    }

    public static String isoDateTime(LocalDateTime dateTime) {
        return dateTime.atZone(ZONE).format(ISO);
    }

    public static boolean isPhone(String phone) {
        return phone != null && phone.matches("^1[3-9]\\d{9}$");
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
