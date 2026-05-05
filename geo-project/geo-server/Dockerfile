# ============================================================
# GEO Server Dockerfile
#
# 重要约束:
# - Playwright Java 版本变更后必须重新 build 镜像
# - 当前对应 playwright-java 1.53.0
# - 推荐容器内存 >= 3GB (JVM + Chromium 同时运行)
# ============================================================

# ============ Build Stage ============
FROM maven:3.9-eclipse-temurin-17 AS builder

ARG MAVEN_MIRROR=https://maven.aliyun.com/repository/public
ARG PLAYWRIGHT_DOWNLOAD_HOST=

ENV PLAYWRIGHT_BROWSERS_PATH=/opt/playwright-browsers

WORKDIR /build

RUN set -eux; \
    if [ -n "${MAVEN_MIRROR}" ]; then \
      printf '%s\n' \
        '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"' \
        '          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"' \
        '          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">' \
        '  <mirrors>' \
        '    <mirror>' \
        '      <id>build-mirror</id>' \
        '      <mirrorOf>*</mirrorOf>' \
        "      <url>${MAVEN_MIRROR}</url>" \
        '    </mirror>' \
        '  </mirrors>' \
        '</settings>' > /tmp/settings.xml; \
    else \
      printf '%s\n' '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"/>' > /tmp/settings.xml; \
    fi

# 先拷 pom,利用 Docker layer cache
COPY pom.xml .
RUN mvn -s /tmp/settings.xml dependency:go-offline -B

# 再拷源码并打包
COPY src/ ./src/
RUN mvn -s /tmp/settings.xml package -DskipTests -B

# 拷出 Playwright 依赖,并在构建阶段预装 Chromium
RUN set -eux; \
    if [ -n "${PLAYWRIGHT_DOWNLOAD_HOST}" ]; then export PLAYWRIGHT_DOWNLOAD_HOST="${PLAYWRIGHT_DOWNLOAD_HOST}"; fi; \
    mvn -s /tmp/settings.xml dependency:copy-dependencies -B \
        -DincludeGroupIds=com.microsoft.playwright \
        -DoutputDirectory=/build/pw-deps; \
    mkdir -p ${PLAYWRIGHT_BROWSERS_PATH}; \
    java -cp "/build/pw-deps/*" com.microsoft.playwright.CLI install chromium

# ============ Runtime Stage ============
FROM eclipse-temurin:17-jre-jammy

# 系统依赖分组说明(说明写在 RUN 外):
# - Chromium 核心运行依赖: libnss3, libnspr4, libatk-bridge2.0-0 等
# - D-Bus 与 GLib: libdbus-1-3, libglib2.0-0
# - X11 相关: libxcomposite1, libxdamage1, libxrandr2, libxss1, libxkbcommon0 等
# - 图形与字体渲染: libcairo2, libpango-1.0-0, libpangocairo-1.0-0, libgtk-3-0
# - 音频(Chromium 依赖): libasound2
# - 辅助技术: libatspi2.0-0
# - 中文字体: fonts-noto-cjk 系列
# - 字体 fallback: fonts-liberation, fonts-wqy-zenhei
# - 基础与健康检查: ca-certificates, tzdata, curl

ENV TZ=Asia/Shanghai
ENV PLAYWRIGHT_BROWSERS_PATH=/opt/playwright-browsers
# Chromium runs as a non-root user in containers; keep sandboxing disabled unless the host grants extra capabilities.
ENV PLAYWRIGHT_CHROMIUM_LAUNCH_ARGS="--no-sandbox --disable-dev-shm-usage"

RUN set -eux; \
    sed -i 's|http://archive.ubuntu.com|http://mirrors.aliyun.com|g; s|http://security.ubuntu.com|http://mirrors.aliyun.com|g' /etc/apt/sources.list; \
    apt-get update; \
    apt-get install -y --no-install-recommends \
    libnss3 \
    libnspr4 \
    libatk-bridge2.0-0 \
    libatspi2.0-0 \
    libdbus-1-3 \
    libglib2.0-0 \
    libdrm2 \
    libgbm1 \
    libasound2 \
    libpangocairo-1.0-0 \
    libpango-1.0-0 \
    libcairo2 \
    libgtk-3-0 \
    libxcomposite1 \
    libxdamage1 \
    libxrandr2 \
    libxfixes3 \
    libxkbcommon0 \
    libxshmfence1 \
    libxcb1 \
    libx11-6 \
    libxss1 \
    fontconfig \
    fonts-noto-cjk \
    fonts-noto-cjk-extra \
    fonts-noto-color-emoji \
    fonts-liberation \
    fonts-wqy-zenhei \
    curl \
    ca-certificates \
    tzdata; \
    fc-cache -fv; \
    fc-list :lang=zh-cn | head -5; \
    fc-list :lang=zh-cn | grep -q .; \
    groupadd --gid 1000 appuser; \
    useradd --uid 1000 --gid 1000 --create-home --shell /usr/sbin/nologin appuser; \
    mkdir -p /app/logs /app/data/presale-exports "${PLAYWRIGHT_BROWSERS_PATH}"; \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 从 builder 阶段拷贝预装好的 Playwright 浏览器和应用产物
COPY --from=builder /opt/playwright-browsers /opt/playwright-browsers
COPY --from=builder /build/target/geo-server.jar /app/geo-server.jar

RUN chown -R appuser:appuser /app /opt/playwright-browsers

EXPOSE 8080

USER appuser

ENTRYPOINT ["java", "-jar", "/app/geo-server.jar"]
