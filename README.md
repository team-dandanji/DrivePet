# DrivePet

**차량 상태 기반 캐릭터 육성 · 차량 관리 · 안전운전 습관 개선 서비스**  
"내 차의 상태가 살아 숨 쉬는 캐릭터가 된다" — 달릴수록 함께 진화하는 나만의 반려차 메이트

2026 현대자동차그룹 차량용 앱 개발 공모전 출품작 (Pleos Connect)

## 저장소 구성

| 경로 | 내용 |
|---|---|
| `vehicle/` | 차량 데이터 모듈 — 팀원은 이 모듈의 상태·이벤트만 구독 ([README](vehicle/README.md)) |
| `vehicle-demo/` | 차량 데이터 데모 앱 — 가짜 데이터 조작, 시연 시나리오 재생, 실제 차 수신 확인 |
| `tools/drivepet-probe/` | 기술 검증 앱과 결과 (Pleos 에뮬레이터 실측, 2026-11-07) |
| (예정) `app/` | DrivePet 본 앱 — `implementation(project(":vehicle"))` |
| (예정) `apk/` | 제출용 `팀명_앱이름_release.apk` |

## 실행 방법

1. Android Studio에서 이 폴더(`settings.gradle.kts`가 있는 곳)를 엽니다. Gradle JVM은 **21**.
2. 에뮬레이터: **Pleos Connect v2.1.0 APIs, API 34** (설치 순서: [tools/drivepet-probe/docs/test-procedure.md](tools/drivepet-probe/docs/test-procedure.md))
3. 실행 대상 `vehicle-demo` → ▶
4. 데모 화면에서 **가짜 데이터** / **시나리오 재생 (출근 12km)** / **실제 차** 중 선택
   - 실제 차 모드에서는 Virtual Console(`adb forward tcp:8088 tcp:8088` → http://127.0.0.1:8088)로 값을 바꿉니다.

규칙 엔진 단위 테스트: `vehicle/src/test/java/com/drivepet/vehicle/engine/EventEngineTest.kt` ▶

## 테스트 환경

| 항목 | 값 |
|---|---|
| IDE | Android Studio, Gradle 8.13, JDK 21, AGP 8.13.0, Kotlin 2.0.21 |
| 에뮬레이터 | Pleos Connect v2.1.0 APIs, x86_64, API 34 (Android 14) |
| SDK | `ai.pleos.playground:Vehicle:2.3.2`, `vehicle-data:1.1.11` |
| 앱 | minSdk 28, targetSdk 34, compileSdk 35 |

## 기술 검증 요약

핵심 기술 항목 8개 실측 결과 **지원 2 / 조건부 5 / 미지원 1**. 자세한 내용은 [tools/drivepet-probe/README.md](tools/drivepet-probe/README.md).
