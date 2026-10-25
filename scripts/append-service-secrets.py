"""Append service credentials without rewriting the existing deployment settings."""
import os
from pathlib import Path
import re
import sys

TOKEN_KEYS = ("APP_ML_SERVICE_TOKEN", "APP_GENERATOR_SERVICE_TOKEN")


def append_service_secrets(path, environ):
    additions = []
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
            target.write(("\n" + "".join(additions)).encode("ascii"))


if __name__ == "__main__":
    try:
        if len(sys.argv) != 2:
            raise ValueError("Expected one environment file path")
        append_service_secrets(sys.argv[1], os.environ)
    except (ValueError, OSError) as error:
        # Never include environment values or file contents in diagnostics.
        print(str(error) if isinstance(error, ValueError) else "Cannot update environment file", file=sys.stderr)
        sys.exit(1)
