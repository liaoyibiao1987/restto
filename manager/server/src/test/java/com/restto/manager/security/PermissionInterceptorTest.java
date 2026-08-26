package com.restto.manager.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restto.manager.common.ResultCode;
import com.restto.manager.service.system.permission.SysPermissionService;
import com.restto.manager.service.system.role.SysRoleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link PermissionInterceptor} 单测：覆盖各类分支。
 */
class PermissionInterceptorTest {

    private SysRoleService roleService;

    private SysPermissionService permService;

    private PermissionInterceptor interceptor;

    static class MethodAnnotated {
        @RequirePermission("system:user:create")
        public void m() {
        }

        public void plain() {
        }
    }

    @RequirePermission("system:role")
    static class ClassAnnotated {
        public void m() {
        }
    }

    @BeforeEach
    void setUp() {
        roleService = mock(SysRoleService.class);
        permService = mock(SysPermissionService.class);
        interceptor = new PermissionInterceptor(roleService, permService, new ObjectMapper());
        UserContext.set(1L, "alice");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private HandlerMethod handler(Object bean, String methodName) throws NoSuchMethodException {
        Method method = bean.getClass().getMethod(methodName);
        return new HandlerMethod(bean, method);
    }

    @Test
    void nonHandlerMethodPassesThrough() throws Exception {
        assertTrue(interceptor.preHandle(null, new MockHttpServletResponse(), "not-a-handler"));
    }

    @Test
    void methodWithoutAnnotationPassesThrough() throws Exception {
        assertTrue(interceptor.preHandle(null, new MockHttpServletResponse(),
                handler(new MethodAnnotated(), "plain")));
    }

    @Test
    void adminBypassesCheck() throws Exception {
        when(roleService.isAdmin(1L)).thenReturn(true);
        assertTrue(interceptor.preHandle(null, new MockHttpServletResponse(),
                handler(new MethodAnnotated(), "m")));
    }

    @Test
    void userWithPermissionPasses() throws Exception {
        when(roleService.isAdmin(1L)).thenReturn(false);
        Set<String> codes = new HashSet<>();
        codes.add("system:user:create");
        when(permService.getPermissionCodes(1L)).thenReturn(codes);
        assertTrue(interceptor.preHandle(null, new MockHttpServletResponse(),
                handler(new MethodAnnotated(), "m")));
    }

    @Test
    void userWithoutPermissionIsForbidden() throws Exception {
        when(roleService.isAdmin(1L)).thenReturn(false);
        when(permService.getPermissionCodes(1L)).thenReturn(Collections.emptySet());
        MockHttpServletResponse resp = new MockHttpServletResponse();
        boolean ok = interceptor.preHandle(null, resp, handler(new MethodAnnotated(), "m"));
        assertFalse(ok);
        assertEquals(403, resp.getStatus());
        assertTrue(resp.getContentAsString().contains(String.valueOf(ResultCode.FORBIDDEN.getCode())));
    }

    @Test
    void classLevelAnnotationUsedAsFallback() throws Exception {
        when(roleService.isAdmin(1L)).thenReturn(false);
        Set<String> codes = new HashSet<>();
        codes.add("system:role");
        when(permService.getPermissionCodes(1L)).thenReturn(codes);
        assertTrue(interceptor.preHandle(null, new MockHttpServletResponse(),
                handler(new ClassAnnotated(), "m")));
    }

    @Test
    void missingUserContextBlocks() throws Exception {
        UserContext.clear();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        boolean ok = interceptor.preHandle(null, resp, handler(new MethodAnnotated(), "m"));
        assertFalse(ok);
        assertEquals(401, resp.getStatus());
    }
}
