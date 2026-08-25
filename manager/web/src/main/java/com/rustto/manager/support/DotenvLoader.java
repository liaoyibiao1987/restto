package com.rustto.manager.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 极简 {@code .env} 加载器：把工作目录下 {@code .env} 的键值写入系统属性。
 *
 * <p>Spring 的 {@code ${VAR}} 占位符会解析系统属性与环境变量，因此 {@code .env}
 * 中的配置（如 DB_HOST、JWT_SECRET）可直接被 application.yml 使用。
 * 已存在的环境变量优先，不覆盖。 */
public final class DotenvLoader {

    private DotenvLoader() {}

    /**
     * 加载 {@code .env}（若存在）。
     */
    public static void load() {
        load(Paths.get(".env"));
    }

    /**
     * 从指定路径加载 {@code .env}。
     *
     * @param file .env 文件路径
     */
    static void load(Path file) {
        if (!Files.exists(file)) {
            return;
        }
        try {
            for (String raw : Files.readAllLines(file)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                String value = stripQuotes(line.substring(eq + 1).trim());
                if (System.getProperty(key) == null && System.getenv(key) == null) {
                    System.setProperty(key, value);
                }
            }
        } catch (IOException e) {
            // 加载失败不阻断启动。
            System.err.println("[DotenvLoader] failed to read .env: " + e.getMessage());
        }
    }

    /**
     * 去除值两端成对的双引号。
     *
     * @param value 原始值
     * @return 去引号后的值
     */
    private static String stripQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
