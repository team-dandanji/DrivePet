-- 서비스 종료 시 모든 사용자 데이터 파기(계정·기기·토큰·QR 세션·프로필·기록). 스키마와 Flyway 기록은 남는다.
-- 실행(서버 폴더에서): docker compose exec -T db psql -U drivepet -d drivepet -v ON_ERROR_STOP=1 < ops/purge-all-user-data.sql
-- 되돌릴 수 없다. DB 볼륨째 지우는 절차는 README "보관·파기" 참고.
truncate sync_records, mission_definitions, sync_changes, sync_batches, profiles, qr_sessions, refresh_tokens, devices, accounts;
