--!nonstrict
-- Sunflower Clocks: widget da barra.
-- Normal: girassol + "feitas/total". Tocando: alarme + nome da tarefa.
-- Clique abre o painel; clique do meio durante o alarme marca como feito.

local day = {}
local ringing = {}
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

  if not online then
    barWidget.setTooltip(noctalia.tr("ui.offline"))
    barWidget.render(container({ align = "center" }, {
      ui.glyph({ name = "plant-2", color = "outline_variant" }),
    }))
    return
  end

  local done, total = counts()
  barWidget.setTooltip(noctalia.tr("ui.progress", { done = done, total = total }))
  barWidget.render(container({ gap = 6, align = "center" }, {
    ui.glyph({ name = "plant-2", color = done == total and total > 0 and "primary" or nil }),
    ui.label({ text = vertical and tostring(done) or `{done}/{total}`, fontWeight = "bold" }),
  }))
end

function update()
  render()
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

function onClick()
  noctalia.togglePanel("dvd-iso/sunflower-clocks:panel")
end

function onMiddleClick()
  if ringing.id then
    noctalia.state.set("sf.cmd", { op = "done", arg = ringing.id, n = noctalia.nowMs() })
  end
end
