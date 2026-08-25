package com.restto.manager.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应包装。
 *
 * @param <T> 业务数据类型
 */
@Data
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务状态码。 */
    private int code;

    /** 提示信息。 */
    private String message;

    /** 业务数据。 */
    private T data;

    /**
     * 构造成功响应。
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return 成功结果
     */
    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.code = ResultCode.SUCCESS.getCode();
        r.message = ResultCode.SUCCESS.getMessage();
        r.data = data;
        return r;
    }

    /**
     * 构造无数据的成功响应。
     *
     * @param <T> 数据类型
     * @return 成功结果
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * 构造失败响应。
     *
     * @param code    状态码
     * @param message 提示信息
     * @param <T>     数据类型
     * @return 失败结果
     */
    public static <T> Result<T> error(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }

    /**
     * 由状态码枚举构造失败响应。
     *
     * @param resultCode 状态码枚举
     * @param <T>        数据类型
     * @return 失败结果
     */
    public static <T> Result<T> error(ResultCode resultCode) {
        return error(resultCode.getCode(), resultCode.getMessage());
    }

    /**
     * 由状态码枚举 + 自定义信息构造失败响应。
     *
     * @param resultCode 状态码枚举
     * @param message    提示信息
     * @param <T>        数据类型
     * @return 失败结果
     */
    public static <T> Result<T> error(ResultCode resultCode, String message) {
        return error(resultCode.getCode(), message);
    }
}
