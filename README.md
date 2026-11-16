# 🌻 SunBi

Companheiro de rotina para pessoas com **transtorno bipolar** e **TDAH**. Ajuda a manter o
ritmo do dia, registrar humor e sono em menos de um minuto, perceber sinais de alerta cedo,
seguir o próprio plano e começar tarefas sem travar. Funciona no **celular Android**, no
**Meta Quest 3/3S** e no **PC com Noctalia**, e tudo sincroniza entre os aparelhos, sem servidor
e sem conta: os dados ficam só com você.

Cada função vem de um método com pesquisa por trás (IPSRT, NIMH Life Chart, psicoeducação de
Barcelona, plano de segurança Stanley-Brown, TCC para TDAH). Veja **[docs/METODOS.md](docs/METODOS.md)**
com as evidências, os limites e as referências.

> O SunBi organiza e observa. Ele **não diagnostica e não substitui** psiquiatra, psicólogo ou
> remédio. Em crise, ligue **CVV 188** ou **SAMU 192**.

## O que tem

Cada tela mostra só o essencial; o resto fica a um toque.

| Tela | O que aparece | Um toque abre |
| --- | --- | --- |
| **Hoje** | A tarefa da vez com **Começar** (bloco de foco) e **Feito**, no máximo um aviso, o resumo do check-in e as tarefas pendentes | O **check-in em 3 passos** (humor, sono, detalhes opcionais), as tarefas feitas, outros dias |
| **Histórico** | Gráfico de humor no estilo Life Chart, sono contra o seu normal e três números | O **relatório para a consulta**, pronto para compartilhar |
| **Plano** | O botão **Preciso de ajuda agora** e uma lista curta | Sinais de alerta, o que fazer quando o humor sobe ou desce, plano de segurança, contatos |
| **Ajustes** (engrenagem) | Um cartão com cores, sons e alarmes | O editor da rotina e a sincronização |

Design em **Material 3 Expressive** (formas, movimento com mola, grupos de botões conectados,
indicadores ondulados), com tema claro e escuro, cores dinâmicas do sistema e ícone
adaptativo com versão monocromática para os **ícones temáticos** do Android 13+.
No Quest e em telas largas, a navegação vira um trilho lateral.

Sem sequências que zeram e sem culpa: um dia sem registro não apaga nada.

## Partes do projeto

| Parte | Pasta | O que faz |
| --- | --- | --- |
| App Android / Quest | `android/` | Tudo acima, com alarmes reais **sem soneca** e o toque Girassol |
| Plugin Noctalia v5 | `noctalia/sunbi/` | Roda inteiro no plugin, **sem daemon**: guarda a rotina, sincroniza com o celular e o Quest, toca os alarmes. Barra com `6/13` e foco; painel com agora/próxima, foco, humor, checklist, anotação e aparelhos |
| Protocolo | `docs/PROTOCOL.md` | Como os aparelhos se descobrem e mesclam os dados |
| Métodos | `docs/METODOS.md` | A pesquisa por trás de cada função |

## 1. Instalar no celular e no Quest

1. Na aba **Releases** do repositório, baixe o `sunbi.apk` mais recente. (Builds de teste ficam
   na aba **Actions**, no artefato **sunbi-apk**.)
2. **Celular:** abra o APK e permita instalar de fontes desconhecidas.
3. **Quest 3/3S:** ative o modo desenvolvedor no app Meta Horizon e instale com SideQuest ou
   `adb install sunbi.apk`. O app aparece em **Biblioteca → Fontes desconhecidas**.
4. Abra o app, permita notificações e, em **Ajustes → Alarmes**, resolva os avisos de
   permissão (alarmes exatos e tela cheia).

Todo APK das Releases instala por cima do anterior, porque é assinado sempre com a mesma chave privada.

### Vindo do Sunflower Clocks

O SunBi é um app novo no Android (`com.dvdiso.sunbi`), então ele aparece ao lado do antigo
e começa com a rotina padrão. Depois de instalar, desinstale o Sunflower Clocks e, se você
usava o `sunflowerd` no PC, desative-o: `systemctl --user disable --now sunflowerd`.

## 2. Instalar no PC (Noctalia)

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

Todos precisam do **mesmo código de pareamento**. O plugin gera um e mostra no rodapé do
painel; no app, vá em **Ajustes → Sincronização → Código de pareamento**, digite o mesmo
código e toque em **Salvar e reconectar**. (Ou o contrário: copie o código do app para
**Settings → Plugins → SunBi → Código de pareamento**.)

Os aparelhos aparecem no rodapé do painel com uma bolinha. No app, o PC aparece como
"conectado" depois da primeira troca. Se a rede bloquear a descoberta automática (redes de
visitante, alguns roteadores), copie o endereço que o app mostra em **Ajustes → Sincronização**
("Este aparelho: 192.168.0.20:47321") para **Aparelhos manuais** nas configurações do plugin.
Com Tailscale, o mesmo vale para sincronizar fora de casa.

## Como funciona a sincronização

Cada registro tem valor, horário e aparelho. Quando dois aparelhos conversam, cada um fica com
a versão mais recente de cada registro. A ordem das trocas não importa, e um aparelho que ficou
desligado recebe tudo quando volta. O celular e o Quest têm um pequeno servidor; o plugin do
Noctalia só chama os dois (a cada 30 s e logo depois de cada mudança feita no PC), e cada
chamada troca os dados nos dois sentidos. Detalhes em [`docs/PROTOCOL.md`](docs/PROTOCOL.md).

**Privacidade:** os dados ficam só nos seus aparelhos. O tráfego na rede local não é
criptografado; o código de pareamento impede que outros aparelhos leiam ou escrevam, mas use
Tailscale/WireGuard fora de casa.

## Limitações conhecidas

- O Quest só toca o alarme se estiver ligado; com o headset dormindo, o celular e o PC cobrem.
- O Quest não faz ligações: no botão de ajuda, os números aparecem para você ligar de outro aparelho.
- No celular, a sincronização em segundo plano usa uma notificação fixa ("Sincronizando..."),
  que pode ser silenciada nas configurações de notificação do app.
- A rotina, os sinais, o plano e os contatos valem para todos os aparelhos; o check-in é por dia.
  O cronômetro de foco é de cada aparelho.

## Desenvolvimento

```sh
# plugin: módulo de dados e testes (luau CLI)
cd noctalia/sunbi && luau tests/store_test.luau && noctalia plugins lint .

# app (Android SDK com platforms;android-37.0 e build-tools;37.0.0, Gradle 9)
cd android
gradle :app:testDebugUnitTest   # lógica + capturas das telas em app/build/shots/
gradle assembleDebug
```

Os testes de tela rodam no JVM com Robolectric (gráficos nativos) e salvam PNGs das abas em
tema claro, escuro, no layout largo do Quest e num celular pequeno com fonte grande. O CI
publica essas imagens no artefato **sunbi-telas**.

Os sons (`android/app/src/main/res/raw/*.ogg` e `noctalia/sunbi/sounds/*.wav`) são sintetizados
por `tools/sounds.py`, sem amostras de terceiros.

## Publicar uma versão

As Releases são assinadas com uma chave privada que **não** fica no repositório. Uma vez só:

```sh
keytool -genkeypair -v -keystore sunbi-release.jks -alias sunbi -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 sunbi-release.jks   # copie a saída
```

Em **Settings → Secrets and variables → Actions**, crie `SUNBI_KEYSTORE_BASE64` (a saída acima),
`SUNBI_KEYSTORE_PASSWORD`, `SUNBI_KEY_ALIAS` (`sunbi`) e `SUNBI_KEY_PASSWORD`. Guarde o `.jks`
num lugar seguro: sem ele, versões futuras não instalam por cima.

Depois, `git tag v0.2.0 && git push --tags` gera a Release com o `sunbi.apk`. Sem os secrets,
o CI ainda gera APKs de teste (assinados com a chave pública `dev-debug.keystore`), mas se recusa
a publicar uma Release.
