# 🌻 Sunflower Clocks

Rotina diária com alarmes, checklist, humor, sono e anotações, no estilo retrô da página
*Rotina Girassol*. Funciona no celular Android, no Meta Quest 3S e no PC com Noctalia.
**Marcou uma tarefa num aparelho, ela aparece marcada em todos**, sem servidor:
os aparelhos se encontram sozinhos na rede de casa e trocam os dados direto entre si.

| Parte | Pasta | O que faz |
| --- | --- | --- |
| App Android / Quest 3S | `android/` | Alarmes reais **sem adiar**, com o toque Girassol; checklist; humor; sono; anotações; grade de 7 dias; relatório para o psicólogo |
| Daemon Linux | `linux/sunflowerd/` | Guarda a rotina no PC e sincroniza com os outros aparelhos |
| Plugin Noctalia v5 | `noctalia/sunflower-clocks/` | Widget na barra (`6/14`), painel com o checklist e alarmes no PC |
| Protocolo | `docs/PROTOCOL.md` | Como os aparelhos se descobrem e mesclam os dados |

## 1. Instalar no celular e no Quest

1. Abra a aba **Actions** do repositório, entre no build mais recente e baixe o artefato
   **sunflower-clocks-apk**. Para uma versão fixa, crie uma tag `v0.1.0`: o APK sai em **Releases**.
2. **Celular Samsung:** abra o APK e permita instalar de fontes desconhecidas.
3. **Quest 3S:** ative o modo desenvolvedor no app Meta Horizon e instale com SideQuest ou
   `adb install sunflower-clocks.apk`. O app aparece em **Biblioteca → Fontes desconhecidas**.
4. Abra o app, permita notificações e, em **Ajustes**, confira os avisos de permissão
   (alarmes exatos e tela cheia).

Todo APK novo instala por cima do anterior, porque é assinado sempre com a mesma chave.

## 2. Instalar no PC (NixOS / Fedora)

```sh
# daemon
pipx install ./linux/sunflowerd            # ou: pip install --user ./linux/sunflowerd
# no NixOS sem pipx:
nix-shell -p 'python3.withPackages (p: [ p.zeroconf ])' --run 'python3 linux/sunflowerd/sunflowerd.py'

# serviço de usuário
mkdir -p ~/.config/systemd/user
cp linux/sunflowerd/sunflowerd.service ~/.config/systemd/user/
systemctl --user enable --now sunflowerd

# plugin do Noctalia
mkdir -p ~/.local/share/noctalia/plugins
ln -s "$PWD/noctalia/sunflower-clocks" ~/.local/share/noctalia/plugins/sunflower-clocks
```

Depois ative o plugin em **Settings → Plugins** e adicione o widget `dvd-iso/sunflower-clocks:bar` na barra.
No NixOS com o serviço declarado no seu config, ajuste o `ExecStart` para o caminho do Nix.

## 3. Parear os aparelhos

Todos precisam do **mesmo código de pareamento**:

```sh
sunflowerd key            # mostra o código do PC
sunflowerd set-key ABC123 # ou define um novo
```

No app: **Ajustes → Código de pareamento**, digite o mesmo código e toque em **Salvar e reconectar**.
Os aparelhos na mesma rede Wi-Fi aparecem em **Aparelhos encontrados** com uma bolinha verde.

Se o Wi-Fi bloquear a descoberta automática (redes de visitante, alguns roteadores), adicione o par manualmente:
no app, `ip:47321` em **Adicionar**; no PC, `sunflowerd add-peer 192.168.0.20:47321`.
Com Tailscale, o mesmo vale para sincronizar fora de casa.

## Como funciona a sincronização

Cada marcação é um registro com valor, horário e aparelho. Quando dois aparelhos conversam, cada um fica com a
versão mais recente de cada registro. Por isso a ordem das trocas não importa, e um aparelho que ficou desligado
recebe tudo quando volta. Detalhes em [`docs/PROTOCOL.md`](docs/PROTOCOL.md).

**Privacidade:** os dados ficam só nos seus aparelhos. O tráfego na rede local não é criptografado; o código de
pareamento impede que outros aparelhos leiam ou escrevam, mas use Tailscale/WireGuard fora de casa.

## Limitações conhecidas

- O Quest só toca o alarme se estiver ligado; com o headset dormindo, o celular e o PC cobrem.
- No celular, a sincronização em segundo plano usa uma notificação fixa ("Sincronizando..."). Pode ser
  silenciada nas configurações de notificação do app.
- As tarefas editadas valem para todos os aparelhos; humor, sono e anotações são por dia.

## Desenvolvimento

```sh
cd linux/sunflowerd && python -m unittest -v tests/test_sync.py
cd android && gradle assembleDebug       # requer Android SDK; o CI faz isso no GitHub Actions
```
