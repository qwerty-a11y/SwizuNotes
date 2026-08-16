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

package com.swizu.swizunotes.common.exception;

import com.swizu.swizunotes.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(ResourceNotFoundException e) {
        return new ResponseEntity<>(new Result<>(e.getMessage(), null), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Result<Void>> handleForbidden(ForbiddenException e) {
        return new ResponseEntity<>(new Result<>(e.getMessage(), null), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Result<Void>> handleUnauthorized(UnauthorizedException e) {
        return new ResponseEntity<>(new Result<>(e.getMessage(), null), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(InternalException.class)
    public ResponseEntity<Result<Void>> handleInternal(InternalException e) {
        return new ResponseEntity<>(new Result<>(e.getMessage(), null), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Result<Void>> handleBadRequest(BadRequestException e) {
        return new ResponseEntity<>(new Result<>(e.getMessage(), null), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return new ResponseEntity<>(new Result<>("上传文件超过大小限制", null), HttpStatus.BAD_REQUEST);
    }

    /** 登录密码错误：统一 401（此前落到默认 /error 返回 500，破坏前端 401 链路） */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Result<Void>> handleBadCredentials(BadCredentialsException e) {
        return new ResponseEntity<>(new Result<>("账号或密码错误", null), HttpStatus.UNAUTHORIZED);
    }

    /** 刷新时用户不存在：统一 401 */
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<Result<Void>> handleUsernameNotFound(UsernameNotFoundException e) {
        return new ResponseEntity<>(new Result<>("账号不存在", null), HttpStatus.UNAUTHORIZED);
    }

    /** DTO 校验失败（@Valid）：取第一条错误消息回 400 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getDefaultMessage())
                .orElse("请求参数校验失败");
        return new ResponseEntity<>(new Result<>(message, null), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBind(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getDefaultMessage())
                .orElse("请求参数校验失败");
        return new ResponseEntity<>(new Result<>(message, null), HttpStatus.BAD_REQUEST);
    }

    /** 请求体 JSON 无法解析 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        return new ResponseEntity<>(new Result<>("请求体格式错误", null), HttpStatus.BAD_REQUEST);
    }

    /** DB 完整性冲突（如超长标题落库、唯一约束）→ 400（此前 500 泄露内部 path） */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        return new ResponseEntity<>(new Result<>("数据不符合要求", null), HttpStatus.BAD_REQUEST);
    }

    /** multipart 缺文件参数 / multipart 解析失败 → 400（此前落兜底 500） */
    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    public ResponseEntity<Result<Void>> handleMultipart(Exception e) {
        return new ResponseEntity<>(new Result<>("缺少文件参数或请求格式错误", null), HttpStatus.BAD_REQUEST);
    }

    /** 兜底：不向外暴露内部细节 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnknown(Exception e) {
        log.error("Unhandled exception", e);
        return new ResponseEntity<>(new Result<>("服务器内部错误", null), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
