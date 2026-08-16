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

import com.swizu.swizunotes.util.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 令牌吊销黑名单（内存态，重启失效）：
 * 退出登录时把该会话的 access/refresh 的 jti 加入黑名单（按各自过期时间惰性清理），
 * 此后校验阶段（JwtAuthenticationFilter / 刷新接口）立即拒绝，实现"退出登录立刻失效"。
 * 注：JWT 本身无状态，多实例部署需换共享存储（如 Redis）。
 */
@Service
public class TokenBlacklistService {

    /** jti -> 过期时间戳（ms），过期条目惰性清理 */
    private final Map<String, Long> revoked = new ConcurrentHashMap<>();

    @Autowired
    private JwtUtils jwtUtils;

    /** 吊销一个令牌（解析 jti 与过期时间入黑名单） */
    public void revoke(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        String jti = jwtUtils.extractJti(token);
        Long expiresAt = jwtUtils.extractExpirationMs(token);
        if (jti != null && expiresAt != null) {
            revoked.put(jti, expiresAt);
        }
    }

    /** 是否已被吊销（已过期的黑名单条目自动清理并视为未吊销） */
    public boolean isRevoked(String jti) {
        if (jti == null) {
            return false;
        }
        Long expiresAt = revoked.get(jti);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt < System.currentTimeMillis()) {
            revoked.remove(jti);
            return false;
        }
        return true;
    }
}
