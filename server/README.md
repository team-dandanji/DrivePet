# DrivePet 서버

차량 앱의 **기기 인증 · 카카오 QR 로그인 · 기록 동기화 · 날씨 프록시 · 주변 정비소 검색**을 맡는 서버입니다. 점수·EXP는 계산하지 않습니다(앱이 기기 안에서 계산).
계약: 팀 노션 「DrivePet API 명세서」 (API 9개). 회원 탈퇴 `DELETE /api/v1/account`는 명세 이후 추가(아래 "보관·파기"), 주변 정비소 `GET /api/v1/places?lat=&lng=`(기기 토큰 필요, 카카오 로컬 키워드 검색, 가까운 순 최대 3곳, 카카오 실패는 502 `EXTERNAL_API_FAILED`, 1분에 기기당 10회·서버 전체 300회를 넘으면 429 `RATE_LIMITED`)도 추가.

Spring Boot 4.0 · Kotlin · JDK 21 · PostgreSQL 17 · Flyway · JWT(HS256)

## 실행

### Docker만 있으면 (팀원용)

```bash
cd server
docker compose up --build
```

- 서버 `http://localhost:8080/api/v1`, DB `localhost:5432` (데이터는 `drivepet-db` 볼륨에 남음, 지우려면 `docker compose down -v`)
- 기본 프로필 `local-fake`: 외부 키 없이 카카오·기상청·에어코리아를 **가짜 응답**으로 씁니다.
- 에뮬레이터(차량 앱)에서 호스트 서버 주소: `http://10.0.2.2:8080/api/v1`

### IDE·JDK로 (개발용)

```bash
cd server
docker compose up -d db
./gradlew bootRun
```

### 테스트

```bash
cd server
./gradlew test
```

Docker가 켜져 있어야 합니다(Testcontainers가 실제 PostgreSQL을 띄움).

## 프로필과 환경변수

`.env.example`을 `.env`로 복사해 값을 넣습니다. **`.env`는 커밋하지 않습니다**(`.gitignore`에 있음).

| 변수 | 설명 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `local-fake`(기본) 또는 `local-live`(실제 외부 API) |
| `DRIVEPET_JWT_SECRET` | JWT 서명 키, 32바이트 이상 무작위. `local-live`에서는 필수 |
| `DRIVEPET_PUBLIC_BASE_URL` | 폰에서 접속되는 서버 주소(HTTPS 터널). 카카오 Redirect URI = 이 주소 + `/api/v1/auth/kakao/callback` |
| `KAKAO_REST_API_KEY`, `KAKAO_CLIENT_SECRET` | 카카오 개발자 앱 (Client Secret을 켠 경우만 두 번째 값). 같은 REST API 키를 카카오 로컬(`/places` 주변 정비소 검색)에도 쓰므로 개발자 콘솔에서 카카오맵(로컬) API 사용을 켜야 함 |
| `KAKAO_ADMIN_KEY` | 카카오 Admin 키. 회원 탈퇴 때 카카오 쪽 앱 연결을 끊는 데만 씀. 비우면 DB만 파기 |
| `DATA_GO_KR_SERVICE_KEY` | 공공데이터포털 일반 인증키(Decoding). 기상청 단기예보·특보, 에어코리아 대기오염·측정소정보 공통 |

`local-live`는 키가 없으면 **시작하지 않습니다**(가짜로 자동 전환하지 않음).

## 카카오 QR 로그인 시험

- **가짜 모드(키 없이):** `POST /auth/kakao/qr-sessions`의 `qrUrl`을 브라우저로 열면 "가짜 사용자 A/B" 선택 페이지가 나옵니다. 고르면 연결되고, 차량 앱은 `GET /auth/kakao/qr-sessions/{id}`에서 `LINKED`와 새 토큰을 받습니다. 이 페이지는 `local-fake`에서만 동작하며 공개 API가 아닙니다.
- **실제 모드:** 폰에서 QR을 찍어야 하므로 HTTPS 터널이 필요합니다.
  1. 터널을 엽니다(예: `cloudflared tunnel --url http://localhost:8080`).
  2. 나온 주소를 `DRIVEPET_PUBLIC_BASE_URL`에 넣고 서버를 다시 시작합니다.
  3. 카카오 개발자 앱의 Redirect URI에 `{그 주소}/api/v1/auth/kakao/callback`을 등록합니다. **터널 주소가 바뀌면 2·3을 다시 합니다.**
- QR 세션은 5분 유효. 만료 직전에 연결돼도 연결 시각부터 3분 동안은 LINKED 토큰을 받을 수 있습니다.

## 클라우드 배포 (상시 서버)

- 주소: **`https://129-225-197-147.sslip.io/api/v1`**. demo·release 앱 빌드의 기본 주소이고, debug는 `10.0.2.2` 그대로입니다.
- VM: Oracle Cloud Japan Central(Osaka), `drivepet-server`(Ampere A1 1 OCPU·6GB, Ubuntu 24.04, Always Free 한도 안). 계정은 Pay As You Go라 유휴 회수 대상이 아닙니다. 예산 알림을 걸어 두세요.
- 구성: `compose.yaml` + `compose.deploy.yaml`. Caddy만 80·443을 열고, `Caddyfile`대로 sslip.io 주소의 Let's Encrypt 인증서를 자동으로 받아 `server:8080`으로 넘깁니다. DB·서버 포트는 바깥에 열지 않습니다. 컨테이너는 재부팅 뒤 자동으로 다시 뜹니다.
- 바깥에 열린 포트: Oracle 보안 목록(Default Security List for drivepet-vcn)과 VM iptables 둘 다 22·80·443만.
- 공개 IP는 임시(ephemeral) IP라 인스턴스를 **종료(terminate)** 하면 바뀝니다. 바뀌면 sslip.io 주소, 앱 `DEFAULT_SERVER_URL`, 카카오 Redirect URI를 모두 다시 맞춰야 합니다. 중지·시작으로는 바뀌지 않습니다.

접속(맥에 있는 전용 키):

```bash
ssh -i ~/.ssh/drivepet_vm ubuntu@129.225.197.147
cd ~/drivepet/server
alias dc='docker compose -f compose.yaml -f compose.deploy.yaml'
```

| 할 일 | 명령 |
|---|---|
| 상태 | `dc ps` |
| 로그 | `dc logs -f server` (Caddy 인증서 문제는 `dc logs caddy`) |
| 재시작 | `dc restart server` |
| 코드 갱신 재배포 | 맥에서 `rsync -az --exclude build --exclude .gradle --exclude .env -e "ssh -i ~/.ssh/drivepet_vm" server/ ubuntu@129.225.197.147:~/drivepet/server/` → VM에서 `dc up -d --build` |
| 비밀값 교체 | VM에서 `nano .env`(권한 600 유지) → `dc up -d` |
| 바깥에서 확인 | `curl -X POST https://129-225-197-147.sslip.io/api/v1/devices -H 'Content-Type: application/json' -d '{"deviceId":"…","deviceSecret":"32자 이상","appVersion":"1.0.0"}'` |

VM의 `.env`는 로컬 `server/.env`에서 scp로 복사한 뒤 아래만 바꿨습니다. 값은 커밋·출력하지 않습니다.

- `SPRING_PROFILES_ACTIVE=local-live`, `DRIVEPET_PUBLIC_BASE_URL=https://129-225-197-147.sslip.io`, `DRIVEPET_HOST=129-225-197-147.sslip.io`
- `DRIVEPET_JWT_SECRET`, `DB_PASSWORD`: VM에서 `openssl rand -hex`로 새로 만든 값(로컬과 다름). JWT 비밀을 바꾸면 기존 기기 토큰이 모두 무효가 되어 앱이 다시 등록합니다. `DB_PASSWORD`는 DB 볼륨을 처음 만들 때만 적용되니, 바꾸려면 DB 안에서 `ALTER USER`도 같이 해야 합니다.
- 카카오 개발자 콘솔 Redirect URI: `https://129-225-197-147.sslip.io/api/v1/auth/kakao/callback`
- `KAKAO_ADMIN_KEY`: 회원 탈퇴 때 카카오 쪽 연결 끊기용. 채팅·커밋에 남기지 않도록 맥 터미널에서 직접 넣습니다(값을 숨겨 입력받아 VM `.env`에 씀):
  `read -rs "K?Kakao Admin 키: " && echo && ssh -i ~/.ssh/drivepet_vm ubuntu@129.225.197.147 "cd ~/drivepet/server && sed -i '/^KAKAO_ADMIN_KEY=/d' .env && cat >> .env && docker compose -f compose.yaml -f compose.deploy.yaml up -d" <<< "KAKAO_ADMIN_KEY=$K"; unset K`

## 수동 확인

`requests/smoke.http` (IntelliJ HTTP Client / VS Code REST Client): 기기 등록 → 날씨 → QR → 동기화 업로드·다운로드.

## 개인정보·로그

- 날씨 요청의 위경도는 저장·로그하지 않고, 기상청 격자 단위로만 캐시합니다(10분, 서버 재시작 시 사라짐).
- 토큰·인가 코드·`deviceSecret`·서비스키는 로그와 오류 메시지에 남기지 않습니다. 갱신 토큰과 `deviceSecret`은 해시만 저장합니다.
- 서버가 저장하는 것: 카카오 고유 ID·닉네임, 기기 ID, 동기화된 프로필 상태와 기록(트립 누적거리 포함), 갱신 토큰·`deviceSecret` 해시.

### 보관·파기

- **보관 기간:** 회원 탈퇴 또는 서비스 종료 때까지. 그 전에는 지우지 않고, 그 뒤에는 바로 지웁니다. 별도 DB 백업은 두지 않습니다(만들었다면 아래 절차 때 함께 지움).
- **회원 탈퇴(즉시 삭제):** 차량 앱 「차 정비소」 탭 → 카카오 계정 → 탈퇴. 서버 API는 `DELETE /api/v1/account`(연결된 기기의 토큰으로 호출, 미연결이면 403 `KAKAO_NOT_LINKED`).
  - 한 트랜잭션으로 지우는 것: 카카오 고유 ID·닉네임(계정), 동기화된 프로필·기록·변경 로그·업로드 묶음 응답·미션 정의, 그 계정의 QR 세션과 갱신 토큰.
  - 이 계정에 연결된 **모든 차량**이 미연결로 바뀌고 기존 토큰은 무효가 됩니다. 응답으로 요청한 차량용 미연결 토큰을 줍니다.
  - 남는 것: 기기 등록(앱이 만든 무작위 기기 ID와 `deviceSecret` 해시). 계정과의 연결은 끊긴 상태로, 그 차량이 미연결로 계속 쓰는 데 필요합니다.
  - 차량 앱은 탈퇴가 성공하면 앱 안의 펫·기록·설정도 지우고 처음 화면으로 돌아갑니다(서버 주소와 기기 등록은 남김).
  - 카카오 쪽 앱 연결은 DB 파기가 끝난 뒤 `KAKAO_ADMIN_KEY`로 끊습니다(`POST /v1/user/unlink`). 키가 없거나 호출이 실패해도 DB 파기는 그대로이고 서버 로그에 경고만 남습니다(카카오 회원번호는 로그에 남기지 않음). 이때는 사용자가 카카오톡 「설정 → 카카오계정 → 연결된 서비스 관리」에서 직접 끊을 수 있습니다.
- **서비스 종료(전체 파기):**
  1. 사용자 데이터만 먼저 지우려면(서버 폴더에서): `docker compose exec -T db psql -U drivepet -d drivepet -v ON_ERROR_STOP=1 < ops/purge-all-user-data.sql`
  2. 서버와 DB 볼륨을 지웁니다: `docker compose down -v` (클라우드 VM이면 `docker compose -f compose.yaml -f compose.deploy.yaml down -v`). `drivepet-db` 볼륨이 지워지면 DB 파일이 남지 않습니다.
  3. 클라우드 VM이면 인스턴스를 종료(Terminate)하면서 부트 볼륨도 영구 삭제를 선택합니다. 서버의 `.env`(키·비밀값)도 이때 함께 사라집니다.
  4. 카카오 개발자 콘솔에서 앱을 삭제하거나 비활성화합니다(카카오 쪽 사용자 연결 정보 정리).

## 아직 확인하지 못한 것

- 실제 키 연동은 11/9에 확인했습니다(`local-live`: 기상청 실황·예보·특보와 에어코리아 실데이터 응답, 터널을 거친 실제 카카오 로그인으로 LINKED·닉네임 수신). 다만 에어코리아 TM 좌표는 중부원점 GRS80(EPSG:5181 정의) 가정이고 서울 근처에서만 확인했습니다(`weather/Geo.kt`의 `AirKoreaTm`). 특보가 실제로 발효 중인 날의 문구 형식은 아직 보지 못했습니다.
- 기상특보는 특보현황 문구를 시·도 이름으로 나눈 **시·도 단위 안내**입니다(정확한 특보 구역 판정 아님). 시·도는 기상청 격자 자료(`src/main/resources/weather/dong-grid.csv`, 공공데이터포털 단기예보 활용가이드 첨부 「격자_위경도(2610)」의 읍면동 3,565개)로 정합니다. 2026년 통합된 전남광주통합특별시는 특보 문구의 옛 이름(광주·전남)도 찾습니다.
- `RATE_LIMITED`(429)는 `/places`에만 적용했습니다(`places/Places.kt`의 `PlacesRateLimiter`, 1분 고정 창, 기기당 10회·전체 300회). 서버 메모리 카운터라 서버를 여러 대로 늘리면 각자 따로 세고, 재시작하면 초기화됩니다. 같은 좌표 결과 캐시는 두지 않았습니다.
