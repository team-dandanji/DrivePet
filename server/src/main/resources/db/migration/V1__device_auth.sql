-- 계정·기기·갱신 토큰
create table accounts (
    account_id    text primary key,
    kakao_subject text unique,
    nickname      text,
    last_revision bigint not null default 0,
    created_at    timestamptz not null
);

create table devices (
    device_id       uuid primary key,
    secret_hash     bytea not null,
    app_version     text not null,
    account_id      text references accounts(account_id),
    binding_version bigint not null default 0,
    created_at      timestamptz not null,
    updated_at      timestamptz not null
);

-- 갱신 토큰은 해시만 저장한다. family_id는 같은 발급 계열(QR 세션 등) 단위 폐기용
create table refresh_tokens (
    token_hash      bytea primary key,
    device_id       uuid not null references devices(device_id),
    account_id      text,
    binding_version bigint not null,
    family_id       uuid not null,
    issued_at       timestamptz not null,
    expires_at      timestamptz not null,
    revoked_at      timestamptz
);
create index refresh_tokens_device_idx on refresh_tokens(device_id) where revoked_at is null;
create index refresh_tokens_family_idx on refresh_tokens(family_id) where revoked_at is null;
