# scripts — 构建与发布脚本

集中管理 `rustto-client` 的本地构建、打包与多平台交叉编译。所有脚本均从仓库根（
`processor/`）执行，可从任意目录调用。

| 脚本          | 作用                                                       |
| ------------- | ---------------------------------------------------------- |
| `build.sh`    | 本地 release 构建（`--dev` 走 debug），打印产物路径        |
| `package.sh`  | release 构建 + 把二进制 / 文档 / `.env.example` 打成归档   |
| `cross.sh`    | 多平台交叉编译并分别归档（优先 `cross`，回退 `cargo`）    |

## 用法

```bash
./scripts/build.sh                       # 本地 release 构建
./scripts/build.sh --dev                 # debug 构建
./scripts/package.sh                     # 打包到 dist/

./scripts/cross.sh                       # 交叉编译全部默认目标
./scripts/cross.sh x86_64-unknown-linux-musl   # 只编译某目标
TARGETS="x86_64-unknown-linux-musl aarch64-unknown-linux-musl" ./scripts/cross.sh
```

## 默认交叉编译目标

- `x86_64-unknown-linux-musl` / `aarch64-unknown-linux-musl` —— 纯静态 Linux 二进制
- `x86_64-pc-windows-gnu` —— Windows
- `x86_64-apple-darwin` / `aarch64-apple-darwin` —— macOS（Intel / Apple Silicon）

## 前置依赖

- **本地构建**：仅需 Rust 工具链（`cargo`）。
- **交叉编译（推荐）**：安装 [`cross`](https://github.com/cross-rs/cross) 与 Docker：
  ```bash
  cargo install cross
  ```
  `cross` 在容器内配好各平台工具链，开箱即用。
- **交叉编译（回退，无 Docker）**：需为目标平台安装 rustup target 与 linker，例如：
  ```bash
  rustup target add x86_64-unknown-linux-musl
  # macOS / Windows 目标还需要对应的 linker / SDK，跨机较麻烦，建议用 cross
  ```
  `cross.sh` 在回退模式下**不会因单个目标失败而中断**，会跳过并汇总成功 / 跳过数量。

## 产物

归档统一输出到 `dist/`，命名 `rustto-client-<version>-<target>.{tar.gz,zip}`
（已在 `.gitignore` 忽略）。

## 单工具构建脚本

每个工具 crate 目录下还有各自的 `build.sh`（构建 + 单测该 crate）：

```bash
./crates/backup-file/build.sh
./crates/backup-mysql/build.sh
```
