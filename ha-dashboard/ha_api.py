"""Home Assistant API clients — REST and WebSocket."""
from __future__ import annotations
import json
import ssl
import asyncio
import requests
import websockets


class HARESTClient:
    """Thin wrapper around the HA REST API."""

    def __init__(self, host: str, token: str) -> None:
        self.base_url = f"https://{host}"
        self.headers = {
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
        }

    def get(self, path: str) -> dict | list:
        resp = requests.get(f"{self.base_url}{path}", headers=self.headers, timeout=15)
        resp.raise_for_status()
        return resp.json()

    def post(self, path: str, data: dict) -> dict:
        resp = requests.post(
            f"{self.base_url}{path}", headers=self.headers, json=data, timeout=15
        )
        resp.raise_for_status()
        return resp.json()

    def ping(self) -> bool:
        """Return True if HA is reachable and token is valid."""
        try:
            result = self.get("/api/")
            return "message" in result
        except Exception:
            return False

    # ── Helpers ──────────────────────────────────────────────────────────────

    def _entity_exists(self, entity_id: str) -> bool:
        states = self.get("/api/states")
        return any(s["entity_id"] == entity_id for s in states)

    def create_input_datetime(self, name: str) -> None:
        """Create a date-only input_datetime helper via config flow."""
        slug = name.lower().replace(" ", "_")
        entity_id = f"input_datetime.{slug}"
        if self._entity_exists(entity_id):
            print(f"  [skip] {entity_id} already exists")
            return

        resp = self.post(
            "/api/config/config_entries/flow", {"handler": "input_datetime"}
        )
        flow_id = resp["flow_id"]
        self.post(
            f"/api/config/config_entries/flow/{flow_id}",
            {"name": name, "has_date": True, "has_time": False, "icon": "mdi:calendar"},
        )
        print(f"  [created] {entity_id}")

    def create_input_select(
        self, name: str, options: list[str], initial: str
    ) -> None:
        """Create an input_select helper via config flow."""
        slug = name.lower().replace(" ", "_")
        entity_id = f"input_select.{slug}"
        if self._entity_exists(entity_id):
            print(f"  [skip] {entity_id} already exists")
            return

        resp = self.post(
            "/api/config/config_entries/flow", {"handler": "input_select"}
        )
        flow_id = resp["flow_id"]
        self.post(
            f"/api/config/config_entries/flow/{flow_id}",
            {
                "name": name,
                "options": "\n".join(options),
                "initial": initial,
                "icon": "mdi:calendar-range",
            },
        )
        print(f"  [created] {entity_id}")

    # ── Automations ───────────────────────────────────────────────────────────

    def create_automation(self, automation_id: str, config: dict) -> None:
        """Upsert an automation via the HA config REST endpoint."""
        self.post(f"/api/config/automation/config/{automation_id}", config)
        print(f"  [created/updated] automation.{automation_id}")
        # Reload automations so the new one is active
        self.post("/api/services/automation/reload", {})
