//! rustto-client 主二进制：agent 友好的 CLI + 常驻 daemon。
//!
//! 命令结构（详见各分支）：
//! - `daemon`：常驻进程（注册 / 心跳 / 接收并执行任务 / 接收二进制下发）。
//! - `tool list`：列出所有已注册工具（agent 发现）。
//! - `tool schema <name>`：打印某工具的参数 JSON Schema（agent 自省）。
//! - `tool run <name> [--args JSON | --args-file FILE | --args -]`：以 JSON 参数运行工具。
//! - `backup-file` / `backup-mysql`：兼容旧命令的便捷封装（转 `tool run`）。
//! - `run-module <name> --args JSON`：兼容服务端旧调用方式。
//!
//! 工具运行统一输出结构化 JSON 信封 `{ ok, tool, output?, error? }`，
//! 成功退出码 0、工具失败 1 —— 便于 agent / 脚本解析。

mod daemon;

use anyhow::{Context, Result};
use clap::{Parser, Subcommand};
use rustto_common::{ToolError, ToolOutput};
use rustto_core::{logger, ClientConfig};
use rustto_tools as tools;
use serde_json::{json, Value};
use std::path::PathBuf;

#[derive(Parser, Debug)]
#[command(
    name = "rustto-client",
    version,
    about = "rustto 分布式备份系统客户端（agent 友好 CLI）"
)]
struct Cli {
    #[command(subcommand)]
    command: Command,
}

#[derive(Subcommand, Debug)]
enum Command {
    /// 常驻：连接 Manager，接收并执行备份任务。
    Daemon,
    /// 工具集操作（agent 调用入口）。
    Tool {
        #[command(subcommand)]
        action: ToolAction,
    },
    /// 兼容：单次文件 / 目录备份。
    BackupFile {
        #[arg(long)]
        path: PathBuf,
        #[arg(long)]
        dest: PathBuf,
    },
    /// 兼容：单次 MySQL 备份（依赖目标机 mysqldump）。
    BackupMysql {
        #[arg(long, default_value = "127.0.0.1")]
        host: String,
        #[arg(long, default_value_t = 3306)]
        port: u16,
        #[arg(long)]
        user: String,
        #[arg(long, default_value = "")]
        password: String,
        #[arg(long)]
        database: String,
        #[arg(long)]
        dest: PathBuf,
    },
    /// 兼容：按模块名 + JSON 参数调度（服务端旧调用方式）。
    RunModule {
        name: String,
        #[arg(long, default_value = "{}")]
        args: String,
    },
}

#[derive(Subcommand, Debug)]
enum ToolAction {
    /// 列出所有已注册工具（name + description）。
    List,
    /// 打印某工具的参数 JSON Schema。
    Schema { name: String },
    /// 运行工具：`--args` 内联 JSON / `--args-file` 文件 / `--args -` 读 stdin。
    Run {
        name: String,
        #[arg(long)]
        args: Option<String>,
        #[arg(long)]
        args_file: Option<PathBuf>,
    },
}

#[tokio::main]
async fn main() -> Result<()> {
    let cli = Cli::parse();
    let cfg = ClientConfig::from_env();
    let _guard = logger::init(&cfg.log_dir);

    match cli.command {
        Command::Daemon => daemon::run(cfg).await,
        Command::Tool { action } => run_tool_action(action).await,
        Command::BackupFile { path, dest } => {
            let args = json!({ "path": path, "dest": dest });
            run_tool_by_name("backup_file", args).await
        }
        Command::BackupMysql {
            host,
            port,
            user,
            password,
            database,
            dest,
        } => {
            let args = json!({
                "host": host, "port": port, "user": user,
                "password": password, "database": database, "dest": dest
            });
            run_tool_by_name("backup_mysql", args).await
        }
        Command::RunModule { name, args } => {
            let v: Value = serde_json::from_str(&args).context("invalid --args json")?;
            run_tool_by_name(&name, v).await
        }
    }
}

/// 处理 `tool` 子命令。
async fn run_tool_action(action: ToolAction) -> Result<()> {
    match action {
        ToolAction::List => {
            let list: Vec<Value> = tools::registry()
                .iter()
                .map(|t| {
                    let info = t.info();
                    json!({ "name": info.name, "description": info.description })
                })
                .collect();
            println!(
                "{}",
                serde_json::to_string_pretty(&json!({ "tools": list }))?
            );
            Ok(())
        }
        ToolAction::Schema { name } => {
            let tool =
                tools::lookup(&name).ok_or_else(|| anyhow::anyhow!("unknown tool: {name}"))?;
            let info = tool.info();
            let body = json!({
                "name": info.name,
                "description": info.description,
                "params_schema": info.params_schema
            });
            println!("{}", serde_json::to_string_pretty(&body)?);
            Ok(())
        }
        ToolAction::Run {
            name,
            args,
            args_file,
        } => {
            let v = resolve_args(args, args_file).await?;
            run_tool_by_name(&name, v).await
        }
    }
}

/// 按名查找并运行工具，打印结构化 JSON 信封；工具失败时退出码 1。
///
/// - `name`: 工具名（未注册则报错）。
/// - `args`: 已解析的参数 JSON。
async fn run_tool_by_name(name: &str, args: Value) -> Result<()> {
    let tool = tools::lookup(name).ok_or_else(|| anyhow::anyhow!("unknown tool: {name}"))?;
    let res = tool.run(&args).await;
    println!(
        "{}",
        serde_json::to_string_pretty(&build_envelope(name, &res))?
    );
    match res {
        Ok(_) => Ok(()),
        Err(_) => std::process::exit(1),
    }
}

/// 解析 `tool run` 的参数来源：内联 / 文件 / stdin / 空对象。
///
/// 优先级：`--args`（值为 `-` 表示读 stdin）> `--args-file` > 空 `{}`。
async fn resolve_args(args: Option<String>, args_file: Option<PathBuf>) -> Result<Value> {
    if let Some(raw) = args {
        if raw == "-" {
            return read_stdin_json().await;
        }
        return serde_json::from_str(&raw).context("invalid --args json");
    }
    if let Some(path) = args_file {
        let data = tokio::fs::read_to_string(&path)
            .await
            .with_context(|| format!("read args-file {}", path.display()))?;
        return serde_json::from_str(&data).context("invalid args-file json");
    }
    Ok(Value::Object(Default::default()))
}

/// 从 stdin 读取并解析 JSON（用于 `--args -` 管道输入）。
async fn read_stdin_json() -> Result<Value> {
    use tokio::io::AsyncReadExt;
    let mut buf = String::new();
    tokio::io::stdin()
        .read_to_string(&mut buf)
        .await
        .context("read stdin")?;
    if buf.trim().is_empty() {
        return Ok(Value::Object(Default::default()));
    }
    serde_json::from_str(&buf).context("invalid stdin json")
}

/// 构造统一的工具运行结果信封（纯函数，便于单测）。
fn build_envelope(name: &str, res: &Result<ToolOutput, ToolError>) -> Value {
    match res {
        Ok(o) => json!({ "ok": true, "tool": name, "output": o }),
        Err(e) => json!({ "ok": false, "tool": name, "error": e.to_string() }),
    }
}

#[cfg(test)]
mod tests {
    //! 覆盖信封构造与参数解析逻辑（不触网 / 不起子进程）。

    use super::*;
    use rustto_common::ToolOutput;

    #[test]
    fn envelope_success_shape() {
        let out = ToolOutput {
            file_path: "/x".into(),
            size: 10,
            checksum: "abc".into(),
        };
        let env = build_envelope("backup_file", &Ok(out));
        assert_eq!(env["ok"], true);
        assert_eq!(env["tool"], "backup_file");
        assert_eq!(env["output"]["size"], 10);
        assert_eq!(env["output"]["checksum"], "abc");
        assert!(env.get("error").is_none());
    }

    #[test]
    fn envelope_error_shape() {
        let err = ToolError::Params("missing args.path".into());
        let env = build_envelope("backup_file", &Err(err));
        assert_eq!(env["ok"], false);
        assert_eq!(env["tool"], "backup_file");
        assert!(env["error"].as_str().unwrap().contains("args.path"));
        assert!(env.get("output").is_none());
    }

    #[tokio::test]
    async fn resolve_args_defaults_to_empty_object() {
        let v = resolve_args(None, None).await.unwrap();
        assert!(v.is_object());
        assert!(v.as_object().unwrap().is_empty());
    }

    #[tokio::test]
    async fn resolve_args_inline_json() {
        let v = resolve_args(Some(r#"{"path":"/data"}"#.into()), None)
            .await
            .unwrap();
        assert_eq!(v["path"], "/data");
    }

    #[tokio::test]
    async fn resolve_args_inline_bad_json_errors() {
        let r = resolve_args(Some("{bad".into()), None).await;
        assert!(r.is_err());
    }

    #[tokio::test]
    async fn resolve_args_from_file() {
        let dir = std::env::temp_dir().join(format!("cli-args-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        let f = dir.join("args.json");
        std::fs::write(&f, r#"{"k":42}"#).unwrap();
        let v = resolve_args(None, Some(f)).await.unwrap();
        assert_eq!(v["k"], 42);
        std::fs::remove_dir_all(&dir).ok();
    }
}
