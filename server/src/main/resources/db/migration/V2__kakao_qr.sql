-- 카카오 QR 로그인 세션. state는 해시만 저장한다
create table qr_sessions (
    session_id              text primary key,
    device_id               uuid not null references devices(device_id),
    created_binding_version bigint not null,
    state_hash              bytea not null unique,
    status                  text not null check (status in ('PENDING', 'LINKED')),
    family_id               uuid not null,
    created_at              timestamptz not null,
    expires_at              timestamptz not null,
    linked_at               timestamptz,
    linked_account_id       text references accounts(account_id),
    linked_binding_version  bigint
);
create index qr_sessions_device_idx on qr_sessions(device_id);
