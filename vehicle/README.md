# vehicle — 차량 데이터 모듈

팀원은 Pleos SDK를 직접 부르지 않고 이 모듈의 **상태(`state`)**와 **이벤트(`events`)**만 구독합니다.
설계 배경: 노션 「DrivePet — 차량 데이터 모듈 설계 (초안)」

## 쓰는 법

```kotlin
// 1) 시작 (포그라운드 서비스까지 켜짐)
VehicleModule.start(context, VehicleModule.Mode.FAKE)   // FAKE | REPLAY | REAL

// 2) 차 상태 구독 — RESUMED가 아니라 STARTED 기준으로 (Pleos에서 앱이 보이면서도 일시정지될 수 있음)
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        VehicleModule.repository.state.collect { s ->
            gearText.text = s.gear.label()          // 값이 없으면 "미측정"/"미지원"/"권한 없음"
        }
    }
}

// 3) 이벤트 구독 — 캐릭터 반응, 점수, 정산
VehicleModule.repository.events.collect { e ->
    when (e) {
        is DriveEvent.TripEnded -> showSettlement(e.trip)          // e.trip.distanceKm × 2 = EXP
        is DriveEvent.DriverDoorOpened -> if (e.parked) character.wave()
        is DriveEvent.PreExitCheck -> showExitCard(e.items)
        else -> Unit
    }
}

// 4) 가짜 모드에서 값 바꾸기
VehicleModule.fake.setGear(Gear.D)
```

## 구성

| 파일 | 역할 |
|---|---|
| `model/VehicleState.kt` | 차 상태. 값마다 `Signal`(Ok / Unsupported / NoPermission / Unknown) |
| `model/DriveEvent.kt` | 이벤트 14종 |
| `model/TripSummary.kt` | 주행 기록(거리 = 도착 − 출발 누적거리) |
| `engine/EventEngine.kt` | 상태 변화 → 이벤트 규칙. 기준 숫자는 `RuleConfig` |
| `source/RealVehicleSource.kt` | Pleos Vehicle SDK + AAOS(주행 제한, 차속 보조) |
| `source/FakeVehicleSource.kt` | 버튼으로 값 바꾸는 가짜 차 |
| `source/ReplayVehicleSource.kt` · `Scenario.kt` | 시나리오 JSON 재생 (시연용) |
| `VehicleRepository.kt` | `state`, `events`, `trip` 제공 |
| `VehicleModule.kt` | 진입점, 모드 전환 |
| `DriveService.kt` | 포그라운드 서비스 (백그라운드 기록 유지) |

## 이벤트 기준 (RuleConfig 기본값)

| 이벤트 | 기준 |
|---|---|
| 주행 시작 | P → D/R |
| 주행 종료 | P가 5초 유지, 30초 미만 주행은 버림 |
| 급조향 | 1초 안에 90° 이상 변화 |
| 방향지시등 미점등 | 45° 이상 꺾을 때 직전 5초 안에 방향지시등 없음 |
| 잔량 부족 | 20% 이하 |

## 테스트

`vehicle/src/test/.../EventEngineTest.kt`를 열고 ▶ 실행 (에뮬레이터 불필요). 규칙 12개를 확인합니다.

## 알려진 제약 (11/7 실측, 11/9 사무국 회신)

- 브레이크는 ON/OFF만, 트립 거리 API 미지원, 차속은 에뮬레이터에서 변경 불가
- 기어 리스너 클래스 경로를 확인하지 못해 0.5초 주기 조회로 받음
- 경고등(`PRIVATE_CAR_SAFETY`)은 개발 단계 전용 → 배포 전 `VehicleModule.usePrivateSafety = false`
- AAOS `CAR_SPEED` 권한은 앱마켓 승인 미확정 → 차속은 보조 데이터
- 연료·배터리(V-09)는 아직 연결 안 함 (`energyPct`는 실제 차 모드에서 Unknown)
- 조향각 부호가 콘솔과 반대로 들어온다는 다른 팀 실측이 있음
