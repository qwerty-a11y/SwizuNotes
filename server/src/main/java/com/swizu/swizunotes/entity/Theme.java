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

package com.swizu.swizunotes.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 主题配置（常变的管理数据：显示名/日期自动切换区间/发布状态）。
 * 主题的 CSS 与亮暗 banner 文件不在此表——文件作为静态资源存 static_resources
 * （id = 文件名 &lt;name&gt;.css / &lt;name&gt;-light.&lt;ext&gt; / &lt;name&gt;-dark.&lt;ext&gt;），
 * 由 StaticResourceService/ThemeService 统一读写。
 */
@Data
@Entity
@Table(name = "themes")
public class Theme {
    /** 主题名（URL 标识，同时是主题文件名的基名，如 orange） */
    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "display_name")
    private String displayName;

    /** 公历自动切换开始日期（含），null = 不限 */
    @Column(name = "start_date")
    private LocalDate startDate;

    /** 公历自动切换结束日期（含），null = 不限 */
    @Column(name = "end_date")
    private LocalDate endDate;

    /** 农历自动切换开始（M-d 或 闰M-d，如 8-15；每年重复），null = 不参与农历匹配 */
    @Column(name = "lunar_start")
    private String lunarStart;

    /** 农历自动切换结束（可空；无开始时单日生效） */
    @Column(name = "lunar_end")
    private String lunarEnd;

    /** 发布状态（unpublished/prerelease/published），VARCHAR 存小写枚举名 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ThemeStatus status;

    @Column(name = "created_at")
    private Instant createdAt;
}
