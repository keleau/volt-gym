package com.gym.gymmanagementsystem.service;

import com.gym.gymmanagementsystem.pojo.VoltMember;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface VoltMemberService {

    Map<String, Object> updateProfile(VoltMember member, Map<String, Object> patch);

    /** 保存本地头像文件，返回相对 URL（如 /uploads/avatars/xxx.jpg）。 */
    String storeAvatar(MultipartFile file);
}
