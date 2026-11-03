package com.gym.gymmanagementsystem.service.impl;

import com.gym.gymmanagementsystem.common.ApiException;
import com.gym.gymmanagementsystem.common.VoltContract;
import com.gym.gymmanagementsystem.mapper.VoltMemberMapper;
import com.gym.gymmanagementsystem.pojo.VoltMember;
import com.gym.gymmanagementsystem.service.VoltMemberService;
import com.gym.gymmanagementsystem.service.VoltViewService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class VoltMemberServiceImpl implements VoltMemberService {

    private final VoltMemberMapper memberMapper;
    private final VoltViewService viewService;

    public VoltMemberServiceImpl(VoltMemberMapper memberMapper, VoltViewService viewService) {
        this.memberMapper = memberMapper;
        this.viewService = viewService;
    }

    @Override
    public Map<String, Object> updateProfile(VoltMember member, Map<String, Object> patch) {
        if (patch == null || patch.isEmpty()) {
            throw ApiException.validation("没有需要更新的字段。", null);
        }
        VoltMember update = new VoltMember();
        update.setMemberId(member.getMemberId());

        String nickname = asString(patch.get("nickname"));
        if (nickname != null) {
            if (nickname.isBlank() || nickname.length() > 16) {
                throw ApiException.validation("昵称需为 1-16 个字符。", Map.of("nickname", "昵称需为 1-16 个字符"));
            }
            update.setNickname(nickname);
        }
        String goal = asString(patch.get("goal"));
        if (goal != null) {
            if (goal.length() > 64) {
                throw ApiException.validation("训练目标最多 64 个字符。", Map.of("goal", "训练目标最多 64 个字符"));
            }
            update.setGoal(goal);
        }
        String avatarUrl = asString(patch.get("avatarUrl"));
        if (avatarUrl != null) {
            if (avatarUrl.length() > 255) {
                throw ApiException.validation("头像地址过长。", Map.of("avatarUrl", "头像地址不能超过 255 个字符"));
            }
            update.setAvatarUrl(avatarUrl);
        }
        Integer height = asInteger(patch.get("heightCm"));
        if (height != null) {
            if (height < VoltContract.HEIGHT_MIN_CM || height > VoltContract.HEIGHT_MAX_CM) {
                String message = "身高需在 " + VoltContract.HEIGHT_MIN_CM + "-" + VoltContract.HEIGHT_MAX_CM + " cm 之间。";
                throw ApiException.validation(message, Map.of("heightCm", message));
            }
            update.setHeightCm(height);
        }
        Double weight = asDouble(patch.get("weightKg"));
        if (weight != null) {
            if (weight < VoltContract.WEIGHT_MIN_KG || weight > VoltContract.WEIGHT_MAX_KG) {
                String message = "体重需在 " + VoltContract.WEIGHT_MIN_KG + "-" + VoltContract.WEIGHT_MAX_KG + " kg 之间。";
                throw ApiException.validation(message, Map.of("weightKg", message));
            }
            update.setWeightKg(BigDecimal.valueOf(weight));
        }
        if (update.getNickname() == null && update.getAvatarUrl() == null && update.getGoal() == null
                && update.getHeightCm() == null && update.getWeightKg() == null) {
            return viewService.memberView(member);
        }

        memberMapper.updateProfile(update);
        VoltMember fresh = memberMapper.findById(member.getMemberId());
        return viewService.memberView(fresh == null ? member : fresh);
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw ApiException.validation("数值格式不正确。", Map.of("value", "数值格式不正确"));
        }
    }

    private Double asDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw ApiException.validation("数值格式不正确。", Map.of("value", "数值格式不正确"));
        }
    }
}
