# SunBi

Routine companion for bipolar disorder and ADHD on Noctalia v5, synced peer-to-peer with the
SunBi Android app (phone and Meta Quest).

## Plugin

- Id: `dvd-iso/sunbi`
- Widget `bar`: flower and tasks done today (`6/13`); the tooltip shows the next task and how long until it.
  During a focus block it shows the countdown; when an alarm rings it shows the task.
- Panel `panel`: now/next task with its first step, focus timer (5, 15 or 25 min), mood on the
  −3…+3 scale, today's checklist and a note for the day.
- Service `service`: talks to the local `sunbid` daemon, rings the alarms and runs the focus timer.

## Requirements

- `sunbid` running on this machine (default port 47321). It stores the routine and syncs it
  with your other devices on the local network. See the main repository README.

## Usage

Add the widget to a bar as `dvd-iso/sunbi:bar`. Click it to open the panel; click a task to mark it
done. When an alarm rings, middle-click the widget to mark the task done.

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
noctalia msg plugin dvd-iso/sunbi:service all focus-stop
noctalia msg plugin dvd-iso/sunbi:service all humor -1         # mood today, -3..3
noctalia msg plugin dvd-iso/sunbi:service all refresh
```

## Settings

- **sunbid port**: local port of the daemon (default 47321).
- **Ring alarms on this PC**: notification and sound when an unfinished task is due.
- **Alarm length**: how long the sound repeats, in seconds. 0 shows only the notification.
