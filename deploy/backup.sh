#!/usr/bin/env bash
# DB 백업: 하루 한 번 cron 으로 돌린다. 압축된 덤프를 BACKUP_DIR 에 만들고 오래된 것은 지운다.
#   예) 매일 새벽 3시:  0 3 * * *  /home/deploy/bigbread/deploy/backup.sh >> /var/log/bigbread-backup.log 2>&1
# 선택: BACKUP_RCLONE_REMOTE (예: r2:bigbread-backups) 를 정하면 rclone 으로 밖에도 복사한다.
#       서버 디스크만 믿지 말고 다른 곳에 한 벌 더 두는 것을 권한다.
set -euo pipefail

cd "$(dirname "$0")/.."
COMPOSE="docker compose -f docker-compose.prod.yml --env-file .env"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/bigbread}"
KEEP_DAYS="${KEEP_DAYS:-14}"

set -a; . ./.env; set +a
mkdir -p "$BACKUP_DIR"
FILE="$BACKUP_DIR/bigbread-$(date +%Y%m%d-%H%M%S).sql.gz"

# 덤프가 중간에 실패하면 빈 파일이 남지 않게 임시 파일로 받은 뒤 이름을 바꾼다.
$COMPOSE exec -T db pg_dump -U "${DB_USERNAME:-bigbread}" -d "${DB_NAME:-bigbread}" --no-owner | gzip > "$FILE.tmp"
mv "$FILE.tmp" "$FILE"
echo "$(date -Is) 백업 완료: $FILE ($(du -h "$FILE" | cut -f1))"

if [ -n "${BACKUP_RCLONE_REMOTE:-}" ]; then
  rclone copy "$FILE" "$BACKUP_RCLONE_REMOTE" && echo "$(date -Is) 외부 복사 완료: $BACKUP_RCLONE_REMOTE"
fi

find "$BACKUP_DIR" -name 'bigbread-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
