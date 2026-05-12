# HA Dashboard "Carros" — Design Spec

**Date:** 2026-05-12
**Goal:** Dashboard Home Assistant "Carros" para acompanhar Jaecoo 7 (PHEV) via dados OBD2/MQTT.

---

## 1. Contexto

Dados publicados pelo app `odb-scanner-mqtt` via MQTT Discovery. Principais entidades disponíveis:

| Entidade | Descrição | Unidade |
|---|---|---|
| `sensor.jaecoo7_jaecoo7_speed` | Velocidade | km/h |
| `sensor.jaecoo7_jaecoo7_control_module_voltage` | Tensão bateria 12V | V |
| `sensor.j7_j7_hybrid_battery` | Bateria de tração PHEV | % |
| `sensor.j7_j7_fuel_level` | Nível combustível | % |
| `sensor.jaecoo7_jaecoo7_rpm` | RPM | rpm |
| `sensor.jaecoo7_jaecoo7_coolant_temp` | Temperatura do motor | °C |
| `sensor.jaecoo7_jaecoo7_engine_load` | Carga do motor | % |

---

## 2. Helpers a Criar

### 2a. utility_meter (consumo por período)

Calculam delta automático (início → fim do ciclo):

| Helper | Fonte | Ciclo |
|---|---|---|
| `sensor.jaecoo_fuel_daily` | `j7_j7_fuel_level` | day |
| `sensor.jaecoo_fuel_weekly` | `j7_j7_fuel_level` | week |
| `sensor.jaecoo_fuel_monthly` | `j7_j7_fuel_level` | month |
| `sensor.jaecoo_battery_daily` | `j7_j7_hybrid_battery` | day |
| `sensor.jaecoo_battery_weekly` | `j7_j7_hybrid_battery` | week |
| `sensor.jaecoo_battery_monthly` | `j7_j7_hybrid_battery` | month |

### 2b. input_datetime (datepicker)

| Helper | Descrição | Padrão |
|---|---|---|
| `input_datetime.jaecoo_chart_start` | Início do período (date only) | hoje |
| `input_datetime.jaecoo_chart_end` | Fim do período (date only) | hoje |

### 2c. input_select (atalhos de período)

| Helper | Opções |
|---|---|
| `input_select.jaecoo_period` | Hoje / 7 dias / 30 dias / Personalizado |

### 2d. Automações

Acionadas ao mudar `input_select.jaecoo_period`:

| Trigger | Ação |
|---|---|
| "Hoje" | `chart_start` = hoje, `chart_end` = hoje |
| "7 dias" | `chart_start` = hoje-7d, `chart_end` = hoje |
| "30 dias" | `chart_start` = hoje-30d, `chart_end` = hoje |
| "Personalizado" | Não altera — usuário edita os campos manualmente |

---

## 3. Layout do Dashboard

**Nome:** Carros  
**Modo:** storage (editável via UI)  
**View:** única, scroll vertical

### Cards (de cima para baixo):

**Card 1 — Seletor de período**
- `entities` card com `input_select.jaecoo_period`
- Dois `input_datetime` (start + end) lado a lado
- Visíveis só quando período = "Personalizado" (via conditional card)

**Card 2 — Resumo (3 colunas)**
- Velocidade média do período: `statistic` mean de `jaecoo7_jaecoo7_speed`
- Consumo combustível: exibe `jaecoo_fuel_daily`, `_weekly` ou `_monthly` conforme `input_select.jaecoo_period` (via 3 `conditional` cards sobrepostos)
- Consumo bateria PHEV: mesma lógica com `jaecoo_battery_daily/weekly/monthly`

**Card 3 — Gráfico bateria PHEV**
- `apexcharts-card`
- Fonte: `j7_j7_hybrid_battery`
- Range: `input_datetime.jaecoo_chart_start` → `jaecoo_chart_end`
- Tipo: área, cor: verde/laranja conforme nível

**Card 4 — Gráfico combustível**
- `apexcharts-card`
- Fonte: `j7_j7_fuel_level`
- Range: mesmo range dinâmico
- Tipo: área

**Card 5 — Gráfico velocidade**
- `apexcharts-card`
- Fonte: `jaecoo7_jaecoo7_speed`
- Range: mesmo range dinâmico
- Tipo: linha

---

## 4. Script de Entrega

Arquivo: `ha-dashboard/create_dashboard.py`

**Stack:** Python 3 + `websockets` (única dependência externa)

**Configuração via variáveis no topo do script:**
```python
HA_HOST = "ha.mybarraco.duckdns.org"
HA_TOKEN = "..."
```

**Sequência de execução:**
1. Conecta WebSocket `wss://<host>/api/websocket`
2. Autentica com token
3. Cria helpers (utility_meter × 6, input_datetime × 2, input_select × 1) — idempotente
4. Cria automações de período (× 4) — idempotente
5. Cria dashboard "Carros" via `lovelace/dashboards/create`
6. Salva config (cards YAML) via `lovelace/config/save`
7. Imprime status de cada passo

**Idempotência:** verifica se helper/dashboard já existe antes de criar. Re-execução segura.

**Tempo esperado:** ~30 segundos

---

## 5. Dependências HACS

| Custom Card | Uso |
|---|---|
| `apexcharts-card` | Todos os 3 gráficos com range dinâmico |
| `custom:button-card` (opcional) | Cards de resumo com estilo customizado |

`apexcharts-card` deve estar instalado via HACS antes de rodar o script.

---

## 6. Entidades Não Usadas (por enquanto)

Disponíveis para expansão futura:
- `jaecoo7_jaecoo7_coolant_temp` — temperatura do motor
- `jaecoo7_jaecoo7_rpm` — RPM
- `j7_j7_fuel_rail_gauge_pressure` — pressão combustível
- `j7_j7_o2_lambda_ratio_afr` — lambda (AFR)
- `j7_j7_odometer` — odômetro (fórmula com bug pendente de correção)
