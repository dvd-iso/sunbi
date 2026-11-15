# Protocolo de sincronização P2P (v1)

Todos os aparelhos (Android, Meta Quest 3S e o daemon `sunflowerd` do Linux) falam
o mesmo protocolo. Não existe servidor central: cada aparelho é um par igual aos outros.

## Modelo de dados

O estado é um mapa de **registros last-writer-wins (LWW)**:

```json
{
  "device": "android-3f9c1a",
  "entries": {
    "tasks":                 {"v": [ ...lista de tarefas... ], "t": 1763250000000, "d": "android-3f9c1a"},
    "2026-11-15/done/t0700": {"v": true,  "t": 1763250100000, "d": "linux-glados"},
    "2026-11-15/mood":       {"v": 3,     "t": 1763250200000, "d": "quest-7a21"},
    "2026-11-15/sleep":      {"v": 6.5,   "t": 1763250300000, "d": "quest-7a21"},
    "2026-11-15/note":       {"v": "...", "t": 1763250400000, "d": "android-3f9c1a"}
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

| Chave | Valor |
| --- | --- |
| `tasks` | lista de `{id, time "HH:MM", label, med?}` |
| `AAAA-MM-DD/done/<id>` | `true` / `false` |
| `AAAA-MM-DD/mood` | 1 a 5 (muito baixo, baixo, estável, agitado, muito agitado) |
| `AAAA-MM-DD/sleep` | horas de sono, número |
| `AAAA-MM-DD/note` | texto livre |

## Descoberta

- DNS-SD/mDNS, tipo `_sunflower._tcp.local.`, porta padrão **47321**.
- Registro TXT: `id=<device>`, `v=1`, `name=<nome legível>`.
- Também é possível cadastrar pares manuais (`ip:porta`), útil com Tailscale/VPN.

## HTTP

Todas as rotas exigem o cabeçalho `X-Sunflower-Key: <código de pareamento>`,
exceto chamadas vindas de `127.0.0.1` ao daemon do Linux.

| Rota | Descrição |
| --- | --- |
| `GET /v1/hello` | `{"device", "name", "v": 1}` |
| `GET /v1/state` | estado completo |
| `POST /v1/merge` | corpo = estado do chamador; o servidor mescla e responde com o estado completo já mesclado. Uma única ida e volta sincroniza os dois lados. |

Rotas só do daemon do Linux, só em `127.0.0.1`, usadas pelo plugin do Noctalia:

| Rota | Descrição |
| --- | --- |
| `GET /v1/day?date=AAAA-MM-DD` | dia já resolvido: tarefas, feitas, humor, sono, nota, pares |
| `POST /v1/set` | `{"key", "value"}`: grava com o horário atual e empurra para os pares |

## Quando sincronizar

1. Logo após qualquer mudança local: `POST /v1/merge` para todos os pares conhecidos.
2. Ao descobrir um par novo.
3. A cada 60 segundos, com todos os pares (cobre mudanças perdidas).

## Segurança

O tráfego fica na rede local e **não é criptografado**. O código de pareamento impede que
outros aparelhos da rede leiam ou escrevam os dados, mas não protege contra quem consegue
espionar a rede. Para sincronizar fora de casa, use Tailscale ou WireGuard com pares manuais.
