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
import com.swizu.swizunotes.services.AvatarContent;
import com.swizu.swizunotes.services.CustomUserDetails;
import com.swizu.swizunotes.services.UserAvatarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users")
public class UsersController {

    @Autowired private UserAvatarService userAvatarService;

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Result<String>> uploadAvatar(@RequestParam("file") MultipartFile file,
                                                       @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(new Result<>("上传成功",
                userAvatarService.uploadAvatar(file, userId(user))));
    }

    @GetMapping("/{userId}/avatar")
    public ResponseEntity<Resource> getAvatar(@PathVariable Integer userId) {
        AvatarContent content = userAvatarService.getAvatar(userId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.avatar().getMimeType()))
                .body(content.resource());
    }

    private Integer userId(CustomUserDetails user) {
        return user == null ? null : user.getId();
    }
}
