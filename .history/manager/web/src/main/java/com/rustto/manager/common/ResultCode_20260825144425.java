package com.restto.manager.common;

/**
 * 业务状态码枚举。
 */
public enum ResultCode {

    /** 成功。 */
    SUCCESS(0, "success"),
    /** 通用业务错误。 */
    BUSINESS_ERROR(40001, "business error"),
    /** 未登录 / Token 缺失。 */
    UNAUTHORIZED(40100, "unauthorized"),
    /** Token 无效或已过期。 */
    TOKEN_INVALID(40101, "token invalid or expired"),
    /** 无权限。 */
    FORBIDDEN(40300, "forbidden"),
    /** 资源不存在。 */
    NOT_FOUND(40400, "resource not found"),
    /** 参数校验失败。 */
    PARAM_INVALID(42200, "invalid parameter"),
    /** 服务器内部错误。 */
    INTERNAL_ERROR(50000, "internal server error");

    private final int code;

    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * @return 状态码
     */
    public int getCode() {
        return code;
    }

    /**
     * @return 默认提示信息
     */
    public String getMessage() {
        return message;
    }
}
