# DrivePet Probe — 차량 신호 검증 앱

**DrivePet 기획의 기술 검증용 앱입니다.** Pleos Vehicle SDK와 AAOS Car API로 차량 신호(기어·브레이크·조향·주행거리·문·창문·조명·방향지시등·경고등·주행 제한)를 실시간으로 받아 화면과 파일에 기록합니다.

> 이 저장소는 DrivePet 본 앱이 아니라, 본 앱을 만들기 전에 "Pleos Connect에서 무엇이 실제로 되는지"를 확인한 도구와 그 결과입니다.

## 검증 결과 요약 (2026-11-07, 에뮬레이터 실측)

| ID | 항목 | 판정 | 핵심 결과 |
|---|---|---|---|
| V-01 | 기어 P/R/N/D | ✅ 지원 | AAOS·SDK 모두 1초 안에 반영 |
| V-02 | 실시간 속도 | 🟡 조건부 | 읽기 가능(0.0), 에뮬레이터에서 값 변경 불가 |
| V-03 | 브레이크·조향·안전벨트 | 🟡 조건부 | 브레이크는 ON/OFF만, 조향각 정상, 벨트는 읽기만 |
| V-04 | 주행 화면 제한 | 🟡 조건부 | DO 선언 있음 → D에서도 화면 유지 / 없음 → 차단 |
| V-05 | 백그라운드 기록 | ✅ 지원 | 포그라운드 서비스로 가려진 동안에도 기록 유지 |
| V-06 | P단 팝업 | 🟡 조건부 | P 감지·알림 등록은 됨, 화면 배너는 안 보임 |
| V-07 | 시동 시 자동 실행 | ❌ 미지원 | 앱이 시동 신호를 받을 경로 없음 |
| V-08 | Vehicle SDK 핵심 상태 | 🟡 조건부 | 누적거리·경고등 수신, 트립 거리 미지원 |

자세한 내용: [docs/verification-results.md](docs/verification-results.md)  
증빙: [docs/evidence/](docs/evidence/) (스크린샷 13장, 기록 파일 787줄)

## 실행 방법

1. Android Studio에서 이 폴더를 엽니다. (`settings.gradle.kts`가 있는 폴더)
2. Gradle JVM은 **21**을 사용합니다. (Gradle 8.13은 JVM 25 미지원)
3. 에뮬레이터는 **Pleos Connect v2.1.0 APIs, API 34** 이미지로 만듭니다. (자세한 순서: [docs/test-procedure.md](docs/test-procedure.md))
4. ▶ Run → 앱에서 **권한 요청** → **검증 시작**
5. Virtual Console(`adb forward tcp:8088 tcp:8088` 후 http://127.0.0.1:8088)의 **Emulation Controls**에서 차량 상태를 바꾸고, 앱의 **현재 값**과 **기록**을 확인합니다.

기록 파일 꺼내기 (Windows PowerShell):

```
cmd /c "`"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`" exec-out run-as com.drivepet.probe --user 10 cat files/probe-log.txt > probe-log.txt"
```

## 테스트 환경

| 항목 | 값 |
|---|---|
| OS | Windows |
| IDE | Android Studio (Gradle 8.13, JDK 21, AGP 8.13.0, Kotlin 2.0.21) |
| 에뮬레이터 | Pleos Connect v2.1.0 APIs, Intel x86_64, API 34 (Android 14) |
| SDK | `ai.pleos.playground:Vehicle:2.3.2`, `vehicle-data:1.1.11` |
| 앱 | minSdk 28, targetSdk 34, compileSdk 35 |

## 구조

```
app/src/main/java/com/drivepet/probe/
├─ Probe.kt          값·기록 저장 (화면, logcat 태그 DrivePetProbe, files/probe-log.txt)
├─ ProbeService.kt   포그라운드 서비스: AAOS 구독 + Vehicle SDK 2초 주기 조회 + P 전환 시 화면·알림 시도
└─ MainActivity.kt   현재 값·기록 표시 화면
```

## APK

검증 도구라 배포용 APK는 올리지 않았습니다. Android Studio에서 바로 실행하면 됩니다.

## 주의

- 에뮬레이터에서 된다고 실차·앱마켓에서 같은 권한이 허용된다는 뜻은 아닙니다. Vehicle SDK 권한은 Pleos App Market 사전 승인 범위에서만 쓸 수 있습니다.
- `PRIVATE_CAR_SAFETY`(경고등·타이어)와 `distractionOptimized` 인정 여부는 공모전 사무국에 문의 중입니다.
