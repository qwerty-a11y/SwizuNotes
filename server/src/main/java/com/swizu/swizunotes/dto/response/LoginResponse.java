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

import lombok.Data;

@Data
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    /** 媒体专用令牌（12 小时，仅媒体 URL query 使用，避免 access token 进入 URL） */
    private String mediaToken;
    private Integer userId;

    public LoginResponse(String accessToken, String refreshToken, String mediaToken, Integer userId) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.mediaToken = mediaToken;
        this.userId = userId;
    }
}
