package com.restto.manager.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 统一响应 Result 测试。
 */
class ResultTest {

    @Test
    void successCarriesData() {
        Result<String> r = Result.success("ok");
        assertEquals(ResultCode.SUCCESS.getCode(), r.getCode());
        assertEquals("ok", r.getData());
    }

    @Test
    void successEmptyHasNullData() {
        Result<Void> r = Result.success();
        assertEquals(ResultCode.SUCCESS.getCode(), r.getCode());
        assertNull(r.getData());
    }

    @Test
    void errorCarriesCodeAndMessage() {
        Result<Object> r = Result.error(ResultCode.NOT_FOUND);
        assertEquals(ResultCode.NOT_FOUND.getCode(), r.getCode());
        assertNotNull(r.getMessage());
    }
}
