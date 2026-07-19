package com.rustto.manager.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rustto.manager.common.Result;
import com.rustto.manager.common.ResultCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Token 鉴权拦截器：校验 {@code Authorization: Bearer <jwt>}，填充 {@link UserContext}。
 *
 * <p>放行登录接口；其余 /api/** 接口必须携带有效 Token。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return writeUnauthorized(response, ResultCode.TOKEN_INVALID, "missing bearer token");
        }
        String token = header.substring(7).trim();
        try {
            Claims claims = jwtUtil.parse(token);
            Long userId = Long.valueOf(claims.getSubject());
            String username = claims.get("username", String.class);
            UserContext.set(userId, username);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("invalid token: {}", e.getMessage());
            return writeUnauthorized(response, ResultCode.TOKEN_INVALID, e.getMessage());
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    /**
     * 写 401 响应。
     *
     * @param response   响应
     * @param resultCode 状态码
     * @param message    提示信息
     * @return 固定 false（拦截）
     */
    private boolean writeUnauthorized(HttpServletResponse response, ResultCode resultCode, String message)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(resultCode.getCode(), message)));
        return false;
    }
}
