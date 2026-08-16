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
import com.swizu.swizunotes.dto.response.PreviewTokenResponse;
import com.swizu.swizunotes.dto.response.ThemeAdminResponse;
import com.swizu.swizunotes.dto.response.ThemeSummaryResponse;
import com.swizu.swizunotes.services.ThemeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * 主题管理（配置在 themes 表；主题文件存 static_resources 表，读取走
 * GET /api/v1/static-resources/{id}）。公开：列表/日期生效主题；管理（ADMIN）：
 * 增删改/全量列表/预览令牌。
 */
@RestController
@RequestMapping("/api/v1/themes")
public class ThemesController {

    @Autowired private ThemeService themeService;

    // ============ 公开 ============

    /** 公开主题列表（导航栏主题选择器） */
    @GetMapping("/")
    public ResponseEntity<Result<List<ThemeSummaryResponse>>> getThemes() {
        return ResponseEntity.ok(new Result<>("获取成功", themeService.getPublicThemes()));
    }

    /** 今天生效的日期主题（无则 data=null） */
    @GetMapping("/active")
    public ResponseEntity<Result<ThemeSummaryResponse>> getActiveTheme() {
        return ResponseEntity.ok(new Result<>("获取成功", themeService.getActiveTheme()));
    }

    // ============ 管理（需 ADMIN） ============

    /** 全部主题（含未公开，管理页） */
    @GetMapping("/admin")
    public ResponseEntity<Result<List<ThemeAdminResponse>>> getAdminThemes() {
        return ResponseEntity.ok(new Result<>("获取成功", themeService.getAllThemes()));
    }

    /** 创建主题（multipart：name/displayName/startDate/endDate/lunarStart/lunarEnd/status + css/bannerLight/bannerDark 可选） */
    @PostMapping(value = "/", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Result<ThemeAdminResponse>> createTheme(
            @RequestParam String name,
            @RequestParam String displayName,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String lunarStart,
            @RequestParam(required = false) String lunarEnd,
            @RequestParam(required = false) String status,
            @RequestParam(value = "css", required = false) MultipartFile css,
            @RequestParam(value = "bannerLight", required = false) MultipartFile bannerLight,
            @RequestParam(value = "bannerDark", required = false) MultipartFile bannerDark) {
        return ResponseEntity.ok(new Result<>("创建成功",
                themeService.createTheme(name, displayName, startDate, endDate,
                        lunarStart, lunarEnd, status, css, bannerLight, bannerDark)));
    }

    /** 更新主题（multipart 同创建，字段/文件均可选；日期与农历不传 = 清空） */
    @PutMapping(value = "/{name}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Result<ThemeAdminResponse>> updateTheme(
            @PathVariable String name,
            @RequestParam(required = false) String displayName,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String lunarStart,
            @RequestParam(required = false) String lunarEnd,
            @RequestParam(required = false) String status,
            @RequestParam(value = "css", required = false) MultipartFile css,
            @RequestParam(value = "bannerLight", required = false) MultipartFile bannerLight,
            @RequestParam(value = "bannerDark", required = false) MultipartFile bannerDark) {
        return ResponseEntity.ok(new Result<>("更新成功",
                themeService.updateTheme(name, displayName, startDate, endDate,
                        lunarStart, lunarEnd, status, css, bannerLight, bannerDark)));
    }

    /** 删除主题（配置 + 文件登记 + 磁盘文件） */
    @DeleteMapping("/{name}")
    public ResponseEntity<Result<Void>> deleteTheme(@PathVariable String name) {
        themeService.deleteTheme(name);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).<Result<Void>>build();
    }

    /** 生成预览令牌（未公开主题预览用，10 分钟有效） */
    @PostMapping("/{name}/preview-token")
    public ResponseEntity<Result<PreviewTokenResponse>> createPreviewToken(@PathVariable String name) {
        return ResponseEntity.ok(new Result<>("获取成功",
                new PreviewTokenResponse(themeService.createPreviewToken(name))));
    }
}
