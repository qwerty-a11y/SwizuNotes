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

package com.swizu.swizunotes.dto.response;

import com.swizu.swizunotes.entity.ThemeStatus;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/** 主题完整信息（管理页列表，含未发布主题与日期配置） */
@Data
public class ThemeAdminResponse {
    /** 主题名（URL 标识） */
    private String name;
    /** 显示名 */
    private String displayName;
    /** 公历自动切换开始日期（含，可空） */
    private LocalDate startDate;
    /** 公历自动切换结束日期（含，可空） */
    private LocalDate endDate;
    /** 农历自动切换开始（M-d 或 闰M-d，每年重复，可空） */
    private String lunarStart;
    /** 农历自动切换结束（可空；无开始时单日生效） */
    private String lunarEnd;
    /** 亮色 banner 文件名（如 mid-autumn-light.png；管理页资源预览用，可空） */
    private String bannerLight;
    /** 暗色 banner 文件名（可空） */
    private String bannerDark;
    /** 发布状态（unpublished/prerelease/published） */
    private ThemeStatus status;
    /** 创建时间 */
    private Instant createdAt;

    public ThemeAdminResponse(String name, String displayName, LocalDate startDate, LocalDate endDate,
                              String lunarStart, String lunarEnd, String bannerLight, String bannerDark,
                              ThemeStatus status, Instant createdAt) {
        this.name = name;
        this.displayName = displayName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.lunarStart = lunarStart;
        this.lunarEnd = lunarEnd;
        this.bannerLight = bannerLight;
        this.bannerDark = bannerDark;
        this.status = status;
        this.createdAt = createdAt;
    }
}
