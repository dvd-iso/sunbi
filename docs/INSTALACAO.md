# Instalar, parear e desenvolver

Guia técnico do SunBi. A apresentação do app está no [README](../README.md).

## 1. Celular

1. Na aba **Releases** do repositório, baixe o `sunbi.apk` mais recente. (Builds de teste ficam
   na aba **Actions**, no artefato **sunbi-apk**.)
2. Abra o APK e permita instalar de fontes desconhecidas.
3. Abra o app: o **tour inicial** monta a sua rotina em onze perguntas (o que te trouxe, sono,
   folga, remédios, o que ocupa o dia, refeições, extras, lembretes e se você quer a assistente
   de IA). Depois, em **Ajustes**, resolva os avisos de permissão
   (notificações, alarmes exatos e tela cheia).

Todo APK das Releases instala por cima do anterior, porque é assinado sempre com a mesma chave privada.

## 2. Computador: extensão do navegador

A extensão (`extension/`) mostra a rotina do dia, marca tarefas e humor, e manda **avisos do
sistema** na hora de cada tarefa, com o navegador em segundo plano. Não tem programa para
instalar nem serviço para manter rodando.

**Chrome, Edge, Brave, Vivaldi, Opera:**

1. Baixe o `sunbi-extensao.zip` da aba **Releases** e descompacte numa pasta que não vá apagar
   (ou use a pasta `extension/` deste repositório).
2. Abra `chrome://extensions`, ligue o **Modo do desenvolvedor** e clique em **Carregar sem
   compactação**. Escolha a pasta.
3. Fixe o ícone da flor na barra do navegador.

**Firefox:** em `about:debugging#/runtime/this-firefox`, **Carregar extensão temporária** e
escolha o `manifest.json`. O Firefox comum só mantém extensões assinadas, então ela sai ao
fechar o navegador; para ficar fixa, use o Firefox Developer Edition ou ESR com
`xpinstall.signatures.required = false`. No Firefox o aviso não tem botão: clicar nele marca a
tarefa como feita.

Para os avisos chegarem com todas as janelas fechadas, deixe o navegador continuar em segundo
plano (Chrome: Configurações → Sistema → "Continuar executando aplicativos em segundo plano").
Com o navegador totalmente fechado não há aviso no computador; o celular continua tocando.

## 3. Parear

**Computador:** no app, abra **Ajustes → Sincronização**. O cartão **Conectar o computador**
mostra o **endereço** (`192.168.0.20:47321`) e o **código**. Clique na flor no navegador,
digite os dois e pronto. O computador aparece no app como "conectado".

A extensão chama o celular a cada minuto e logo depois de cada mudança. Se o celular não
responder (tela apagada em economia de Wi-Fi, fora de casa), ela segue funcionando com o que
tem e envia tudo quando ele voltar. Se o roteador der outro endereço ao celular, ela procura
por ele na mesma rede e se reconecta sozinha; o botão **Sincronizar** força essa busca na hora.
Com Tailscale, use o endereço do celular na tailnet para sincronizar fora de casa.

**Outro celular ou tablet:** todos precisam do **mesmo código de pareamento**. No segundo
aparelho, o tour inicial tem a opção **"Já uso o SunBi em outro aparelho"**, que pede só o
código. Eles se acham sozinhos na mesma rede Wi-Fi (mDNS); se a rede bloquear a descoberta
(redes de visitante, alguns roteadores), adicione o `ip:porta` do outro em Ajustes → Sincronização.

## 4. Modo avançado (configurar pelo computador)

Em **Ajustes → Modo avançado**, ligue o interruptor. O app mostra um endereço
(`http://192.168.0.20:47321/`) e um **PIN de 6 dígitos**. Abra o endereço no navegador de um
computador na mesma rede e digite o PIN. Com a extensão instalada, o botão **Abrir o modo avançado** (nos ajustes dela) abre a página direto. A página edita as duas rotinas, os sinais de alerta,
os planos, as pessoas, os registros dos últimos dias e os ajustes, e faz **backup** (exportar e
importar um JSON).

O endereço precisa começar com `http://`: navegadores que completam sozinhos com `https://` (Chrome, Helium) dão erro de conexão. A página só responde com o modo ligado; desliga sozinha depois de 1 hora sem uso, e cinco PINs errados também desligam. O tráfego é HTTP
na rede local, sem criptografia: ligue só enquanto usa e desligue depois.

## 5. Tela inicial (modo launcher)

Em **Ajustes → Tela inicial**, ligue **"Usar o SunBi como tela inicial"** e toque em **"Definir
como tela inicial padrão"**: o Android abre o próprio diálogo de escolha (papel `HOME`). Para
voltar ao launcher antigo, desligue o interruptor ou troque em Ajustes do Android → Apps
padrão → App de início. A atividade de tela inicial fica desligada no manifesto até a pessoa
ligar, então o SunBi não aparece na lista de launchers de quem não usa o recurso.

O launcher é código próprio do SunBi (MIT). O comportamento (favoritos em texto, busca que abre
sozinha, esconder apps) segue a linha do [Olauncher](https://github.com/tanujnotes/Olauncher),
que é GPL-3.0 e por isso não foi copiado.

## 6. Extras que dependem do sistema

- **Widget:** segure a tela inicial do Android → Widgets → "SunBi · Agora". Atualiza a cada
  mudança, a cada alarme e a cada meia hora.
- **Sono do relógio:** precisa do app Health Connect (embutido no Android 14+) e de um relógio
  ou pulseira que grave sessões de sono nele. A permissão de leitura é pedida no primeiro
  "Puxar do relógio". Não foi testado em aparelho real nesta versão.
- **Assistente:** é opcional. Recusada no tour ou desligada em **Ajustes → Assistente de IA**,
  a aba some e nada é enviado. Ligada, funciona com qualquer API no formato da OpenAI. O app traz OpenCode Go,
  OpenAI, OpenRouter, Groq e DeepSeek prontos, e a opção **Outro** aceita um endereço próprio
  (Ollama, LM Studio, LiteLLM, Mistral, Together...). Cada provedor guarda a própria chave e o
  próprio modelo; "Buscar os modelos da minha conta" lista o que a chave enxerga. Modelos sem
  suporte a ferramentas conversam, mas não ajustam a rotina.
- **Resumo semanal da assistente:** roda pelo WorkManager (precisa de rede) no domingo às 19h e
  usa a chave deste aparelho; ligue só num aparelho para não receber dois.
- **Lembrete de check-in** e **resumo semanal** usam alarmes inexatos (`setAndAllowWhileIdle`),
  então podem atrasar alguns minutos em modo de economia de bateria.

## Como funciona a sincronização

Cada registro tem valor, horário e aparelho. Quando dois aparelhos conversam, cada um fica com
a versão mais recente de cada registro. A ordem das trocas não importa, e um aparelho que ficou
desligado recebe tudo quando volta. O celular tem um pequeno servidor; a extensão do navegador
só chama o celular (a cada minuto e logo depois de cada mudança feita no computador), e cada
chamada troca os dados nos dois sentidos. Detalhes em [PROTOCOL.md](PROTOCOL.md).

## Limitações conhecidas

- No celular, a sincronização em segundo plano usa uma notificação fixa ("Sincronizando..."),
  que pode ser silenciada nas configurações de notificação do app.
- Com a tela apagada, alguns celulares economizam Wi-Fi e demoram a responder ao computador.
  Nada se perde: a extensão tenta de novo a cada minuto.
- A extensão pede acesso a endereços `http://` porque o celular atende por HTTP na rede local e
  o endereço dele não é conhecido de antemão. Ela só chama o endereço que você digitou e, para
  reencontrar o celular, os vizinhos dele na mesma rede.
- A rotina, os sinais, o plano, os contatos e o check-in valem para todos os aparelhos. O cronômetro
  de foco, as chaves da assistente, a conversa com ela e o PIN do modo avançado são de cada aparelho.

## Desenvolvimento

```sh
# extensão: módulo de dados (Node 22+), sem dependências
node --test extension/tests/store.test.mjs

# app (Android SDK com platforms;android-37.0 e build-tools;37.0.0, Gradle 9)
cd android
gradle :app:testDebugUnitTest   # lógica + capturas das telas em app/build/shots/
gradle assembleDebug
```

Os testes de tela rodam no JVM com Robolectric (gráficos nativos) e salvam PNGs das abas, do
tour, das conquistas, do check-in, da assistente, do alarme e do relatório em tema claro, escuro, no
layout largo (tablet) e num celular pequeno com fonte grande. O CI publica essas imagens no
artefato **sunbi-telas**; as do README ficam em `docs/telas/`.

Os sons (`android/app/src/main/res/raw/*.ogg`) são sintetizados
por `tools/sounds.py`, sem amostras de terceiros. As vibrações são composições do próprio
Android (`Haptics.kt`).

### Estrutura da extensão

| Arquivo | O que faz |
| --- | --- |
| `lib/store.js` | registros LWW e as regras do dia (folga, feriados, dia difícil), iguais às do app; puro e testado |
| `background.js` | service worker: guarda em `chrome.storage.local`, sincroniza, reencontra o celular e avisa das tarefas |
| `popup.html`, `popup.js`, `popup.css` | a janela: tarefa da vez, rotina, humor, ajustes |

### Estrutura do app

| Arquivo | O que faz |
| --- | --- |
| `Repo.kt` | registros LWW, rotinas, folga/férias, check-in, plano, contatos; `Prefs` locais |
| `Onboarding.kt` | respostas do tour e o montador de rotina (`RoutineBuilder`) |
| `Game.kt` | XP, níveis, sequência e conquistas, tudo derivado dos registros |
| `Insights.kt` | avisos automáticos (subida, descida, sono, remédios, ritmo) |
| `Assistant.kt` | assistente por qualquer API compatível com OpenAI, com ferramentas de rotina e regras |
| `Report.kt`, `ReportPdf.kt`, `Share.kt` | relatório em texto e PDF, envio por WhatsApp |
| `Sync.kt` | servidor HTTP, mDNS e sincronização; `WebConfig.kt` | API da página do modo avançado (`assets/web/index.html`) |
| `Alarms.kt` | alarmes, toque, bloco de foco; `Haptics.kt`, `Sfx.kt` | vibrações e sons |
| `Reminders.kt` | lembrete de check-in e resumo semanal (WorkManager); `Widget.kt` | widget "Agora" |
| `Launcher.kt`, `ui/LauncherScreen.kt` | modo launcher (sem acessibilidade: o SunBi não usa serviços de acessibilidade) |
| `Health.kt` | sono do relógio pelo Health Connect |
| `ui/` | telas em Compose (Material 3 Expressive) |

## Publicar uma versão

As Releases são assinadas com uma chave privada que **não** fica no repositório. Uma vez só:

```sh
keytool -genkeypair -v -keystore sunbi-release.jks -alias sunbi -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 sunbi-release.jks   # copie a saída
```

Em **Settings → Secrets and variables → Actions**, crie `SUNBI_KEYSTORE_BASE64` (a saída acima),
`SUNBI_KEYSTORE_PASSWORD`, `SUNBI_KEY_ALIAS` (`sunbi`) e `SUNBI_KEY_PASSWORD`. Guarde o `.jks`
num lugar seguro: sem ele, versões futuras não instalam por cima.

Depois, `git tag v0.6.0 && git push --tags` gera a Release com o `sunbi.apk` e o `sunbi-extensao.zip`. Sem os secrets,
o CI ainda gera APKs de teste (assinados com a chave pública `dev-debug.keystore`), mas se recusa
a publicar uma Release.
