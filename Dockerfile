# syntax=docker/dockerfile:1
# 多阶段构建：build 阶段经 Maven 坐标解析公共依赖并打可执行 jar，run 阶段仅装 JRE + jar。
#
# 公共依赖在 GitHub Packages，build 阶段需凭据：用 BuildKit secret 挂载 Maven settings.xml，
# 避免 token 落入镜像层。示例：
#   DOCKER_BUILDKIT=1 docker build --secret id=maven_settings,src=$HOME/.m2/settings.xml -t hashmatrix-security:local .

# ---- build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN --mount=type=secret,id=maven_settings,target=/root/.m2/settings.xml \
    --mount=type=cache,target=/root/.m2/repository \
    mvn -B -ntp -DskipTests clean package

# ---- run ----
FROM eclipse-temurin:17-jre-alpine AS run
WORKDIR /app
# 非 root 运行
RUN addgroup -S app && adduser -S app -G app
# 取 exec 分类器的可执行 fat-jar（瘦 jar 为主制品，见 pom 的 repackage classifier 注释）
COPY --from=build /workspace/target/hashmatrix-security-*-exec.jar app.jar
USER app
# 业务端口 8083 / 管理(actuator)端口 9083（平台基线，可经 SERVER_PORT/MANAGEMENT_SERVER_PORT 覆盖）
EXPOSE 8083 9083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
