# restto-client 容器镜像。
#
# 使用 scripts/cross.sh 产出的静态二进制（CGO_ENABLED=0，无任何动态库依赖）。
# 最终镜像 FROM scratch，含主程序与 clis/ 下全部第三方 CLI（装入 PATH，
# runner 的 PATH 兜底即可发现，无需 DATA_DIR/clis）。
#
# 先交叉编译产出归档（在仓库 processor/ 下）：
#   ./scripts/cross.sh linux/amd64 linux/arm64
#
# 单架构构建（amd64）：
#   docker build -t restto-client:0.1.0 .
#   docker run --rm restto-client:0.1.0 --help
#
# 多架构构建并推送（buildx）：
#   docker buildx build --platform linux/amd64,linux/arm64 -t restto-client:0.1.0 --push .
#
# 注：restto-client 走自定义 TCP 协议直连 Manager，无 HTTPS，因此 scratch 不需要 CA 证书。
#     如未来引入 TLS，改用 FROM alpine 或在 scratch 里追加 ca-certificates。
# 注：backupmysql CLI 需要系统 mysqldump；scratch 镜像不含该工具，MySQL 备份
#     场景请改用含 mysqldump 的基础镜像或宿主机部署。

# ---- 解压阶段：用 alpine 的 tar 把对应架构的 tar.gz 解出来 ----
FROM alpine:latest AS unpack
ARG TARGETARCH=amd64
COPY dist/ /tmp/dist/
WORKDIR /out
RUN case "$TARGETARCH" in \
      amd64) arch=x86_64 ;; \
      arm64) arch=aarch64 ;; \
      *) echo "unsupported TARGETARCH=$TARGETARCH" >&2; exit 1 ;; \
      esac && \
      tar -xzf /tmp/dist/restto-client-*-${arch}-unknown-linux-musl.tar.gz -C /out && \
      test -x /out/restto-client && \
      test -x /out/clis/backupfile && \
      test -x /out/clis/backupmysql

# ---- 运行阶段：scratch 只装静态二进制 ----
FROM scratch
LABEL org.opencontainers.image.title="restto-client" \
      org.opencontainers.image.source="https://github.com/restto/restto"
COPY --from=unpack /out/restto-client /usr/local/bin/restto-client
COPY --from=unpack /out/clis/ /usr/local/bin/
ENTRYPOINT ["/usr/local/bin/restto-client"]
