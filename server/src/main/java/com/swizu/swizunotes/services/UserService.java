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
import com.swizu.swizunotes.dto.response.CurrentUserResponse;
import com.swizu.swizunotes.entity.User;
import com.swizu.swizunotes.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    @Autowired private UserRepository userRepository;

    /** 用户主页信息（公开；不暴露管理员标记） */
    @Transactional(readOnly = true)
    public CurrentUserResponse getProfile(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("资源不存在"));
        return toResponse(user, null);
    }

    /** 当前用户信息（含管理员标记） */
    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("资源不存在"));
        return toResponse(user, user.getIsAdmin());
    }

    /** 更新昵称（仅本人） */
    @Transactional
    public CurrentUserResponse updateUsername(Integer userId, String username) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("资源不存在"));
        user.setUsername(username);
        return toResponse(userRepository.save(user), user.getIsAdmin());
    }

    private CurrentUserResponse toResponse(User user, Boolean isAdmin) {
        return new CurrentUserResponse(user.getId(), user.getAccount(), user.getUsername(), isAdmin);
    }
}
