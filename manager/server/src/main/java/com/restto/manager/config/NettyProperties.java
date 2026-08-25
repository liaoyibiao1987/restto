package com.restto.manager.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Netty 通信配置（restto.netty.*）。
 */
@Data
@ConfigurationProperties(prefix = "restto.netty")
public class NettyProperties {

    /** Netty TCP 监听端口。 */
    private int port = 9600;
}
