# DrivePet

**차량 상태 기반 캐릭터 육성 · 차량 관리 · 안전운전 습관 개선 서비스**  
"내 차의 상태가 살아 숨 쉬는 캐릭터가 된다" — 달릴수록 함께 진화하는 나만의 반려차 메이트

2026 현대자동차그룹 차량용 앱 개발 공모전 출품작 (Pleos Connect) · 앱 버전 1.0.0

## APK

**[`apk/단단지_Carpet_release.apk`](apk/단단지_Carpet_release.apk)** — release 빌드, 버전 1.0.0

## 실행 방법

### APK로 바로 실행

1. Pleos Connect 에뮬레이터를 켭니다(아래 [테스트 환경](#테스트-환경)).
2. APK를 설치합니다.
   ```bash
   adb install apk/단단지_Carpet_release.apk
   ```
3. 하단 앱 목록에서 **DrivePet**을 엽니다.
4. 처음 실행하면 권한을 두 번 묻습니다. 모두 **허용**을 누릅니다. (차속 권한은 에뮬레이터에서 "위치 정보에 액세스" 창으로 표시됩니다. 두 번째는 알림 권한입니다.)
5. 온보딩(차량 정보 확인 → 소모품 관리 이력 → 메이트 알 고르기·이름 짓기)을 마치면 캐릭터 라운지가 열립니다.

### 앱에서 해 볼 것

| 화면 | 해 볼 것 |
|---|---|
| 라운지 | 캐릭터(병아리·공룡·슬라임)와 건강점수·배터리, 특이사항. **오늘의 미션** 3개(서울 날짜마다 바뀜) — 완료하면 "받기 +30" |
| 차 정비소 | 소모품(EV 3종 / 내연기관·하이브리드 4종)의 남은 관리 주기, 정기검사 D-Day. "방금 교체했어요" → 관리 기록(+50 EXP, 같은 소모품 하루 1회), "관리 이력 수정"에서 관리일·주행거리·검사일 입력 |
| 정비소·충전소 안내 | 경고등이 켜지면 라운지의 "병원 가기", 배터리가 20% 이하면 "밥 주러 가기" → 내비게이션의 장소 검색(정비소)·주변 충전소 화면을 엽니다 |
| 주행기록 | 지난 주행 목록 |
| 창 크기 | 분할 세로 창·가로 창에서도 라운지가 창에 맞춰 다시 배치됩니다 |

### 주행을 보는 방법 (Virtual Console)

에뮬레이터에는 실제 주행이 없으므로, Pleos Virtual Console로 차량 값을 바꿔 주행을 만듭니다. 서버 연결은 필요 없습니다(1.0.0은 기기 안에서 점수·EXP를 계산합니다).

1. 포트를 연결하고 브라우저에서 Virtual Console을 엽니다.
   ```bash
   adb forward tcp:8088 tcp:8088
   ```
   → http://127.0.0.1:8088
   왼쪽 아래 **Emulation Controls**를 열면 아래 항목이 모여 있습니다(위쪽 검색창에 이름을 치면 빨리 찾습니다).
2. **Gear Selection → D**: 주행이 시작됩니다. 주행 중에는 시스템이 앱 화면을 가립니다("주행 중에는 사용할 수 없습니다"). 정상 동작입니다.
3. **Odometer**(누적 주행거리)를 올립니다.
   - 막대를 마우스로 클릭하면 한 번에 수만 km가 들어갑니다. 이런 값(한 번 주행에 1,000km 초과)은 앱이 "거리 미측정"으로 처리해 EXP를 주지 않습니다.
   - **막대 손잡이를 한 번 누른 뒤 키보드 → 키를 한 번씩** 누르면 1km씩 올라갑니다(빠르게 연타하면 일부만 반영됩니다). 예: 12번 → 12km.
   - 누적 주행거리는 줄일 수 없습니다.
4. **D 상태로 30초 이상** 지난 뒤 **Gear Selection → P**: 몇 초 뒤 주행 정산 화면(거리, 안전점수, EXP)이 나옵니다. 30초 미만 주행은 기록하지 않습니다.
   - 예: 12km → 거리 12.0 km, 주행 EXP +24, 급조향 없음 +15.
5. 하차 점검: 정차 상태에서 전조등(**Exterior Lights Mode → Low beam**)을 켜거나 창문을 연 채 운전석 문(**Door Position → Open**)을 열면 "하차 전에 확인해주세요"가 뜹니다. 전조등을 끄면(**Off**) 실시간으로 "✓ 전조등 꺼짐 · 점검 완료!"로 바뀝니다.
6. 경고등: **Telltales**에서 항목(예: Parking Brake)을 **On** → 라운지 캐릭터가 아픈 표정(×﹏×)으로 바뀌고, 특이사항에 표시되며 건강점수가 내려갑니다.

> Virtual Console에는 경고등(Telltale) 묶음과 실제 조작 버튼의 이름이 비슷한 것이 있습니다. 예: "Door Open"은 경고등이고, 실제 문은 "Door Position"입니다.
> 캐릭터 음성은 현재 버전에서 말풍선으로 표시합니다.

### 소스에서 빌드

1. Android Studio에서 이 폴더(`settings.gradle.kts`가 있는 곳)를 엽니다. Gradle JVM은 **21**.
2. 실행 대상 `app` → ▶ (개발용 debug 빌드에는 시연 시나리오 재생용 개발자 메뉴가 있습니다. 라운지 캐릭터를 길게 누르면 열립니다.)
3. 제출용 APK 만들기: `./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk`

단위 테스트: `./gradlew :app:testDebugUnitTest :vehicle:testDebugUnitTest`  
서버(선택, 1.0.0 앱은 사용하지 않음): [server/README.md](server/README.md)

## 테스트 환경

| 항목 | 값 |
|---|---|
| 에뮬레이터 | **Pleos Connect v2.1.0 APIs**, API 34 (Android 14), Android Automotive. 개발 PC에 따라 arm64-v8a(Apple Silicon) 또는 x86_64 이미지 |
| 화면 규격 | **2560 × 1440 px, 160 dpi** (가로형 단일 디스플레이). 앱은 홈 화면 오른쪽 분할 창(약 1682 × 1210 px) 안에서 실행됩니다 |
| 실행 사용자 | 에뮬레이터 앱은 Android 사용자 10에서 실행됩니다. `adb shell` 명령으로 앱을 다룰 때는 `--user 10`을 붙입니다(예: `adb shell am start --user 10 -n com.drivepet.app/.MainActivity`) |
| 필요한 설정 | 차속 권한(`android.car.permission.CAR_SPEED`)·알림 권한 허용(내비 화면 열기 권한 `pleos.car.permission.NAVI_CUSTOM_MAP`은 설치 때 자동 허용). Virtual Console 사용 시 `adb forward tcp:8088 tcp:8088` |
| IDE·빌드 | Android Studio, Gradle 8.13, JDK 21, AGP 8.13.0, Kotlin 2.0.21 |
| SDK | `ai.pleos.playground:Vehicle:2.3.2`, `vehicle-data:1.1.11`, `NaviHelper:3.0.5`(정비소·충전소 안내) |
| 앱 | minSdk 28, targetSdk 34, compileSdk 35 |

에뮬레이터 설치 순서: [tools/drivepet-probe/docs/test-procedure.md](tools/drivepet-probe/docs/test-procedure.md)

## 저장소 구성

| 경로 | 내용 |
|---|---|
| `app/` | DrivePet 차량 앱 |
| `apk/` | 제출용 release APK |
| `vehicle/` | 차량 데이터 모듈 — 앱은 이 모듈의 상태·이벤트만 구독 ([README](vehicle/README.md)) |
| `vehicle-demo/` | 차량 데이터 데모 앱 — 가짜 데이터 조작, 시연 시나리오 재생, 실제 차 수신 확인 |
| `server/` | 백엔드 서버(기기 인증·카카오 QR 로그인·기록 동기화·날씨) |
| `tools/drivepet-probe/` | 기술 검증 앱과 결과 (Pleos 에뮬레이터 실측, 2026-11-07) |
| `docs/research/` | 건강점수·안전점수 산출 근거 조사 |

## 기술 검증 요약

핵심 기술 항목 8개 실측 결과 **지원 2 / 조건부 5 / 미지원 1**. 자세한 내용은 [tools/drivepet-probe/README.md](tools/drivepet-probe/README.md).
