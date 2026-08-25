package com.restto.manager.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 备份相关配置（restto.backup.*）。
 */
@Data
@ConfigurationProperties(prefix = "restto.backup")
public class BackupProperties {

    /** 接收产物 / 二进制存放目录。 */
    private String dataDir = "data";
}
