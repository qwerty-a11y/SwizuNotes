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

package com.swizu.swizunotes.repository;

import com.swizu.swizunotes.entity.Theme;
import com.swizu.swizunotes.entity.ThemeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/** 主题配置仓库（主题文件清单在 StaticResourceRepository，两表通过 id 命名约定关联） */
@Repository
public interface ThemeRepository extends JpaRepository<Theme, String> {

    /** 全部主题（管理页，后上传的在前） */
    List<Theme> findAllByOrderByCreatedAtDesc();

    /** 已发布主题（主题选择器，后上传的在前） */
    List<Theme> findByStatusOrderByCreatedAtDesc(ThemeStatus status);

    /** 日期自动切换候选（预发布 + 已发布，后上传的在前；日期命中在 ThemeService 内存过滤） */
    List<Theme> findByStatusInOrderByCreatedAtDesc(Collection<ThemeStatus> statuses);
}
