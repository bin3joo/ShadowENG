"""Persist per-request traces for offline analysis."""

from __future__ import annotations

import json
import logging
import time
import uuid
from datetime import datetime
from pathlib import Path
from typing import Any

import config

logger = logging.getLogger(__name__)
_TRACE_ROOT = Path(__file__).resolve().parents[1]


def _normalize_for_json(value: Any) -> Any:
    """Convert arbitrary nested values into JSON-serializable primitives."""
    if value is None or isinstance(value, (str, int, float, bool)):
        return value
    if isinstance(value, Path):
        return str(value)
    if isinstance(value, dict):
        return {
            str(key): _normalize_for_json(item)
            for key, item in value.items()
        }
    if isinstance(value, (list, tuple, set)):
        return [_normalize_for_json(item) for item in value]

    item_method = getattr(value, "item", None)
    if callable(item_method):
        try:
            return item_method()
        except Exception:
            pass

    tolist_method = getattr(value, "tolist", None)
    if callable(tolist_method):
        try:
            return tolist_method()
        except Exception:
            pass

    model_dump = getattr(value, "model_dump", None)
    if callable(model_dump):
        try:
            return _normalize_for_json(model_dump())
        except Exception:
            pass

    return str(value)


def create_trace_context(endpoint: str) -> dict[str, Any]:
    """Create a lightweight trace context for a request."""
    return {
        "request_id": uuid.uuid4().hex,
        "endpoint": endpoint,
        "started_at": time.time(),
    }


def persist_request_trace(
    *,
    trace_context: dict[str, Any],
    request_payload: dict[str, Any],
    intermediate: dict[str, Any] | None = None,
    response_payload: dict[str, Any] | None = None,
    error_payload: dict[str, Any] | None = None,
) -> str | None:
    """Persist a request trace as a JSON file if enabled."""
    if not config.REQUEST_TRACE_ENABLED:
        return None

    try:
        trace_dir = Path(config.REQUEST_TRACE_DIR)
        if not trace_dir.is_absolute():
            trace_dir = _TRACE_ROOT / trace_dir

        now = datetime.now()
        day_dir = trace_dir / now.strftime("%Y-%m-%d")
        day_dir.mkdir(parents=True, exist_ok=True)

        request_id = str(trace_context.get("request_id", uuid.uuid4().hex))
        started_at = float(trace_context.get("started_at", time.time()))
        endpoint = str(trace_context.get("endpoint", "unknown"))
        safe_endpoint = endpoint.strip("/").replace("/", "_") or "root"
        file_name = (
            f"{now.strftime('%H%M%S_%f')}_{safe_endpoint}_{request_id}.json"
        )
        file_path = day_dir / file_name

        payload = {
            "request_id": request_id,
            "endpoint": endpoint,
            "captured_at": now.isoformat(timespec="milliseconds"),
            "duration_ms": round((time.time() - started_at) * 1000.0, 2),
            "request": _normalize_for_json(request_payload),
            "intermediate": _normalize_for_json(intermediate or {}),
            "response": _normalize_for_json(response_payload),
            "error": _normalize_for_json(error_payload),
        }

        with file_path.open("w", encoding="utf-8") as file_obj:
            json.dump(payload, file_obj, ensure_ascii=False, indent=2)

        logger.info("Saved request trace: %s", file_path)
        return str(file_path)
    except Exception as exc:
        logger.warning("Failed to save request trace: %s", exc)
        return None
