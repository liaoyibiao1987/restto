package com.restto.manager.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置（restto.jwt.*）。
 */
@Data
@ConfigurationProperties(prefix = "restto.jwt")
public class JwtProperties {

    /** 签名密钥（至少 32 字节）。 */
    private String secret = "restto-dev-secret-change-me-please-with-32-bytes";

    /** Token 有效期（分钟）。 */
    private long expireMinutes = 720L;
}
