# 테스트 절차

## 1. 에뮬레이터 준비

1. Android Studio → Settings → Languages & Frameworks → Android SDK → **SDK Update Sites**에 추가
   - Windows/Linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
   - Mac(Apple 칩): `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
2. **SDK Platforms** → Show Package Details → Android 14.0 (API 34) → **Pleos Connect v2.1.0 APIs** 설치
3. Device Manager → Create Virtual Device → Automotive → **Pleos Connect v2.1.0** → 시스템 이미지 **API 34 / Pleos Connect v2.1.0 APIs** 선택
   - 오른쪽 정보에 OEM `42dot`이 보여야 함
   - Additional settings: Boot **Cold**, RAM **4 GB**
4. 기기 설명이 Android 14로 나오는지 확인

## 2. 앱 실행

1. 실행 대상을 Pleos 에뮬레이터로 선택 → ▶
2. 앱에서 **권한 요청** → 허용 → **검증 시작**
3. "현재 값"에 기어·브레이크·조향 등이 채워지는지 확인

## 3. Virtual Console

```
adb forward tcp:8088 tcp:8088
```

브라우저에서 http://127.0.0.1:8088 → 왼쪽 위 **Emulation Controls** (검색창에 항목 이름 입력)

## 4. 항목별 확인

| 항목 | 조작 | 앱에서 볼 값 |
|---|---|---|
| V-01 기어 | Gear Selection P↔D | `기어 (AAOS)`, `기어 (SDK)` |
| V-03 브레이크 | Brake Pedal 슬라이더 | `브레이크 페달 (SDK 수신)` |
| V-03 조향 | 메인 화면 Steering | `조향각 (SDK)` |
| V-04 주행 제한 | 앱을 띄운 채 D | 화면 유지/차단 (매니페스트의 `distractionOptimized` 유무로 비교) |
| V-05 백그라운드 | 다른 영역으로 이동 후 1~2분 | `살아 있음` 줄이 10초마다 이어지는지 |
| V-06 P단 | D → P | 기록의 `P 전환 감지`, `dumpsys notification` |
| V-08 주행거리 | Odometer 값 변경 | `누적 주행거리 km (SDK)` |
| V-08 경고등 | Telltales → On | `경고등 (SDK)` |
| 반응 데이터 | Door Position / Window / Light / Turn Signal | 각 `(SDK)` 값 |

## 5. 기록 꺼내기

```
cmd /c "`"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`" exec-out run-as com.drivepet.probe --user 10 cat files/probe-log.txt > probe-log.txt"
```

알림 확인:

```
adb shell dumpsys notification --noredact | findstr /i "drivepet"
```
