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

| Aba | Para quê |
| --- | --- |
| **Hoje** | Cartão **Agora/Próxima** com o tempo visível ("em 35 min") e botão **Começar** (bloco de foco de 5 a 25 min). Avisos gentis quando o sono cai, o humor muda ou os seus sinais aparecem. **Check-in**: humor de −3 a +3, hora de deitar e acordar, energia, foco, irritação, ansiedade, sinais e acontecimentos. **Rotina** com âncoras, remédios, primeiro passo e duração de cada tarefa. |
| **Histórico** | Gráfico de humor no estilo Life Chart, barras de sono contra o seu sono habitual, % de remédios marcados, âncoras no horário, grade da semana e **relatório em texto para a consulta**. |
| **Plano** | Botão **Preciso de ajuda agora**. Seus sinais de subida e de descida, o que fazer em cada caso, plano de segurança em 6 passos e contatos com ligação direta. |
| **Ajustes** | Cores do sistema (Material You) ou paleta Girassol, alarmes, editor da rotina, sincronização. |

Design em **Material 3 Expressive** (formas, movimento com mola, grupos de botões conectados,
indicadores ondulados), com tema claro e escuro, cores dinâmicas do sistema e ícone
adaptativo com versão monocromática para os **ícones temáticos** do Android 13+.
No Quest e em telas largas, a navegação vira um trilho lateral.

Sem sequências que zeram e sem culpa: um dia sem registro não apaga nada.

## Partes do projeto

| Parte | Pasta | O que faz |
| --- | --- | --- |
| App Android / Quest | `android/` | Tudo acima, com alarmes reais **sem soneca** e o toque Girassol |
| Daemon Linux | `linux/sunbid/` | Guarda a rotina no PC e sincroniza com os outros aparelhos |
| Plugin Noctalia v5 | `noctalia/sunbi/` | Barra com `6/13`, próxima tarefa e foco; painel com agora/próxima, foco, humor, checklist e anotação; alarmes no PC |
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

O SunBi é um app novo no Android (`com.dvdiso.sunbi`), então ele aparece ao lado do antigo.
Para não perder nada: atualize o PC primeiro (o `sunbid` copia os dados do `sunflowerd`
sozinho), instale o SunBi nos outros aparelhos, use o mesmo código de pareamento e espere
sincronizar. Depois desinstale o Sunflower Clocks. O humor antigo (1 a 5) é lido na escala
nova (−2 a +2).

## 2. Instalar no PC (NixOS / Fedora)

```sh
# daemon
pipx install ./linux/sunbid            # ou: pip install --user ./linux/sunbid
# no NixOS sem pipx:
nix-shell -p 'python3.withPackages (p: [ p.zeroconf ])' --run 'python3 linux/sunbid/sunbid.py'

# serviço de usuário
mkdir -p ~/.config/systemd/user
cp linux/sunbid/sunbid.service ~/.config/systemd/user/
systemctl --user enable --now sunbid

# plugin do Noctalia
mkdir -p ~/.local/share/noctalia/plugins
ln -s "$PWD/noctalia/sunbi" ~/.local/share/noctalia/plugins/sunbi
```

Depois ative o plugin em **Settings → Plugins** e adicione o widget `dvd-iso/sunbi:bar` na barra.
No NixOS com o serviço declarado no seu config, ajuste o `ExecStart` para o caminho do Nix.
Se você usava o Sunflower Clocks, desative o serviço antigo: `systemctl --user disable --now sunflowerd`.

## 3. Parear os aparelhos

Todos precisam do **mesmo código de pareamento**:

```sh
sunbid key            # mostra o código do PC
sunbid set-key ABC123 # ou define um novo
```

No app: **Ajustes → Sincronização → Código de pareamento**, digite o mesmo código e toque em
**Salvar e reconectar**. Os aparelhos na mesma rede Wi-Fi aparecem com uma bolinha verde.

Se o Wi-Fi bloquear a descoberta automática (redes de visitante, alguns roteadores), adicione o
par manualmente: no app, `ip:47321`; no PC, `sunbid add-peer 192.168.0.20:47321`.
Com Tailscale, o mesmo vale para sincronizar fora de casa.

## Como funciona a sincronização

Cada registro tem valor, horário e aparelho. Quando dois aparelhos conversam, cada um fica com
a versão mais recente de cada registro. A ordem das trocas não importa, e um aparelho que ficou
desligado recebe tudo quando volta. Detalhes em [`docs/PROTOCOL.md`](docs/PROTOCOL.md).

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
# daemon
cd linux/sunbid && python -m unittest -v tests/test_sync.py

# app (Android SDK com platforms;android-37.0 e build-tools;37.0.0, Gradle 9)
cd android
gradle :app:testDebugUnitTest   # lógica + capturas das telas em app/build/shots/
gradle assembleDebug
```

Os testes de tela rodam no JVM com Robolectric (gráficos nativos) e salvam PNGs das abas em
tema claro, escuro e no layout largo do Quest. O CI publica essas imagens no artefato
**sunbi-telas**.

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
