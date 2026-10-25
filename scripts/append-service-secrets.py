"""Append runtime overrides and credentials without rewriting existing settings."""
import os
import json
from pathlib import Path
import re
import sys

OVERRIDE_KEYS = {
    "SPRING_PROFILES_ACTIVE", "SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME",
    "SPRING_DATASOURCE_PASSWORD", "APP_AUTH_JWT_SECRET", "APP_AUTH_SECURE_COOKIE",
    "APP_ML_ENABLED", "APP_ML_BASE_URL", "APP_ML_MODEL_VERSION", "APP_ML_REQUEST_TIMEOUT",
    "APP_ML_CACHE_TTL", "APP_ML_MAX_HISTORY_EVENTS", "APP_AUTH_OIDC_ENABLED",
    "APP_AUTH_OIDC_AUTO_LINK_BY_EMAIL", "APP_AUTH_OIDC_ISSUER_URI", "APP_AUTH_OIDC_CLIENT_ID",
    "APP_AUTH_OIDC_CLIENT_SECRET", "APP_AUTH_OIDC_SUCCESS_REDIRECT_URI",
    "APP_GENERATOR_ENABLED", "APP_GENERATOR_USE_FOR_DASHBOARD", "APP_GENERATOR_SOURCE_ID",
    "APP_GENERATOR_BASE_URL", "APP_GENERATOR_POLL_DELAY_MS", "APP_GENERATOR_PAGE_SIZE",
    "APP_GENERATOR_APPLY_BATCH_SIZE", "APP_GENERATOR_REQUEST_TIMEOUT",
}

TOKEN_KEYS = ("APP_ML_SERVICE_TOKEN", "APP_GENERATOR_SERVICE_TOKEN")
GENERATOR_BOOLEAN_KEYS = {"APP_GENERATOR_ENABLED", "APP_GENERATOR_USE_FOR_DASHBOARD"}


def append_service_secrets(path, environ):
    additions = []
    raw_overrides = environ.get("API_RUNTIME_OVERRIDES", "")
    if raw_overrides:
        try:
            overrides = json.loads(raw_overrides)
        except (ValueError, TypeError):
            raise ValueError("API_RUNTIME_OVERRIDES must be a JSON object") from None
        if not isinstance(overrides, dict) or set(overrides) - OVERRIDE_KEYS:
            raise ValueError("API_RUNTIME_OVERRIDES contains unsupported keys")
        for key, value in overrides.items():
            if not isinstance(value, str) or not value or any(c in value for c in "\r\n'\\"):
                raise ValueError("API_RUNTIME_OVERRIDES contains an invalid single-line value")
            if key in GENERATOR_BOOLEAN_KEYS and value not in ("true", "false"):
                raise ValueError("Generator boolean overrides must be true or false")
            additions.append(f"{key}='{value}'\n")
    # Non-secret repository variables can explicitly select the live consumer and
    # dashboard source without replacing the existing secret JSON.
    for key in sorted(GENERATOR_BOOLEAN_KEYS):
        value = environ.get(key, "")
        if value != "":
            if value not in ("true", "false"):
                raise ValueError(f"{key} must be true or false")
            additions.append(f"{key}='{value}'\n")
    for key in TOKEN_KEYS:
        value = environ.get(key, "")
        if not value:
            continue
        if not re.fullmatch(r"[A-Za-z0-9_-]{32,256}", value):
            raise ValueError(f"{key} must contain 32-256 URL-safe characters")
        additions.append(f"{key}='{value}'\n")
    # Validate every value before writing anything. Compose uses the last value
    # for duplicate keys; keeping the original bytes preserves multiline values.
    if additions:
        with Path(path).open("ab") as target:
            target.write(("\n" + "".join(additions)).encode("utf-8"))


if __name__ == "__main__":
    try:
        if len(sys.argv) != 2:
            raise ValueError("Expected one environment file path")
        append_service_secrets(sys.argv[1], os.environ)
    except (ValueError, OSError) as error:
        # Never include environment values or file contents in diagnostics.
        print(str(error) if isinstance(error, ValueError) else "Cannot update environment file", file=sys.stderr)
        sys.exit(1)
