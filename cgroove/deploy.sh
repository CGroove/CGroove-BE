#!/bin/bash
set -euo pipefail

# ─────────────────────────────────────────────
# C.Groove 배포 스크립트 (Oracle Cloud)
# 환경변수로 오버라이드 가능:
#   CGROOVE_SERVER_IP / CGROOVE_SERVER_USER / CGROOVE_SSH_KEY
# ─────────────────────────────────────────────
SERVER_IP="${CGROOVE_SERVER_IP:-152.67.210.66}"
SERVER_USER="${CGROOVE_SERVER_USER:-ubuntu}"
SSH_KEY="${CGROOVE_SSH_KEY:-$HOME/.ssh/cgroove-oci.key}"
REMOTE_DIR="/home/${SERVER_USER}/app"
SERVICE="cgroove"

SSH="ssh -i ${SSH_KEY} -o BatchMode=yes ${SERVER_USER}@${SERVER_IP}"

if [ ! -f "$SSH_KEY" ]; then
  echo "❌ SSH 키를 찾을 수 없습니다: $SSH_KEY"
  exit 1
fi

echo "🚀 빌드 시작! (Build)"
./gradlew bootJar

JAR_PATH=$(ls -t build/libs/*.jar | grep -v plain | head -1)
if [ -z "$JAR_PATH" ]; then
  echo "❌ 빌드 결과 jar를 찾을 수 없습니다"
  exit 1
fi
echo "   → ${JAR_PATH} ($(du -h "$JAR_PATH" | cut -f1))"

echo "📦 파일 보내는 중... (Upload)"
# 업로드 도중 서비스가 죽지 않도록 임시 파일로 올린 뒤 교체
scp -i "$SSH_KEY" -o BatchMode=yes "$JAR_PATH" \
    "${SERVER_USER}@${SERVER_IP}:${REMOTE_DIR}/cgroove.jar.new"

echo "🔥 서버 재시작! (Restart)"
$SSH "set -e
  mv ${REMOTE_DIR}/cgroove.jar.new ${REMOTE_DIR}/cgroove.jar
  sudo systemctl restart ${SERVICE}
"

echo "⏳ 기동 확인 중..."
for i in $(seq 1 30); do
  if curl -sf --max-time 3 "http://${SERVER_IP}:8080/actuator/health" >/dev/null 2>&1; then
    echo "✅ 배포 완료! 수고하셨습니다!"
    curl -s "http://${SERVER_IP}:8080/actuator/health"; echo
    exit 0
  fi
  sleep 3
done

echo "❌ 기동 확인 실패 — 로그를 확인하세요:"
echo "   ssh -i ${SSH_KEY} ${SERVER_USER}@${SERVER_IP} 'sudo journalctl -u ${SERVICE} -n 80 --no-pager'"
$SSH "sudo journalctl -u ${SERVICE} -n 40 --no-pager" || true
exit 1
