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
public class CurrentUserResponse {
    private Integer id;
    private String account;
    private String username;
    /** 是否管理员（仅当前用户接口返回真实值，公开主页为 null） */
    private Boolean isAdmin;

    public CurrentUserResponse(Integer id, String account, String username, Boolean isAdmin) {
        this.id = id;
        this.account = account;
        this.username = username;
        this.isAdmin = isAdmin;
    }
}
