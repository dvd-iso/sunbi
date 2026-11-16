# Protocolo de sincronização P2P do SunBi (v1)

Todos os aparelhos (Android, Meta Quest e o daemon `sunbid` do Linux) falam
o mesmo protocolo. Não existe servidor central: cada aparelho é um par igual aos outros.

## Modelo de dados

O estado é um mapa de **registros last-writer-wins (LWW)**:

```json
{
  "device": "android-3f9c1a",
  "entries": {
    "tasks":                 {"v": [ ...lista de tarefas... ], "t": 1763250000000, "d": "android-3f9c1a"},
    "2026-11-15/done/wake":  {"v": "07:12", "t": 1763250100000, "d": "linux-glados"},
    "2026-11-15/humor":      {"v": -1,      "t": 1763250200000, "d": "quest-7a21"},
    "2026-11-15/bed":        {"v": "23:40", "t": 1763250300000, "d": "quest-7a21"},
    "2026-11-15/note":       {"v": "...",   "t": 1763250400000, "d": "android-3f9c1a"}
  }
}
```

- `v`: valor (qualquer JSON; `null` apaga).
- `t`: momento da escrita, em milissegundos Unix.
- `d`: id do aparelho que escreveu.

### Regra de mesclagem

Para cada chave, vence o registro com o maior par `(t, d)`; empate em `t` é decidido
pela comparação de texto de `d`. A regra é comutativa, associativa e idempotente,
então qualquer ordem de trocas leva todos os aparelhos ao mesmo estado.

### Chaves

Globais (valem para todos os aparelhos):

| Chave | Valor |
| --- | --- |
| `tasks` | lista de `{id, time "HH:MM", label, med?, anchor?, step?, min?}` |
| `signs` | lista de `{id, label, pole: "up" \| "down"}`: sinais de alerta pessoais |
| `plan/<seção>` | lista de textos. Seções: `up`, `down` (plano de ação), `warn`, `cope`, `distract`, `safe`, `reasons` (plano de segurança) |
| `contacts` | lista de `{name, role, phone}` |

Por dia:

| Chave | Valor |
| --- | --- |
| `AAAA-MM-DD/done/<id>` | `"HH:MM"` (feito, com o horário real), `true` (feito, sem horário) ou `false` |
| `AAAA-MM-DD/humor` | −3 a +3 (−3 muito para baixo, 0 estável, +3 muito acelerado) |
| `AAAA-MM-DD/bed`, `/wake` | `"HH:MM"`: hora de deitar (noite anterior) e de acordar |
| `AAAA-MM-DD/sleep` | horas de sono, número (calculado de `bed`/`wake` quando existem) |
| `AAAA-MM-DD/energy`, `/focus` | 1 a 5 |
| `AAAA-MM-DD/irrit`, `/anx` | 0 a 3 |
| `AAAA-MM-DD/signs` | lista de ids de `signs` que apareceram no dia |
| `AAAA-MM-DD/events` | lista de acontecimentos: `stress`, `conflict`, `late`, `allnight`, `travel`, `alcohol`, `caffeine`, `screens`, `sick`, `period`, `medchange`, `good` |
| `AAAA-MM-DD/note` | texto livre |
| `AAAA-MM-DD/mood` | escala antiga do Sunflower Clocks (1 a 5); lida como `humor = mood − 3` quando `humor` não existe |

Quem não conhece uma chave apenas a guarda e repassa, então versões diferentes convivem.

## Descoberta

- DNS-SD/mDNS, tipo `_sunbi._tcp.local.`, porta padrão **47321**.
- Registro TXT: `id=<device>`, `v=1`, `name=<nome legível>`.
- Também é possível cadastrar pares manuais (`ip:porta`), útil com Tailscale/VPN.

## HTTP

Todas as rotas exigem o cabeçalho `X-SunBi-Key: <código de pareamento>`,
exceto chamadas vindas de `127.0.0.1` ao daemon do Linux.

| Rota | Descrição |
| --- | --- |
| `GET /v1/hello` | `{"device", "name", "v": 1}` |
| `GET /v1/state` | estado completo |
| `POST /v1/merge` | corpo = estado do chamador; o servidor mescla e responde com o estado completo já mesclado. Uma única ida e volta sincroniza os dois lados. |

Rotas só do daemon do Linux, só em `127.0.0.1`, usadas pelo plugin do Noctalia:

| Rota | Descrição |
| --- | --- |
| `GET /v1/day?date=AAAA-MM-DD` | dia já resolvido: tarefas, `done`, `doneAt`, `humor`, `humorLabel`, `energy`, `focus`, `sleep`, `bed`, `wake`, `signs`, `events`, `note`, pares |
| `POST /v1/set` | `{"key", "value"}`: grava com o horário atual e empurra para os pares |

## Quando sincronizar

1. Logo após qualquer mudança local: `POST /v1/merge` para todos os pares conhecidos.
2. Ao descobrir um par novo.
3. A cada 60 segundos, com todos os pares (cobre mudanças perdidas).

## Segurança

O tráfego fica na rede local e **não é criptografado**. O código de pareamento impede que
outros aparelhos da rede leiam ou escrevam os dados, mas não protege contra quem consegue
espionar a rede. Para sincronizar fora de casa, use Tailscale ou WireGuard com pares manuais.
