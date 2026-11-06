# 배포 가이드 (서버 1대 + Docker Compose)

한 대의 서버에 `Caddy(웹·HTTPS)` → `API` → `PostgreSQL+PostGIS`를 Docker Compose로 올린다. 사진은 서버 디스크가 아니라 S3 호환 저장소(Cloudflare R2, AWS S3 등)에 둔다.

```
브라우저 ─HTTPS→ Caddy ─┬─ /  (프론트 정적 파일)
                        └─ /api, /oauth2, /login/oauth2 → API(Spring Boot) → DB
                                                                  └→ 사진 저장소(S3 호환)
```

## 0. 준비물

| 준비 | 비고 |
|---|---|
| 서버 1대 | Ubuntu 22.04/24.04, 메모리 2GB 이상 권장 (사진 변환과 PostGIS 때문에 1GB는 빠듯), 서울 리전 |
| 도메인 | DNS A 레코드를 서버 IP로 (HTTPS 인증서 자동 발급에 필요) |
| 사진 저장소 | R2 또는 S3 버킷 1개 + 접근 키 (버킷을 공개로 열 필요 없음 — 사진은 API가 대신 내려준다) |
| 카카오 | JS 키(공개용)에 **이 도메인을 웹 플랫폼으로 등록**, REST 키(서버 전용), 로그인 리다이렉트 URI `https://도메인/login/oauth2/code/kakao` |
| 메일 서비스 | (이메일 가입·비밀번호 재설정을 쓸 때) SMTP 서비스 1개 — 아래 "메일 설정" 참고. 없으면 소셜 로그인만 된다 |
| 네이버·구글 | 콜백 주소 `https://도메인/login/oauth2/code/naver`, `…/google` 등록 (쓸 때만) |

## 1. 서버 기본 보안 (처음 한 번)

```bash
# 일반 사용자를 만들고 SSH 키로만 접속하게 한다 (root 로그인·비밀번호 로그인 끄기)
adduser deploy && usermod -aG sudo deploy
# /etc/ssh/sshd_config:  PermitRootLogin no / PasswordAuthentication no  → sudo systemctl restart ssh

# 방화벽: SSH, HTTP, HTTPS 만 연다 (DB 포트는 아예 열리지 않는다)
sudo ufw allow OpenSSH && sudo ufw allow 80/tcp && sudo ufw allow 443 && sudo ufw enable

# 자동 보안 업데이트
sudo apt-get install -y unattended-upgrades

# Docker
curl -fsSL https://get.docker.com | sudo sh && sudo usermod -aG docker deploy
```

## 2. 올리기

```bash
git clone https://github.com/Newbress/BigBread.git && cd BigBread
git checkout main                      # 운영은 main 만 쓴다
cp .env.production.example .env && chmod 600 .env
nano .env                              # DOMAIN, APP_BASE_URL, DB_PASSWORD, 키들, 사진 저장소를 채운다
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
docker compose -f docker-compose.prod.yml ps        # api 가 healthy 가 될 때까지 1~2분
docker compose -f docker-compose.prod.yml logs -f api
```

- 처음 시작할 때 DB 마이그레이션이 자동 적용된다 (샘플 데이터는 들어가지 않는다).
- **설정이 위험하면 API가 시작하지 않고 이유를 한꺼번에 알려 준다** (http 주소, 약한 DB 비밀번호, 보안 쿠키 꺼짐, dev 프로필 등). 로그에서 `운영 설정이 안전하지 않아` 를 찾으면 된다.
- 접속: `https://도메인` (인증서는 처음 접속 때 자동 발급, DNS가 맞아야 한다).

## 3. 첫 데이터

```bash
# 빵집 데이터: 카카오 로컬 API 로 대전 5개 구를 모은다 (일회성, 몇 분 걸린다)
docker compose -f docker-compose.prod.yml run --rm collector --collector.job=kakao-bakeries --collector.dry-run=true   # 미리보기
docker compose -f docker-compose.prod.yml run --rm collector --collector.job=kakao-bakeries                              # 저장

# 첫 관리자: 사이트에서 가입(또는 소셜 로그인)한 뒤 DB 에서 한 줄로 지정한다
docker compose -f docker-compose.prod.yml exec db psql -U bigbread -d bigbread -c "SELECT id, nickname FROM app_user ORDER BY id DESC LIMIT 5"
docker compose -f docker-compose.prod.yml exec db psql -U bigbread -d bigbread -c "UPDATE app_user SET role='ADMIN' WHERE id=여기에번호"
```

## 4. 업데이트

```bash
cd ~/BigBread && git pull
docker compose -f docker-compose.prod.yml --env-file .env up -d --build   # 바뀐 것만 다시 만들어 교체한다
```

DB 구조가 바뀌는 업데이트는 API가 시작할 때 자동으로 적용된다. 되돌릴 일을 대비해 **업데이트 전에 백업**을 한 번 돌려 두면 좋다.

## 5. 백업과 복구

```bash
# 백업 (압축 덤프, 14일 보관). 매일 새벽 3시에 돌리려면 crontab -e 에:
#   0 3 * * *  /home/deploy/BigBread/deploy/backup.sh >> /var/log/bigbread-backup.log 2>&1
./deploy/backup.sh
# 서버 밖에도 한 벌 두려면 rclone 을 설정하고 BACKUP_RCLONE_REMOTE=r2:버킷이름 을 준다.

# 복구 (새 서버나 빈 DB 에): 서비스를 멈추고 덤프를 넣는다
docker compose -f docker-compose.prod.yml stop api web
gunzip -c /var/backups/bigbread/bigbread-날짜.sql.gz | docker compose -f docker-compose.prod.yml exec -T db psql -U bigbread -d bigbread
docker compose -f docker-compose.prod.yml start api web
```

> 복구를 **한 번은 실제로 해 보세요.** 백업이 쓸 수 있는지는 복구해 봐야 압니다.
> 사진은 S3 호환 저장소에 있어서 DB 백업과 별개입니다 (저장소 자체의 버전 관리·보관 설정을 확인하세요).

## 6. 점검

| 할 일 | 방법 |
|---|---|
| 서버가 살아 있나 | `docker compose -f docker-compose.prod.yml ps`, 무료 가동 감시(UptimeRobot 등)에 `https://도메인/` 등록 |
| 로그 | `docker compose -f docker-compose.prod.yml logs --tail 200 api` (파일은 자동으로 10MB×5개까지만 남는다) |
| 디스크·메모리 | `df -h`, `free -m` (디스크가 차면 DB가 멈춘다) |
| 사진 변환 | 데스크톱에서 `.heic` 업로드 시험 (아이폰 사진은 브라우저가 변환한다) |

## 메일 설정 (이메일 가입·비밀번호 재설정)

SMTP 를 쓰는 서비스면 어디든 된다. 무료 한도로 시작하기 쉬운 순서:

1. **Brevo** (하루 300통 무료): 가입 → SMTP & API → SMTP 키 생성. `MAIL_HOST=smtp-relay.brevo.com`, `MAIL_PORT=587`, `MAIL_USERNAME`=로그인 이메일(표시된 SMTP 로그인), `MAIL_PASSWORD`=SMTP 키.
2. **AWS SES**: 서울 리전, 도메인 인증 후 SMTP 자격 증명 생성. `MAIL_HOST=email-smtp.ap-northeast-2.amazonaws.com`.

공통 순서:

1. 메일 서비스에서 **내 도메인을 인증**한다 (서비스가 알려 주는 SPF·DKIM DNS 레코드를 도메인 DNS에 추가). 인증 안 한 주소로 보내면 스팸함에 가거나 거절된다.
2. `MAIL_FROM` 을 그 도메인의 주소(예: `no-reply@도메인`)로 정한다.
3. `.env` 에 `MAIL_*` 값을 채우고 `docker compose -f docker-compose.prod.yml up -d` 로 api 를 다시 띄운다. 키는 `.env` 에만 둔다 (커밋·채팅 금지).
4. 확인: 이메일로 가입해 인증 메일이 오는지 본다. 로그에는 받는 사람 일부(`a**@…`)와 실패 이유만 남고 링크는 남지 않는다.

`MAIL_HOST` 가 있는데 `MAIL_FROM` 이 비었거나 `MAIL_SECURITY=NONE` 이면 서버가 시작하지 않는다. 메일 서버가 느리거나 죽으면 가입은 "잠시 후 다시 시도" 안내와 함께 취소되고 다시 시도할 수 있다.

## 7. 아직 안 된 것 (다음 작업)

- GitHub Actions 자동 배포: 지금은 서버에서 `git pull` 후 위 명령으로 올린다.
- 이용약관·개인정보 처리방침 페이지.
- Content-Security-Policy 같은 추가 보안 헤더 (카카오 지도 SDK 도메인을 확인한 뒤 적용).
