package com.rustto.manager.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Netty 通信配置（rustto.netty.*）。
 */
@Data
@ConfigurationProperties(prefix = "rustto.netty")
public class NettyProperties {

    /** Netty TCP 监听端口。 */
    private int port = 9600;
}
