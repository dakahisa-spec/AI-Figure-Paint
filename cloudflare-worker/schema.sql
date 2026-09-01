PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS licenses (
    id TEXT PRIMARY KEY NOT NULL,
    activation_code_hash TEXT UNIQUE NOT NULL,
    max_devices INTEGER NOT NULL DEFAULT 3 CHECK(max_devices BETWEEN 1 AND 3),
    monthly_budget_krw INTEGER NOT NULL DEFAULT 3000 CHECK(monthly_budget_krw BETWEEN 1 AND 3000),
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS devices (
    license_id TEXT NOT NULL,
    device_hash TEXT NOT NULL,
    public_key TEXT NOT NULL,
    app_version TEXT NOT NULL DEFAULT '',
    first_activated_at TEXT NOT NULL,
    last_used_at TEXT NOT NULL,
    revoked_at TEXT,
    PRIMARY KEY (license_id, device_hash),
    FOREIGN KEY (license_id) REFERENCES licenses(id) ON DELETE CASCADE
);

CREATE TRIGGER IF NOT EXISTS enforce_device_limit
BEFORE INSERT ON devices
WHEN (
    SELECT COUNT(*) FROM devices
    WHERE license_id = NEW.license_id AND revoked_at IS NULL
) >= (
    SELECT max_devices FROM licenses WHERE id = NEW.license_id
)
BEGIN
    SELECT RAISE(ABORT, 'DEVICE_LIMIT');
END;

CREATE TABLE IF NOT EXISTS nonces (
    nonce TEXT PRIMARY KEY NOT NULL,
    license_id TEXT NOT NULL,
    device_hash TEXT NOT NULL,
    expires_at INTEGER NOT NULL,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS index_nonces_device ON nonces(license_id, device_hash);

CREATE TABLE IF NOT EXISTS monthly_usage (
    license_id TEXT NOT NULL,
    year_month TEXT NOT NULL,
    used_krw INTEGER NOT NULL DEFAULT 0,
    reserved_krw INTEGER NOT NULL DEFAULT 0,
    input_tokens INTEGER NOT NULL DEFAULT 0,
    output_tokens INTEGER NOT NULL DEFAULT 0,
    image_requests INTEGER NOT NULL DEFAULT 0,
    updated_at TEXT NOT NULL,
    PRIMARY KEY (license_id, year_month),
    FOREIGN KEY (license_id) REFERENCES licenses(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS api_requests (
    request_id TEXT PRIMARY KEY NOT NULL,
    license_id TEXT NOT NULL,
    device_hash TEXT NOT NULL,
    model TEXT NOT NULL,
    reserved_cost_krw INTEGER NOT NULL,
    actual_cost_krw INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL,
    created_at TEXT NOT NULL,
    completed_at TEXT
);

CREATE INDEX IF NOT EXISTS index_api_requests_license_month ON api_requests(license_id, created_at);
