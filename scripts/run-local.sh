#!/usr/bin/env bash
# 本地一键起栈 + 启动应用，验证 /actuator/health 通过。
# 用法：bash scripts/run-local.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "▶ 启动本地依赖栈（PostgreSQL）"
docker compose -f docker-compose.local.yml up -d

echo "▶ 等待依赖就绪 ..."
for i in $(seq 1 30); do
  if docker compose -f docker-compose.local.yml exec -T postgres pg_isready -U security >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

echo "▶ 构建并启动应用（local profile）"
mvn -B -ntp -DskipTests spring-boot:run -Dspring-boot.run.profiles=local
