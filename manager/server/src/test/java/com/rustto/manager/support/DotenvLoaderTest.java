package com.restto.manager.support;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * .env 加载器测试。
 */
class DotenvLoaderTest {

    @Test
    void parsesKeyValueAndQuotes() throws IOException {
        Path file = Files.createTempFile("env-" + UUID.randomUUID(), ".env");
        Files.write(file, Arrays.asList(
                "FOO=bar",
                "# a comment",
                "",
                "BAZ=\"qux\"",
                "EMPTY="));
        // 清理可能残留的同名属性
        System.clearProperty("FOO");
        System.clearProperty("BAZ");
        System.clearProperty("EMPTY");

        DotenvLoader.load(file);

        assertEquals("bar", System.getProperty("FOO"));
        assertEquals("qux", System.getProperty("BAZ"));
        assertEquals("", System.getProperty("EMPTY"));
    }

    @Test
    void missingFileIsNoop() {
        Path missing = Paths.get("/tmp/does-not-exist-" + UUID.randomUUID() + ".env");
        DotenvLoader.load(missing); // 不应抛异常
        assertNull(System.getProperty("restto_NO_SUCH_KEY_" + UUID.randomUUID()));
    }
}
