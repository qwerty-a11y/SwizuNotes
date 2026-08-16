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

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

@Service
public class UserAvatarService {

    /** 头像大小上限（5MB，远小于全局 multipart 上限；头像经 <img> 内联输出，过大拖慢带宽） */
    private static final long MAX_AVATAR_BYTES = 5 * 1024 * 1024;

    /** SVG 是脚本注入高危载体（可内嵌 <script>/事件），与媒体上传一致一律拒绝 */
    private static final String SVG_MIME = "image/svg+xml";

    /** 常见位图格式魔数（hex 前缀；RIFF+x 表示偏移 0 匹配 RIFF 且偏移 4 匹配 x） */
    private static final Map<String, String[]> IMAGE_MAGIC = Map.ofEntries(
            Map.entry("image/png", new String[]{"89504E47"}),
            Map.entry("image/jpeg", new String[]{"FFD8FF"}),
            Map.entry("image/gif", new String[]{"47494638"}),
            Map.entry("image/webp", new String[]{"RIFF+57454250"}), // RIFF....WEBP
            Map.entry("image/bmp", new String[]{"424D"})
    );

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
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new BadRequestException("头像不能超过 5MB");
        }
        String mimeType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!mimeType.startsWith("image/")) {
            throw new BadRequestException("头像必须是图片");
        }
        if (SVG_MIME.equals(mimeType)) {
            throw new BadRequestException("不支持 SVG 头像（可内嵌脚本，存在安全风险）");
        }
        verifyImageMagic(file, mimeType);
        // 先落盘新文件，成功后再动 DB（store 失败时旧头像原样保留）
        String id = UUID.randomUUID().toString().replace("-", "");
        localFileStorageService.store(file, id);
        // 旧头像的 DB 行与磁盘文件（在新行保存成功后再删旧文件）
        UserAvatar oldAvatar = userAvatarRepository.findByUserId(userId).orElse(null);
        try {
            // 先删旧行再存新行：user_avatars.user_id 唯一约束，新行必须等旧行删除后才能插入。
            // 必须显式 flush()——Hibernate 的 flush 顺序中 insert 先于 delete 执行，
            // 不 flush 时同事务"先删后插"仍会撞 UNIQUE 冲突
            if (oldAvatar != null) {
                userAvatarRepository.delete(oldAvatar);
                userAvatarRepository.flush();
            }
            UserAvatar avatar = new UserAvatar();
            avatar.setId(id);
            avatar.setUserId(userId);
            avatar.setMimeType(mimeType);
            userAvatarRepository.save(avatar);
        } catch (RuntimeException e) {
            localFileStorageService.delete(id);
            throw e;
        }
        // 替换语义：新头像已入库，删除旧头像的磁盘文件（失败仅残留孤儿文件，不阻断）
        if (oldAvatar != null) {
            try {
                localFileStorageService.delete(oldAvatar.getId());
            } catch (RuntimeException ignored) {
                // 旧文件删除失败不影响新头像
            }
        }
        return id;
    }

    /** 已知位图格式魔数比对（未知/少见格式不拦截，仅靠 Content-Type 把关） */
    private void verifyImageMagic(MultipartFile file, String mimeType) {
        String[] magics = IMAGE_MAGIC.get(mimeType);
        if (magics == null) {
            return;
        }
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(12);
            for (String magic : magics) {
                if (magic.startsWith("RIFF+")) {
                    // RIFF 头 = 'RIFF' + 4 字节大小 + 4 字节类型码：类型码位于偏移 8
                    if (startsWithHex(head, "52494646", 0) && startsWithHex(head, magic.substring(5), 8)) {
                        return;
                    }
                } else if (startsWithHex(head, magic, 0)) {
                    return;
                }
            }
            throw new BadRequestException("文件内容与图片类型不符，请检查文件是否损坏或类型错误");
        } catch (IOException e) {
            throw new BadRequestException("文件读取失败");
        }
    }

    private boolean startsWithHex(byte[] head, String hex, int offset) {
        if (head.length < offset + hex.length() / 2) {
            return false;
        }
        for (int i = 0; i < hex.length(); i += 2) {
            int expected = Integer.parseInt(hex.substring(i, i + 2), 16);
            if ((head[offset + i / 2] & 0xFF) != expected) {
                return false;
            }
        }
        return true;
    }
}
