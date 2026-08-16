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
import com.swizu.swizunotes.common.exception.ResourceNotFoundException;
import com.swizu.swizunotes.common.exception.UnauthorizedException;
import com.swizu.swizunotes.dto.request.UpdateUserRequest;
import com.swizu.swizunotes.dto.response.ArticleSummaryResponse;
import com.swizu.swizunotes.dto.response.CurrentUserResponse;
import com.swizu.swizunotes.services.ArticleService;
import com.swizu.swizunotes.services.AvatarContent;
import com.swizu.swizunotes.services.CustomUserDetails;
import com.swizu.swizunotes.services.UserAvatarService;
import com.swizu.swizunotes.services.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UsersController {

    @Autowired private UserAvatarService userAvatarService;
    @Autowired private ArticleService articleService;
    @Autowired private UserService userService;

    /** 默认头像占位图（SVG 半身像，内容缩放 60% + 四周留白避免圆形裁剪；与前端 public/images/default-avatar.svg 同款） */
    private static final String DEFAULT_AVATAR_SVG = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024"><g transform="translate(205 205) scale(0.6)"><path d="M506.075809 546.976206c-145.260076 0-263.436846-118.16774-263.436846-263.418785 0-145.260076 118.17677-263.436846 263.436846-263.436846 145.260076 0 263.436846 118.16774 263.436846 263.436846C769.512655 428.799436 651.335885 546.976206 506.075809 546.976206zM506.075809 76.996419c-113.896181 0-206.561002 92.664821-206.561002 206.561002S392.179628 490.100362 506.075809 490.100362c113.905212 0 206.561002-92.646759 206.561002-206.54294S619.981021 76.996419 506.075809 76.996419z" fill="#94a3b8"/><path d="M514.754388 621.191146c-250.902125 0-455.024817 174.88103-455.024817 389.840656l28.437922 0c0-199.607302 190.991939-361.411765 426.586895-361.411765s426.586895 161.804462 426.586895 361.411765l20.156698 0 8.281224 0C969.788235 796.072176 765.647482 621.191146 514.754388 621.191146z" fill="#94a3b8"/><path d="M514.754388 678.057959c219.547262 0 398.148973 149.360049 398.148973 332.964812l28.437922 0c0-199.607302-190.991939-361.411765-426.586895-361.411765S88.167493 811.4245 88.167493 1011.031802l28.437922 0C116.605415 827.427039 295.207126 678.057959 514.754388 678.057959z" fill="#94a3b8"/></g></svg>
            """;

    /** 当前登录用户信息（前端用于纠正本地持久化的 userId、判断管理员） */
    @GetMapping("/me")
    public ResponseEntity<Result<CurrentUserResponse>> getCurrentUser(@AuthenticationPrincipal CustomUserDetails user) {
        if (user == null) {
            throw new UnauthorizedException("请先登录");
        }
        return ResponseEntity.ok(new Result<>("获取成功", userService.getCurrentUser(user.getId())));
    }

    /** 更新昵称（仅本人） */
    @PutMapping("/me")
    public ResponseEntity<Result<CurrentUserResponse>> updateUsername(@Valid @RequestBody UpdateUserRequest request,
                                                                      @AuthenticationPrincipal CustomUserDetails user) {
        if (user == null) {
            throw new UnauthorizedException("请先登录");
        }
        return ResponseEntity.ok(new Result<>("更新成功",
                userService.updateUsername(user.getId(), request.getUsername())));
    }

    /** 用户主页信息（公开） */
    @GetMapping("/{userId}/profile")
    public ResponseEntity<Result<CurrentUserResponse>> getUserProfile(@PathVariable Integer userId) {
        return ResponseEntity.ok(new Result<>("获取成功", userService.getProfile(userId)));
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Result<String>> uploadAvatar(@RequestParam("file") MultipartFile file,
                                                       @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(new Result<>("上传成功",
                userAvatarService.uploadAvatar(file, userId(user))));
    }

    @GetMapping("/{userId}/avatar")
    public ResponseEntity<Resource> getAvatar(@PathVariable Integer userId) {
        try {
            AvatarContent content = userAvatarService.getAvatar(userId);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(content.avatar().getMimeType()))
                    .body(content.resource());
        } catch (ResourceNotFoundException e) {
            // 无头像（或用户不存在）：返回默认占位图，避免 <img> 404 噪音
            return defaultAvatar();
        } catch (com.swizu.swizunotes.common.exception.InternalException e) {
            // DB 有登记但磁盘文件缺失/读取失败：同样回退默认占位图，不对外暴露 500
            return defaultAvatar();
        }
    }

    private ResponseEntity<Resource> defaultAvatar() {
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("image/svg+xml"))
                .body(new ByteArrayResource(DEFAULT_AVATAR_SVG.getBytes(StandardCharsets.UTF_8)));
    }

    @GetMapping("/{userId}/articles")
    public ResponseEntity<Result<List<ArticleSummaryResponse>>> getAuthorArticles(@PathVariable Integer userId,
                                                                                  @RequestParam(required = false) String keyword,
                                                                                  @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(new Result<>("获取文章成功",
                articleService.getAuthorArticles(userId, userId(user), keyword)));
    }

    private Integer userId(CustomUserDetails user) {
        return user == null ? null : user.getId();
    }
}
