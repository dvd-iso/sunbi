# Instalar, parear e desenvolver

Guia técnico do SunBi. A apresentação do app está no [README](../README.md).

## 1. Celular e Meta Quest

1. Na aba **Releases** do repositório, baixe o `sunbi.apk` mais recente. (Builds de teste ficam
   na aba **Actions**, no artefato **sunbi-apk**.)
2. **Celular:** abra o APK e permita instalar de fontes desconhecidas.
3. **Quest 3/3S:** ative o modo desenvolvedor no app Meta Horizon e instale com SideQuest ou
   `adb install -r sunbi.apk`. O app aparece em **Biblioteca → Fontes desconhecidas**.
4. Abra o app: o **tour inicial** monta a sua rotina em oito perguntas. Depois, em
   **Ajustes**, resolva os avisos de permissão (notificações, alarmes exatos e tela cheia).

Todo APK das Releases instala por cima do anterior, porque é assinado sempre com a mesma chave privada.

## 2. PC com Noctalia

O plugin não precisa de daemon nem de Python: tudo roda dentro do Noctalia.

```sh
git clone git@github.com:dvd-iso/sunbi.git ~/Projects/sunbi
mkdir -p ~/.local/share/noctalia/plugins
ln -s ~/Projects/sunbi/noctalia/sunbi ~/.local/share/noctalia/plugins/sunbi
noctalia msg plugins enable dvd-iso/sunbi
```

Depois adicione o widget `dvd-iso/sunbi:bar` na barra (Settings → Bar). Os dados ficam em
`~/.local/state/noctalia/plugins/data/dvd-iso/sunbi/`.

Para achar o celular e o Quest sozinho, o plugin usa o `avahi-browse` (pacote **avahi**; no
NixOS, `services.avahi.enable = true`). Sem ele, cadastre os aparelhos à mão (abaixo).

## 3. Parear os aparelhos

Todos precisam do **mesmo código de pareamento**. No primeiro aparelho ele é gerado sozinho e
aparece em **Ajustes → Sincronização**; nos outros, o tour inicial tem a opção
**"Já uso o SunBi em outro aparelho"**, que pede só o código. No plugin, o código fica em
**Settings → Plugins → SunBi → Código de pareamento** e no rodapé do painel.

Os aparelhos aparecem no rodapé do painel com uma bolinha. No app, o PC aparece como
"conectado" depois da primeira troca. Se a rede bloquear a descoberta automática (redes de
visitante, alguns roteadores), copie o endereço que o app mostra em **Ajustes → Sincronização**
("Este aparelho: 192.168.0.20:47321") para **Aparelhos manuais** nas configurações do plugin.
Com Tailscale, o mesmo vale para sincronizar fora de casa.

## 4. Modo complexo (configurar pelo computador)

Em **Ajustes → Modo complexo**, ligue o interruptor. O app mostra um endereço
(`http://192.168.0.20:47321/`) e um **PIN de 6 dígitos**. Abra o endereço no navegador de um
computador na mesma rede e digite o PIN. A página edita as duas rotinas, os sinais de alerta,
os planos, as pessoas, os registros dos últimos dias e os ajustes, e faz **backup** (exportar e
importar um JSON).

A página só responde com o modo ligado; cinco PINs errados desligam o modo. O tráfego é HTTP
na rede local, sem criptografia: ligue só enquanto usa e desligue depois.

## Como funciona a sincronização

Cada registro tem valor, horário e aparelho. Quando dois aparelhos conversam, cada um fica com
a versão mais recente de cada registro. A ordem das trocas não importa, e um aparelho que ficou
desligado recebe tudo quando volta. O celular e o Quest têm um pequeno servidor; o plugin do
Noctalia só chama os dois (a cada 30 s e logo depois de cada mudança feita no PC), e cada
chamada troca os dados nos dois sentidos. Detalhes em [PROTOCOL.md](PROTOCOL.md).

## Limitações conhecidas

- O Quest só toca o alarme se estiver ligado; com o headset dormindo, o celular e o PC cobrem.
- O Quest não faz ligações: no botão de ajuda, os números aparecem para você ligar de outro aparelho.
- No celular, a sincronização em segundo plano usa uma notificação fixa ("Sincronizando..."),
  que pode ser silenciada nas configurações de notificação do app.
- A rotina, os sinais, o plano, os contatos e o check-in valem para todos os aparelhos. O cronômetro
  de foco, a chave da SunAI, a conversa com ela e o PIN do modo complexo são de cada aparelho.

## Desenvolvimento

```sh
# plugin: módulo de dados e testes (luau CLI)
cd noctalia/sunbi && luau tests/store_test.luau && noctalia plugins lint .

# app (Android SDK com platforms;android-37.0 e build-tools;37.0.0, Gradle 9)
cd android
gradle :app:testDebugUnitTest   # lógica + capturas das telas em app/build/shots/
gradle assembleDebug
```

Os testes de tela rodam no JVM com Robolectric (gráficos nativos) e salvam PNGs das abas, do
tour, das conquistas, do check-in, da SunAI, do alarme e do relatório em tema claro, escuro, no
layout largo do Quest e num celular pequeno com fonte grande. O CI publica essas imagens no
artefato **sunbi-telas**; as do README ficam em `docs/telas/`.

Os sons (`android/app/src/main/res/raw/*.ogg` e `noctalia/sunbi/sounds/*.wav`) são sintetizados
por `tools/sounds.py`, sem amostras de terceiros. As vibrações são composições do próprio
Android (`Haptics.kt`).

### Estrutura do app

| Arquivo | O que faz |
| --- | --- |
| `Repo.kt` | registros LWW, rotinas, folga/férias, check-in, plano, contatos; `Prefs` locais |
| `Onboarding.kt` | respostas do tour e o montador de rotina (`RoutineBuilder`) |
| `Game.kt` | XP, níveis, sequência e conquistas, tudo derivado dos registros |
| `Insights.kt` | avisos automáticos (subida, descida, sono, remédios, ritmo) |
| `SunAI.kt` | assistente via OpenCode Go, com ferramentas de rotina e regras |
| `Report.kt`, `ReportPdf.kt`, `Share.kt` | relatório em texto e PDF, envio por WhatsApp |
| `Sync.kt` | servidor HTTP, mDNS e sincronização; `WebConfig.kt` | API da página do modo complexo (`assets/web/index.html`) |
| `Alarms.kt` | alarmes, toque, bloco de foco; `Haptics.kt`, `Sfx.kt` | vibrações e sons |
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

Depois, `git tag v0.6.0 && git push --tags` gera a Release com o `sunbi.apk`. Sem os secrets,
o CI ainda gera APKs de teste (assinados com a chave pública `dev-debug.keystore`), mas se recusa
a publicar uma Release.
