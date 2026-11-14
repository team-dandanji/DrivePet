# Carpet

**차량 상태 기반 캐릭터 육성 · 차량 관리 · 안전운전 습관 개선 서비스**  
"내 차의 상태가 살아 숨 쉬는 캐릭터가 된다" — 달릴수록 함께 진화하는 나만의 반려차 메이트

2026 현대자동차그룹 차량용 앱 개발 공모전 출품작 (Pleos Connect) · 앱 버전 1.1.2

## APK

**[`apk/Carpet_release.apk`](apk/Carpet_release.apk)** — release 빌드, 버전 1.1.2

## 실행 방법

### APK로 바로 실행

1. Pleos Connect 에뮬레이터를 켭니다(아래 [테스트 환경](#테스트-환경)). 에뮬레이터가 인터넷에 연결돼 있어야 합니다.
2. APK를 설치합니다.
   ```bash
   adb install apk/Carpet_release.apk
   ```
3. 하단 앱 목록에서 **Carpet**을 엽니다.
4. 처음 실행하면 권한을 묻습니다(차속·알림·대략적인 위치). 모두 **허용**을 누릅니다. 차속 권한은 에뮬레이터에서 "위치 정보에 액세스" 창으로 표시됩니다.
5. 온보딩: 시작하기 → 약관 동의 → **카카오 QR 로그인**(휴대폰 카메라나 카카오톡으로 QR을 찍어 로그인, 5분 안에) → 차량 정보 확인 → 소모품 관리 이력(전기차 3개, 내연기관·하이브리드 4개) → 누적 주행거리 → 메이트 알 고르기·이름 짓기 → 라운지
   - 이미 가입한 카카오 계정으로 로그인하면 저장된 차량·펫·기록을 불러옵니다.

### 앱에서 해 볼 것

| 화면 | 해 볼 것 |
|---|---|
| 인트로·메이트 선택 | 처음 실행하면 Carpet 로고와 달리는 자동차 모션이 나옵니다. 메이트 알을 고르면 알이 깨지며 캐릭터가 나오는 모션이 재생됩니다 |
| 라운지 | 캐릭터(병아리·공룡·눈사람, 병아리 2단계는 움직이는 모션), 배터리·건강점수, 특이사항, 날씨. 들어오면 캐릭터가 인사를 음성으로 읽어 줍니다. **오늘의 미션** 2개(날짜마다 바뀜)는 달성하면 바로 +30 EXP가 들어옵니다 |
| 돌봄 코스 | 경고등이 켜지면 라운지의 "돌보러 가기" → 주변 정비소 3곳 → 고르면 내비게이션 안내가 시작됩니다. 다녀와서 주행이 끝나면 정비 기록을 남길 수 있습니다 |
| 차 정비소 | 소모품(전기차 3종 / 내연기관·하이브리드 4종)의 남은 관리 주기, 정기검사 D-Day. 교체 버튼 → 관리 기록(+50 EXP, 같은 소모품 하루 1회), "관리 이력 수정"에서 관리일·주행거리·검사일 입력 |
| 주행기록 | 지난 주행 목록 |
| 헤더 | 왼쪽 "Carpet ▾" → 프로필 수정, 오른쪽 ⋯ → 카카오 연결·연결 해제·계정 탈퇴 |
| 창 크기 | 분할 세로 창·가로 창에서도 라운지가 창에 맞춰 다시 배치됩니다 |

### 주행을 보는 방법 (Virtual Console)

에뮬레이터에는 실제 주행이 없으므로, Pleos Virtual Console로 차량 값을 바꿔 주행을 만듭니다.

1. 포트를 연결하고 브라우저에서 Virtual Console을 엽니다.
   ```bash
   adb forward tcp:8088 tcp:8088
   ```
   → http://127.0.0.1:8088
   왼쪽 아래 **Emulation Controls**를 열면 아래 항목이 모여 있습니다(위쪽 검색창에 이름을 치면 빨리 찾습니다).
2. **Gear Selection → D**: 주행이 시작됩니다. 주행 중에는 앱 위에 "주행 중에는 서비스 사용이 제한돼요" 창이 뜨고, "네비게이션으로 돌아가기"로 내비 화면을 엽니다. 정상 동작입니다.
3. **Odometer**(누적 주행거리)를 올립니다.
   - 막대를 마우스로 클릭하면 한 번에 수만 km가 들어갑니다. 이런 값(한 번 주행에 1,000km 초과)은 앱이 "거리 미측정"으로 처리해 EXP를 주지 않습니다.
   - **막대 손잡이를 한 번 누른 뒤 키보드 → 키를 한 번씩** 누르면 1km씩 올라갑니다(빠르게 연타하면 일부만 반영됩니다). 예: 12번 → 12km.
   - 누적 주행거리는 줄일 수 없습니다.
4. **D 상태로 30초 이상** 지난 뒤 **Gear Selection → P**: 몇 초 뒤 주행 정산 화면(거리, 안전점수, EXP)이 나옵니다. 30초 미만 주행은 기록하지 않습니다.
   - 예: 12km → 거리 12.0 km, 주행 EXP +24, 급조향 없음 +15.
   - "확인했어요 (라운지로)"를 누르면 라운지로 돌아갑니다.
5. 하차 점검: 정차 상태에서 창문을 연 채 운전석 문(**Door Position → Open**)을 열면 "하차 전, 창문과 조명을 확인해주세요"가 뜹니다(실내등이 켜져 있어도 뜹니다). 창문을 닫으면 항목에 ✓가 켜지고, 모두 확인되면 앱이 스스로 닫힙니다.
6. 경고등: **Telltales**에서 항목(예: TPMS)을 **On** → 특이사항에 표시되고 건강점수가 내려가며, "돌보러 가기"로 주변 정비소를 찾을 수 있습니다. 병아리 2단계는 아픈 모션으로 바뀝니다(다른 캐릭터·단계는 말풍선으로 기분을 보여 줍니다).

> Virtual Console에는 경고등(Telltale) 묶음과 실제 조작 버튼의 이름이 비슷한 것이 있습니다. 예: "Door Open"은 경고등이고, 실제 문은 "Door Position"입니다.

### 소스에서 빌드

1. Android Studio에서 이 폴더(`settings.gradle.kts`가 있는 곳)를 엽니다. Gradle JVM은 **21**.
2. 실행 대상 `app` → ▶ (개발용 debug·demo 빌드에는 시연 시나리오 재생용 개발자 메뉴가 있습니다. 라운지 캐릭터를 길게 누르면 열립니다. release 빌드에는 없습니다.)
3. 제출용 APK 만들기: `./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk`

단위 테스트: `./gradlew :app:testDebugUnitTest :vehicle:testDebugUnitTest`  
서버: 앱은 클라우드 서버(`https://129-225-197-147.sslip.io/api/v1`)에 연결합니다. 서버를 직접 띄우는 방법은 [server/README.md](server/README.md)

## 테스트 환경

| 항목 | 값 |
|---|---|
| 에뮬레이터 | **Pleos Connect v2.1.0 APIs**, API 34 (Android 14), Android Automotive. 개발 PC에 따라 arm64-v8a(Apple Silicon) 또는 x86_64 이미지 |
| 화면 규격 | **2560 × 1440 px, 160 dpi** (가로형 단일 디스플레이). 앱은 홈 화면 오른쪽 분할 창(약 1682 × 1210 px) 안에서 실행됩니다 |
| 실행 사용자 | 에뮬레이터 앱은 Android 사용자 10에서 실행됩니다. `adb shell` 명령으로 앱을 다룰 때는 `--user 10`을 붙입니다(예: `adb shell am start --user 10 -n com.drivepet.app/.MainActivity`) |
| 필요한 설정 | 인터넷 연결. 차속 권한(`android.car.permission.CAR_SPEED`)·알림·대략적인 위치 권한 허용(내비게이션·음성 권한은 설치 때 자동 허용). 카카오 로그인에 쓸 휴대폰. Virtual Console 사용 시 `adb forward tcp:8088 tcp:8088` |
| IDE·빌드 | Android Studio, Gradle 8.13, JDK 21, AGP 8.13.0, Kotlin 2.0.21 |
| SDK | `ai.pleos.playground:Vehicle:2.3.2`, `vehicle-data:1.1.11`, `NaviHelper:3.0.5`(정비소 안내), `TextToSpeech:3.3.1`(음성) |
| 앱 | minSdk 28, targetSdk 34, compileSdk 35 |

에뮬레이터 설치 순서: [tools/drivepet-probe/docs/test-procedure.md](tools/drivepet-probe/docs/test-procedure.md)

## 저장소 구성

| 경로 | 내용 |
|---|---|
| `app/` | Carpet 차량 앱 |
| `apk/` | 제출용 release APK |
| `vehicle/` | 차량 데이터 모듈 — 앱은 이 모듈의 상태·이벤트만 구독 ([README](vehicle/README.md)) |
| `vehicle-demo/` | 차량 데이터 데모 앱 — 가짜 데이터 조작, 시연 시나리오 재생, 실제 차 수신 확인 |
| `server/` | 백엔드 서버(기기 인증·카카오 QR 로그인·기록 동기화·날씨·주변 정비소·회원 탈퇴) |
| `tools/drivepet-probe/` | 기술 검증 앱과 결과 (Pleos 에뮬레이터 실측, 2026-11-07) |
| `docs/research/` | 건강점수·안전점수 산출 근거 조사 |

## 기술 검증 요약

핵심 기술 항목 8개 실측 결과 **지원 2 / 조건부 5 / 미지원 1**. 자세한 내용은 [tools/drivepet-probe/README.md](tools/drivepet-probe/README.md).
