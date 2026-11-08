# 검증 결과 상세 (2026-11-07)

검증자: DrivePet 팀 SDK 담당  
방법: 이 저장소의 검증 앱을 Pleos Connect v2.1.0(API 34) 에뮬레이터에 설치하고, Virtual Console의 Emulation Controls로 값을 바꾸며 수신 여부를 기록했습니다.

판정 기준
- **지원**: 에뮬레이터에서 API 호출·권한·값 변경 수신까지 확인
- **조건부**: 일부 조건(권한 승인, 값 변경 불가 등)에서만 가능
- **미지원**: 공개 API로 구현 불가

## V-01 ~ V-08

### V-01 기어 P/R/N/D — ✅ 지원
- AAOS `GEAR_SELECTION` 구독, Vehicle SDK `Gear.getCurrentGearSelectionState` 모두 정상
- D↔P 전환 시 두 값이 1초 안에 바뀜 (`GEAR_DRIVE` / `GEAR_PARK`)
- 증빙: `screenshots/04-v01-v03-gear-brake-steering.png`, 기록 파일

### V-02 실시간 속도 — 🟡 조건부
- AAOS `PERF_VEHICLE_SPEED` 읽기 정상(0.0). `android.car.permission.CAR_SPEED`는 런타임 권한 요청 필요
- Vehicle SDK에는 차속 API 없음
- Emulation Controls에 속도 항목이 없어 변화는 확인 불가 → 점수 입력에서 제외, 시연은 리플레이 데이터

### V-03 브레이크·조향각·안전벨트 — 🟡 조건부
- 브레이크: Emulation Controls는 0~100% 슬라이더지만 SDK는 `true/false`만 전달 (조회·리스너 모두)
- 조향각: −180, −142, −229 등 바꾼 값이 그대로 반영
- 안전벨트: `Seat.getCurrentSeatBeltBuckledState(ROW_1_LEFT)` 읽기 정상(false = 미착용). 조작 항목이 없어 변경은 확인 불가. 순정 화면의 "안전 벨트를 매세요"와 일치

### V-04 주행 화면 제한 — 🟡 조건부
- `CarUxRestrictionsManager`: P → `requiresDO=false, 0x0`, D → `requiresDO=true, 0x50`(NO_VIDEO | NO_SETUP)
- **매니페스트에 `distractionOptimized=true` 선언 시**: D에서 25분 이상 앱 화면 유지, 조작 가능 (`screenshots/01-...`)
- **선언 제거 후 재설치 시**: D가 되는 즉시(0km/h) "주행 중에는 사용할 수 없습니다" 차단 화면 (`screenshots/02-...`)
- 직접 설치한 앱에서도 선언이 인정됨. 앱마켓 배포 앱에서도 인정되는지는 사무국 문의 중
- 참고: 차단 화면이 떠도 앱의 onPause는 기록되지 않음 → 판정은 스크린샷 기준

### V-05 백그라운드 기록 — ✅ 지원
- 포그라운드 서비스(type=specialUse) 실행 중 앱이 가려진 44초 동안에도 10초 간격 기록이 끊기지 않음
- 기어 D 상태 25분 이상 기록 유지

### V-06 P단 진입 팝업 — 🟡 조건부
- D→P 즉시 감지, `startActivity` 요청과 알림 게시가 오류 없이 실행
- `dumpsys notification`: 알림이 `importance=4`로 시스템에 등록됨. 화면 배너는 보이지 않음
- Pleos 화면이 영역으로 나뉘어 다른 앱을 띄워도 우리 앱이 뒤로 가지 않아, "다른 앱 위로 뜨는지"는 재현 불가

### V-07 시동 시 자동 실행 — ❌ 미지원
- 앱이 시동 신호를 받을 공개 경로 없음(문서 판정 유지). 사용자가 앱을 실행하면 서비스가 유지되는 구조로 설계

### V-08 Vehicle SDK 핵심 상태 — 🟡 조건부
- 누적 주행거리: 0 → 717451 변경값 수신 (`screenshots/05-...`)
- 트립 거리: `ERROR_NOT_SUPPORTED_PROPERTY` → 출발·도착 누적거리 차이로 계산
- 경고등: Emulation Controls에서 켠 `PARKING_BRAKE`, `SEAT_BELT` 경고등이 `state=ON`으로 수신 (`PRIVATE_CAR_SAFETY` 필요)
- AAOS 주차브레이크: 읽기 정상(false), 조작 항목이 없어 변경 확인 불가. 경고등과 실제 주차브레이크 신호는 별개

## 캐릭터 반응 데이터

| 데이터 | API | 결과 |
|---|---|---|
| 운전석 문 | `Door.getCurrentDoorPosition(ROW_1_LEFT)` | ✅ 0 → 1 |
| 운전석 창문 | `Window.getCurrentWindowPosition(ROW_1_LEFT)` | ✅ 0 → 4 → 0 (0~10단계) |
| 전조등 상향 | `Light.getCurrentHighBeamLightState` | ✅ OFF → ON → OFF |
| 방향지시등 | `TurnSignal.getCurrentTurnSignalState` | ✅ OFF → RIGHT → OFF |
| 조향각 | `SteeringWheel.getCurrentSteeringWheelAngle` | ✅ |
| 누적 주행거리 | `Odometer.getCurrentDistanceDriven` | ✅ |
| 안전벨트 | `Seat.getCurrentSeatBeltBuckledState` | ⚠️ 읽기만 |

방향지시등을 켜면 **순정 측면 카메라 화면이 앱 위에 약 13초 동안 표시**되어 앱이 가려짐 (`screenshots/12-...`).

## 개발 시 주의할 점

1. 에뮬레이터 시스템 이미지는 반드시 **API 34 / Pleos Connect v2.1.0 APIs**. 기기 이름이 Pleos여도 API 35 일반 이미지를 고르면 Pleos SDK가 모두 `need Permission`으로 실패함
2. Pleos 권한은 매니페스트에 하나씩 선언해야 함 (`CAR_DRIVE`, `CAR_POWERTRAIN`, `CAR_MILEAGE`, `READ_CAR_STEERING`, `CAR_SEATS`, `CAR_DOORS`, `CAR_WINDOWS`, `CAR_LIGHTS`, `CAR_SAFETY`, `PRIVATE_CAR_SAFETY` 등)
3. 앱은 **사용자 10번(운전자 프로필)**으로 실행됨 → `run-as`, `pm` 명령에 `--user 10` 필요
4. Pleos 화면은 영역이 나뉘어 있어, 다른 영역을 누르면 앱이 **화면에 보이면서도 일시정지**됨 → 화면 갱신을 onResume에만 묶으면 캐릭터가 멈춰 보임
5. 브레이크는 ON/OFF만, 트립 API는 미지원, 차속은 에뮬레이터에서 변경 불가

## 증빙 파일

- `evidence/probe-log-2026-11-07.txt`: 앱 기록 전체. 앞부분(10:4x)은 API 35 일반 이미지에서 실행해 SDK가 `need Permission`으로 실패한 기록이고, 이후가 Pleos 이미지 실측입니다. 에뮬레이터를 다시 만들면서 시계 기준이 바뀌어 시각이 연속되지 않습니다.
- `evidence/screenshots/`: 항목별 화면 13장
