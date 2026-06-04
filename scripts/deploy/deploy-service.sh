#!/usr/bin/env bash
set -euo pipefail

# 首次部署建议流程：
#   sudo ./scripts/deploy/deploy-service.sh init
#   sudo cp scripts/deploy/aaf-service.supervisor.conf /etc/supervisor/conf.d/aaf-service.conf
#   sudo supervisorctl reread
#   sudo supervisorctl update
#   sudo SUPERVISOR_PROGRAM=aaf-service ./scripts/deploy/deploy-service.sh build-release
#
# 日常发布：
#   sudo SUPERVISOR_PROGRAM=aaf-service ./scripts/deploy/deploy-service.sh release
#
# 如服务器不安装 Supervisor，可直接使用 start/restart/release 的 nohup fallback。

# 应用与部署目录。APP_NAME 同时决定运行 jar 名称：${BASE_PATH}/${APP_NAME}.jar。
APP_NAME=${APP_NAME:-aaf-service}
APP_PORT=${APP_PORT:-8080}
BASE_PATH=${BASE_PATH:-/opt/aaf/service}
SOURCE_JAR=${SOURCE_JAR:-}

# Linux 服务用户。Supervisor 模板默认使用 aaf，正式环境不建议用 root 运行应用。
RUN_USER=${RUN_USER:-aaf}
RUN_GROUP=${RUN_GROUP:-${RUN_USER}}
CREATE_RUN_USER=${CREATE_RUN_USER:-true}

# Spring 运行参数与 JVM 参数。JAVA_OPS / APP_ARGS 可由环境变量整体覆盖。
SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-prod}
JAVA_BIN=${JAVA_BIN:-java}
HEAP_DUMP_DIR=${HEAP_DUMP_DIR:-"${BASE_PATH}/heap-dumps"}
LOG_DIR=${LOG_DIR:-"${BASE_PATH}/logs"}
JAVA_OPS=${JAVA_OPS:-"-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:InitialRAMPercentage=25 -XX:MaxRAMPercentage=75 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=${HEAP_DUMP_DIR} -XX:+ExitOnOutOfMemoryError -Xlog:gc*:${LOG_DIR}/gc.log:time,tags:filecount=5,filesize=50m -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai --enable-preview"}
APP_ARGS=${APP_ARGS:-"--server.port=${APP_PORT} --spring.profiles.active=${SPRING_PROFILES_ACTIVE}"}
HEALTH_CHECK_URL=${HEALTH_CHECK_URL:-"http://127.0.0.1:${APP_PORT}/actuator/health"}

# 传入 Supervisor program 名称时，脚本通过 supervisorctl 管理进程；为空时使用 nohup fallback。
SUPERVISOR_PROGRAM=${SUPERVISOR_PROGRAM:-}

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# 默认通过 Nx 构建 service；SOURCE_JAR 为空时，从 JAR_PATTERN 中取最新 jar 作为发布包。
BUILD_CWD=${BUILD_CWD:-"${REPO_ROOT}"}
BUILD_COMMAND=${BUILD_COMMAND:-"pnpm nx build service"}
JAR_PATTERN=${JAR_PATTERN:-"${REPO_ROOT}/apps/service/aaf-api/target/aaf-api-*.jar"}
CURRENT_JAR="${BASE_PATH}/${APP_NAME}.jar"
PID_FILE="${BASE_PATH}/${APP_NAME}.pid"
BACKUP_DIR="${BASE_PATH}/backup"

resolve_source_jar() {
  if [[ -n "${SOURCE_JAR}" ]]; then
    echo "${SOURCE_JAR}"
    return
  fi

  local jar
  jar=$(ls -t ${JAR_PATTERN} 2>/dev/null | head -n 1 || true)
  if [[ -z "${jar}" ]]; then
    echo "未找到构建产物：${JAR_PATTERN}" >&2
    exit 1
  fi
  echo "${jar}"
}

ensure_dirs() {
  mkdir -p "${BASE_PATH}" "${LOG_DIR}" "${BACKUP_DIR}" "${HEAP_DUMP_DIR}"
}

ensure_run_user() {
  if [[ "${CREATE_RUN_USER}" != "true" ]]; then
    return
  fi

  if id "${RUN_USER}" >/dev/null 2>&1; then
    return
  fi

  if ! command -v useradd >/dev/null 2>&1; then
    echo "未找到 useradd，无法创建运行用户：${RUN_USER}" >&2
    exit 1
  fi

  useradd -r -s /usr/sbin/nologin "${RUN_USER}" 2>/dev/null \
    || useradd -r -s /sbin/nologin "${RUN_USER}"
}

fix_permissions() {
  if id "${RUN_USER}" >/dev/null 2>&1; then
    chown -R "${RUN_USER}:${RUN_GROUP}" "${BASE_PATH}"
  fi
}

init_runtime() {
  ensure_run_user
  ensure_dirs
  fix_permissions
  echo "初始化完成：BASE_PATH=${BASE_PATH}, RUN_USER=${RUN_USER}"
}

build_jar() {
  (
    cd "${BUILD_CWD}"
    ${BUILD_COMMAND}
  )
}

deploy_jar() {
  ensure_dirs
  local source_jar
  source_jar=$(resolve_source_jar)
  if [[ ! -f "${source_jar}" ]]; then
    echo "部署包不存在：${source_jar}" >&2
    exit 1
  fi

  if [[ -f "${CURRENT_JAR}" ]]; then
    cp "${CURRENT_JAR}" "${BACKUP_DIR}/${APP_NAME}-$(date +%Y%m%d%H%M%S).jar"
  fi

  cp "${source_jar}" "${CURRENT_JAR}"
  fix_permissions
  echo "已部署：${source_jar} -> ${CURRENT_JAR}"
}

start_app() {
  ensure_dirs
  if [[ ! -f "${CURRENT_JAR}" ]]; then
    echo "当前部署包不存在：${CURRENT_JAR}，请先执行 deploy" >&2
    exit 1
  fi

  if [[ -n "${SUPERVISOR_PROGRAM}" ]]; then
    supervisorctl start "${SUPERVISOR_PROGRAM}"
    return
  fi

  if [[ -f "${PID_FILE}" ]] && kill -0 "$(cat "${PID_FILE}")" 2>/dev/null; then
    echo "${APP_NAME} 已在运行，PID=$(cat "${PID_FILE}")"
    return
  fi

  nohup "${JAVA_BIN}" ${JAVA_OPS} \
    -jar "${CURRENT_JAR}" \
    ${APP_ARGS} \
    >> "${LOG_DIR}/console.log" 2>&1 &

  echo $! > "${PID_FILE}"
  echo "已启动 ${APP_NAME}，PID=$(cat "${PID_FILE}")，profile=${SPRING_PROFILES_ACTIVE}"
}

stop_app() {
  if [[ -n "${SUPERVISOR_PROGRAM}" ]]; then
    supervisorctl stop "${SUPERVISOR_PROGRAM}" || true
    return
  fi

  if [[ ! -f "${PID_FILE}" ]]; then
    echo "${APP_NAME} 未运行：缺少 ${PID_FILE}"
    return
  fi

  local pid
  pid=$(cat "${PID_FILE}")
  if kill -0 "${pid}" 2>/dev/null; then
    kill -15 "${pid}"
    for _ in {1..60}; do
      if ! kill -0 "${pid}" 2>/dev/null; then
        rm -f "${PID_FILE}"
        echo "${APP_NAME} 已停止"
        return
      fi
      sleep 1
    done
    echo "优雅停止超时，强制停止 PID=${pid}"
    kill -9 "${pid}" || true
  fi
  rm -f "${PID_FILE}"
}

status_app() {
  if [[ -n "${SUPERVISOR_PROGRAM}" ]]; then
    supervisorctl status "${SUPERVISOR_PROGRAM}"
    return
  fi

  if [[ -f "${PID_FILE}" ]] && kill -0 "$(cat "${PID_FILE}")" 2>/dev/null; then
    echo "${APP_NAME} RUNNING，PID=$(cat "${PID_FILE}")"
  else
    echo "${APP_NAME} STOPPED"
  fi
}

health_check() {
  if [[ -z "${HEALTH_CHECK_URL}" ]]; then
    return
  fi

  echo "健康检查：${HEALTH_CHECK_URL}"
  for _ in {1..120}; do
    local status
    status=$(curl -m 5 -o /dev/null -s -w "%{http_code}" "${HEALTH_CHECK_URL}" || echo "000")
    if [[ "${status}" == "200" ]]; then
      echo "健康检查通过"
      return
    fi
    sleep 1
  done
  echo "健康检查未通过" >&2
  exit 1
}

case "${1:-}" in
  init)
    init_runtime
    ;;
  build)
    build_jar
    ;;
  deploy)
    deploy_jar
    ;;
  start)
    start_app
    health_check
    ;;
  stop)
    stop_app
    ;;
  restart)
    stop_app
    start_app
    health_check
    ;;
  release)
    deploy_jar
    stop_app
    start_app
    health_check
    ;;
  build-release)
    build_jar
    deploy_jar
    stop_app
    start_app
    health_check
    ;;
  status)
    status_app
    ;;
  *)
    echo "用法：$0 {init|build|deploy|start|stop|restart|release|build-release|status}"
    exit 1
    ;;
esac
