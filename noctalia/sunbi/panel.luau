--!nonstrict
-- SunBi: painel enxuto. De cima para baixo: a tarefa da vez, as pendentes, o humor e uma
-- linha de status. O resto (feitas, anotação, aparelhos) aparece só quando pedido.

local S = require("./lib/store.luau")
local I = require("./lib/i18n.luau")

noctalia.setUpdateInterval(1000)

local day = {}
local ringing = {}
local focus = {}
local sync = {}
local online = false
local noteDraft = nil
local showDone = false
local showNote = false

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

local function card(fill, children)
  return ui.column({ gap = 8, padding = 14, fill = fill, radius = 16 }, children)
end

local function small(text, color)
  return ui.label({ text = text, fontSize = 12, fontWeight = "semibold", color = color or "on_surface_variant", maxLines = 1 })
end

local function offText()
  if day.off == "vacation" then
    return I.t("ui.off_vacation")
  elseif day.off == "holiday" then
    return I.t("ui.off_holiday", { name = day.holiday or "" })
  elseif day.off == "weekend" then
    return I.t("ui.off_weekend")
  elseif day.off then
    return I.t("ui.off_manual")
  end
  return nil
end

local function header(done, total)
  local off = offText()
  return ui.column({ gap = 8 }, {
    ui.row({ gap = 8, align = "center" }, {
      -- tocar na flor liga/desliga o modo férias
      ui.row({ gap = 8, align = "center", flexGrow = 1, radius = 10, tooltip = I.t("ui.vacation_hint"),
        onClick = function() send("vacation") end }, {
        ui.glyph({ name = day.off and "sunglasses" or "flower", size = 22, color = "primary" }),
        ui.label({ text = "SunBi", fontSize = 17, fontWeight = "bold" }),
      }),
      ui.label({ text = `{done}/{total}`, fontSize = 15, fontWeight = "bold", color = "primary" }),
      ui.button({ glyph = "settings", variant = "ghost", controlSize = "sm", tooltip = I.t("ui.settings"),
        onClick = function() noctalia.openSettings() end }),
    }),
    ui.progress({ progress = total > 0 and done / total or 0, height = 6, radius = 3, fill = "primary", track = "surface_container_high" }),
    off and ui.row({ paddingH = 10, paddingV = 6, radius = 10, fill = "tertiary/0.18" }, {
      ui.label({ text = off, fontSize = 12, fontWeight = "semibold", color = "on_surface", maxLines = 2 }),
    }) or ui.spacer({ height = 0 }),
  })
end

-- Alarme tocando, foco em andamento ou a tarefa da vez: só um cartão no topo.
local function topCard()
  if ringing.id then
    return card("error_container", {
      small(ringing.time or "", "error"),
      ui.label({ text = ringing.label or "", fontSize = 16, fontWeight = "bold", maxLines = 3 }),
      ui.row({ gap = 8 }, {
        ui.button({ text = I.t("ui.stop"), variant = "outline", flexGrow = 1, onClick = function() send("stop") end }),
        ui.button({ text = I.t("ui.done"), variant = "primary", glyph = "check", flexGrow = 1, onClick = function() send("done", ringing.id) end }),
      }),
    })
  end

  if focus.endsAt then
    local left = focus.endsAt - os.time()
    local total = math.max(1, focus.total or 1)
    local label = focus.task and focus.task.label
    return card("primary_container", {
      small(string.upper(I.t("ui.focus_title")), "primary"),
      ui.row({ gap = 10, align = "center" }, {
        ui.label({ text = mmss(left), fontSize = 30, fontWeight = "bold" }),
        label and ui.label({ text = label, fontSize = 13, maxLines = 2, flexGrow = 1 }) or ui.spacer({ flexGrow = 1 }),
      }),
      ui.progress({ progress = math.clamp(left / total, 0, 1), height = 6, radius = 3, fill = "primary", track = "surface_container_high" }),
      ui.row({ gap = 8 }, {
        ui.button({ text = I.t("ui.stop"), variant = "outline", flexGrow = 1, onClick = function() send("focus-stop") end }),
        ui.button({ text = "+5 min", variant = "secondary", flexGrow = 1, onClick = function() send("extend", "5") end }),
        (focus.task and focus.task.id) and ui.button({ text = I.t("ui.done"), glyph = "check", variant = "primary", flexGrow = 1,
          onClick = function() send("done", focus.task.id) end }) or ui.spacer({ width = 0 }),
      }),
    })
  end

  local t, mins, late = S.nowOrNext(day, nowMin())
  if not t then
    return card("surface_container", {
      ui.label({ text = I.t("ui.all_done"), fontSize = 15, fontWeight = "semibold" }),
    })
  end
  local when = late and I.t("ui.late_min", { m = mins }) or I.t("ui.in_min", { m = mins })
  local children = {
    small(string.upper(late and I.t("ui.now") or I.t("ui.next")) .. " · " .. t.time .. " · " .. when, "primary"),
    ui.label({ text = t.label, fontSize = 16, fontWeight = "bold", maxLines = 3 }),
  }
  if t.step and t.step ~= "" then
    table.insert(children, ui.label({ text = I.t("ui.first_step", { s = t.step }), fontSize = 13, color = "on_surface_variant", maxLines = 2 }))
  end
  table.insert(children, ui.button({ text = I.t("ui.done"), glyph = "check", variant = "primary", onClick = function() send("toggle", t.id) end }))
  return card("primary_container", children)
end

local function taskRow(task, isDone)
  local at = day.doneAt and day.doneAt[task.id]
  return ui.row({
    key = task.id,
    gap = 10,
    align = "center",
    paddingH = 10,
    paddingV = 7,
    radius = 12,
    tooltip = (task.step and task.step ~= "" and not isDone) and I.t("ui.first_step", { s = task.step }) or nil,
    onClick = function() send("toggle", task.id) end,
  }, {
    ui.glyph({ name = isDone and "circle-check-filled" or "circle", size = 20, color = isDone and "primary" or "on_surface_variant" }),
    ui.label({ text = task.label, fontSize = 14, color = isDone and "on_surface_variant" or "on_surface", maxLines = 1, flexGrow = 1 }),
    ui.label({ text = (isDone and at) or task.time, fontSize = 13, fontWeight = "semibold", color = isDone and "on_surface_variant" or "primary" }),
  })
end

local function tasks()
  local rows, doneN = {}, 0
  for _, task in ipairs(day.tasks or {}) do
    if day.done and day.done[task.id] then
      doneN += 1
    else
      table.insert(rows, taskRow(task, false))
    end
  end
  if doneN > 0 then
    table.insert(rows, ui.button({
      text = showDone and I.t("ui.hide_done") or I.t("ui.show_done", { n = doneN }),
      glyph = showDone and "chevron-up" or "chevron-down",
      variant = "ghost", controlSize = "sm", contentAlign = "start",
      onClick = function()
        showDone = not showDone
        render()
      end,
    }))
    if showDone then
      for _, task in ipairs(day.tasks or {}) do
        if day.done and day.done[task.id] then
          table.insert(rows, taskRow(task, true))
        end
      end
    end
  end
  -- ocupa o espaço que sobra no painel e rola por dentro
  return ui.scroll({ gap = 2, flexGrow = 1 }, rows)
end

local function moodColor(v)
  if v == 0 then
    return "primary"
  end
  local dark = noctalia.isDarkMode()
  if v < 0 then
    return dark and "#8AB4F8" or "#3D6FD6" -- azul: para baixo
  end
  return dark and "#FFB866" or "#C26A00" -- âmbar: acelerado
end

local function mood()
  -- escala visual: bolinhas maiores nas pontas, preenchida a escolhida
  local dots = {}
  for i = -3, 3 do
    local sel = day.humor == i
    local size = 14 + 4 * math.abs(i)
    local color = moodColor(i)
    table.insert(dots, ui.column({ align = "center", justify = "center", width = 36, height = 36, radius = 18,
      fill = sel and (color .. (string.sub(color, 1, 1) == "#" and "44" or "/0.25")) or nil,
      tooltip = S.HUMOR[i], onClick = function() send("humor", tostring(i)) end }, {
      ui.box({ width = size, height = size, radius = size / 2,
        fill = sel and color or (string.sub(color, 1, 1) == "#" and (color .. "66") or (color .. "/0.4")) }),
    }))
  end
  local children = {
    ui.row({ align = "center", gap = 8 }, {
      ui.label({ text = I.t("ui.humor"), fontSize = 14, fontWeight = "semibold", maxLines = 1 }),
      ui.spacer({ flexGrow = 1 }),
      ui.label({ text = day.humorLabel or I.t("ui.humor_pick"), fontSize = 13, fontWeight = "semibold",
        color = day.humor ~= nil and moodColor(day.humor) or "on_surface_variant", maxLines = 1 }),
    }),
    ui.row({ justify = "space_between", align = "center" }, dots),
    ui.row({ align = "center" }, {
      ui.label({ text = I.t("ui.down"), fontSize = 11, color = moodColor(-2), maxLines = 1 }),
      ui.spacer({ flexGrow = 1 }),
      ui.label({ text = I.t("ui.up"), fontSize = 11, color = moodColor(2), maxLines = 1 }),
    }),
  }
  local hasNote = (day.note or "") ~= ""
  if showNote or hasNote then
    table.insert(children, ui.input({
      key = "note",
      value = noteDraft or day.note or "",
      placeholder = I.t("ui.note_placeholder"),
      submitOnEnter = true,
      onChange = function(v) noteDraft = v end,
      onSubmit = function(v)
        send("note", v)
        noteDraft = nil
      end,
    }))
  else
    table.insert(children, ui.button({
      text = I.t("ui.add_note"), glyph = "pencil", variant = "ghost", controlSize = "sm", contentAlign = "start",
      onClick = function()
        showNote = true
        render()
      end,
    }))
  end
  return card("surface_container", children)
end

-- Uma linha: aparelhos e código. Os detalhes ficam na dica.
local function status()
  local peers = sync.peers or {}
  local ok, tip = 0, {}
  for _, p in ipairs(peers) do
    if p.ok then
      ok += 1
    end
    table.insert(tip, `{p.ok and "●" or "○"} {p.name}` .. (p.why and (" · " .. p.why) or ""))
  end
  local text
  if #peers == 0 then
    text = I.t("ui.no_devices_short")
    table.insert(tip, I.t("ui.no_devices"))
  else
    text = I.tp("ui.devices_count", ok, { n = ok })
  end
  return ui.row({ align = "center", gap = 6, tooltip = table.concat(tip, "\n") }, {
    ui.glyph({ name = ok > 0 and "devices-check" or "devices", size = 16, color = ok > 0 and "primary" or "on_surface_variant" }),
    small(text .. " · " .. I.t("ui.code", { k = sync.key or "" })),
    ui.spacer({ flexGrow = 1 }),
    ui.button({ glyph = "refresh", variant = "ghost", controlSize = "sm", tooltip = I.t("ui.sync_now"), onClick = function() send("sync") end }),
  })
end

function render()
  if not online then
    panel.render(ui.column({ gap = 12, padding = 18 }, {
      ui.row({ gap = 8, align = "center" }, {
        ui.glyph({ name = "flower", size = 22, color = "primary" }),
        ui.label({ text = "SunBi", fontSize = 17, fontWeight = "bold" }),
      }),
      ui.label({ text = I.t("ui.offline"), fontSize = 14, maxLines = 4 }),
    }))
    return
  end
  local total = #(day.tasks or {})
  panel.render(ui.column({ gap = 12, padding = 16, flexGrow = 1 }, {
    header(day.doneCount or 0, total),
    topCard(),
    tasks(),
    mood(),
    status(),
  }))
end

function onOpen(_context)
  day = noctalia.state.get("sb.day") or {}
  ringing = noctalia.state.get("sb.ringing") or {}
  focus = noctalia.state.get("sb.focus") or {}
  sync = noctalia.state.get("sb.sync") or {}
  online = noctalia.state.get("sb.online") == true
  showDone, showNote = false, false
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
