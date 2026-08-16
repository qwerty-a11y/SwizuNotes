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
import com.swizu.swizunotes.common.exception.UnauthorizedException;
import com.swizu.swizunotes.dto.request.LoginRequest;
import com.swizu.swizunotes.dto.request.LogoutRequest;
import com.swizu.swizunotes.dto.request.RefreshTokenRequest;
import com.swizu.swizunotes.dto.response.LoginResponse;
import com.swizu.swizunotes.services.CustomUserDetails;
import com.swizu.swizunotes.services.CustomUserDetailsService;
import com.swizu.swizunotes.services.LoginAttemptService;
import com.swizu.swizunotes.services.TokenBlacklistService;
import com.swizu.swizunotes.util.JwtUtils;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/session")
public class SessionController {

    @Autowired
    private JwtUtils jwtUtils;
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private CustomUserDetailsService customUserDetailsService;
    @Autowired
    private LoginAttemptService loginAttemptService;
    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @PostMapping("/")
    public ResponseEntity<Result<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
                                                       HttpServletRequest httpRequest) {
        String key = clientKey(httpRequest, request.getAccount());
        // 登录频率限制（同设备同账号连续失败锁定）
        loginAttemptService.checkLocked(key);
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getAccount(),
                            request.getPassword()
                    )
            );
            loginAttemptService.reset(key);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            CustomUserDetails details = (CustomUserDetails) authentication.getPrincipal();
            LoginResponse response = new LoginResponse(
                    jwtUtils.generateAccessToken(request.getAccount()),
                    jwtUtils.generateRefreshToken(request.getAccount()),
                    jwtUtils.generateMediaToken(request.getAccount()),
                    details.getId()
            );
            return new ResponseEntity<>(new Result<>("登录成功", response), HttpStatus.OK);
        } catch (BadCredentialsException e) {
            loginAttemptService.recordFailure(key);
            throw e;
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<Result<LoginResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        if (!jwtUtils.isRefreshToken(request.getRefreshToken())) {
            throw new UnauthorizedException("刷新令牌无效");
        }
        String account;
        try {
            account = jwtUtils.extractAccount(request.getRefreshToken());
        } catch (JwtException e) {
            throw new UnauthorizedException("刷新令牌无效或已过期");
        }
        // 退出登录后该 refresh token 已被吊销：黑名单拒绝
        if (tokenBlacklistService.isRevoked(jwtUtils.extractJti(request.getRefreshToken()))) {
            throw new UnauthorizedException("刷新令牌已失效");
        }
        CustomUserDetails details = (CustomUserDetails) customUserDetailsService.loadUserByUsername(account);
        LoginResponse response = new LoginResponse(
                jwtUtils.generateAccessToken(account),
                jwtUtils.generateRefreshToken(account),
                jwtUtils.generateMediaToken(account),
                details.getId()
        );
        return new ResponseEntity<>(new Result<>("刷新成功", response), HttpStatus.OK);
    }

    /** 退出登录：吊销当前会话的 access（Header）/ refresh / media 令牌，立即失效 */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @RequestBody(required = false) LogoutRequest request) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            tokenBlacklistService.revoke(authorization.substring(7));
        }
        if (request != null) {
            if (request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
                tokenBlacklistService.revoke(request.getRefreshToken());
            }
            if (request.getMediaToken() != null && !request.getMediaToken().isBlank()) {
                tokenBlacklistService.revoke(request.getMediaToken());
            }
        }
        return ResponseEntity.noContent().build();
    }

    /** 同设备（IP）+ 账号作为限流键 */
    private String clientKey(HttpServletRequest request, String account) {
        String ip = request.getRemoteAddr();
        return ip + ":" + account;
    }
}
