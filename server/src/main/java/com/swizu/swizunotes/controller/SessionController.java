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
import com.swizu.swizunotes.dto.request.RefreshTokenRequest;
import com.swizu.swizunotes.dto.response.LoginResponse;
import com.swizu.swizunotes.services.CustomUserDetailsService;
import com.swizu.swizunotes.util.JwtUtils;
import io.jsonwebtoken.JwtException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
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

    @PostMapping("/")
    public ResponseEntity<Result<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getAccount(),
                        request.getPassword()
                )
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        LoginResponse response = new LoginResponse(
                jwtUtils.generateAccessToken(request.getAccount()),
                jwtUtils.generateRefreshToken(request.getAccount())
        );
        return new ResponseEntity<>(new Result<>("登录成功", response), HttpStatus.OK);
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
        customUserDetailsService.loadUserByUsername(account);
        LoginResponse response = new LoginResponse(
                jwtUtils.generateAccessToken(account),
                jwtUtils.generateRefreshToken(account)
        );
        return new ResponseEntity<>(new Result<>("刷新成功", response), HttpStatus.OK);
    }
}
