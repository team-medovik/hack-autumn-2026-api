#!/usr/bin/env python3
"""Read-only runtime diagnosis. Never emit environment, response bodies or stderr."""
import json
import hmac
import subprocess
from urllib.parse import urlsplit

API_CONTAINER = "hack-autumn-2026-api"

# Only fixed paths are requested. Credentials travel over stdin, never argv.
PROBE = r'''
import json, math, re, sys, time, urllib.request, urllib.error
from datetime import datetime

def status_contract(body, size):
    invalid = []
    if size > 32 * 1024:
        invalid.append("bodySize")
    if not isinstance(body, dict):
        return {"contractValid": False, "invalidFields": invalid + ["root"]}
    core = body.get("generator")
    core = core if isinstance(core, dict) else {}
    run = core.get("runId")
    if not isinstance(run, str) or not run.strip() or len(run.encode("utf-16-le", errors="surrogatepass")) // 2 > 255:
        invalid.append("generator.runId")
    virtual_time = core.get("virtualTime")
    try:
        if not isinstance(virtual_time, str) or not re.fullmatch(
                r"\d{4}-\d{2}-\d{2}[Tt]\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:[Zz]|[+-]\d{2}:\d{2})", virtual_time):
            raise ValueError()
        datetime.fromisoformat(virtual_time.upper().replace("Z", "+00:00"))
    except (ValueError, OverflowError):
        invalid.append("generator.virtualTime")
    if type(core.get("finished")) is not bool:
        invalid.append("generator.finished")
    scale = body.get("timeScale")
    try:
        valid_scale = type(scale) in (int, float) and math.isfinite(scale) and scale > 0
    except OverflowError:
        valid_scale = False
    if not valid_scale:
        invalid.append("timeScale")
    state = body.get("workerState")
    if not isinstance(state, str) or state not in {"starting", "running", "paused", "finished", "error"}:
        invalid.append("workerState")
    if body.get("syntheticOnly") is not True:
        invalid.append("syntheticOnly")
    return {"contractValid": not invalid, "invalidFields": invalid}

def safe_status(body):
    result = {}
    if not isinstance(body, dict):
        return result
    enums = {
        "status": {"alive", "ready", "not_ready"},
        "code": {"STATE_UNAVAILABLE", "UNAUTHORIZED", "RUN_NOT_FOUND", "HTTP_ERROR"},
        "workerState": {"running", "paused", "stopped", "stale", "finished", "completed", "initializing", "starting", "error"},
        "mode": {"live_simulation"},
    }
    for key, allowed in enums.items():
        value = body.get(key)
        if isinstance(value, str) and value in allowed:
            result[key] = value
    if type(body.get("timeScale")) in (int, float):
        result["timeScale"] = body["timeScale"]
    generator = body.get("generator")
    if isinstance(generator, dict):
        result["generator"] = {key: generator[key] for key in
            ("revision", "eventCount", "decisionCount", "timeScale")
            if type(generator.get(key)) in (int, float)}
    return result

def classify(error):
    name = type(error).__name__
    if isinstance(error, urllib.error.URLError):
        name = type(error.reason).__name__
    reasons = {"UndefinedTable": "schema_missing", "UndefinedColumn": "schema_outdated",
               "InvalidSchemaName": "schema_missing", "OperationalError": "database_connection_failed",
               "ConnectionError": "connection_failed", "TimeoutError": "timeout",
               "ModuleNotFoundError": "runtime_module_missing", "gaierror": "dns_resolution_failed",
               "ConnectionRefusedError": "connection_refused", "SSLCertVerificationError": "tls_verification_failed"}
    if name == "StoreUnavailable":
        return "state_not_initialized"
    if name == "CheckpointError":
        return "state_schema_engine_or_python_incompatible"
    return reasons.get(name, "read_failed")

def main():
    request = json.load(sys.stdin)
    # Do not follow redirects with a service credential.
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args, **kwargs):
            return None
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
    for path in ("/health/live", "/health/ready", "/internal/v1/generator/status"):
        result = {"path": path}
        started = time.monotonic()
        try:
            req = urllib.request.Request(request["url"].rstrip("/") + path,
                headers={"Authorization": "Bearer " + request["token"]})
            try:
                response = opener.open(req, timeout=10)
            except urllib.error.HTTPError as error:
                response = error
            with response:
                result["httpStatus"] = response.code
                try:
                    raw = response.read(32769)
                    if path.endswith("/status") and response.code == 200:
                        if len(raw) > 32768:
                            result.update({"contractValid": False, "invalidFields": ["bodySize"]})
                        else:
                            result.update(status_contract(json.loads(raw), len(raw)))
                    else:
                        result.update(safe_status(json.loads(raw)))
                except (ValueError, UnicodeError):
                    result.update({"contractValid": False, "invalidFields": ["json"]})
        except Exception as error:
            result["reason"] = classify(error)
        result["elapsedMs"] = round((time.monotonic() - started) * 1000)
        print(json.dumps(result), flush=True)
    try:
        from generator_service.state import Settings
        from generator_service.postgres import PostgresStore
        settings = Settings.from_env()
        if not settings.database_url:
            print(json.dumps({"store": "checkpoint", "databaseConfigured": False}))
            return
        store = PostgresStore(settings.database_url, settings.run_key)
        # Independent SELECTs distinguish compatibility failures without writing a run.
        with store.connect() as connection:
            connection.execute("SET TRANSACTION READ ONLY")
            row = connection.execute("SELECT schema_version, engine_fingerprint, python_version "
                "FROM generator_sim.runs WHERE run_key=%s", (settings.run_key,)).fetchone()
            if row is None:
                result = {"store": "postgres", "reason": "run_not_initialized"}
            else:
                from generator_service.postgres import SCHEMA, engine_fingerprint
                result = {"store": "postgres", "runPresent": True,
                    "schemaMatches": row["schema_version"] == SCHEMA,
                    "engineMatches": row["engine_fingerprint"] == engine_fingerprint(),
                    "pythonMatches": row["python_version"] == f"{sys.version_info.major}.{sys.version_info.minor}"}
        print(json.dumps(result), flush=True)
    except Exception as error:
        print(json.dumps({"store": "read_failed", "reason": classify(error)}), flush=True)

main()
'''

API_DB_PROBE = r'''
import json, sys
import psycopg
from psycopg.rows import dict_row

request = json.load(sys.stdin)
url = request["url"]
if url.startswith("jdbc:"):
    url = url[5:]
result = {"apiStore": "generator_consumer"}
try:
    with psycopg.connect(url, user=request["user"], password=request["password"],
            connect_timeout=10, row_factory=dict_row,
            options="-c statement_timeout=10000 -c default_transaction_read_only=on") as connection:
        row = connection.execute("""SELECT received_sequence, applied_sequence, published_sequence,
            published_revision, published_at IS NOT NULL AS published, failure_count, last_error,
            tail_complete, source_revision,
            CASE WHEN published_projection IS NULL THEN NULL
                 ELSE jsonb_array_length(published_projection->'accounts') END AS accounts,
            CASE WHEN published_projection IS NULL THEN NULL
                 ELSE jsonb_array_length(published_projection->'services') END AS services
            FROM generator_consumers WHERE source_id=%s""", (request["source"],)).fetchone()
    if row is None:
        result["progress"] = "not_bound"
    else:
        error = row.pop("last_error")
        row["last_error"] = error if error in (None, "FETCH_FAILED", "APPLY_FAILED") else "UNKNOWN"
        result["progress"] = row
except Exception:
    result["progress"] = "read_failed"
print(json.dumps(result, default=str))
'''


def command(args, data=None, timeout=15):
    try:
        result = subprocess.run(args, input=data, text=True, capture_output=True, timeout=timeout)
        return result.stdout if result.returncode == 0 else None
    except (OSError, subprocess.TimeoutExpired):
        return None


def inspect(container):
    output = command(["docker", "inspect", container])
    return json.loads(output)[0] if output else None


def environment(container):
    return dict(value.split("=", 1) for value in container["Config"].get("Env", []) if "=" in value)


def main():
    api = inspect(API_CONTAINER)
    if not api:
        print(json.dumps({"api": "not_found_or_docker_unavailable"}))
        return
    env = environment(api)
    url = env.get("APP_GENERATOR_BASE_URL", "http://generator-api:8000")
    token = env.get("APP_GENERATOR_SERVICE_TOKEN", "")
    parsed = urlsplit(url)
    if parsed.scheme not in ("http", "https") or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment:
        print(json.dumps({"configuration": "invalid_base_url"}))
        return
    print(json.dumps({"enabled": env.get("APP_GENERATOR_ENABLED", "false") == "true",
        "useForDashboardEnvironment": env.get("APP_GENERATOR_USE_FOR_DASHBOARD"),
        "baseHost": parsed.hostname, "basePort": parsed.port or (443 if parsed.scheme == "https" else 80),
        "tokenPresent": bool(token)}))
    api_networks = set(api["NetworkSettings"]["Networks"])
    ids = command(["docker", "ps", "-aq"])
    generators = []
    ml_images = []
    for container_id in (ids or "").split():
        container = inspect(container_id)
        if not container:
            continue
        config = container["Config"]
        service = (config.get("Labels") or {}).get("com.docker.compose.service", "")
        name = container.get("Name", "").lstrip("/")
        relevant = any(part in (service + " " + name + " " + config["Image"]).lower()
                       for part in ("generator", "hack-autumn-2026-ml", "ml-service"))
        if not relevant and service != "postgres" and name != "postgres":
            continue
        networks = set(container["NetworkSettings"]["Networks"])
        state = container["State"]
        print(json.dumps({"container": name, "service": service, "image": config["Image"],
            "state": state["Status"], "health": state.get("Health", {}).get("Status", "none"),
            "publishedPorts": container["NetworkSettings"].get("Ports", {}),
            "networks": sorted(networks), "sharedApiNetworks": sorted(networks & api_networks)}))
        if service == "generator-api" or name == "generator-api":
            generators.append(container)
        if service == "ml-service" and state["Status"] == "running":
            ml_images.append(config["Image"])
    for container in generators:
        generator_token = environment(container).get("GENERATOR_SERVICE_TOKEN", "")
        print(json.dumps({"probeFrom": container["Name"].lstrip("/"),
            "serviceTokensMatch": bool(token) and hmac.compare_digest(token.encode(), generator_token.encode()),
            "apiTokenMeetsMinimumLength": len(token) >= 16}))
        output = command(["docker", "exec", "-i", container["Id"], "python", "-B", "-c", PROBE],
            json.dumps({"url": url, "token": token}), timeout=100)
        print(output.strip() if output else json.dumps({"probe": "execution_failed"}))
    if not generators:
        print(json.dumps({"generatorApi": "container_not_found"}))
    if generators:
        database = {
            "url": env.get("SPRING_DATASOURCE_URL", ""),
            "user": env.get("SPRING_DATASOURCE_USERNAME", ""),
            "password": env.get("SPRING_DATASOURCE_PASSWORD", ""),
            "source": env.get("APP_GENERATOR_SOURCE_ID", "aeza-live"),
        }
        if all(database[key] for key in ("url", "user", "password")):
            output = command(["docker", "run", "--rm", "--pull", "never", "-i", "--read-only",
                "--cap-drop", "ALL", "--security-opt", "no-new-privileges",
                "--network", "container:" + API_CONTAINER, "--entrypoint", "python",
                generators[0]["Config"]["Image"], "-B", "-c", API_DB_PROBE],
                json.dumps(database), timeout=30)
            print(output.strip() if output else json.dumps({"apiStore": "probe_failed"}))
        else:
            print(json.dumps({"apiStore": "database_configuration_missing"}))
    if ml_images:
        # Use an already installed Python image in the API's exact network namespace.
        # No host mounts, published ports, application writes or image downloads.
        output = command(["docker", "run", "--rm", "--pull", "never", "-i", "--read-only",
            "--cap-drop", "ALL", "--security-opt", "no-new-privileges",
            "--network", "container:" + API_CONTAINER, "--entrypoint", "python",
            ml_images[0], "-B", "-c", PROBE], json.dumps({"url": url, "token": token}), timeout=100)
        print(json.dumps({"probeFrom": "api_network_namespace"}))
        print(output.strip() if output else json.dumps({"probe": "execution_failed"}))
    # curl is optional in the minimal JVM image; report explicitly if absent.
    available = command(["docker", "exec", API_CONTAINER, "sh", "-c", "command -v curl"])
    if available:
        config = "url = " + json.dumps(url.rstrip("/") + "/internal/v1/generator/status") + "\n"
        config += "header = " + json.dumps("Authorization: Bearer " + token) + "\n"
        output = command(["docker", "exec", "-i", API_CONTAINER, "curl", "--config", "-",
            "--silent", "--noproxy", "*", "--max-time", "10", "--output", "/dev/null", "--write-out", "%{http_code}"], config)
        print(json.dumps({"apiNetworkHttpStatus": int(output) if output and output.isdigit() else "connection_failed"}))
    else:
        print(json.dumps({"apiNetworkProbe": "curl_not_available"}))


if __name__ == "__main__":
    try:
        main()
    except Exception:
        # Exception text may include a malformed environment value or Docker output.
        print(json.dumps({"diagnostic": "failed_without_exposing_runtime_details"}))
        raise SystemExit(1)
