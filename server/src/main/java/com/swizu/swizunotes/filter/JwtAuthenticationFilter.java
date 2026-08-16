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

package com.swizu.swizunotes.filter;

import com.swizu.swizunotes.services.CustomUserDetailsService;
import com.swizu.swizunotes.services.TokenBlacklistService;
import com.swizu.swizunotes.util.JwtUtils;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    @Autowired
    private JwtUtils jwtUtils;
    @Autowired
    private CustomUserDetailsService customUserDetailsService;
    @Autowired
    private TokenBlacklistService tokenBlacklistService;


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        log.debug("JWT filter enter: {} {}, authHeader={}", request.getMethod(), request.getRequestURI(),
                request.getHeader("Authorization") != null);
        String jwt = null;
        boolean fromHeader = false;
        final String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7);
            fromHeader = true;
        } else if (isMediaRead(request)) {
            // <img> 标签无法携带 Authorization 头，草稿文章的媒体读取允许用 query 参数携带
            // **媒体专用令牌**（type=media，权限面仅媒体读取）；access/refresh 令牌不再接受进 URL
            String queryToken = request.getParameter("token");
            if (queryToken != null && jwtUtils.isMediaToken(queryToken)) {
                jwt = queryToken;
            }
        }

        if (jwt != null){
            String account = null;
            try {
                account = jwtUtils.extractAccount(jwt);
            } catch (JwtException e) {
                log.debug("JWT filter: invalid or expired token: {}", e.getMessage());
            }
            log.debug("JWT filter: account={}", account);

            if (account != null && SecurityContextHolder.getContext().getAuthentication() == null){
                UserDetails user = null;
                try {
                    user = customUserDetailsService.loadUserByUsername(account);
                } catch (UsernameNotFoundException e) {
                    // 签名合法但账号已删除：不设认证，交由后续 401（不落入容器 /error 的 500）
                    log.debug("JWT filter: account not found: {}", account);
                } catch (DataAccessException e) {
                    // DB 临时故障：按匿名处理，避免过滤器内未捕获异常传播成 500
                    log.warn("JWT filter: load user failed, treating as anonymous", e);
                }
                // 类型闸门：Header 路径只认 access，query 路径只认 media；再查吊销黑名单
                boolean typeOk = fromHeader ? jwtUtils.isAccessToken(jwt) : jwtUtils.isMediaToken(jwt);
                if (user != null
                        && jwtUtils.validateToken(jwt)
                        && typeOk
                        && !tokenBlacklistService.isRevoked(jwtUtils.extractJti(jwt))){
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("JWT filter: authentication set for {}", account);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isMediaRead(HttpServletRequest request) {
        return "GET".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().startsWith("/api/v1/media/");
    }
}
