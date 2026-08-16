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
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    @Autowired private MediaService mediaService;

    /**
     * 媒体二进制流。image/audio/video 默认 inline 播放（支持 HTTP Range 分段请求，
     * 大文件视频/音频 Seek 依赖 206 响应）；?download=1 一律 attachment 全量下载。
     * 返回类型必须声明为 ResponseEntity&lt;StreamingResponseBody&gt;（通配符 ResponseEntity&lt;?&gt;
     * 会让 Spring 无法识别流式 body 的 handler 而报 500）。
     */
    @GetMapping("/{mediaId}")
    public ResponseEntity<StreamingResponseBody> getMedia(@PathVariable String mediaId,
                                                          @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
                                                          @RequestParam(required = false, defaultValue = "false") boolean download,
                                                          @AuthenticationPrincipal CustomUserDetails user) throws IOException {
        MediaContent content = mediaService.getMedia(mediaId, userId(user));
        Resource resource = content.resource();
        MediaType type = mediaType(content.media());
        String disposition = contentDisposition(content.media(), download);
        long length = resource.contentLength();
        if (download || rangeHeader == null || length <= 0) {
            // 下载模式与无 Range 请求：全量 200（流式写）
            return ResponseEntity.ok()
                    .contentType(type)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentLength(length)
                    .body((StreamingResponseBody) out -> {
                        try (InputStream in = resource.getInputStream()) {
                            in.transferTo(out);
                        }
                    });
        }
        List<HttpRange> ranges;
        try {
            ranges = HttpRange.parseRanges(rangeHeader);
        } catch (IllegalArgumentException e) {
            // 非法 Range 头：忽略并全量返回
            return ResponseEntity.ok()
                    .contentType(type)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentLength(length)
                    .body((StreamingResponseBody) out -> {
                        try (InputStream in = resource.getInputStream()) {
                            in.transferTo(out);
                        }
                    });
        }
        if (ranges.isEmpty()) {
            return ResponseEntity.ok()
                    .contentType(type)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentLength(length)
                    .body((StreamingResponseBody) out -> {
                        try (InputStream in = resource.getInputStream()) {
                            in.transferTo(out);
                        }
                    });
        }
        // 单段 Range（浏览器 Seek 均为单段）：206 分段响应。
        // 手动流式写区间字节（StreamingResponseBody），不依赖 ResourceRegion converter
        HttpRange range = ranges.get(0);
        long start = range.getRangeStart(length);
        long end = range.getRangeEnd(length);
        if (start >= length) {
            // 区间起点越界：416 Range Not Satisfiable
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + length)
                    .build();
        }
        long count = end - start + 1;
        long finalStart = start;
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + length)
                .contentLength(count)
                .body((StreamingResponseBody) out -> {
                    try (InputStream in = resource.getInputStream()) {
                        in.skipNBytes(finalStart);
                        long remaining = count;
                        byte[] buf = new byte[8192];
                        while (remaining > 0) {
                            int n = in.read(buf, 0, (int) Math.min(buf.length, remaining));
                            if (n < 0) break;
                            out.write(buf, 0, n);
                            remaining -= n;
                        }
                    }
                });
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
    private String contentDisposition(Media media, boolean forceDownload) {
        if (media.getType() != MediaCategory.file && !forceDownload) {
            return "inline";
        }
        String raw = mediaService.getDownloadFilename(media)
                .replace("\"", "'")
                .replace(";", "")
                .replace("\r", "")
                .replace("\n", "");
        // raw filename 段只保留 ASCII 可打印字符：响应头含非 ISO-8859-1 字符（如中文文件名）
        // 会被 Tomcat 拒绝并静默剥离整个 Content-Disposition 头；中文等非 ASCII
        // 走 filename*=UTF-8'' 编码段（RFC 5987）
        String ascii = raw.replaceAll("[^\\x20-\\x7E]", "_");
        String encoded = URLEncoder.encode(raw, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }

    private MediaType mediaType(Media media) {
        if (media.getMimeType() != null) {
            try {
                return MediaType.parseMediaType(media.getMimeType());
            } catch (InvalidMediaTypeException e) {
                // 存储的 mime 异常：按类型推断默认值，避免 500
            }
        }
        return switch (media.getType()) {
            case image -> MediaType.IMAGE_JPEG;
            case video -> MediaType.parseMediaType("video/mp4");
            case audio -> MediaType.parseMediaType("audio/mpeg");
            case file -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
