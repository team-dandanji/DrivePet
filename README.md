# DrivePet

**차량 상태 기반 캐릭터 육성 · 차량 관리 · 안전운전 습관 개선 서비스**  
"내 차의 상태가 살아 숨 쉬는 캐릭터가 된다" — 달릴수록 함께 진화하는 나만의 반려차 메이트

2026 현대자동차그룹 차량용 앱 개발 공모전 출품작 (Pleos Connect)

## 저장소 구성

| 경로 | 내용 |
|---|---|
| `tools/drivepet-probe/` | 기술 검증 앱과 결과 (Pleos 에뮬레이터 실측, 2026-11-07) |
| (예정) `app/` | DrivePet 본 앱 |

## 기술 검증 요약

Pleos Connect Emulator v2.1.0(API 34)에서 핵심 기술 항목 8개를 직접 측정했습니다. 결과는 **지원 2 / 조건부 5 / 미지원 1**입니다.

- 기어, 조향각, 문, 창문, 전조등, 방향지시등, 누적 주행거리, 경고등 수신 확인
- 포그라운드 서비스로 백그라운드 주행 기록 유지 확인
- 주행 중(D) 화면은 `distractionOptimized` 선언 유무에 따라 유지/차단

자세한 내용: [tools/drivepet-probe/README.md](tools/drivepet-probe/README.md)

## 실행 방법 · 테스트 환경 · APK

- 검증 앱 실행 방법과 테스트 환경: [tools/drivepet-probe/README.md](tools/drivepet-probe/README.md)
- 본 앱 APK는 개발 후 `apk/` 폴더(`팀명_앱이름_release.apk`)에 올릴 예정입니다.
