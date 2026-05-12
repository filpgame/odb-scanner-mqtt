#!/usr/bin/env python3
"""
Create the 'Carros' HA dashboard with helpers, automations and Lovelace config.

Usage:
    python create_dashboard.py           # creates everything
    python create_dashboard.py --dry-run # prints config without calling HA
"""
from __future__ import annotations

import asyncio
import json
import sys

from ha_api import HARESTClient, HAWebSocketClient
from dashboard_config import build_dashboard_config

# ── Configuration ─────────────────────────────────────────────────────────────
HA_HOST = "ha.mybarraco.duckdns.org"
HA_TOKEN = (
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
    ".eyJpc3MiOiI1NjVmNGZkYzViYWQ0NzBmYWZiYzhiODBjYzgyNWZlMiIsImlhdCI6MTc3NzM0Mjg1MCwiZXhwIjoyMDkyNzAyODUwfQ"
    ".9rlwlUV0m09PrPHPu2U-Og_02jNUAlUDufnieVAztfc"
)

# ── Automation definitions ─────────────────────────────────────────────────────
AUTOMATIONS = [
    {
        "id": "jaecoo_period_hoje",
        "alias": "Jaecoo - Período Hoje",
        "mode": "single",
        "trigger": [
            {
                "platform": "state",
                "entity_id": "input_select.jaecoo_period",
                "to": "Hoje",
            }
        ],
        "action": [
            {
                "service": "input_datetime.set_datetime",
                "target": {"entity_id": "input_datetime.jaecoo_chart_start"},
                "data": {"date": "{{ now().date().isoformat() }}"},
            },
            {
                "service": "input_datetime.set_datetime",
                "target": {"entity_id": "input_datetime.jaecoo_chart_end"},
                "data": {"date": "{{ now().date().isoformat() }}"},
            },
        ],
    },
    {
        "id": "jaecoo_period_7dias",
        "alias": "Jaecoo - Período 7 dias",
        "mode": "single",
        "trigger": [
            {
                "platform": "state",
                "entity_id": "input_select.jaecoo_period",
                "to": "7 dias",
            }
        ],
        "action": [
            {
                "service": "input_datetime.set_datetime",
                "target": {"entity_id": "input_datetime.jaecoo_chart_start"},
                "data": {
                    "date": "{{ (now() - timedelta(days=6)).date().isoformat() }}"
                },
            },
            {
                "service": "input_datetime.set_datetime",
                "target": {"entity_id": "input_datetime.jaecoo_chart_end"},
                "data": {"date": "{{ now().date().isoformat() }}"},
            },
        ],
    },
    {
        "id": "jaecoo_period_30dias",
        "alias": "Jaecoo - Período 30 dias",
        "mode": "single",
        "trigger": [
            {
                "platform": "state",
                "entity_id": "input_select.jaecoo_period",
                "to": "30 dias",
            }
        ],
        "action": [
            {
                "service": "input_datetime.set_datetime",
                "target": {"entity_id": "input_datetime.jaecoo_chart_start"},
                "data": {
                    "date": "{{ (now() - timedelta(days=29)).date().isoformat() }}"
                },
            },
            {
                "service": "input_datetime.set_datetime",
                "target": {"entity_id": "input_datetime.jaecoo_chart_end"},
                "data": {"date": "{{ now().date().isoformat() }}"},
            },
        ],
    },
]


# ── Main ──────────────────────────────────────────────────────────────────────

def create_helpers(rest: HARESTClient) -> None:
    print("\n[1/4] Creating helpers...")
    rest.create_input_datetime("Jaecoo Chart Start")
    rest.create_input_datetime("Jaecoo Chart End")
    rest.create_input_select(
        "Jaecoo Period",
        options=["Hoje", "7 dias", "30 dias", "Personalizado"],
        initial="Hoje",
    )


def create_automations(rest: HARESTClient) -> None:
    print("\n[2/4] Creating automations...")
    for automation in AUTOMATIONS:
        rest.create_automation(automation["id"], automation)


async def create_dashboard(ws: HAWebSocketClient, config: dict) -> None:
    print("\n[3/4] Creating dashboard...")
    await ws.create_dashboard("carros", "Carros", icon="mdi:car")
    print("\n[4/4] Saving dashboard config...")
    await ws.save_dashboard_config("carros", config)


async def main(dry_run: bool = False) -> None:
    config = build_dashboard_config()

    if dry_run:
        print("=== DRY RUN — dashboard config ===")
        print(json.dumps(config, indent=2, ensure_ascii=False))
        print("\n=== DRY RUN — automations ===")
        print(json.dumps(AUTOMATIONS, indent=2, ensure_ascii=False))
        return

    rest = HARESTClient(HA_HOST, HA_TOKEN)

    print("Pinging HA...")
    if not rest.ping():
        print("ERROR: cannot reach HA. Check HA_HOST and HA_TOKEN.")
        sys.exit(1)
    print("  [ok] HA is reachable")

    create_helpers(rest)
    create_automations(rest)

    ws = HAWebSocketClient(HA_HOST, HA_TOKEN)
    await ws.connect()
    try:
        await create_dashboard(ws, config)
    finally:
        await ws.close()

    print(
        f"\n✓ Done! Open https://{HA_HOST}/carros to see your dashboard."
    )


if __name__ == "__main__":
    dry_run = "--dry-run" in sys.argv
    asyncio.run(main(dry_run=dry_run))
