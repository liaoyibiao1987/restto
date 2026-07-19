//! 客户端配置：从 `.env` 与环境变量加载。

use std::env;

/// 客户端运行配置。
#[derive(Debug, Clone)]
pub struct ClientConfig {
    /// Manager 主机地址。
    pub manager_host: String,
    /// Manager Netty 端口。
    pub manager_port: u16,
    /// 本节点名称（注册时上报）。
    pub node_name: String,
    /// 节点 Token（注册时校验）。
    pub node_token: String,
    /// 客户端版本号。
    pub version: String,
    /// 日志目录。
    pub log_dir: String,
    /// 备份产物默认存放目录。
    pub data_dir: String,
    /// 心跳间隔（秒）。
    pub heartbeat_interval_secs: u64,
}

impl Default for ClientConfig {
    fn default() -> Self {
        Self {
            manager_host: "127.0.0.1".into(),
            manager_port: 9600,
            node_name: "default-node".into(),
            node_token: String::new(),
            version: "0.1.0".into(),
            log_dir: "logs".into(),
            data_dir: "data".into(),
            heartbeat_interval_secs: 15,
        }
    }
}

impl ClientConfig {
    /// 先加载 `.env`（若存在），再从环境变量读取配置，缺失项使用默认值。
    pub fn from_env() -> Self {
        let _ = dotenvy::dotenv();
        let mut cfg = Self::default();
        if let Ok(v) = env::var("MANAGER_HOST") {
            cfg.manager_host = v;
        }
        if let Ok(v) = env::var("MANAGER_PORT") {
            if let Ok(port) = v.parse::<u16>() {
                cfg.manager_port = port;
            }
        }
        if let Ok(v) = env::var("NODE_NAME") {
            cfg.node_name = v;
        }
        if let Ok(v) = env::var("NODE_TOKEN") {
            cfg.node_token = v;
        }
        if let Ok(v) = env::var("CLIENT_VERSION") {
            cfg.version = v;
        }
        if let Ok(v) = env::var("LOG_DIR") {
            cfg.log_dir = v;
        }
        if let Ok(v) = env::var("DATA_DIR") {
            cfg.data_dir = v;
        }
        if let Ok(v) = env::var("HEARTBEAT_INTERVAL_SECS") {
            if let Ok(n) = v.parse::<u64>() {
                cfg.heartbeat_interval_secs = n;
            }
        }
        cfg
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn default_values() {
        let cfg = ClientConfig::default();
        assert_eq!(cfg.manager_port, 9600);
        assert_eq!(cfg.heartbeat_interval_secs, 15);
    }
}
