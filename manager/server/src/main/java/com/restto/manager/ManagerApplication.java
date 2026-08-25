package com.restto.manager;

import com.restto.manager.support.DotenvLoader;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * restto 管理端启动类。
 *
 * <p>启动流程：先加载工作目录下的 {@code .env}（若存在）到系统属性，再启动 Spring。
 * 开启 {@code @EnableScheduling} 供备份任务调度使用。 */
@SpringBootApplication
@MapperScan("com.restto.manager.mapper")
@ConfigurationPropertiesScan("com.restto.manager.config")
@EnableScheduling
public class ManagerApplication {

    /**
     * 应用入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        DotenvLoader.load();
        SpringApplication.run(ManagerApplication.class, args);
    }
}
