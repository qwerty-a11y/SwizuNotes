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

import com.swizu.swizunotes.services.StaticResourceService;
import com.swizu.swizunotes.services.ThemeService;
import com.swizu.swizunotes.services.ThemeService.StaticResourceContent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Optional;

/**
 * 静态资源白名单读取（id=文件名）。主题文件也登记在此表，读取时先交给
 * ThemeService 判定并处理可见性（已发布公开/未发布需 preview_token）与
 * CSS 占位符替换；非主题文件走原白名单逻辑。
 */
@RestController
@RequestMapping("/api/v1/static-resources")
public class StaticResourceController {

    @Autowired private StaticResourceService staticResourceService;
    @Autowired private ThemeService themeService;

    @GetMapping("/{id}")
    public ResponseEntity<Resource> getResource(@PathVariable String id,
                                                // 参数名必须与前端约定一致（preview_token 下划线）：
                                                // 若写 previewToken（驼峰），前端 ?preview_token= 绑定不到，
                                                // 未发布/预发布主题一律 404
                                                @RequestParam(name = "preview_token", required = false) String previewToken) {
        Optional<StaticResourceContent> themeContent = themeService.tryReadThemeFile(id, previewToken);
        if (themeContent.isPresent()) {
            StaticResourceContent content = themeContent.get();
            ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                    .contentType(content.mediaType());
            // 未发布/预发布主题的读取携带预览令牌（特权访问）：不得公开缓存，
            // 否则带令牌的 URL 会被浏览器/代理/CDN 缓存 30 天放大泄露面（问题 9）
            if (previewToken != null && !previewToken.isBlank()) {
                builder.cacheControl(CacheControl.noStore());
            } else {
                builder.cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic());
            }
            return builder.body(content.resource());
        }
        Resource resource = staticResourceService.getResource(id);
        MediaType mediaType = MediaTypeFactory.getMediaType(resource.getFilename())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(resource);
    }
}
