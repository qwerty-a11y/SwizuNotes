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

package com.swizu.swizunotes.controller;

import com.swizu.swizunotes.common.Result;
import com.swizu.swizunotes.dto.response.MediaResponse;
import com.swizu.swizunotes.entity.Media;
import com.swizu.swizunotes.entity.MediaCategory;
import com.swizu.swizunotes.services.CustomUserDetails;
import com.swizu.swizunotes.services.MediaContent;
import com.swizu.swizunotes.services.MediaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    @Autowired private MediaService mediaService;

    @GetMapping("/{mediaId}")
    public ResponseEntity<Resource> getMedia(@PathVariable String mediaId,
                                             @RequestParam(required = false, defaultValue = "false") boolean download,
                                             @AuthenticationPrincipal CustomUserDetails user) {
        MediaContent content = mediaService.getMedia(mediaId, userId(user));
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(mediaType(content.media()))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(content.media(), mediaId, userId(user), download));
        return builder.body(content.resource());
    }

    @GetMapping("/{mediaId}/info")
    public ResponseEntity<Result<MediaResponse>> getMediaInfo(@PathVariable String mediaId, @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(new Result<>("获取成功", mediaService.getMediaInfo(mediaId, userId(user))));
    }

    @DeleteMapping("/{mediaId}")
    public ResponseEntity<Void> deleteMedia(@PathVariable String mediaId, @AuthenticationPrincipal CustomUserDetails user) {
        mediaService.deleteMedia(mediaId, userId(user));
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Result<MediaResponse>> saveMedia(@RequestParam("file") MultipartFile file,
                                                           @RequestParam("articleId") Integer articleId,
                                                           @RequestParam("fileType") String fileType,
                                                           @RequestParam("metadata") String metadata,
                                                           @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(new Result<>("上传成功",
                mediaService.saveMedia(file, articleId, fileType, metadata, userId(user))));
    }

    @PutMapping("/{mediaId}")
    public ResponseEntity<Result<MediaResponse>> updateMediaMetadata(@PathVariable String mediaId,
                                                                     @RequestBody String metadata,
                                                                     @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(new Result<>("更新成功",
                mediaService.updateMetadata(mediaId, metadata, userId(user))));
    }

    private Integer userId(CustomUserDetails user) {
        return user == null ? null : user.getId();
    }

    /** 仅 file 类型加 attachment（下载）；image/audio/video 默认 inline 播放；?download=1 时一律 attachment */
    private String contentDisposition(Media media, String mediaId, Integer userId, boolean forceDownload) {
        if (media.getType() != MediaCategory.file && !forceDownload) {
            return "inline";
        }
        String raw = mediaService.getDownloadFilename(mediaId, userId)
                .replace("\"", "'")
                .replace(";", "")
                .replace("\r", "")
                .replace("\n", "");
        String encoded = URLEncoder.encode(raw, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + raw + "\"; filename*=UTF-8''" + encoded;
    }

    private MediaType mediaType(Media media) {
        if (media.getMimeType() != null) {
            return MediaType.parseMediaType(media.getMimeType());
        }
        return switch (media.getType()) {
            case image -> MediaType.IMAGE_JPEG;
            case video -> MediaType.parseMediaType("video/mp4");
            case audio -> MediaType.parseMediaType("audio/mpeg");
            case file -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
