--!nonstrict
-- Sunflower Clocks: painel com o checklist do dia, humor e anotação.

local day = {}
local ringing = {}
local online = false
local noteDraft = nil
local MOODS = { "Muito baixo", "Baixo", "Estável", "Agitado", "Muito agitado" }

local function send(op, arg)
  -- n muda a cada envio para o watch disparar mesmo com o comando repetido
  noctalia.state.set("sf.cmd", { op = op, arg = arg, n = noctalia.nowMs() })
end

local function render()
  if not online then
    panel.render(ui.column({ gap = 12, padding = 16 }, {
      ui.label({ text = "Sunflower Clocks", fontSize = 16, fontWeight = "bold", color = "primary" }),
      ui.label({ text = noctalia.tr("ui.offline"), maxLines = 4 }),
      ui.label({ text = "systemctl --user start sunflowerd", color = "outline_variant" }),
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
    table.insert(children, ui.column({ gap = 8, padding = 12, fill = "error/0.15", radius = 8 }, {
      ui.label({ text = `{ringing.time}  {ringing.label}`, fontWeight = "bold", color = "error", maxLines = 2 }),
      ui.row({ gap = 8, justify = "end" }, {
        ui.button({ text = noctalia.tr("ui.stop"), variant = "outline", onClick = function() send("stop") end }),
        ui.button({ text = noctalia.tr("ui.done"), variant = "primary", glyph = "check", onClick = function() send("done", ringing.id) end }),
      }),
    }))
  end

  table.insert(children, ui.row({ justify = "space_between", align = "center" }, {
    ui.label({ text = "Sunflower Clocks", fontSize = 16, fontWeight = "bold", color = "primary" }),
    ui.label({ text = `{done}/{total}`, fontWeight = "bold" }),
  }))
  table.insert(children, ui.progress({ progress = total > 0 and done / total or 0, height = 6, radius = 3 }))

  local rows = {}
  for _, t in ipairs(tasks) do
    local isDone = day.done and day.done[t.id] == true
    table.insert(rows, ui.row({
      key = t.id,
      gap = 10,
      align = "center",
      paddingH = 8,
      paddingV = 6,
      radius = 6,
      fill = isDone and "primary/0.10" or nil,
      onClick = function() send("toggle", t.id) end,
    }, {
      ui.glyph({ name = isDone and "square-check" or "square", color = isDone and "primary" or "outline_variant" }),
      ui.label({ text = t.time, fontWeight = "bold", color = "primary" }),
      ui.label({ text = t.label, color = isDone and "outline_variant" or nil, maxLines = 1, flexGrow = 1 }),
      t.med and ui.glyph({ name = "pill", color = "tertiary" }) or ui.spacer({ width = 1 }),
    }))
  end
  table.insert(children, ui.scroll({ gap = 2, flexGrow = 1 }, rows))

  table.insert(children, ui.separator({}))
  table.insert(children, ui.label({ text = noctalia.tr("ui.mood"), fontWeight = "bold" }))
  local moodButtons = {}
  for i = 1, 5 do
    table.insert(moodButtons, ui.button({
      text = tostring(i),
      tooltip = MOODS[i],
      variant = day.mood == i and "primary" or "outline",
      flexGrow = 1,
      controlSize = "sm",
      onClick = function() send("mood", tostring(i)) end,
    }))
  end
  table.insert(children, ui.row({ gap = 6 }, moodButtons))

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
  day = noctalia.state.get("sf.day") or {}
  ringing = noctalia.state.get("sf.ringing") or {}
  online = noctalia.state.get("sf.online") == true
  send("refresh")
  render()
end

function onClose()
  if noteDraft ~= nil then
    send("note", noteDraft)
    noteDraft = nil
  end
end

noctalia.state.watch("sf.day", function(v)
  day = v or {}
  render()
end)

noctalia.state.watch("sf.ringing", function(v)
  ringing = v or {}
  render()
end)

noctalia.state.watch("sf.online", function(v)
  online = v == true
  render()
end)
