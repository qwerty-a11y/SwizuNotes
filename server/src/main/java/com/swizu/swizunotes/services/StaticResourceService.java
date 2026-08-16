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

import com.swizu.swizunotes.common.exception.ResourceNotFoundException;
import com.swizu.swizunotes.entity.StaticResource;
import com.swizu.swizunotes.repository.StaticResourceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 静态资源白名单读取（id=文件名，path=磁盘路径）。
 * 主题文件也登记在 static_resources 表，但可见性（发布/预览令牌）与
 * CSS 占位符替换由 ThemeService 处理（见 StaticResourceController 的转发逻辑），
 * 本服务只负责"表里有就按白名单读文件"。
 */
@Service
public class StaticResourceService {

    @Autowired private StaticResourceRepository staticResourceRepository;

    public Resource getResource(String id) {
        StaticResource staticResource = staticResourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("资源不存在"));
        Path path = Path.of(staticResource.getPath());
        if (!Files.exists(path)) {
            throw new ResourceNotFoundException("资源不存在");
        }
        return new FileSystemResource(path);
    }
}
