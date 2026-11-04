package com.gym.gymmanagementsystem.common;

import java.util.List;
import java.util.Map;

/**
 * 社区的编辑型内容（榜单、故事卡、数据条），与官网 src/data/community.js 保持一致。
 * 这些是运营内容，不是业务数据；后续要上 CMS 时从这里搬走即可。
 */
public final class CommunityContent {

    public static final List<Map<String, Object>> STATS = List.of(
            Map.of("value", "3,204", "label", "在册社员", "en", "MEMBERS"),
            Map.of("value", "12", "label", "本周活动", "en", "EVENTS"),
            Map.of("value", "84,206", "label", "累计训练人次", "en", "WORKOUTS"));

    public static final List<Map<String, Object>> RANKING = List.of(
            Map.of("no", "01", "code", "VOLT-0457", "sessions", 23, "minutes", 1720, "weeks", 6),
            Map.of("no", "02", "code", "VOLT-1120", "sessions", 21, "minutes", 1585, "weeks", 5),
            Map.of("no", "03", "code", "VOLT-0893", "sessions", 19, "minutes", 1460, "weeks", 5),
            Map.of("no", "04", "code", "VOLT-2008", "sessions", 17, "minutes", 1295, "weeks", 4),
            Map.of("no", "05", "code", "VOLT-3371", "sessions", 15, "minutes", 1180, "weeks", 4));

    public static final List<Map<String, Object>> STORIES = List.of(
            Map.of("name", "阿 K", "role", "搏击教练 · 天河工场", "tag", "01",
                    "quote", "我不教你把动作做漂亮，我教你在一分钟后还站得住。",
                    "sessions", "640 节", "streak", "28 周", "favorite", "光影搏击",
                    "img", "/images/coach-03-960.webp"),
            Map.of("name", "林岚", "role", "瑜伽教练 · 海珠 / 天河", "tag", "02",
                    "quote", "练到能停下来，才算真的变强。别急，呼吸先跟上。",
                    "sessions", "412 节", "streak", "19 周", "favorite", "静流瑜伽",
                    "img", "/images/coach-02-960.webp"));

    private CommunityContent() {
    }
}
