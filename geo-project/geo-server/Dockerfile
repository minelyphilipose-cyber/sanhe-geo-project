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

WORKDIR /build

# 先拷 pom,利用 Docker layer cache
COPY pom.xml .
RUN mvn -B dependency:go-offline

# 再拷源码并打包
COPY src ./src
RUN mvn -B clean package -DskipTests

# 拷出 Playwright 依赖,并在构建阶段预装 Chromium
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
# 如遇网络问题可取消下行注释切换镜像源
# ENV PLAYWRIGHT_DOWNLOAD_HOST=https://playwright.azureedge.net

RUN mvn -B dependency:copy-dependencies \
        -DincludeArtifactIds=playwright \
        -DoutputDirectory=/build/pw-deps && \
    mkdir -p ${PLAYWRIGHT_BROWSERS_PATH} && \
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
# - 基础: ca-certificates, tzdata

RUN apt-get update && apt-get install -y --no-install-recommends \
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
    fonts-noto-cjk \
    fonts-noto-cjk-extra \
    fonts-noto-color-emoji \
    fonts-liberation \
    fonts-wqy-zenhei \
    ca-certificates \
    tzdata \
    && rm -rf /var/lib/apt/lists/*

ENV TZ=Asia/Shanghai
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

WORKDIR /app

# 从 builder 阶段拷贝预装好的 Playwright 浏览器和应用产物
COPY --from=builder /ms-playwright /ms-playwright
COPY --from=builder /build/target/geo-server.jar /app/app.jar

EXPOSE 8080

# JVM 参数说明:
# -XX:+UseContainerSupport      读取容器 cgroup 限制(JDK 17 默认开启,显式声明)
# -XX:MaxRAMPercentage=70.0     最大堆内存 70%,留 30% 给 Chromium 等进程
# -XX:InitialRAMPercentage=40.0 初始堆 40%,避免启动就抢内存
# -XX:+ExitOnOutOfMemoryError   OOM 直接退出让容器重启,避免卡死
# -Djava.security.egd=...       加速 SecureRandom 初始化

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=70.0", "-XX:InitialRAMPercentage=40.0", "-XX:+ExitOnOutOfMemoryError", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]