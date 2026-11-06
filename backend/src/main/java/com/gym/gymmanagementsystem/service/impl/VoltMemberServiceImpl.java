package com.gym.gymmanagementsystem.service.impl;

import com.gym.gymmanagementsystem.common.ApiException;
import com.gym.gymmanagementsystem.common.VoltContract;
import com.gym.gymmanagementsystem.mapper.VoltMemberMapper;
import com.gym.gymmanagementsystem.pojo.VoltMember;
import com.gym.gymmanagementsystem.service.VoltMemberService;
import com.gym.gymmanagementsystem.service.VoltViewService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

@Service
public class VoltMemberServiceImpl implements VoltMemberService {

    private final VoltMemberMapper memberMapper;
    private final VoltViewService viewService;

    @Value("${volt.upload.avatar-dir:uploads/avatars}")
    private String avatarDir;

    public VoltMemberServiceImpl(VoltMemberMapper memberMapper, VoltViewService viewService) {
        this.memberMapper = memberMapper;
        this.viewService = viewService;
    }

    @Override
    public String storeAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.validation("请选择头像图片。", Map.of("file", "请选择头像图片"));
        }
        if (file.getSize() > 5 * 1024 * 1024L) {
            throw ApiException.validation("头像不能超过 5MB。", Map.of("file", "头像不能超过 5MB"));
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        String extension;
        if ("image/jpeg".equals(contentType) || "image/jpg".equals(contentType)) {
            extension = ".jpg";
        } else if ("image/png".equals(contentType)) {
            extension = ".png";
        } else if ("image/webp".equals(contentType)) {
            extension = ".webp";
        } else {
            throw ApiException.validation("只支持 JPG / PNG / WebP 图片。", Map.of("file", "图片格式不支持"));
        }
        try {
            Path dir = Paths.get(avatarDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String filename = UUID.randomUUID().toString().replace("-", "") + extension;
            file.transferTo(dir.resolve(filename));
            return "/uploads/avatars/" + filename;
        } catch (IOException | IllegalStateException ex) {
            throw new ApiException(500, "UPLOAD_FAILED", "头像保存失败，请稍后再试。");
        }
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
