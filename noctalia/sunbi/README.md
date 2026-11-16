# SunBi

Routine companion for bipolar disorder and ADHD on Noctalia v5, synced peer-to-peer with the
SunBi app (Android phone and Meta Quest). **Runs entirely inside the plugin: no daemon.**

## Plugin

- Id: `dvd-iso/sunbi`
- Widget `bar`: flower and tasks done today (`6/13`); the tooltip shows the next task and how long until it.
  During a focus block it shows the countdown; when an alarm rings it shows the task.
- Panel `panel`: kept short on purpose. The task to do now (focus 5 min or done), pending tasks (done ones collapse),
  mood on the −3…+3 scale, an optional note, and one status line with devices and the pairing code.
- Service `service`: stores the routine in the plugin data dir, discovers the app on the local
  network, syncs with it, rings the alarms, runs the focus timer and plays the sounds.

## How sync works without a daemon

The app runs a small HTTP server; the plugin is a client only. Every 30 seconds, and right
after any change made on the PC, it sends its records to each device (`POST /v1/merge`) and
merges the reply, so one call syncs both sides. Devices are found with `avahi-browse`
(the `avahi` package; on NixOS `services.avahi.enable = true`), or added by hand in
**Manual devices** as `ip:port`.

Data lives in `~/.local/state/noctalia/plugins/data/dvd-iso/sunbi/`.

## Usage

Add the widget to a bar as `dvd-iso/sunbi:bar`. Click it to open the panel; click a task to mark it
done. When an alarm rings, middle-click the widget to mark the task done.

Use the same pairing code on every device: the plugin shows its code at the bottom of the panel,
or you can type the app's code in the plugin settings.

Open the panel from a keybind:

```sh
noctalia msg panel-toggle dvd-iso/sunbi:panel
```

Control the service over IPC:

```sh
noctalia msg plugin dvd-iso/sunbi:service all toggle wake      # toggle a task
noctalia msg plugin dvd-iso/sunbi:service all done             # mark the ringing task done
noctalia msg plugin dvd-iso/sunbi:service all stop             # silence the alarm
noctalia msg plugin dvd-iso/sunbi:service all focus "15 start" # 15-minute focus block for a task
noctalia msg plugin dvd-iso/sunbi:service all extend 5         # add 5 minutes to the focus block
noctalia msg plugin dvd-iso/sunbi:service all focus-stop
noctalia msg plugin dvd-iso/sunbi:service all humor " -1"      # mood today, -3..3 ("clear" removes it)
noctalia msg plugin dvd-iso/sunbi:service all sync             # look for devices and sync now
```

## Settings

- **Pairing code**: same code on every device. Empty uses the code this PC generated.
- **Manual devices**: `ip:port` of the phone or Quest, for networks that block discovery or for Tailscale.
- **Ring alarms on this PC**, **Alarm sound** (Amanhecer or Girassol) and **Alarm length**.
- **Interface sounds**: little sounds when checking tasks, picking a mood, finishing the day and for focus.

## Tests

```sh
luau tests/store_test.luau   # data model: merge, day view, sleep, avahi parsing
noctalia plugins lint .
```
