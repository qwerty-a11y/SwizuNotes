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

import com.swizu.swizunotes.entity.MediaCategory;
import lombok.Data;

@Data
public class MediaResponse {
    private String id;
    private Integer articleId;
    private MediaCategory type;
    private String mimeType;
    private String metadata;
    private Long size;

    public MediaResponse(String id, Integer articleId, MediaCategory type, String mimeType, String metadata, Long size) {
        this.id = id;
        this.articleId = articleId;
        this.type = type;
        this.mimeType = mimeType;
        this.metadata = metadata;
        this.size = size;
    }
}
