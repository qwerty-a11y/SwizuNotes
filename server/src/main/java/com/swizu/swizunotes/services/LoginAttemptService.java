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

import com.swizu.swizunotes.common.exception.BadRequestException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录尝试频率限制（内存态）：同一 IP+账号 连续失败达到阈值后锁定一段时间，
 * 防止对登录接口的字典/暴力破解。重启后计数清零（可接受，个人博客场景）。
 */
@Service
public class LoginAttemptService {

    private record Attempt(int count, long lockUntil) {}

    /** 连续失败次数阈值 */
    private static final int MAX_ATTEMPTS = 5;
    /** 锁定时长（毫秒） */
    private static final long LOCK_MS = 5 * 60 * 1000L;

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    /** 检查是否处于锁定（锁定中抛 400 提示稍后再试） */
    public void checkLocked(String key) {
        Attempt attempt = attempts.get(key);
        if (attempt != null && attempt.lockUntil() > System.currentTimeMillis()) {
            throw new BadRequestException("尝试次数过多，请 5 分钟后再试");
        }
        if (attempt != null && attempt.lockUntil() > 0 && attempt.lockUntil() <= System.currentTimeMillis()) {
            attempts.remove(key); // 锁定到期自动清除
        }
    }

    /** 记录一次失败；达到阈值进入锁定 */
    public void recordFailure(String key) {
        attempts.compute(key, (k, attempt) -> {
            int count = (attempt == null || attempt.lockUntil() > 0) ? 1 : attempt.count() + 1;
            if (count >= MAX_ATTEMPTS) {
                return new Attempt(0, System.currentTimeMillis() + LOCK_MS);
            }
            return new Attempt(count, 0);
        });
    }

    /** 登录成功清除计数 */
    public void reset(String key) {
        attempts.remove(key);
    }
}
