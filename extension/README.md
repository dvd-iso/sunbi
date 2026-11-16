# SunBi para navegador

A rotina do SunBi no computador: a tarefa da vez, a lista do dia, o humor e **avisos do sistema
na hora de cada tarefa**, com o navegador em segundo plano. Substitui o antigo plugin do Noctalia.

Funciona em qualquer sistema com Chrome, Edge, Brave, Vivaldi ou Opera (e no Firefox, com as
ressalvas abaixo). Não instala programa nenhum e não deixa serviço rodando.

## Instalar

**Chrome e parecidos**

1. Baixe o `sunbi-extensao.zip` das Releases e descompacte (ou use esta pasta).
2. Abra `chrome://extensions`, ligue o **Modo do desenvolvedor** e clique em **Carregar sem
   compactação**. Escolha a pasta.
3. Fixe a flor na barra do navegador.

**Firefox:** `about:debugging#/runtime/this-firefox` → **Carregar extensão temporária** →
`manifest.json`. O Firefox comum descarta extensões não assinadas ao fechar; no Developer
Edition ou ESR, `xpinstall.signatures.required = false` a mantém. Os avisos do Firefox não têm
botão: clicar no aviso marca a tarefa como feita.

## Conectar ao celular

No app: **Ajustes → Sincronização → Conectar o computador**. Digite o **endereço** e o
**código** na janela da extensão. Os dois precisam estar na mesma rede (ou na mesma tailnet).

## O que ela faz por baixo

- Guarda uma cópia dos registros no próprio navegador e chama o celular **a cada minuto** e logo
  depois de cada mudança. Uma chamada troca os dados nos dois sentidos.
- Sem o celular por perto, continua funcionando; o que você marcar segue quando ele voltar.
- Se o celular mudar de endereço na rede, procura por ele nos endereços vizinhos e se reconecta.
- Um alarme por minuto acorda a extensão para avisar das tarefas. Se o app estiver com os
  alarmes em "só neste aparelho", o computador fica quieto.

Para os avisos chegarem com todas as janelas fechadas, deixe o navegador rodar em segundo plano
(Chrome: Configurações → Sistema). Com o navegador encerrado não há aviso.

## Permissões

| Permissão | Para quê |
| --- | --- |
| `storage` | guardar os registros e a conexão |
| `alarms` | acordar a cada minuto |
| `notifications` | os avisos das tarefas |
| `http://*/*` | falar com o celular, que atende por HTTP num endereço da sua rede |

A extensão só chama o endereço que você digitou e, para reencontrar o celular, os vizinhos
dele na mesma rede. Não fala com mais nada.

## Desenvolver

```sh
node --test extension/tests/store.test.mjs
```

`lib/store.js` tem as regras do dia (folga, feriados, dia difícil, mesclagem), iguais às do
app. `background.js` sincroniza e avisa. `popup.*` é a janela. Os ícones saem de `icons/icon.svg`.
