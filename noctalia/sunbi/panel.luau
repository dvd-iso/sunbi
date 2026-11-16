--!nonstrict
-- SunBi: painel com agora/próxima, foco, humor, checklist, anotação e aparelhos.

local S = require("./lib/store.luau")

noctalia.setUpdateInterval(1000)

local day = {}
local ringing = {}
local focus = {}
local sync = {}
local online = false
local noteDraft = nil

local function send(op, arg)
  -- n muda a cada envio para o watch disparar mesmo com o comando repetido
  noctalia.state.set("sb.cmd", { op = op, arg = arg, n = noctalia.nowMs() })
end

local function mmss(s)
  s = math.max(0, s)
  return string.format("%d:%02d", s // 60, s % 60)
end

local function nowMin()
  return tonumber(os.date("%H")) * 60 + tonumber(os.date("%M"))
end

local function card(fill, children, gap)
  return ui.column({ gap = gap or 8, padding = 14, fill = fill, radius = 16 }, children)
end

local function caption(text, color)
  return ui.label({ text = text, fontSize = 12, fontWeight = "semibold", color = color or "on_surface_variant", maxLines = 1 })
end

local function header(done, total)
  return ui.column({ gap = 10 }, {
    ui.row({ gap = 10, align = "center" }, {
      ui.glyph({ name = "flower", size = 26, color = "primary" }),
      ui.column({ gap = 0, flexGrow = 1 }, {
        ui.label({ text = "SunBi", fontSize = 18, fontWeight = "bold" }),
        caption(os.date("%d/%m") .. " · " .. noctalia.tr("ui.progress", { done = done, total = total })),
      }),
      ui.label({ text = `{done}/{total}`, fontSize = 20, fontWeight = "bold", color = "primary" }),
    }),
    ui.progress({ progress = total > 0 and done / total or 0, height = 8, radius = 4, fill = "primary", track = "surface_container_high" }),
  })
end

local function ringingCard()
  return card("error_container", {
    ui.row({ gap = 8, align = "center" }, {
      ui.glyph({ name = "alarm", size = 22, color = "error" }),
      ui.label({ text = ringing.time or "", fontSize = 18, fontWeight = "bold", color = "error" }),
    }),
    ui.label({ text = ringing.label or "", fontSize = 16, fontWeight = "semibold", maxLines = 3 }),
    ui.row({ gap = 8 }, {
      ui.button({ text = noctalia.tr("ui.stop"), variant = "outline", flexGrow = 1, onClick = function() send("stop") end }),
      ui.button({ text = noctalia.tr("ui.done"), variant = "primary", glyph = "check", flexGrow = 1, onClick = function() send("done", ringing.id) end }),
    }),
  })
end

local function focusCard()
  local left = focus.endsAt - os.time()
  local total = math.max(1, focus.total or 1)
  local children = {
    ui.row({ gap = 8, align = "center" }, {
      ui.glyph({ name = "hourglass-high", size = 20, color = "primary" }),
      caption(string.upper(noctalia.tr("ui.focus", { m = math.floor(total / 60) })), "primary"),
    }),
    ui.label({ text = mmss(left), fontSize = 34, fontWeight = "bold", color = "on_surface" }),
    ui.progress({ progress = math.clamp(left / total, 0, 1), height = 6, radius = 3, fill = "primary", track = "surface_container_high" }),
  }
  if focus.task and focus.task.label then
    table.insert(children, ui.label({ text = focus.task.label, fontSize = 14, maxLines = 2 }))
  end
  table.insert(children, ui.row({ gap = 8 }, {
    ui.button({ text = noctalia.tr("ui.stop"), variant = "outline", flexGrow = 1, onClick = function() send("focus-stop") end }),
    ui.button({ text = "+5 min", variant = "secondary", flexGrow = 1, onClick = function() send("extend", "5") end }),
    (focus.task and focus.task.id) and ui.button({ text = noctalia.tr("ui.done"), glyph = "check", variant = "primary", flexGrow = 1,
      onClick = function() send("done", focus.task.id) end }) or ui.spacer({ width = 0 }),
  }))
  return card("primary_container", children)
end

local function nowCard()
  local t, mins, late = S.nowOrNext(day, nowMin())
  if not t then
    return card("surface_container", {
      ui.row({ gap = 8, align = "center" }, {
        ui.glyph({ name = "mood-smile", size = 20, color = "primary" }),
        ui.label({ text = noctalia.tr("ui.all_done"), fontSize = 15, fontWeight = "semibold" }),
      }),
    })
  end
  local when = late and noctalia.tr("ui.late_min", { m = mins }) or noctalia.tr("ui.in_min", { m = mins })
  local children = {
    caption(string.upper(late and noctalia.tr("ui.now") or noctalia.tr("ui.next")) .. " · " .. t.time .. " · " .. when, "primary"),
    ui.label({ text = t.label, fontSize = 17, fontWeight = "bold", maxLines = 3 }),
  }
  if t.step and t.step ~= "" then
    table.insert(children, ui.label({ text = noctalia.tr("ui.first_step", { s = t.step }), fontSize = 13, color = "on_surface_variant", maxLines = 2 }))
  end
  if not focus.endsAt then
    table.insert(children, ui.row({ gap = 6 }, {
      ui.button({ text = noctalia.tr("ui.focus", { m = 5 }), glyph = "player-play", variant = "primary", flexGrow = 2,
        onClick = function() send("focus", "5 " .. t.id) end }),
      ui.button({ text = "15", variant = "outline", flexGrow = 1, tooltip = noctalia.tr("ui.focus", { m = 15 }),
        onClick = function() send("focus", "15 " .. t.id) end }),
      ui.button({ text = "25", variant = "outline", flexGrow = 1, tooltip = noctalia.tr("ui.focus", { m = 25 }),
        onClick = function() send("focus", "25 " .. t.id) end }),
      ui.button({ glyph = "check", variant = "secondary", tooltip = noctalia.tr("ui.done"), onClick = function() send("toggle", t.id) end }),
    }))
  end
  return card("surface_container", children)
end

local function checklist()
  local rows = {}
  for _, task in ipairs(day.tasks or {}) do
    local isDone = day.done and day.done[task.id] == true
    local at = day.doneAt and day.doneAt[task.id]
    local trailing
    if isDone and at then
      trailing = ui.label({ text = at, fontSize = 12, color = "on_surface_variant" })
    elseif task.med then
      trailing = ui.glyph({ name = "pill", size = 16, color = "tertiary" })
    elseif task.anchor then
      trailing = ui.glyph({ name = "anchor", size = 16, color = "secondary" })
    else
      trailing = ui.spacer({ width = 1 })
    end
    table.insert(rows, ui.row({
      key = task.id,
      gap = 10,
      align = "center",
      paddingH = 10,
      paddingV = 8,
      radius = 12,
      fill = isDone and "primary/0.10" or nil,
      tooltip = (task.step and task.step ~= "" and not isDone) and noctalia.tr("ui.first_step", { s = task.step }) or nil,
      onClick = function() send("toggle", task.id) end,
    }, {
      ui.glyph({ name = isDone and "circle-check-filled" or "circle", size = 20, color = isDone and "primary" or "on_surface_variant" }),
      ui.label({ text = task.time, fontSize = 14, fontWeight = "bold", color = isDone and "on_surface_variant" or "primary" }),
      ui.label({ text = task.label, fontSize = 14, color = isDone and "on_surface_variant" or "on_surface", maxLines = 1, flexGrow = 1 }),
      trailing,
    }))
  end
  return ui.scroll({ gap = 2, flexGrow = 1, height = 280 }, rows)
end

local function moodCard()
  local hb = {}
  for i = -3, 3 do
    table.insert(hb, ui.button({
      text = i > 0 and ("+" .. i) or tostring(i),
      tooltip = S.HUMOR[i],
      variant = day.humor == i and "primary" or "ghost",
      selected = day.humor == i,
      flexGrow = 1,
      controlSize = "sm",
      onClick = function() send("humor", tostring(i)) end,
    }))
  end
  local children = {
    ui.row({ align = "center" }, {
      ui.label({ text = noctalia.tr("ui.humor"), fontSize = 14, fontWeight = "semibold", flexGrow = 1 }),
      ui.label({ text = day.humorLabel or noctalia.tr("ui.humor_hint"), fontSize = 12, color = day.humorLabel and "primary" or "on_surface_variant" }),
    }),
    ui.row({ gap = 2 }, hb),
  }
  if day.sleep then
    table.insert(children, caption(noctalia.tr("ui.sleep", { h = tostring(day.sleep) })))
  end
  table.insert(children, ui.input({
    key = "note",
    value = noteDraft or day.note or "",
    placeholder = noctalia.tr("ui.note_placeholder"),
    submitOnEnter = true,
    multiline = true,
    onChange = function(v) noteDraft = v end,
    onSubmit = function(v)
      send("note", v)
      noteDraft = nil
    end,
  }))
  return card("surface_container", children)
end

local function devices()
  local peers = sync.peers or {}
  local dots = {}
  for _, p in ipairs(peers) do
    table.insert(dots, ui.row({ gap = 4, align = "center", tooltip = p.addr .. (p.why and (" · " .. p.why) or "") }, {
      ui.glyph({ name = p.ok and "circle-filled" or "circle-x", size = 12, color = p.ok and "primary" or (p.ok == false and "error" or "on_surface_variant") }),
      ui.label({ text = p.name, fontSize = 12, maxLines = 1 }),
    }))
  end
  if #dots == 0 then
    table.insert(dots, ui.label({ text = noctalia.tr("ui.no_devices"), fontSize = 12, color = "on_surface_variant", maxLines = 3 }))
  end
  return ui.column({ gap = 6 }, {
    ui.row({ align = "center", gap = 8 }, {
      ui.glyph({ name = "devices", size = 16, color = "on_surface_variant" }),
      caption(noctalia.tr("ui.devices") .. " · " .. noctalia.tr("ui.code", { k = sync.key or "" })),
      ui.spacer({ flexGrow = 1 }),
      ui.button({ glyph = "refresh", variant = "ghost", controlSize = "sm", tooltip = noctalia.tr("ui.sync_now"), onClick = function() send("sync") end }),
    }),
    ui.column({ gap = 4 }, dots),
  })
end

local function render()
  if not online then
    panel.render(ui.column({ gap = 12, padding = 18 }, {
      ui.row({ gap = 8, align = "center" }, {
        ui.glyph({ name = "flower", size = 24, color = "primary" }),
        ui.label({ text = "SunBi", fontSize = 18, fontWeight = "bold" }),
      }),
      ui.label({ text = noctalia.tr("ui.offline"), fontSize = 14, maxLines = 4 }),
    }))
    return
  end

  local total = #(day.tasks or {})
  local done = day.doneCount or 0
  local children = { header(done, total) }
  if ringing.id then
    table.insert(children, ringingCard())
  end
  if focus.endsAt then
    table.insert(children, focusCard())
  end
  table.insert(children, nowCard())
  table.insert(children, checklist())
  table.insert(children, moodCard())
  table.insert(children, ui.separator({ color = "outline_variant" }))
  table.insert(children, devices())
  panel.render(ui.column({ gap = 12, padding = 16 }, children))
end

function onOpen(_context)
  day = noctalia.state.get("sb.day") or {}
  ringing = noctalia.state.get("sb.ringing") or {}
  focus = noctalia.state.get("sb.focus") or {}
  sync = noctalia.state.get("sb.sync") or {}
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
    render() -- contagem regressiva do foco
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

noctalia.state.watch("sb.sync", function(v)
  sync = v or {}
  render()
end)

noctalia.state.watch("sb.online", function(v)
  online = v == true
  render()
end)
