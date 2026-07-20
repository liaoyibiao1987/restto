package com.rustto.manager.config;

import com.rustto.manager.security.PermissionInterceptor;
import com.rustto.manager.security.TokenInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册 Token + 权限拦截器（顺序敏感）、放开 CORS（开发期前端独立端口）。
 *
 * <p>拦截器顺序：Token(order=1) 必须先于 Permission(order=2)，因为后者依赖前者填充的
 * {@link com.rustto.manager.security.UserContext}。显式 {@code order()} 防止后续重构打乱顺序。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final TokenInterceptor tokenInterceptor;

    private final PermissionInterceptor permissionInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1) 鉴权：校验 Token，填充 UserContext
        registry.addInterceptor(tokenInterceptor)
                .addPathPatterns("/api/**")
                // 登录/注册接口不校验 Token
                .excludePathPatterns("/api/auth/login", "/api/auth/register", "/error")
                .order(1);

        // 2) 授权：校验 @RequirePermission；登录后引导接口（info/menus）仅需 Token、不校验具体权限
        registry.addInterceptor(permissionInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/auth/info",
                        "/api/auth/menus",
                        "/error")
                .order(2);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}

