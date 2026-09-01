# scripts — 构建与发布脚本

集中管理 `restto-client` 及 `clis/` 下全部第三方 CLI 的本地构建、打包与多平台
交叉编译。所有脚本均从仓库根（`processor/`）执行，可从任意目录调用。

| 脚本          | 作用                                                       |
| ------------- | ---------------------------------------------------------- |
| `build.sh`    | 本地优化构建主程序 + 全部 CLI（`--dev` 关闭优化），打印产物路径 |
| `package.sh`  | 优化构建 + 把 bin + clis + 文档 + `.env.example` 打成归档  |
| `cross.sh`    | 多平台交叉编译（主程序 + CLI）并分别归档（Go 原生 GOOS/GOARCH 切换） |

## 用法

```bash
./scripts/build.sh                       # 优化构建（bin/restto-client + bin/clis/*）
./scripts/build.sh --dev                 # 关闭优化（便于调试）

./scripts/package.sh                     # 打包到 dist/

./scripts/cross.sh                       # 交叉编译全部默认目标
./scripts/cross.sh linux/arm64           # 只编译某目标（GOOS/GOARCH）
TARGETS="linux/amd64 linux/arm64" ./scripts/cross.sh
```

## 默认交叉编译目标

- `linux/amd64` / `linux/arm64` / `linux/386` —— Linux（对应归档名 `x86_64/aarch64/i686-unknown-linux-musl`）
- `windows/amd64` —— Windows（归档名 `x86_64-pc-windows-gnu`）
- `darwin/amd64` / `darwin/arm64` —— macOS（Intel / Apple Silicon）

## 前置依赖

- **本地构建 / 交叉编译**：仅需 Go 工具链（1.24+）。Go 原生支持交叉编译：
  脚本内部以 `CGO_ENABLED=0 GOOS=… GOARCH=… go build` 产出各平台静态二进制，
  **无需 Docker、无需外部交叉工具链**。
- Windows 目标产 zip 归档需要 `zip`；未安装时自动改打 `.tar.gz`。

## 产物

- `build.sh`：`bin/restto-client` + `bin/clis/<name>`（后者也是主程序运行时
  的默认查找目录之一，见 `runner.DefaultDirs`）。
- `cross.sh` 中间产物在 `dist-bin/`（已加入 `.gitignore`）；每个目标的归档
  **保留目录树**（`restto-client` 与 `clis/` 同级），解压即得完整部署目录。
- `package.sh` 归档内含 `restto-client`、`clis/`、`README.md`、`.env.example`。
- 归档统一输出到 `dist/`，命名 `restto-client-<version>-<target>.{tar.gz,zip}`
  （已在 `.gitignore` 忽略）。
- 版本号取自 `.env.example` 的 `CLIENT_VERSION`（与运行时默认一致）。

## 容器镜像

配合根目录 `Dockerfile`（`FROM scratch`，主程序与 clis 均为静态二进制，
clis 装入 `/usr/local/bin/` 由 PATH 兜底发现）：

```bash
./scripts/cross.sh linux/amd64 linux/arm64   # 先产出 dist/ 归档
docker build -t restto-client:0.1.0 .
```

> scratch 镜像不含 `mysqldump`；容器内跑 `backup_mysql` 需换含该工具的基础镜像。
