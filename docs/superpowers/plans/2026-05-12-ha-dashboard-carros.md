# HA Dashboard "Carros" — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Script Python que cria helpers, automações e dashboard "Carros" no Home Assistant via REST + WebSocket API, exibindo histórico de bateria PHEV, combustível e velocidade do Jaecoo 7 com seletor de período dinâmico.

**Architecture:** Três módulos Python — `ha_api.py` (cliente REST+WS), `dashboard_config.py` (configuração pura dos cards como dicts Python), `create_dashboard.py` (orquestrador). Helpers `input_datetime`/`input_select` criados via REST config flow. Automações criadas via REST. Dashboard via WebSocket `lovelace/*`. Consumo mostrado via card `statistic` built-in do HA (usa long-term statistics automáticas — sem utility_meter).

**Tech Stack:** Python 3.11+, `websockets>=12`, `requests>=2.31`, `apexcharts-card` (HACS, já instalado), HA WebSocket API, HA REST API.

**HA endpoint:** `https://ha.mybarraco.duckdns.org`  
**Token:** variável `HA_TOKEN` no topo do script (não comitar!)

---

## Nota: utility_meter substituído por statistic card

`utility_meter` é YAML-only no HA — não pode ser criado via API. Em vez disso, o dashboard usa o card `statistic` nativo com `stat_type: change` (mostra o delta do período). HA coleta long-term statistics automaticamente para todos os sensores numéricos, portanto nenhum helper extra é necessário para consumo.

---

## File Structure

```
ha-dashboard/
├── ha_api.py             # REST client + WebSocket async client
├── dashboard_config.py   # Card configs — funções puras retornando dicts
├── create_dashboard.py   # Orquestrador: chama ha_api + dashboard_config
├── requirements.txt      # websockets>=12.0 requests>=2.31.0
└── test_config.py        # Unit tests para dashboard_config (sem I/O)
```

---

## Task 1: Project Setup

**Files:**
- Create: `ha-dashboard/requirements.txt`
- Create: `ha-dashboard/test_config.py` (vazio, estrutura base)

- [ ] **Step 1: Criar requirements.txt**

```
websockets>=12.0
requests>=2.31.0
```

- [ ] **Step 2: Criar test_config.py com estrutura base**

```python
"""Unit tests for dashboard_config.py — pure dict generation, no I/O."""
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import pytest
```

- [ ] **Step 3: Instalar dependências**

```bash
cd ha-dashboard
pip install -r requirements.txt
```

Expected: `Successfully installed websockets-... requests-...`

- [ ] **Step 4: Commit**

```bash
git add ha-dashboard/requirements.txt ha-dashboard/test_config.py
git commit -m "chore: add ha-dashboard project skeleton"
```

---

## Task 2: ha_api.py — REST Client

**Files:**
- Create: `ha-dashboard/ha_api.py`

- [ ] **Step 1: Criar ha_api.py com HARESTClient**

```python
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
        resp = requests.post(
            f"{self.base_url}/api/config/automation/config/{automation_id}",
            headers=self.headers,
            json=config,
            timeout=15,
        )
        if resp.status_code == 200:
            print(f"  [created/updated] automation.{automation_id}")
        else:
            raise Exception(
                f"Failed to create automation {automation_id}: "
                f"{resp.status_code} {resp.text}"
            )
        # Reload automations so the new one is active
        self.post("/api/services/automation/reload", {})
```

- [ ] **Step 2: Verificar que o módulo importa sem erros**

```bash
cd ha-dashboard
python -c "from ha_api import HARESTClient; print('OK')"
```

Expected: `OK`

- [ ] **Step 3: Commit**

```bash
git add ha-dashboard/ha_api.py
git commit -m "feat: add HARESTClient with helper and automation creation"
```

---

## Task 3: ha_api.py — WebSocket Client

**Files:**
- Modify: `ha-dashboard/ha_api.py` (adicionar HAWebSocketClient ao final)

- [ ] **Step 1: Adicionar HAWebSocketClient ao ha_api.py**

Acrescentar ao final do arquivo:

```python

class HAWebSocketClient:
    """Async HA WebSocket client for Lovelace API."""

    def __init__(self, host: str, token: str) -> None:
        self.host = host
        self.token = token
        self._ws = None
        self._msg_id = 0

    async def connect(self) -> None:
        ssl_ctx = ssl.create_default_context()
        self._ws = await websockets.connect(
            f"wss://{self.host}/api/websocket",
            ssl=ssl_ctx,
            ping_interval=20,
        )
        msg = json.loads(await self._ws.recv())
        assert msg["type"] == "auth_required", f"Unexpected: {msg}"

        await self._ws.send(
            json.dumps({"type": "auth", "access_token": self.token})
        )
        msg = json.loads(await self._ws.recv())
        if msg["type"] != "auth_ok":
            raise Exception(f"HA auth failed: {msg}")
        print(f"  [ws] Authenticated to {self.host}")

    async def command(self, payload: dict) -> dict:
        self._msg_id += 1
        payload["id"] = self._msg_id
        await self._ws.send(json.dumps(payload))
        while True:
            msg = json.loads(await self._ws.recv())
            if msg.get("id") == self._msg_id:
                if not msg.get("success", True):
                    raise Exception(f"WS command failed: {msg}")
                return msg.get("result", {})

    async def close(self) -> None:
        if self._ws:
            await self._ws.close()

    # ── Lovelace ─────────────────────────────────────────────────────────────

    async def list_dashboards(self) -> list:
        result = await self.command({"type": "lovelace/dashboards/list"})
        return result if isinstance(result, list) else []

    async def create_dashboard(
        self, url_path: str, title: str, icon: str = "mdi:car"
    ) -> dict:
        dashboards = await self.list_dashboards()
        existing = [d for d in dashboards if d.get("url_path") == url_path]
        if existing:
            print(f"  [skip] Dashboard '{url_path}' already exists")
            return existing[0]

        result = await self.command(
            {
                "type": "lovelace/dashboards/create",
                "url_path": url_path,
                "title": title,
                "icon": icon,
                "show_in_sidebar": True,
                "require_admin": False,
                "mode": "storage",
            }
        )
        print(f"  [created] Dashboard '{title}' at /{url_path}")
        return result

    async def save_dashboard_config(self, url_path: str, config: dict) -> None:
        await self.command(
            {
                "type": "lovelace/config/save",
                "url_path": url_path,
                "config": config,
                "force": True,
            }
        )
        print(f"  [saved] Dashboard config for '{url_path}'")
```

- [ ] **Step 2: Verificar**

```bash
python -c "from ha_api import HARESTClient, HAWebSocketClient; print('OK')"
```

Expected: `OK`

- [ ] **Step 3: Commit**

```bash
git add ha-dashboard/ha_api.py
git commit -m "feat: add HAWebSocketClient with lovelace create/save"
```

---

## Task 4: dashboard_config.py — Card Definitions

**Files:**
- Create: `ha-dashboard/dashboard_config.py`

- [ ] **Step 1: Criar dashboard_config.py**

```python
"""
Dashboard card configurations — pure functions returning Python dicts.
No I/O, no HA dependencies. Fully testable in isolation.
"""
from __future__ import annotations

# ── Constants ────────────────────────────────────────────────────────────────

PERIOD_ENTITY = "input_select.jaecoo_period"
START_ENTITY = "input_datetime.jaecoo_chart_start"
END_ENTITY = "input_datetime.jaecoo_chart_end"

SPEED_ENTITY = "sensor.jaecoo7_jaecoo7_speed"
BATTERY_ENTITY = "sensor.j7_j7_hybrid_battery"
FUEL_ENTITY = "sensor.j7_j7_fuel_level"

PERIOD_OPTIONS = ["Hoje", "7 dias", "30 dias", "Personalizado"]

# Maps input_select option → HA calendar period for statistic card
PERIOD_TO_CALENDAR = {
    "Hoje": "day",
    "7 dias": "week",
    "30 dias": "month",
}

# graph_span Jinja2 template — requires apexcharts-card v2.1+
GRAPH_SPAN_TEMPLATE = (
    "{%- set p = states('" + PERIOD_ENTITY + "') -%}"
    "{%- if p == 'Hoje' -%}24h"
    "{%- elif p == '7 dias' -%}7d"
    "{%- elif p == '30 dias' -%}30d"
    "{%- else -%}"
    "{{ [((as_timestamp(states('" + END_ENTITY + "'))"
    " - as_timestamp(states('" + START_ENTITY + "')))"
    " / 3600) | int, 1] | max }}h"
    "{%- endif -%}"
)


# ── Helper cards ─────────────────────────────────────────────────────────────

def _conditional(state: str, card: dict) -> dict:
    """Wrap a card in a conditional that shows only when PERIOD_ENTITY == state."""
    return {
        "type": "conditional",
        "conditions": [{"entity": PERIOD_ENTITY, "state": state}],
        "card": card,
    }


def _statistic_card(title: str, entity: str, stat_type: str, period: str) -> dict:
    """Built-in statistic card for a single calendar period."""
    return {
        "type": "statistic",
        "entity": entity,
        "stat_type": stat_type,
        "period": {"calendar": {"period": period}},
        "name": title,
    }


# ── Public card builders ──────────────────────────────────────────────────────

def period_selector_card() -> dict:
    """
    Entities card with:
     - input_select dropdown (always visible)
     - start/end input_datetime rows (visible only when Personalizado)
    """
    return {
        "type": "entities",
        "title": "Período",
        "entities": [
            {"entity": PERIOD_ENTITY, "name": "Período"},
            {
                "type": "conditional",
                "conditions": [{"entity": PERIOD_ENTITY, "state": "Personalizado"}],
                "row": {"entity": START_ENTITY, "name": "De"},
            },
            {
                "type": "conditional",
                "conditions": [{"entity": PERIOD_ENTITY, "state": "Personalizado"}],
                "row": {"entity": END_ENTITY, "name": "Até"},
            },
        ],
    }


def summary_stat_column(title: str, entity: str, stat_type: str) -> dict:
    """
    Vertical stack of conditional statistic cards — one per preset period.
    For 'Personalizado' shows current value (statistic unavailable for arbitrary range).
    """
    preset_cards = [
        _conditional(
            label,
            _statistic_card(title, entity, stat_type, calendar_period),
        )
        for label, calendar_period in PERIOD_TO_CALENDAR.items()
    ]
    custom_card = _conditional(
        "Personalizado",
        {"type": "entity", "entity": entity, "name": f"{title} (atual)"},
    )
    return {"type": "vertical-stack", "cards": preset_cards + [custom_card]}


def summary_row() -> dict:
    """Horizontal stack of 3 stat columns: speed avg, fuel change, battery change."""
    return {
        "type": "horizontal-stack",
        "cards": [
            summary_stat_column("Vel. Média", SPEED_ENTITY, "mean"),
            summary_stat_column("Combustível", FUEL_ENTITY, "change"),
            summary_stat_column("Bateria PHEV", BATTERY_ENTITY, "change"),
        ],
    }


def apexcharts_card(title: str, entity: str, color: str, unit: str) -> dict:
    """Area chart with dynamic graph_span driven by period selector."""
    return {
        "type": "custom:apexcharts-card",
        "header": {
            "show": True,
            "title": title,
            "colorize_states": True,
            "show_states": True,
        },
        "update_interval": "30s",
        "graph_span": GRAPH_SPAN_TEMPLATE,
        "apex_config": {
            "chart": {"type": "area"},
            "tooltip": {"x": {"format": "dd/MM HH:mm"}},
            "fill": {"opacity": 0.15},
        },
        "series": [
            {
                "entity": entity,
                "name": title,
                "stroke_width": 2,
                "fill": "tozeroy",
                "color": color,
            }
        ],
        "yaxis": [
            {
                "decimals": 1,
                "apex_config": {"title": {"text": unit}},
            }
        ],
    }


def build_dashboard_config() -> dict:
    """Return the complete Lovelace dashboard configuration dict."""
    return {
        "title": "Carros",
        "views": [
            {
                "title": "Jaecoo 7",
                "path": "jaecoo7",
                "icon": "mdi:car",
                "cards": [
                    period_selector_card(),
                    summary_row(),
                    apexcharts_card(
                        "🔋 Bateria PHEV",
                        BATTERY_ENTITY,
                        "#4CAF50",
                        "%",
                    ),
                    apexcharts_card(
                        "⛽ Combustível",
                        FUEL_ENTITY,
                        "#FF9800",
                        "%",
                    ),
                    apexcharts_card(
                        "🚀 Velocidade",
                        SPEED_ENTITY,
                        "#2196F3",
                        "km/h",
                    ),
                ],
            }
        ],
    }
```

- [ ] **Step 2: Verificar importação**

```bash
python -c "from dashboard_config import build_dashboard_config; import json; print(json.dumps(build_dashboard_config(), indent=2)[:300])"
```

Expected: JSON parcial do dashboard config impresso sem erros.

- [ ] **Step 3: Commit**

```bash
git add ha-dashboard/dashboard_config.py
git commit -m "feat: add dashboard card config (pure functions)"
```

---

## Task 5: test_config.py — Unit Tests

**Files:**
- Modify: `ha-dashboard/test_config.py`

- [ ] **Step 1: Escrever tests**

```python
"""Unit tests for dashboard_config.py — pure dict generation, no I/O."""
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from dashboard_config import (
    period_selector_card,
    summary_row,
    apexcharts_card,
    build_dashboard_config,
    PERIOD_ENTITY,
    BATTERY_ENTITY,
    FUEL_ENTITY,
    SPEED_ENTITY,
    GRAPH_SPAN_TEMPLATE,
)


def test_period_selector_has_input_select():
    card = period_selector_card()
    assert card["type"] == "entities"
    entities = card["entities"]
    main = entities[0]
    assert main["entity"] == PERIOD_ENTITY


def test_period_selector_has_datetime_conditionals():
    card = period_selector_card()
    conditionals = [e for e in card["entities"] if e.get("type") == "conditional"]
    assert len(conditionals) == 2
    # Both conditionals only show when Personalizado
    for c in conditionals:
        assert c["conditions"][0]["state"] == "Personalizado"


def test_summary_row_has_three_columns():
    row = summary_row()
    assert row["type"] == "horizontal-stack"
    assert len(row["cards"]) == 3


def test_summary_row_speed_uses_mean():
    row = summary_row()
    speed_col = row["cards"][0]
    # Each column is a vertical-stack with conditional statistic cards
    preset_cards = [
        c["card"]
        for c in speed_col["cards"]
        if c.get("type") == "conditional" and c["conditions"][0]["state"] != "Personalizado"
    ]
    for c in preset_cards:
        assert c["stat_type"] == "mean"


def test_summary_row_fuel_uses_change():
    row = summary_row()
    fuel_col = row["cards"][1]
    preset_cards = [
        c["card"]
        for c in fuel_col["cards"]
        if c.get("type") == "conditional" and c["conditions"][0]["state"] != "Personalizado"
    ]
    for c in preset_cards:
        assert c["stat_type"] == "change"


def test_apexcharts_card_structure():
    card = apexcharts_card("Bateria", BATTERY_ENTITY, "#4CAF50", "%")
    assert card["type"] == "custom:apexcharts-card"
    assert card["series"][0]["entity"] == BATTERY_ENTITY
    assert card["series"][0]["color"] == "#4CAF50"
    assert "graph_span" in card
    assert GRAPH_SPAN_TEMPLATE in card["graph_span"] or card["graph_span"] == GRAPH_SPAN_TEMPLATE


def test_build_dashboard_config_has_one_view():
    config = build_dashboard_config()
    assert config["title"] == "Carros"
    assert len(config["views"]) == 1


def test_build_dashboard_config_has_five_cards():
    config = build_dashboard_config()
    cards = config["views"][0]["cards"]
    assert len(cards) == 5  # period_selector, summary_row, battery, fuel, speed


def test_build_dashboard_config_cards_order():
    config = build_dashboard_config()
    cards = config["views"][0]["cards"]
    assert cards[0]["type"] == "entities"          # period selector
    assert cards[1]["type"] == "horizontal-stack"  # summary row
    assert cards[2]["type"] == "custom:apexcharts-card"  # battery
    assert cards[3]["type"] == "custom:apexcharts-card"  # fuel
    assert cards[4]["type"] == "custom:apexcharts-card"  # speed
```

- [ ] **Step 2: Rodar tests — devem passar**

```bash
cd ha-dashboard
python -m pytest test_config.py -v
```

Expected:
```
test_config.py::test_period_selector_has_input_select PASSED
test_config.py::test_period_selector_has_datetime_conditionals PASSED
test_config.py::test_summary_row_has_three_columns PASSED
test_config.py::test_summary_row_speed_uses_mean PASSED
test_config.py::test_summary_row_fuel_uses_change PASSED
test_config.py::test_apexcharts_card_structure PASSED
test_config.py::test_build_dashboard_config_has_one_view PASSED
test_config.py::test_build_dashboard_config_has_five_cards PASSED
test_config.py::test_build_dashboard_config_cards_order PASSED
9 passed
```

- [ ] **Step 3: Commit**

```bash
git add ha-dashboard/test_config.py
git commit -m "test: add unit tests for dashboard card config"
```

---

## Task 6: create_dashboard.py — Orchestrator

**Files:**
- Create: `ha-dashboard/create_dashboard.py`

- [ ] **Step 1: Criar create_dashboard.py**

```python
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
```

- [ ] **Step 2: Testar dry-run (sem chamar HA)**

```bash
cd ha-dashboard
python create_dashboard.py --dry-run
```

Expected: JSON do dashboard config + automações impresso, sem erros.

- [ ] **Step 3: Commit**

```bash
git add ha-dashboard/create_dashboard.py
git commit -m "feat: add create_dashboard.py orchestrator with dry-run mode"
```

---

## Task 7: Run Against HA + Verify

- [ ] **Step 1: Rodar o script real**

```bash
cd ha-dashboard
python create_dashboard.py
```

Expected output:
```
Pinging HA...
  [ok] HA is reachable

[1/4] Creating helpers...
  [created] input_datetime.jaecoo_chart_start
  [created] input_datetime.jaecoo_chart_end
  [created] input_select.jaecoo_period

[2/4] Creating automations...
  [created/updated] automation.jaecoo_period_hoje
  [created/updated] automation.jaecoo_period_7dias
  [created/updated] automation.jaecoo_period_30dias

[3/4] Creating dashboard...
  [ws] Authenticated to ha.mybarraco.duckdns.org
  [created] Dashboard 'Carros' at /carros

[4/4] Saving dashboard config...
  [saved] Dashboard config for 'carros'

✓ Done! Open https://ha.mybarraco.duckdns.org/carros to see your dashboard.
```

- [ ] **Step 2: Verificar no HA**

1. Abrir `https://ha.mybarraco.duckdns.org/carros`
2. Confirmar que dashboard "Carros" aparece no sidebar
3. Confirmar que seletor de período (Hoje/7 dias/30 dias/Personalizado) está visível
4. Mudar para "7 dias" → campos de data mudam automaticamente
5. Confirmar que 3 gráficos apexcharts aparecem (podem estar vazios se carro não conectado)

- [ ] **Step 3: Se algum helper já existir em 2ª execução — rodar novamente**

```bash
python create_dashboard.py
```

Expected: todos os passos mostram `[skip]` para entidades existentes. Nenhum erro.

- [ ] **Step 4: Commit final**

```bash
git add -A
git commit -m "feat: HA dashboard Carros — helpers, automations, apexcharts cards

Dashboard view única com:
- Seletor Hoje/7d/30d/Personalizado + datepicker
- Resumo: velocidade média, consumo combustível, consumo bateria PHEV
- Gráficos apexcharts-card com graph_span dinâmico (Jinja2)
- Script idempotente: re-execução segura"
git push
```

---

## Notas de Troubleshooting

**Se os gráficos apexcharts mostrarem 'Error: custom:apexcharts-card not found':**
- Instalar via HACS: HACS → Frontend → apexcharts-card → Install
- Reiniciar o HA

**Se `graph_span` template não funcionar (gráfico não muda com período):**
- Verificar versão do apexcharts-card (precisa v2.1+)
- Fallback: substituir `GRAPH_SPAN_TEMPLATE` por `"24h"` e usar conditional cards por período

**Se os helpers não forem criados (erro 400 no config flow):**
- Os campos do form podem variar entre versões do HA. Criar manualmente: HA → Settings → Devices & Services → Helpers → Create Helper → Date/Time (date only) e input Select
- Nomes exatos: "Jaecoo Chart Start", "Jaecoo Chart End", "Jaecoo Period"

**Se automação não disparar:**
- HA → Settings → Automations → procurar "Jaecoo"
- Verificar que automation reload foi feito

**Os cards `statistic` mostram valores negativos para consumo:**
- Esperado: nível caiu de 15% → 12% = -3%. O negativo indica consumo.
- Para mostrar positivo, adicionar no futuro um template sensor que multiplica por -1.
