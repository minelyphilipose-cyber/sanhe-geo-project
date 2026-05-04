package com.huanjing.geo.common.exception;

import com.huanjing.geo.common.result.R;
import com.fasterxml.jackson.databind.JsonMappingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<?> handleBizException(BizException e, HttpServletRequest request, HttpServletResponse response) {
        if (e.getHttpStatus() > 0 && e.getHttpStatus() != 200) {
            response.setStatus(e.getHttpStatus());
        }
        if (e.getCode() >= 500) {
            log.error("Business exception code={} path={} method={} ip={} msg={}",
                    e.getCode(), request.getRequestURI(), request.getMethod(), request.getRemoteAddr(), e.getMessage(), e);
        } else {
            log.warn("Business exception code={} path={} method={} ip={} msg={}",
                    e.getCode(), request.getRequestURI(), request.getMethod(), request.getRemoteAddr(), e.getMessage());
        }
        return R.fail(e.getCode(), e.getMessage(), e.getData());
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R<?> handleBadCredentials(BadCredentialsException e, HttpServletRequest request) {
        log.warn("Auth failed path={} method={} ip={} msg={}",
                request.getRequestURI(), request.getMethod(), request.getRemoteAddr(), e.getMessage());
        return R.fail(401, "Invalid username or password");
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<?> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        log.warn("Access denied path={} method={} ip={} msg={}",
                request.getRequestURI(), request.getMethod(), request.getRemoteAddr(), e.getMessage());
        return R.fail(403, "Forbidden");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<?> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(400);
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst()
                .orElse("Request validation failed");
        log.warn("Validation failed path={} method={} ip={} msg={}",
                request.getRequestURI(), request.getMethod(), request.getRemoteAddr(), msg);
        return R.fail(400, msg);
    }

    @ExceptionHandler(BindException.class)
    public R<?> handleBind(BindException e, HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(400);
        String msg = e.getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst()
                .orElse("Request bind failed");
        log.warn("Bind failed path={} method={} ip={} msg={}",
                request.getRequestURI(), request.getMethod(), request.getRemoteAddr(), msg);
        return R.fail(400, msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public R<?> handleConstraint(ConstraintViolationException e, HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(400);
        log.warn("Constraint failed path={} method={} ip={} msg={}",
                request.getRequestURI(), request.getMethod(), request.getRemoteAddr(), e.getMessage());
        return R.fail(400, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<?> handleMessageNotReadable(HttpMessageNotReadableException e, HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(400);
        String msg = resolveUnreadableMessage(e);
        log.warn("Request body parse failed path={} method={} ip={} msg={}",
                request.getRequestURI(), request.getMethod(), request.getRemoteAddr(), msg);
        return R.fail(400, msg);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public R<?> handleMaxUploadSize(MaxUploadSizeExceededException e, HttpServletRequest request) {
        log.warn("Upload size exceeded path={} method={} ip={} msg={}",
                request.getRequestURI(), request.getMethod(), request.getRemoteAddr(), e.getMessage());
        return R.fail(400, "Upload file exceeds 10MB limit");
    }

    @ExceptionHandler(MultipartException.class)
    public R<?> handleMultipart(MultipartException e, HttpServletRequest request) {
        Throwable cause = e.getCause();
        if (cause instanceof MaxUploadSizeExceededException) {
            return handleMaxUploadSize((MaxUploadSizeExceededException) cause, request);
        }
        log.warn("Multipart failed path={} method={} ip={} msg={}",
                request.getRequestURI(), request.getMethod(), request.getRemoteAddr(), e.getMessage());
        return R.fail(400, "Invalid upload request");
    }

    private String resolveUnreadableMessage(HttpMessageNotReadableException e) {
        Throwable current = e;
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (current instanceof JsonMappingException mappingException
                    && mappingException.getCause() instanceof IllegalArgumentException illegalArgumentException) {
                return illegalArgumentException.getMessage();
            }
            if (current instanceof IllegalArgumentException illegalArgumentException) {
                return illegalArgumentException.getMessage();
            }
            current = current.getCause();
        }
        return "Invalid request body";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<?> handleException(Exception e, HttpServletRequest request) {
        log.error("Unhandled exception path={} method={} ip={}",
                request.getRequestURI(), request.getMethod(), request.getRemoteAddr(), e);
        return R.fail(500, "Internal server error");
    }
}
