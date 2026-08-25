# scripts — 构建与发布脚本

集中管理 `rustto-client` 的本地构建、打包与多平台交叉编译。所有脚本均从仓库根（
`processor/`）执行，可从任意目录调用。

| 脚本          | 作用                                                       |
| ------------- | ---------------------------------------------------------- |
| `build.sh`    | 本地优化构建（`--dev` 关闭优化），打印产物路径             |
| `package.sh`  | 优化构建 + 把二进制 / 文档 / `.env.example` 打成归档       |
| `cross.sh`    | 多平台交叉编译并分别归档（Go 原生 GOOS/GOARCH 切换）      |

## 用法

```bash
./scripts/build.sh                       # 本地优化构建（输出 bin/rustto-client）
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

## 产物

- `build.sh` 输出到 `bin/`；`cross.sh` 中间产物在 `dist-bin/`（均已加入 `.gitignore`）。
- 归档统一输出到 `dist/`，命名 `rustto-client-<version>-<target>.{tar.gz,zip}`
  （已在 `.gitignore` 忽略）。
- 版本号取自 `.env.example` 的 `CLIENT_VERSION`（与运行时默认一致）。

## 容器镜像

配合根目录 `Dockerfile`（`FROM scratch`，只装静态二进制）：

```bash
./scripts/cross.sh linux/amd64 linux/arm64   # 先产出 dist/ 归档
docker build -t rustto-client:0.1.0 .
```
