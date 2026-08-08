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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Service
public class MediaService {

    @Autowired private MediaRepository mediaRepository;
    @Autowired private ArticleService articleService;
    @Autowired private LocalFileStorageService localFileStorageService;
    @Autowired private ObjectMapper objectMapper;

    public MediaContent getMedia(String mediaId, Integer userId) {
        Media media = mediaRepository.findById(mediaId).orElseThrow(
                () -> new ResourceNotFoundException("资源不存在")
        );
        if (articleService.getEditPermission(media.getArticleId(), userId) == ArticleEditPermission.HIDDEN) {
            throw new ResourceNotFoundException("资源不存在");
        }
        return new MediaContent(media, localFileStorageService.load(mediaId));
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
        mediaRepository.delete(media);
        localFileStorageService.delete(mediaId);
    }

    @Transactional
    public MediaResponse saveMedia(MultipartFile file, Integer articleId, String fileType, String metadata, Integer userId) {
        switch (articleService.getEditPermission(articleId, userId)) {
            case HIDDEN -> throw new ResourceNotFoundException("资源不存在");
            case VIEW_ONLY -> throw new ForbiddenException("无编辑权限");
            case EDITABLE -> { }
        }
        String id = UUID.randomUUID().toString().replace("-", "");
        Media media = new Media();
        media.setId(id);
        media.setArticleId(articleId);
        media.setMimeType(file.getContentType());
        try {
            media.setType(MediaCategory.valueOf(fileType));
        } catch (IllegalArgumentException e){
            throw new BadRequestException("文件类型错误");
        }
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
        return new MediaResponse(media.getId(), media.getArticleId(), media.getType(), media.getMimeType(), media.getMetadata());
    }

    private String validateAndCompact(String json, Class<? extends AbstractMediaMetadata> metadataClass) {
        try {
            AbstractMediaMetadata parsed = objectMapper.readValue(json, metadataClass);
            return objectMapper.writeValueAsString(parsed);
        } catch (Exception e) {
            throw new BadRequestException("元数据格式错误");
        }
    }

}
