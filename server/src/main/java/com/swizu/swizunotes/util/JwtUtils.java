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

package com.swizu.swizunotes.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtils {

    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";
    /** 媒体专用令牌：仅用于 <img>/<audio>/<video> 无法携带 Header 的场景（URL query），权限面极小 */
    private static final String TYPE_MEDIA = "media";

    @Value("${jwt.secret}")
    private String secret;
    @Value("${jwt.access-expiration}")
    private long accessExpiration;
    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;
    /** 媒体令牌有效期（默认 12 小时：泄露窗口可控且避免用户停留页面时媒体加载过期） */
    @Value("${jwt.media-expiration:43200000}")
    private long mediaExpiration;

    private String buildToken(String username, String type, long expiration) {
        return Jwts.builder()
                .claims()
                    .subject(username)
                    .add(CLAIM_TYPE, type)
                    .id(UUID.randomUUID().toString())
                    .issuedAt(new Date())
                    .expiration(new Date(new Date().getTime() + expiration))
                .and()
                .signWith(getSignKey())
                .compact();
    }

    public String generateAccessToken(String username) {
        return buildToken(username, TYPE_ACCESS, accessExpiration);
    }

    public String generateRefreshToken(String username) {
        return buildToken(username, TYPE_REFRESH, refreshExpiration);
    }

    public String generateMediaToken(String username) {
        return buildToken(username, TYPE_MEDIA, mediaExpiration);
    }

    public String extractAccount(String token) {
        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    /** 令牌唯一 id（吊销黑名单用） */
    public String extractJti(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSignKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getId();
        } catch (Exception e) {
            return null;
        }
    }

    /** 令牌过期时间戳（ms，吊销黑名单惰性清理用） */
    public Long extractExpirationMs(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSignKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getExpiration() == null ? null : claims.getExpiration().getTime();
        } catch (Exception e) {
            return null;
        }
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSignKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isAccessToken(String token) {
        return hasType(token, TYPE_ACCESS);
    }

    public boolean isRefreshToken(String token) {
        return hasType(token, TYPE_REFRESH);
    }

    public boolean isMediaToken(String token) {
        return hasType(token, TYPE_MEDIA);
    }

    private boolean hasType(String token, String expectedType) {
        try {
            String type = Jwts.parser()
                    .verifyWith(getSignKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .get(CLAIM_TYPE, String.class);
            return expectedType.equals(type);
        } catch (Exception e) {
            return false;
        }
    }

    private SecretKey getSignKey() {
        byte[] keybytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keybytes);
    }
}
