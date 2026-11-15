# Sunflower Clocks

Daily routine with alarms and a checklist for Noctalia v5, synced peer-to-peer with the
Sunflower Clocks Android app (phone and Meta Quest 3S).

## Plugin

- Id: `dvd-iso/sunflower-clocks`
- Widget `bar`: sunflower icon and tasks done today (`6/14`). Shows the task when its alarm rings.
- Panel `panel`: today's checklist, mood (1 to 5) and a note for the day.
- Service `service`: talks to the local `sunflowerd` daemon and rings the alarms.

## Requirements

- `sunflowerd` running on this machine (default port 47321). It stores the routine and syncs it
  with your other devices on the local network. See the main repository README.

## Usage

Add the widget to a bar as `dvd-iso/sunflower-clocks:bar`. Click it to open the panel; click a task to mark it
done. When an alarm rings, middle-click the widget to mark the task done.

Open the panel from a keybind:

```sh
noctalia msg panel-toggle dvd-iso/sunflower-clocks:panel
```

Control the service over IPC:

```sh
noctalia msg plugin dvd-iso/sunflower-clocks:service all toggle t0930   # toggle a task
noctalia msg plugin dvd-iso/sunflower-clocks:service all done           # mark the ringing task done
noctalia msg plugin dvd-iso/sunflower-clocks:service all stop           # silence the alarm
noctalia msg plugin dvd-iso/sunflower-clocks:service all refresh
```

## Settings

- **sunflowerd port**: local port of the daemon (default 47321).
- **Ring alarms on this PC**: notification and sound when an unfinished task is due.
- **Alarm length**: how long the sound repeats, in seconds. 0 shows only the notification.
