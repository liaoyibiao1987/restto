package com.rustto.manager.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明接口所需权限码。
 *
 * <p>可标注于方法或类。解析规则：方法注解优先；方法无注解则回退到类注解；均无则放行。
 * admin 角色（{@code role_code='admin'}）直接 bypass，不校验具体权限。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /**
     * @return 所需权限码，如 {@code system:user:create}
     */
    String value();
}
