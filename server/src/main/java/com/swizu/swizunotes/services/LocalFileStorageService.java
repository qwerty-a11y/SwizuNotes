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

import com.swizu.swizunotes.common.exception.InternalException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class LocalFileStorageService {

     public String store(MultipartFile file, String id){
        String dir = "uploads/"+id.substring(0, 2)+"/";
        try {
            Files.createDirectories(Paths.get(dir));
            Path filePath = Paths.get(dir, id);
            file.transferTo(filePath);
            return filePath.toString();
        } catch (IOException e){
            throw new InternalException("文件保存失败");
        }
    }

    public void delete(String id){
        String dir = "uploads/"+id.substring(0, 2)+"/";
        try {
            Files.deleteIfExists(Paths.get(dir, id));
        } catch (IOException e){
            throw new InternalException("文件删除失败");
        }
    }

    public Resource load(String id){
        String dir = "uploads/"+id.substring(0, 2)+"/";
        Path path = Paths.get(dir, id).toAbsolutePath();
        try {
            if (Files.exists(path)) {
                return new FileSystemResource(path);
            } else {
                throw new InternalException("文件获取错误");
            }
        } catch (Exception e){
            throw new InternalException("文件加载失败");
        }
    }
}
