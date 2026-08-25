-- restto RBAC 迁移 V2（对应 AGENT.MD migrations/ 目录，只增不改不删）
-- 引入标准 RBAC：用户-角色-权限 + 独立菜单树 + 操作日志。
-- 注意：legacy sys_user.role 列保留（只增不删），但自此不再作为鉴权依据，改由 sys_user_role 决定。

-- 1) 扩展现有 sys_user（纯增量列；Flyway 只执行一次，无需 IF NOT EXISTS）
ALTER TABLE `sys_user`
    ADD COLUMN `status`   TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '0 停用 / 1 启用' AFTER `role`,
    ADD COLUMN `nickname` VARCHAR(64)  NULL COMMENT '昵称' AFTER `status`,
    ADD COLUMN `email`    VARCHAR(128) NULL COMMENT '邮箱' AFTER `nickname`;

-- 2) 角色
CREATE TABLE IF NOT EXISTS `sys_role` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `role_code`  VARCHAR(64)  NOT NULL,
    `role_name`  VARCHAR(64)  NOT NULL,
    `status`     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '0 停用 / 1 启用',
    `remark`     VARCHAR(255) NULL,
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

-- 3) 菜单（树形，parent_id=0 为顶层；menu_type: 1 目录 2 菜单）
CREATE TABLE IF NOT EXISTS `sys_menu` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `parent_id`  BIGINT       NOT NULL DEFAULT 0 COMMENT '0=顶层',
    `menu_name`  VARCHAR(64)  NOT NULL,
    `menu_type`  TINYINT(1)   NOT NULL COMMENT '1 目录 2 菜单',
    `perms`      VARCHAR(128) NULL     COMMENT '关联权限码（可空）',
    `path`       VARCHAR(128) NULL,
    `component`  VARCHAR(128) NULL,
    `icon`       VARCHAR(64)  NULL,
    `sort`       INT          NOT NULL DEFAULT 0,
    `visible`    TINYINT(1)   NOT NULL DEFAULT 1,
    `status`     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '0 停用 / 1 启用',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_sys_menu_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单（树形）';

-- 4) 权限点（与菜单分离，独立模块维护）
CREATE TABLE IF NOT EXISTS `sys_permission` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `permission_code` VARCHAR(128) NOT NULL,
    `permission_name` VARCHAR(64)  NOT NULL,
    `module`          VARCHAR(64)  NOT NULL COMMENT 'system / backup 等',
    `remark`          VARCHAR(255) NULL,
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_permission_code` (`permission_code`),
    KEY `idx_sys_permission_module` (`module`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限点';

-- 5) 关联表
CREATE TABLE IF NOT EXISTS `sys_user_role` (
    `user_id` BIGINT NOT NULL,
    `role_id` BIGINT NOT NULL,
    PRIMARY KEY (`user_id`, `role_id`),
    KEY `idx_sur_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色';

CREATE TABLE IF NOT EXISTS `sys_role_menu` (
    `role_id` BIGINT NOT NULL,
    `menu_id` BIGINT NOT NULL,
    PRIMARY KEY (`role_id`, `menu_id`),
    KEY `idx_srm_menu` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-菜单';

CREATE TABLE IF NOT EXISTS `sys_role_permission` (
    `role_id`       BIGINT NOT NULL,
    `permission_id` BIGINT NOT NULL,
    PRIMARY KEY (`role_id`, `permission_id`),
    KEY `idx_srp_perm` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限';

-- 6) 操作日志
CREATE TABLE IF NOT EXISTS `sys_oper_log` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `title`          VARCHAR(128) NOT NULL COMMENT '操作描述（@OperLog.value）',
    `oper_user`      VARCHAR(64)  NULL     COMMENT '操作者用户名',
    `oper_user_id`   BIGINT       NULL,
    `oper_uri`       VARCHAR(255) NULL,
    `oper_method`    VARCHAR(255) NULL     COMMENT 'Controller#method',
    `request_method` VARCHAR(8)   NULL     COMMENT 'GET/POST/PUT/DELETE',
    `request_params` TEXT         NULL     COMMENT '序列化参数（敏感字段已脱敏）',
    `status`         TINYINT(1)   NOT NULL COMMENT '1 成功 / 0 失败',
    `error_msg`      TEXT         NULL,
    `cost_ms`        BIGINT       NOT NULL DEFAULT 0,
    `oper_ip`        VARCHAR(64)  NULL,
    `oper_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_sys_oper_log_time` (`oper_time`),
    KEY `idx_sys_oper_log_user` (`oper_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志';

-- =========================================================
-- 种子数据（INSERT IGNORE 保证幂等；硬编码 id 以维持父子引用）
-- ⚠️ 上线前请在干净 staging 库验证 id 不与历史手动数据冲突（AGENT.MD §5.2）
-- =========================================================

-- 角色
INSERT IGNORE INTO `sys_role` (`id`, `role_code`, `role_name`, `status`, `remark`) VALUES
    (1, 'admin',  '超级管理员', 1, '内置超管，bypass 权限校验'),
    (2, 'viewer', '只读访客',  1, '备份模块只读');

-- 菜单树
INSERT IGNORE INTO `sys_menu`
    (`id`, `parent_id`, `menu_name`, `menu_type`, `perms`, `path`, `component`, `icon`, `sort`, `visible`, `status`) VALUES
    -- 概览
    ( 1, 0,  '概览',     1, NULL,                '/dashboard',        NULL,                       'dashboard',  0, 1, 1),
    ( 2, 1,  '仪表盘',   2, NULL,                '/dashboard/index',  'Dashboard',                'chart',      0, 1, 1),
    -- 系统管理
    (10, 0,  '系统管理', 1, NULL,                '/system',           NULL,                       'setting',    10, 1, 1),
    (11, 10, '用户管理', 2, 'system:user',       '/system/user',      'system/UserManage',        'user',       0, 1, 1),
    (12, 10, '角色管理', 2, 'system:role',       '/system/role',      'system/RoleManage',        'peoples',    1, 1, 1),
    (13, 10, '菜单管理', 2, 'system:menu',       '/system/menu',      'system/MenuManage',        'tree-table', 2, 1, 1),
    (14, 10, '权限管理', 2, 'system:permission', '/system/permission','system/PermissionManage',  'key',        3, 1, 1),
    (15, 10, '操作日志', 2, 'system:log',        '/system/log',       'system/OperLog',           'log',        4, 1, 1),
    -- 备份管理
    (20, 0,  '备份管理', 1, NULL,                '/backup',           NULL,                       'backup',     20, 1, 1),
    (21, 20, '节点管理', 2, 'backup:node',       '/backup/node',      'backup/NodeManage',        'server',     0, 1, 1),
    (22, 20, '任务管理', 2, 'backup:task',       '/backup/task',      'backup/TaskManage',        'task',       1, 1, 1),
    (23, 20, '记录管理', 2, 'backup:record',     '/backup/record',    'backup/RecordHistory',     'list',       2, 1, 1),
    (24, 20, '二进制管理', 2, 'backup:binary',   '/backup/binary',    'backup/BinaryManage',      'binary',     3, 1, 1);

-- 权限点
INSERT IGNORE INTO `sys_permission` (`id`, `permission_code`, `permission_name`, `module`, `remark`) VALUES
    -- user
    (101, 'system:user:list',           '用户-列表',     'system', '用户分页查询'),
    (102, 'system:user:query',          '用户-详情',     'system', '用户详情'),
    (103, 'system:user:create',         '用户-新增',     'system', '创建用户'),
    (104, 'system:user:update',         '用户-修改',     'system', '修改用户'),
    (105, 'system:user:delete',         '用户-删除',     'system', '删除用户'),
    (106, 'system:user:assign-roles',   '用户-分配角色', 'system', '给用户分配角色'),
    (107, 'system:user:reset-password', '用户-重置密码', 'system', '重置用户密码'),
    -- role
    (111, 'system:role:list',           '角色-列表',     'system', NULL),
    (112, 'system:role:query',          '角色-详情',     'system', NULL),
    (113, 'system:role:create',         '角色-新增',     'system', NULL),
    (114, 'system:role:update',         '角色-修改',     'system', NULL),
    (115, 'system:role:delete',         '角色-删除',     'system', NULL),
    (116, 'system:role:assign-menus',   '角色-分配菜单', 'system', NULL),
    (117, 'system:role:assign-perms',   '角色-分配权限', 'system', NULL),
    -- menu
    (121, 'system:menu:list',           '菜单-列表',     'system', NULL),
    (122, 'system:menu:query',          '菜单-详情',     'system', NULL),
    (123, 'system:menu:create',         '菜单-新增',     'system', NULL),
    (124, 'system:menu:update',         '菜单-修改',     'system', NULL),
    (125, 'system:menu:delete',         '菜单-删除',     'system', NULL),
    -- permission
    (131, 'system:permission:list',     '权限-列表',     'system', NULL),
    (132, 'system:permission:query',    '权限-详情',     'system', NULL),
    -- log
    (141, 'system:log:list',            '日志-列表',     'system', NULL),
    (142, 'system:log:query',           '日志-详情',     'system', NULL),
    (143, 'system:log:delete',          '日志-删除',     'system', NULL),
    -- backup
    (201, 'backup:node:list',           '节点-列表',     'backup', NULL),
    (202, 'backup:node:query',          '节点-详情',     'backup', NULL),
    (203, 'backup:node:create',         '节点-新增',     'backup', NULL),
    (204, 'backup:node:delete',         '节点-删除',     'backup', NULL),
    (211, 'backup:task:list',           '任务-列表',     'backup', NULL),
    (212, 'backup:task:query',          '任务-详情',     'backup', NULL),
    (213, 'backup:task:create',         '任务-新增',     'backup', NULL),
    (214, 'backup:task:update',         '任务-修改',     'backup', NULL),
    (215, 'backup:task:delete',         '任务-删除',     'backup', NULL),
    (216, 'backup:task:run',            '任务-立即触发', 'backup', NULL),
    (221, 'backup:record:list',         '记录-列表',     'backup', NULL),
    (222, 'backup:record:query',        '记录-详情',     'backup', NULL),
    (231, 'backup:binary:list',         '二进制-列表',   'backup', NULL),
    (232, 'backup:binary:query',        '二进制-详情',   'backup', NULL),
    (233, 'backup:binary:upload',       '二进制-上传',   'backup', NULL),
    (234, 'backup:binary:push',         '二进制-下发',   'backup', NULL);

-- 把 V1 的 admin 用户（按 username 定位，稳健于自增 id）绑定到 admin 角色
INSERT IGNORE INTO `sys_user_role` (`user_id`, `role_id`)
SELECT u.`id`, 1 FROM `sys_user` u WHERE u.`username` = 'admin';

-- admin 角色拥有全部菜单与权限
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `id` FROM `sys_menu`;

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, `id` FROM `sys_permission`;

-- viewer 角色：备份只读菜单 + 备份读权限
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
    (2, 1), (2, 2),
    (2, 20),
    (2, 21), (2, 22), (2, 23), (2, 24);

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES
    (2, 201), (2, 202),
    (2, 211), (2, 212),
    (2, 221), (2, 222),
    (2, 231), (2, 232);
