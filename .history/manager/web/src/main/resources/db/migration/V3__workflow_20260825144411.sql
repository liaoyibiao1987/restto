-- restto 工作流迁移 V3（对应 AGENT.MD migrations/ 目录，只增不改不删）
-- 在「备份管理」模块下新增工作流：把若干 backup_task 编排成 DAG（串行/并行/条件分支），
-- 复用现有 BackupTaskService.runNow 下发 + Netty TASK_RESULT 回传，不改协议、不动 Rust 客户端。
-- status 沿用 backup_record 的字符串习惯（success/failed/...），与 TINYINT 状态无关。

-- 1) 工作流定义
CREATE TABLE IF NOT EXISTS `workflow` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `name`        VARCHAR(128) NOT NULL,
    `description` VARCHAR(255) NULL,
    `cron_expr`   VARCHAR(64)  NULL COMMENT '工作流级 cron；空表示仅手动触发',
    `enabled`     TINYINT(1)   NOT NULL DEFAULT 1,
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_workflow_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流定义';

-- 2) 工作流节点（引用 backup_task.id）
CREATE TABLE IF NOT EXISTS `workflow_node` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `workflow_id` BIGINT       NOT NULL,
    `node_key`    VARCHAR(48)  NOT NULL COMMENT '工作流内唯一节点键（前端生成）',
    `label`       VARCHAR(128) NULL,
    `task_id`     BIGINT       NOT NULL COMMENT '引用 backup_task.id',
    `pos_x`       INT          NULL COMMENT '画布横坐标（前端布局，引擎忽略）',
    `pos_y`       INT          NULL COMMENT '画布纵坐标（前端布局，引擎忽略）',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_wn_workflow_node` (`workflow_id`, `node_key`),
    KEY `idx_wn_workflow` (`workflow_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流节点';

-- 3) 工作流边（依赖关系 + 条件）
CREATE TABLE IF NOT EXISTS `workflow_edge` (
    `id`             BIGINT      NOT NULL AUTO_INCREMENT,
    `workflow_id`    BIGINT      NOT NULL,
    `source_key`     VARCHAR(48) NOT NULL,
    `target_key`     VARCHAR(48) NOT NULL,
    `edge_condition` VARCHAR(16) NOT NULL DEFAULT 'always' COMMENT 'on_success / on_failed / always',
    `created_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_we_workflow` (`workflow_id`),
    KEY `idx_we_source` (`workflow_id`, `source_key`),
    KEY `idx_we_target` (`workflow_id`, `target_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流边';

-- 4) 工作流执行（一次运行）
CREATE TABLE IF NOT EXISTS `workflow_execution` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT,
    `workflow_id`  BIGINT      NOT NULL,
    `status`       VARCHAR(16) NOT NULL COMMENT 'running / success / failed',
    `trigger_type` VARCHAR(16) NOT NULL COMMENT 'manual / schedule',
    `triggered_by` BIGINT      NULL COMMENT '触发用户 id（@OperLog 审计）',
    `start_at`     DATETIME    NULL,
    `end_at`       DATETIME    NULL,
    `error_msg`    TEXT        NULL COMMENT '整体失败原因（首个失败节点+原因）',
    `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_we_workflow_status` (`workflow_id`, `status`),
    KEY `idx_we_workflow_start` (`workflow_id`, `start_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流执行';

-- 5) 工作流执行节点（每步结果）
CREATE TABLE IF NOT EXISTS `workflow_execution_node` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT,
    `execution_id` BIGINT      NOT NULL,
    `node_key`     VARCHAR(48) NOT NULL,
    `task_id`      BIGINT      NOT NULL,
    `status`       VARCHAR(16) NOT NULL COMMENT 'waiting / running / success / failed / skipped',
    `error_msg`    TEXT        NULL,
    `start_at`     DATETIME    NULL,
    `end_at`       DATETIME    NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_wen_exec_node` (`execution_id`, `node_key`),
    KEY `idx_wen_execution` (`execution_id`),
    KEY `idx_wen_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流执行节点';

-- =========================================================
-- RBAC 种子（INSERT IGNORE 幂等；id 不与现有 backup 菜单 20-24 / 权限 201-234 冲突）
-- ⚠️ V2 的 `INSERT ... SELECT 1,id FROM sys_menu` 只绑定 V2 时已存在的行；
--    V3 新增菜单/权限不会自动绑 admin，故下方必须显式重绑（否则 admin 静默无权限）。
-- =========================================================

-- 菜单（挂在「备份管理」parent_id=20 下）
INSERT IGNORE INTO `sys_menu`
    (`id`, `parent_id`, `menu_name`, `menu_type`, `perms`, `path`, `component`, `icon`, `sort`, `visible`, `status`) VALUES
    (25, 20, '工作流设计',   2, 'workflow:workflow',   '/backup/workflow',         'workflow/WorkflowList',   'share', 5, 1, 1),
    (26, 20, '执行历史',     2, 'workflow:execution',  '/backup/workflow/history', 'workflow/WorkflowHistory', 'timer', 6, 1, 1),
    (27, 20, '工作流编辑器', 2, NULL,                  '/backup/workflow/design',  'workflow/WorkflowDesign',  NULL,    7, 0, 1);
    -- 27 visible=0：仅用于注册隐藏路由，设计器由列表页跳转进入，不出现在侧边栏。

-- 权限点 module='workflow'
INSERT IGNORE INTO `sys_permission` (`id`, `permission_code`, `permission_name`, `module`, `remark`) VALUES
    (241, 'workflow:workflow:list',   '工作流-列表',     'workflow', NULL),
    (242, 'workflow:workflow:query',  '工作流-详情',     'workflow', NULL),
    (243, 'workflow:workflow:create', '工作流-新增',     'workflow', NULL),
    (244, 'workflow:workflow:update', '工作流-修改',     'workflow', NULL),
    (245, 'workflow:workflow:delete', '工作流-删除',     'workflow', NULL),
    (246, 'workflow:workflow:run',    '工作流-运行',     'workflow', '手动触发一次工作流'),
    (247, 'workflow:execution:list',  '执行历史-列表',   'workflow', NULL),
    (248, 'workflow:execution:query', '执行历史-详情',   'workflow', NULL),
    (249, 'workflow:execution:run',   '执行历史-运行',   'workflow', '从历史详情触发');

-- admin 角色绑定全部新菜单 + 新权限（V2 的全量绑定不会覆盖 V3 新行，必须重绑）
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `id` FROM `sys_menu` WHERE `id` IN (25, 26, 27);

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, `id` FROM `sys_permission` WHERE `module` = 'workflow';

-- viewer 角色：工作流只读（仿 backup 只读）
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (2, 25), (2, 26);

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES
    (2, 241), (2, 242), (2, 247), (2, 248);
