--!nonstrict
-- SunBi: widget da barra.
-- Normal: flor + "feitas/total" (dica mostra a próxima tarefa e quanto falta).
-- Foco: ampulheta + tempo restante. Alarme: sino + nome da tarefa.
-- Clique abre o painel; clique do meio durante o alarme marca como feito.

local day = {}
local ringing = {}
local focus = {}
local online = false

local function counts()
  local total, done = 0, 0
  for _, t in ipairs(day.tasks or {}) do
    total += 1
    if day.done and day.done[t.id] then
      done += 1
    end
  end
  return done, total
end

local function minutesOf(hm)
  local h, m = string.match(hm or "", "^(%d+):(%d+)$")
  if not h then
    return nil
  end
  return tonumber(h) * 60 + tonumber(m)
end

local function nextTask()
  local now = tonumber(os.date("%H")) * 60 + tonumber(os.date("%M"))
  for _, t in ipairs(day.tasks or {}) do
    local m = minutesOf(t.time)
    if m and m > now and not (day.done and day.done[t.id]) then
      return t, m - now
    end
  end
  return nil, nil
end

local function mmss(s)
  s = math.max(0, s)
  return string.format("%d:%02d", s // 60, s % 60)
end

local function render()
  local vertical = barWidget.isVertical()
  local container = vertical and ui.column or ui.row

  if ringing.id then
    barWidget.setTooltip(ringing.label)
    barWidget.render(container({ gap = 6, align = "center" }, {
      ui.glyph({ name = "alarm", color = "error" }),
      ui.label({ text = vertical and ringing.time or (ringing.time .. " " .. ringing.label), fontWeight = "bold", color = "error", maxWidth = 260, maxLines = 1 }),
    }))
    return
  end

  if focus.endsAt then
    local left = focus.endsAt - os.time()
    barWidget.setTooltip(focus.task and focus.task.label or noctalia.tr("ui.focus_left", { t = mmss(left) }))
    barWidget.render(container({ gap = 6, align = "center" }, {
      ui.glyph({ name = "hourglass-high", color = "primary" }),
      ui.label({ text = mmss(left), fontWeight = "bold", color = "primary" }),
    }))
    return
  end

  if not online then
    barWidget.setTooltip(noctalia.tr("ui.offline"))
    barWidget.render(container({ align = "center" }, {
      ui.glyph({ name = "flower", color = "outline_variant" }),
    }))
    return
  end

  local done, total = counts()
  local tip = noctalia.tr("ui.progress", { done = done, total = total })
  local t, inMin = nextTask()
  if t then
    tip = tip .. `\n{noctalia.tr("ui.next")}: {t.time} {t.label} ({noctalia.tr("ui.in_min", { m = inMin })})`
  end
  barWidget.setTooltip(tip)
  barWidget.render(container({ gap = 6, align = "center" }, {
    ui.glyph({ name = "flower", color = done == total and total > 0 and "primary" or nil }),
    ui.label({ text = vertical and tostring(done) or `{done}/{total}`, fontWeight = "bold" }),
  }))
end

function update()
  render()
end

noctalia.state.watch("sb.day", function(v)
  day = v or {}
  render()
end)

noctalia.state.watch("sb.ringing", function(v)
  ringing = v or {}
  render()
end)

noctalia.state.watch("sb.focus", function(v)
  focus = v or {}
  render()
end)

noctalia.state.watch("sb.online", function(v)
  online = v == true
  render()
end)

function onClick()
  noctalia.togglePanel("dvd-iso/sunbi:panel")
end

function onMiddleClick()
  if ringing.id then
    noctalia.state.set("sb.cmd", { op = "done", arg = ringing.id, n = noctalia.nowMs() })
  end
end
