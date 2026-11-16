--!nonstrict
-- SunBi: painel com agora/próxima, foco, humor, checklist e anotação.

local day = {}
local ringing = {}
local focus = {}
local online = false
local noteDraft = nil
local HUMOR = {
  [-3] = "Muito para baixo", [-2] = "Para baixo", [-1] = "Um pouco para baixo", [0] = "Estável",
  [1] = "Um pouco acelerado", [2] = "Acelerado", [3] = "Muito acelerado",
}

local function send(op, arg)
  -- n muda a cada envio para o watch disparar mesmo com o comando repetido
  noctalia.state.set("sb.cmd", { op = op, arg = arg, n = noctalia.nowMs() })
end

local function minutesOf(hm)
  local h, m = string.match(hm or "", "^(%d+):(%d+)$")
  if not h then
    return nil
  end
  return tonumber(h) * 60 + tonumber(m)
end

-- Tarefa atrasada (até 3 h) ou a próxima: torna o tempo visível (TDAH).
local function nowOrNext()
  local now = tonumber(os.date("%H")) * 60 + tonumber(os.date("%M"))
  local late, nxt = nil, nil
  for _, t in ipairs(day.tasks or {}) do
    local m = minutesOf(t.time)
    if m and not (day.done and day.done[t.id]) then
      if m <= now and now - m <= 180 then
        late = t
      elseif m > now and not nxt then
        nxt = t
      end
    end
  end
  if late then
    return late, now - minutesOf(late.time), true
  end
  if nxt then
    return nxt, minutesOf(nxt.time) - now, false
  end
  return nil, nil, false
end

local function mmss(s)
  s = math.max(0, s)
  return string.format("%d:%02d", s // 60, s % 60)
end

local function render()
  if not online then
    panel.render(ui.column({ gap = 12, padding = 16 }, {
      ui.label({ text = "SunBi", fontSize = 16, fontWeight = "bold", color = "primary" }),
      ui.label({ text = noctalia.tr("ui.offline"), maxLines = 4 }),
      ui.label({ text = "systemctl --user start sunbid", color = "outline_variant" }),
      ui.button({ text = noctalia.tr("ui.retry"), variant = "primary", onClick = function() send("refresh") end }),
    }))
    return
  end

  local tasks = day.tasks or {}
  local done = 0
  for _, t in ipairs(tasks) do
    if day.done and day.done[t.id] then
      done += 1
    end
  end
  local total = #tasks

  local children = {}

  if ringing.id then
    table.insert(children, ui.column({ gap = 8, padding = 12, fill = "error/0.15", radius = 12 }, {
      ui.label({ text = `{ringing.time}  {ringing.label}`, fontWeight = "bold", color = "error", maxLines = 2 }),
      ui.row({ gap = 8, justify = "end" }, {
        ui.button({ text = noctalia.tr("ui.stop"), variant = "outline", onClick = function() send("stop") end }),
        ui.button({ text = noctalia.tr("ui.done"), variant = "primary", glyph = "check", onClick = function() send("done", ringing.id) end }),
      }),
    }))
  end

  table.insert(children, ui.row({ justify = "space_between", align = "center" }, {
    ui.row({ gap = 8, align = "center" }, {
      ui.glyph({ name = "flower", color = "primary" }),
      ui.label({ text = "SunBi", fontSize = 16, fontWeight = "bold", color = "primary" }),
    }),
    ui.label({ text = `{done}/{total}`, fontWeight = "bold" }),
  }))
  table.insert(children, ui.progress({ progress = total > 0 and done / total or 0, height = 6, radius = 3 }))

  -- foco em andamento
  if focus.endsAt then
    local left = focus.endsAt - os.time()
    table.insert(children, ui.column({ gap = 6, padding = 12, fill = "primary/0.12", radius = 12 }, {
      ui.row({ gap = 8, align = "center" }, {
        ui.glyph({ name = "hourglass-high", color = "primary" }),
        ui.label({ text = noctalia.tr("ui.focus_left", { t = mmss(left) }), fontWeight = "bold", flexGrow = 1 }),
        ui.button({ text = noctalia.tr("ui.stop"), variant = "outline", controlSize = "sm", onClick = function() send("focus-stop") end }),
      }),
      ui.progress({ progress = (focus.total or 1) > 0 and math.max(0, left) / focus.total or 0, height = 4, radius = 2 }),
      (focus.task and focus.task.label) and ui.label({ text = focus.task.label, color = "outline_variant", maxLines = 1 }) or ui.spacer({ height = 1 }),
    }))
  end

  -- agora / próxima
  local t, mins, late = nowOrNext()
  if t then
    local when = late and noctalia.tr("ui.late_min", { m = mins }) or noctalia.tr("ui.in_min", { m = mins })
    local box = {
      ui.label({ text = (late and noctalia.tr("ui.now") or noctalia.tr("ui.next")) .. " · " .. t.time .. " · " .. when, color = "primary", fontWeight = "bold" }),
      ui.label({ text = t.label, fontSize = 15, fontWeight = "bold", maxLines = 2 }),
    }
    if t.step and t.step ~= "" then
      table.insert(box, ui.label({ text = noctalia.tr("ui.first_step", { s = t.step }), color = "outline_variant", maxLines = 2 }))
    end
    if not focus.endsAt then
      table.insert(box, ui.row({ gap = 6 }, {
        ui.button({ text = noctalia.tr("ui.focus", { m = 5 }), variant = "primary", glyph = "player-play", controlSize = "sm",
          onClick = function() send("focus", "5 " .. t.id) end }),
        ui.button({ text = "15", variant = "outline", controlSize = "sm", onClick = function() send("focus", "15 " .. t.id) end }),
        ui.button({ text = "25", variant = "outline", controlSize = "sm", onClick = function() send("focus", "25 " .. t.id) end }),
        ui.spacer({ flexGrow = 1 }),
        ui.button({ text = noctalia.tr("ui.done"), variant = "outline", glyph = "check", controlSize = "sm", onClick = function() send("toggle", t.id) end }),
      }))
    end
    table.insert(children, ui.column({ gap = 6, padding = 12, fill = "surface_variant/0.6", radius = 12 }, box))
  else
    table.insert(children, ui.label({ text = noctalia.tr("ui.all_done"), color = "outline_variant" }))
  end

  -- checklist
  local rows = {}
  for _, task in ipairs(tasks) do
    local isDone = day.done and day.done[task.id] == true
    local at = day.doneAt and day.doneAt[task.id]
    table.insert(rows, ui.row({
      key = task.id,
      gap = 10,
      align = "center",
      paddingH = 8,
      paddingV = 6,
      radius = 8,
      fill = isDone and "primary/0.10" or nil,
      onClick = function() send("toggle", task.id) end,
    }, {
      ui.glyph({ name = isDone and "circle-check" or "circle", color = isDone and "primary" or "outline_variant" }),
      ui.label({ text = task.time, fontWeight = "bold", color = "primary" }),
      ui.label({ text = task.label, color = isDone and "outline_variant" or nil, maxLines = 1, flexGrow = 1 }),
      (isDone and at) and ui.label({ text = at, color = "outline_variant" })
        or (task.med and ui.glyph({ name = "pill", color = "tertiary" }))
        or (task.anchor and ui.glyph({ name = "anchor", color = "secondary" }))
        or ui.spacer({ width = 1 }),
    }))
  end
  table.insert(children, ui.scroll({ gap = 2, flexGrow = 1 }, rows))

  -- humor -3..+3 (escala do NIMH Life Chart)
  table.insert(children, ui.separator({}))
  local hlabel = day.humor ~= nil and (HUMOR[day.humor] or "") or ""
  table.insert(children, ui.row({ justify = "space_between" }, {
    ui.label({ text = noctalia.tr("ui.humor"), fontWeight = "bold", maxLines = 1, flexGrow = 1 }),
    ui.label({ text = hlabel, color = "primary" }),
  }))
  local hb = {}
  for i = -3, 3 do
    table.insert(hb, ui.button({
      text = i > 0 and ("+" .. i) or tostring(i),
      tooltip = HUMOR[i],
      variant = day.humor == i and "primary" or "outline",
      flexGrow = 1,
      controlSize = "sm",
      onClick = function() send("humor", tostring(i)) end,
    }))
  end
  table.insert(children, ui.row({ gap = 4 }, hb))
  if day.sleep then
    table.insert(children, ui.label({ text = noctalia.tr("ui.sleep", { h = tostring(day.sleep) }), color = "outline_variant" }))
  end

  table.insert(children, ui.input({
    key = "note",
    value = noteDraft or day.note or "",
    placeholder = noctalia.tr("ui.note_placeholder"),
    submitOnEnter = true,
    onChange = function(v) noteDraft = v end,
    onSubmit = function(v)
      send("note", v)
      noteDraft = nil
    end,
  }))

  panel.render(ui.column({ gap = 10, padding = 14 }, children))
end

function onOpen(_context)
  day = noctalia.state.get("sb.day") or {}
  ringing = noctalia.state.get("sb.ringing") or {}
  focus = noctalia.state.get("sb.focus") or {}
  online = noctalia.state.get("sb.online") == true
  send("refresh")
  render()
end

function onClose()
  if noteDraft ~= nil then
    send("note", noteDraft)
    noteDraft = nil
  end
end

function update()
  if focus.endsAt then
    render() -- atualiza a contagem regressiva
  end
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
