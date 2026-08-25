package com.restto.manager.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解：标注于 Controller 写操作方法，由 {@link OperLogAspect} 切面异步记录到 sys_oper_log。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperLog {

    /**
     * @return 操作描述，如 {@code "新增用户"}
     */
    String value();

    /**
     * @return 入参序列化时需脱敏的字段名（递归匹配）
     */
    String[] excludeParams() default {"password", "newPassword", "oldPassword", "passwordHash"};
}
