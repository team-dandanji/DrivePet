-- 동기화 저장소. 누적 EXP·레벨은 저장하지 않는다(앱이 기록으로 계산)
create table profiles (
    profile_id       uuid primary key,
    account_id       text not null references accounts(account_id),
    state            jsonb,                 -- 삭제되면 null
    state_revision   bigint not null,
    profile_revision bigint not null,
    created_at       timestamptz not null,
    deleted_at       timestamptz,
    deleted_revision bigint
);
create index profiles_account_idx on profiles(account_id) where deleted_at is null;

create table sync_records (
    account_id    text not null,
    profile_id    uuid not null references profiles(profile_id),
    record_id     uuid not null,
    kind          text not null,
    occurred_at   timestamptz not null,
    rule_version  text not null,
    record        jsonb not null,
    content_hash  bytea not null,
    revision      bigint not null,
    primary key (account_id, profile_id, record_id)
);

-- 계정 변경 로그. 프로필이 삭제되면 그 프로필의 이전 변경은 지우고 PROFILE_DELETED만 남긴다
create table sync_changes (
    account_id       text not null references accounts(account_id),
    revision         bigint not null,
    profile_id       uuid not null,
    type             text not null check (type in ('PROFILE_CREATED', 'STATE_UPDATED', 'RECORD_APPENDED', 'PROFILE_DELETED')),
    saved_at         timestamptz not null,
    source_device_id uuid not null,
    payload          jsonb not null,
    primary key (account_id, revision)
);
create index sync_changes_profile_idx on sync_changes(account_id, profile_id);

-- 업로드 묶음 멱등 응답 (요청 원문은 저장하지 않고 정규화 해시만)
create table sync_batches (
    account_id      text not null references accounts(account_id),
    client_event_id uuid not null,
    request_hash    bytea not null,
    response        jsonb not null,
    committed_at    timestamptz not null,
    primary key (account_id, client_event_id)
);

create table mission_definitions (
    account_id   text not null,
    profile_id   uuid not null references profiles(profile_id),
    mission_id   text not null,
    period_key   text not null,
    rule_version text not null,
    reward_exp   text not null,
    primary key (account_id, profile_id, mission_id, period_key)
);
