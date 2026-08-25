-- restto 初始化迁移 V1（对应 AGENT.MD migrations/ 目录，只增不改不删）

-- 管理后台用户
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `username`      VARCHAR(64)  NOT NULL,
    `password_hash` VARCHAR(128) NOT NULL,
    `role`          VARCHAR(32)  NOT NULL DEFAULT 'admin',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台用户';

-- 客户端节点
CREATE TABLE IF NOT EXISTS `backup_node` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
    `node_name`          VARCHAR(128) NOT NULL,
    `node_token`         VARCHAR(128) NOT NULL,
    `status`             VARCHAR(16)  NOT NULL DEFAULT 'offline',   -- offline / online
    `version`            VARCHAR(32)  NULL,
    `last_heartbeat_at`  DATETIME     NULL,
    `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_backup_node_name` (`node_name`),
    KEY `idx_backup_node_token` (`node_token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户端节点';

-- 备份任务
CREATE TABLE IF NOT EXISTS `backup_task` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `name`        VARCHAR(128) NOT NULL,
    `node_id`     BIGINT       NOT NULL,
    `module`      VARCHAR(64)  NOT NULL,                            -- backup_file / backup_mysql
    `params_json` TEXT         NULL,
    `cron_expr`   VARCHAR(64)  NULL,
    `enabled`     TINYINT(1)   NOT NULL DEFAULT 1,
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_backup_task_node` (`node_id`),
    KEY `idx_backup_task_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='备份任务';

-- 备份执行记录
CREATE TABLE IF NOT EXISTS `backup_record` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `task_id`    BIGINT       NOT NULL,
    `node_id`    BIGINT       NOT NULL,
    `status`     VARCHAR(16)  NOT NULL,                              -- pending / success / failed
    `file_path`  VARCHAR(512) NULL,
    `size`       BIGINT       NULL,
    `checksum`   VARCHAR(128) NULL,
    `error_msg`  TEXT         NULL,
    `start_at`   DATETIME     NULL,
    `end_at`     DATETIME     NULL,
    PRIMARY KEY (`id`),
    KEY `idx_backup_record_task` (`task_id`),
    KEY `idx_backup_record_node` (`node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='备份执行记录';

-- 客户端二进制版本
CREATE TABLE IF NOT EXISTS `client_binary` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `version`    VARCHAR(32)  NOT NULL,
    `file_path`  VARCHAR(512) NOT NULL,
    `checksum`   VARCHAR(128) NULL,
    `size`       BIGINT       NULL,
    `uploaded_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_client_binary_version` (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户端二进制版本';

-- 内置初始管理员：用户名 admin，密码 admin123（BCrypt），便于首次登录。
-- 该 BCrypt 哈希对应明文 "admin123"，生产环境请立即修改。
INSERT IGNORE INTO `sys_user` (`username`, `password_hash`, `role`)
VALUES ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin');
