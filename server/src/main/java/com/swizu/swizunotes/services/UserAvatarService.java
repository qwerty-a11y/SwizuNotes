/*
 * Copyright (C) 2026 qwerty-a11y
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.swizu.swizunotes.services;

import com.swizu.swizunotes.common.exception.BadRequestException;
import com.swizu.swizunotes.common.exception.ResourceNotFoundException;
import com.swizu.swizunotes.common.exception.UnauthorizedException;
import com.swizu.swizunotes.entity.UserAvatar;
import com.swizu.swizunotes.repository.UserAvatarRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public class UserAvatarService {

    @Autowired private UserAvatarRepository userAvatarRepository;
    @Autowired private LocalFileStorageService localFileStorageService;

    public AvatarContent getAvatar(Integer userId) {
        UserAvatar avatar = userAvatarRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("资源不存在"));
        return new AvatarContent(avatar, localFileStorageService.load(avatar.getId()));
    }

    @Transactional
    public String uploadAvatar(MultipartFile file, Integer userId) {
        if (userId == null) {
            throw new UnauthorizedException("请先登录");
        }
        if (file.isEmpty()) {
            throw new BadRequestException("文件为空");
        }
        String mimeType = file.getContentType();
        if (mimeType == null || !mimeType.startsWith("image/")) {
            throw new BadRequestException("头像必须是图片");
        }
        userAvatarRepository.findByUserId(userId).ifPresent(old -> {
            userAvatarRepository.delete(old);
            localFileStorageService.delete(old.getId());
        });
        String id = UUID.randomUUID().toString().replace("-", "");
        localFileStorageService.store(file, id);
        UserAvatar avatar = new UserAvatar();
        avatar.setId(id);
        avatar.setUserId(userId);
        avatar.setMimeType(mimeType);
        try {
            userAvatarRepository.save(avatar);
        } catch (RuntimeException e) {
            localFileStorageService.delete(id);
            throw e;
        }
        return id;
    }
}
