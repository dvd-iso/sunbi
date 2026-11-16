# Protocolo de sincronização P2P do SunBi (v1)

O app (Android e Meta Quest) e o plugin do Noctalia falam o mesmo protocolo. Não existe
servidor central. O app é um par completo (servidor + cliente); o plugin do Noctalia é só
**cliente**: ele não abre porta, só chama os aparelhos, e cada chamada sincroniza os dois lados.

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
| `tasks` | rotina de dias de semana: lista de `{id, time "HH:MM", label, med?, anchor?, step?}` |
| `tasks_off` | rotina de folga (fins de semana, feriados, férias), mesmo formato |
| `vacation` | `{"since": "AAAA-MM-DD"}` enquanto o modo férias está ligado; `null` desligado |
| `off/weekends`, `off/holidays` | `true`/`false`: usar a rotina de folga nos fins de semana / feriados nacionais (padrão `true`) |
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
| `AAAA-MM-DD/off` | `true`/`false`: força rotina de folga ou de semana nesse dia (vence fim de semana e feriado) |
| `AAAA-MM-DD/mood` | escala antiga do Sunflower Clocks (1 a 5); lida como `humor = mood − 3` quando `humor` não existe |

Qual rotina vale num dia: `AAAA-MM-DD/off` se existir; senão folga se o modo férias começou
nesse dia ou antes; senão folga em feriado nacional (com `off/holidays`) ou sábado e domingo
(com `off/weekends`); senão a rotina de semana.

Quem não conhece uma chave apenas a guarda e repassa, então versões diferentes convivem.

Um aparelho recém-instalado **não tem `tasks` nem `tasks_off`** até o tour inicial montar a
rotina (ou até receber a de outro aparelho). O plugin do Noctalia semeia a rotina padrão com
`t = 1`, que perde para qualquer rotina montada ou editada.

## Descoberta

- DNS-SD/mDNS, tipo `_sunbi._tcp.local.`, porta padrão **47321**.
- Registro TXT: `id=<device>`, `v=1`, `name=<nome legível>`.
- Também é possível cadastrar pares manuais (`ip:porta`), útil com Tailscale/VPN.

## HTTP

Todas as rotas exigem o cabeçalho `X-SunBi-Key: <código de pareamento>` (sem diferenciar
maiúsculas). Só o app abre servidor; o plugin do Noctalia é só cliente.

| Rota | Descrição |
| --- | --- |
| `GET /v1/hello` | `{"device", "name", "v": 1}` |
| `GET /v1/state` | estado completo |
| `POST /v1/merge` | corpo = estado do chamador; o servidor mescla e responde com o estado completo já mesclado. Uma única ida e volta sincroniza os dois lados. |

Corpo do `POST /v1/merge`:

```json
{"device": "linux-a2efaa", "name": "nixos", "listen": false, "entries": { ... }}
```

- `name`: nome legível de quem chama, mostrado na lista de aparelhos.
- `listen` (padrão `true`): `false` diz que quem chama **não tem servidor** (o plugin do
  Noctalia). O app então só registra o aparelho como "conectado" e não tenta chamá-lo de volta.
  Com `true`, o app adiciona quem chamou como par, o que ajuda quando o mDNS só funciona num sentido.

## Página do modo complexo (só no app)

Na mesma porta, o app serve uma página de configuração para o navegador, **fora do protocolo
de sync**: ela usa o cabeçalho `X-SunBi-Pin` (PIN de 6 dígitos mostrado em Ajustes) e só
responde com o modo ligado. Cinco PINs errados desligam o modo.

| Rota | Descrição |
| --- | --- |
| `GET /` | a página (HTML) |
| `GET /api/config` | rotinas, sinais, planos, contatos, folga, ajustes e estatísticas |
| `POST /api/config` | corpo com qualquer subconjunto das chaves acima; valida horários e devolve a configuração nova |
| `GET /api/days?n=60` | os últimos `n` dias (humor, sono, níveis, sinais, acontecimentos, anotação, tarefas feitas) |
| `POST /api/day` | `{"day": "AAAA-MM-DD", ...}` edita um dia |
| `GET /api/export` | o estado completo (backup) |
| `POST /api/import` | mescla um backup pela regra LWW; devolve quantos registros mudaram |

## Quando sincronizar

1. Logo após qualquer mudança local: `POST /v1/merge` para todos os pares conhecidos.
2. Ao descobrir um par novo.
3. Periodicamente com todos os pares (o app a cada 60 s, o plugin a cada 30 s), o que cobre
   mudanças perdidas e, para o plugin, traz as mudanças feitas no celular.

## Segurança

O tráfego fica na rede local e **não é criptografado**. O código de pareamento impede que
outros aparelhos da rede leiam ou escrevam os dados, mas não protege contra quem consegue
espionar a rede. Para sincronizar fora de casa, use Tailscale ou WireGuard com pares manuais.
