package com.restto.manager.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restto.manager.common.Result;
import com.restto.manager.common.ResultCode;
import com.restto.manager.service.system.permission.SysPermissionService;
import com.restto.manager.service.system.role.SysRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 权限校验拦截器：解析 Controller 上的 {@link RequirePermission}，按当前用户权限码校验。
 *
 * <p>注册于 {@link com.restto.manager.security.TokenInterceptor} 之后（依赖其填充的 {@link UserContext}）。
 * admin 角色直接 bypass；无注解的方法/类放行；缺少权限则返回 403 业务 JSON。
 *
 * <p>⚠️ 鉴权核心逻辑（AGENT.MD §5.2）：admin bypass、UserContext 缺失分支、FORBIDDEN 返回体需人工审核。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionInterceptor implements HandlerInterceptor {

    private final SysRoleService sysRoleService;

    private final SysPermissionService sysPermissionService;

    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        // 非控制器（静态资源/404）直接放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod hm = (HandlerMethod) handler;
        RequirePermission required = hm.getMethodAnnotation(RequirePermission.class);
        if (required == null) {
            required = hm.getBeanType().getAnnotation(RequirePermission.class);
        }
        // 方法/类均无注解 → 不做权限约束
        if (required == null) {
            return true;
        }

        UserContext.CurrentUser current = UserContext.get();
        // 正常流程下 TokenInterceptor 已填充；此处为防御性校验
        if (current == null) {
            return writeForbidden(response, ResultCode.UNAUTHORIZED, "未登录或会话已失效");
        }

        // 超管 bypass
        if (sysRoleService.isAdmin(current.userId)) {
            return true;
        }

        Set<String> held = sysPermissionService.getPermissionCodes(current.userId);
        if (held == null || held.isEmpty() || !held.contains(required.value())) {
            log.warn("forbidden: user={}, require={}", current.username, required.value());
            return writeForbidden(response, ResultCode.FORBIDDEN, "缺少权限: " + required.value());
        }
        return true;
    }

    /**
     * 写 403/401 业务 JSON。
     *
     * @param response   响应
     * @param resultCode 状态码
     * @param message    提示
     * @return 固定 false（拦截）
     */
    private boolean writeForbidden(HttpServletResponse response, ResultCode resultCode, String message)
            throws IOException {
        boolean unauthorized = resultCode == ResultCode.UNAUTHORIZED || resultCode == ResultCode.TOKEN_INVALID;
        response.setStatus(unauthorized ? HttpStatus.UNAUTHORIZED.value() : HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(resultCode, message)));
        return false;
    }
}
