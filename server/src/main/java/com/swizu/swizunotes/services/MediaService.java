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
import com.swizu.swizunotes.common.exception.ForbiddenException;
import com.swizu.swizunotes.common.exception.InternalException;
import com.swizu.swizunotes.common.exception.ResourceNotFoundException;
import com.swizu.swizunotes.dto.response.MediaResponse;
import com.swizu.swizunotes.entity.Media;
import com.swizu.swizunotes.entity.MediaCategory;
import com.swizu.swizunotes.entity.MediaMetadata.AbstractMediaMetadata;
import com.swizu.swizunotes.entity.MediaMetadata.AudioMetadata;
import com.swizu.swizunotes.entity.MediaMetadata.FileMetadata;
import com.swizu.swizunotes.entity.MediaMetadata.ImageMetadata;
import com.swizu.swizunotes.entity.MediaMetadata.VideoMetadata;
import com.swizu.swizunotes.repository.MediaRepository;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class MediaService {

    /**
     * 常见格式魔数。语法：
     *  - 纯 hex 前缀：匹配文件偏移 0（如 PNG 签名 89504E47）
     *  - `hex@N`：匹配文件偏移 N（如 mp4 的 ftyp 位于偏移 4：66747970@4）
     *  - `RIFF+x`：偏移 0 为 'RIFF' 且偏移 8 为 x（RIFF 头 = 'RIFF' + 4 字节大小 + 4 字节类型码，
     *    如 WEBP/WAVE/AVI 的类型码位于偏移 8）
     * 未知格式返回 true 不做拦截（仅 Content-Type 大类把关）
     */
    private static final Map<String, String[]> MAGIC = Map.ofEntries(
            Map.entry("image/png", new String[]{"89504E47"}),
            Map.entry("image/jpeg", new String[]{"FFD8FF"}),
            Map.entry("image/gif", new String[]{"47494638"}),
            Map.entry("image/webp", new String[]{"RIFF+57454250"}), // RIFF....WEBP
            Map.entry("image/bmp", new String[]{"424D"}),
            Map.entry("audio/mpeg", new String[]{"494433", "FFFB", "FFF3", "FFF2"}), // ID3 或 MPEG 帧头
            Map.entry("audio/flac", new String[]{"664C614300000022"}),
            Map.entry("audio/wav", new String[]{"RIFF+57415645"}), // RIFF....WAVE
            Map.entry("audio/ogg", new String[]{"4F676753"}),
            Map.entry("audio/mp4", new String[]{"66747970@4"}), // size+ftyp（ftyp 在偏移 4）
            Map.entry("audio/m4a", new String[]{"66747970@4"}),
            Map.entry("audio/x-m4a", new String[]{"66747970@4"}),
            Map.entry("video/mp4", new String[]{"66747970@4"}),
            Map.entry("video/webm", new String[]{"1A45DFA3"}),
            Map.entry("video/avi", new String[]{"RIFF+41564920"}), // RIFF....AVI
            Map.entry("video/quicktime", new String[]{"66747970@4"}),
            Map.entry("video/x-m4v", new String[]{"66747970@4"}),
            Map.entry("application/pdf", new String[]{"25504446"})
    );

    /** SVG 是脚本注入高危载体（可内嵌 <script>/事件），一律拒绝上传 */
    private static final String SVG_MIME = "image/svg+xml";

    @Autowired private MediaRepository mediaRepository;
    @Autowired private ArticleService articleService;
    @Autowired private LocalFileStorageService localFileStorageService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private Validator validator;

    public MediaContent getMedia(String mediaId, Integer userId) {
        Media media = getVisibleMedia(mediaId, userId);
        return new MediaContent(media, localFileStorageService.load(mediaId));
    }

    /** 查询媒体元数据（不含文件流），权限与读取一致 */
    public MediaResponse getMediaInfo(String mediaId, Integer userId) {
        Media media = getVisibleMedia(mediaId, userId);
        return new MediaResponse(media.getId(), media.getArticleId(), media.getType(), media.getMimeType(),
                media.getMetadata(), localFileStorageService.sizeOf(mediaId));
    }

    /** 下载文件名：优先 metadata.name，否则用媒体 id */
    public String getDownloadFilename(String mediaId, Integer userId) {
        return getDownloadFilename(getVisibleMedia(mediaId, userId));
    }

    /** 基于已加载的 Media 计算下载文件名（不重复查库/鉴权，供读取响应复用） */
    public String getDownloadFilename(Media media) {
        try {
            var node = objectMapper.readTree(media.getMetadata());
            var name = node.get("name");
            if (name != null && !name.isNull() && !name.asText().isBlank()) {
                return name.asText();
            }
        } catch (Exception ignored) {
        }
        return media.getId();
    }

    private Media getVisibleMedia(String mediaId, Integer userId) {
        Media media = mediaRepository.findById(mediaId).orElseThrow(
                () -> new ResourceNotFoundException("资源不存在")
        );
        if (articleService.getEditPermission(media.getArticleId(), userId) == ArticleEditPermission.HIDDEN) {
            throw new ResourceNotFoundException("资源不存在");
        }
        return media;
    }

    @Transactional
    public void deleteMedia(String mediaId, Integer userId) {
        Media media = mediaRepository.findById(mediaId).orElseThrow(
                () -> new ResourceNotFoundException("资源不存在")
        );
        switch (articleService.getEditPermission(media.getArticleId(), userId)) {
            case HIDDEN -> throw new ResourceNotFoundException("资源不存在");
            case VIEW_ONLY -> throw new ForbiddenException("无删除权限");
            case EDITABLE -> { }
        }
        // 级联：音频删除时同步删除其封面图片媒体（metadata.imageId 指向的同文章 image 媒体）
        String coverId = media.getType() == MediaCategory.audio ? extractImageId(media.getMetadata()) : null;
        mediaRepository.delete(media);
        localFileStorageService.delete(mediaId);
        if (coverId != null) {
            deleteCoverMediaQuietly(coverId, media.getArticleId());
        }
    }

    @Transactional
    public MediaResponse saveMedia(MultipartFile file, Integer articleId, String fileType, String metadata, Integer userId) {
        if (file.isEmpty()) {
            throw new BadRequestException("文件为空");
        }
        switch (articleService.getEditPermission(articleId, userId)) {
            case HIDDEN -> throw new ResourceNotFoundException("资源不存在");
            case VIEW_ONLY -> throw new ForbiddenException("无编辑权限");
            case EDITABLE -> { }
        }
        String id = UUID.randomUUID().toString().replace("-", "");
        MediaCategory type;
        try {
            type = MediaCategory.valueOf(fileType);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("文件类型错误");
        }
        verifyFileContent(file, type);
        Media media = new Media();
        media.setId(id);
        media.setArticleId(articleId);
        media.setMimeType(file.getContentType());
        media.setType(type);
        media.setMetadata(validateAndCompact(metadata, switch (media.getType()) {
            case image -> ImageMetadata.class;
            case video -> VideoMetadata.class;
            case audio -> AudioMetadata.class;
            case file -> FileMetadata.class;
            default -> throw new InternalException("未完善的文件类型：" + fileType);
        }));
        localFileStorageService.store(file, id);
        try {
            mediaRepository.save(media);
        } catch (RuntimeException e) {
            localFileStorageService.delete(id);
            throw e;
        }
        return new MediaResponse(media.getId(), media.getArticleId(), media.getType(), media.getMimeType(),
                media.getMetadata(), file.getSize());
    }

    @Transactional
    public MediaResponse updateMetadata(String mediaId, String metadata, Integer userId) {
        Media media = mediaRepository.findById(mediaId).orElseThrow(
                () -> new ResourceNotFoundException("资源不存在")
        );
        switch (articleService.getEditPermission(media.getArticleId(), userId)) {
            case HIDDEN -> throw new ResourceNotFoundException("资源不存在");
            case VIEW_ONLY -> throw new ForbiddenException("无编辑权限");
            case EDITABLE -> { }
        }
        String oldCoverId = media.getType() == MediaCategory.audio ? extractImageId(media.getMetadata()) : null;
        media.setMetadata(validateAndCompact(metadata, switch (media.getType()) {
            case image -> ImageMetadata.class;
            case video -> VideoMetadata.class;
            case audio -> AudioMetadata.class;
            case file -> FileMetadata.class;
            default -> throw new InternalException("未完善的文件类型：" + media.getType());
        }));
        mediaRepository.save(media);
        // 级联：更换/移除音频封面时（imageId 从 X 变为 Y 或 null）自动删除旧封面媒体，
        // 避免旧封面文件与 DB 行永久残留
        if (oldCoverId != null && !oldCoverId.equals(extractImageId(media.getMetadata()))) {
            deleteCoverMediaQuietly(oldCoverId, media.getArticleId());
        }
        return new MediaResponse(media.getId(), media.getArticleId(), media.getType(), media.getMimeType(),
                media.getMetadata(), localFileStorageService.sizeOf(mediaId));
    }

    /** 解析 metadata 中的音频封面媒体 id（imageId 字段；缺省/解析失败返回 null） */
    private String extractImageId(String metadata) {
        if (metadata == null) {
            return null;
        }
        try {
            var node = objectMapper.readTree(metadata);
            var id = node.get("imageId");
            return id != null && !id.isNull() && !id.asText().isBlank() ? id.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 级联删除封面媒体（静默安全）：仅当目标存在、为 image 类型且属于同一文章时删除
     * （DB 行 + 磁盘文件）；脏数据/异常引用直接跳过，不阻断主流程。
     */
    private void deleteCoverMediaQuietly(String coverId, Integer articleId) {
        if (coverId == null || coverId.isBlank()) {
            return;
        }
        mediaRepository.findById(coverId).ifPresent(cover -> {
            if (cover.getType() == MediaCategory.image && Objects.equals(cover.getArticleId(), articleId)) {
                mediaRepository.delete(cover);
                localFileStorageService.delete(cover.getId());
            }
        });
    }

    private String validateAndCompact(String json, Class<? extends AbstractMediaMetadata> metadataClass) {
        try {
            AbstractMediaMetadata parsed = objectMapper.readValue(json, metadataClass);
            // Bean Validation 必须显式调用（Jackson 反序列化不会触发注解）：
            // 此前 @Size/@Min/@ValidImageId 等校验注解全部是死代码，音频封面可指向不存在/非图片的媒体
            var violations = validator.validate(parsed);
            if (!violations.isEmpty()) {
                String message = violations.iterator().next().getMessage();
                throw new BadRequestException("元数据校验失败：" + message);
            }
            return objectMapper.writeValueAsString(parsed);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("元数据格式错误");
        }
    }

    /**
     * 文件类型核查（问题 2）：声明类型（fileType）与 Content-Type 大类必须一致，
     * 且已知格式的魔数必须匹配；不一致报 400。
     * 目的：防止"fileType=image 但 Content-Type=text/html" 的伪造文件被内联输出执行脚本。
     */
    private void verifyFileContent(MultipartFile file, MediaCategory type) {
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (SVG_MIME.equals(contentType)) {
            throw new BadRequestException("不支持 SVG 图片（可内嵌脚本，存在安全风险）");
        }
        boolean categoryOk = switch (type) {
            case image -> contentType.startsWith("image/");
            case audio -> contentType.startsWith("audio/");
            case video -> contentType.startsWith("video/");
            case file -> !contentType.startsWith("image/") && !contentType.startsWith("audio/")
                    && !contentType.startsWith("video/") && !contentType.startsWith("text/html");
        };
        if (!categoryOk) {
            throw new BadRequestException("文件内容类型与声明类型不一致");
        }
        String[] magics = MAGIC.get(contentType);
        if (magics == null) {
            return; // 未知/少见格式：仅靠 Content-Type 大类把关
        }
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(12);
            if (!magicMatches(head, magics)) {
                throw new BadRequestException("文件内容与类型不符，请检查文件是否损坏或类型错误");
            }
        } catch (IOException e) {
            throw new InternalException("文件读取失败");
        }
    }

    /**
     * 头部字节与魔数表比对：任一命中即通过。
     * 语法见 MAGIC 表注释——`RIFF+x` 偏移 0 为 RIFF 且偏移 8 为 x；`hex@N` 匹配偏移 N；纯 hex 匹配偏移 0
     */
    private boolean magicMatches(byte[] head, String[] magics) {
        for (String magic : magics) {
            if (magic.startsWith("RIFF+")) {
                if (startsWithHex(head, "52494646", 0) && startsWithHex(head, magic.substring(5), 8)) {
                    return true;
                }
            } else if (magic.contains("@")) {
                int at = magic.indexOf('@');
                int offset = Integer.parseInt(magic.substring(at + 1));
                if (startsWithHex(head, magic.substring(0, at), offset)) {
                    return true;
                }
            } else if (startsWithHex(head, magic, 0)) {
                return true;
            }
        }
        return false;
    }

    private boolean startsWithHex(byte[] head, String hex, int offset) {
        byte[] pattern = hexStringToBytes(hex);
        if (offset + pattern.length > head.length) return false;
        for (int i = 0; i < pattern.length; i++) {
            if (head[offset + i] != pattern[i]) return false;
        }
        return true;
    }

    private byte[] hexStringToBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

}
