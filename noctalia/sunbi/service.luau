--!nonstrict
-- SunBi: serviço em segundo plano.
--
-- Lê o dia do daemon sunbid a cada poucos segundos e publica em noctalia.state
-- para a barra e o painel. Toca o alarme quando chega a hora de uma tarefa ainda
-- não feita e controla o cronômetro de foco (TDAH). Comandos chegam por
-- noctalia.state("sb.cmd") ou por IPC.

noctalia.setUpdateInterval(1000)

local POLL_EVERY = 5 -- segundos
local SOUND = "girassol"
local SOUND_LEN = 14 -- duração do arquivo, em segundos

local soundReady = false
local tick = 0
local day = nil
local fired = {} -- ["AAAA-MM-DD/id"] = true
local ringing = nil -- tarefa tocando agora
local ringElapsed = 0
local focus = nil -- { endsAt = segundos, total = segundos, task = {id, label, step} }

local function port()
  return noctalia.getConfig("port") or 47321
end

local function base()
  return `http://127.0.0.1:{port()}`
end

local function today()
  return os.date("%Y-%m-%d")
end

local function publish()
  noctalia.state.set("sb.day", day or {})
  noctalia.state.set("sb.ringing", ringing or {})
  noctalia.state.set("sb.focus", focus or {})
end

local function refresh()
  noctalia.http({ url = base() .. "/v1/day?date=" .. today() }, function(resp)
    if resp.ok and resp.status == 200 then
      local ok, data = pcall(noctalia.json.decode, resp.body)
      if ok and type(data) == "table" then
        day = data
        noctalia.state.set("sb.online", true)
        publish()
        return
      end
    end
    noctalia.state.set("sb.online", false)
  end)
end

local function setKey(key, value)
  noctalia.http({
    url = base() .. "/v1/set",
    method = "POST",
    headers = { "Content-Type: application/json" },
    body = noctalia.json.encode({ key = key, value = value }),
  }, function(resp)
    if not (resp.ok and resp.status == 200) then
      noctalia.notifyError("SunBi", noctalia.tr("ui.offline"))
    end
    refresh()
  end)
end

local function isDone(id)
  return day and day.done and day.done[id] == true
end

local function taskById(id)
  for _, t in ipairs((day and day.tasks) or {}) do
    if t.id == id then
      return t
    end
  end
  return nil
end

local function stopRinging()
  ringing = nil
  ringElapsed = 0
  publish()
end

local function setDone(id, value)
  if day and day.done then
    day.done[id] = value -- resposta imediata na interface
    publish()
  end
  -- feito hoje guarda o horário real (regularidade das âncoras); desfeito é false
  setKey(`{today()}/done/{id}`, value and os.date("%H:%M") or false)
end

local function ring(task)
  ringing = task
  ringElapsed = 0
  publish()
  local body = task.label
  if task.step and task.step ~= "" then
    body = body .. "\n" .. noctalia.tr("ui.first_step", { s = task.step })
  end
  noctalia.notify("SunBi · " .. task.time, body)
  if soundReady then
    noctalia.sound.play(SOUND)
  end
end

local function checkAlarms()
  if not day or not day.tasks or noctalia.getConfig("alarms") == false then
    return
  end
  local hm = os.date("%H:%M")
  local d = today()
  for _, task in ipairs(day.tasks) do
    local k = `{d}/{task.id}`
    if task.time == hm and not fired[k] then
      fired[k] = true
      if not isDone(task.id) then
        ring(task)
      end
    end
  end
end

local function startFocus(minutes, id)
  local m = tonumber(minutes) or 5
  local t = id and taskById(id) or nil
  focus = {
    endsAt = os.time() + m * 60,
    total = m * 60,
    task = t and { id = t.id, label = t.label, step = t.step } or {},
  }
  publish()
end

local function checkFocus()
  if focus and focus.endsAt and os.time() >= focus.endsAt then
    local label = focus.task and focus.task.label
    focus = nil
    publish()
    noctalia.notify(noctalia.tr("ui.focus_end"), (label and (label .. " · ") or "") .. noctalia.tr("ui.focus_end_body"))
    if soundReady then
      noctalia.sound.play(SOUND)
    end
  end
end

local commands = {
  toggle = function(arg)
    setDone(arg, not isDone(arg))
  end,
  done = function(arg)
    local id = arg or (ringing and ringing.id)
    if id then
      setDone(id, true)
      if focus and focus.task and focus.task.id == id then
        focus = nil
      end
    end
    stopRinging()
  end,
  stop = function()
    stopRinging()
  end,
  humor = function(arg)
    local n = tonumber(arg)
    if day then
      day.humor = n -- resposta imediata
      publish()
    end
    setKey(`{today()}/humor`, n)
  end,
  note = function(arg)
    setKey(`{today()}/note`, arg or "")
  end,
  focus = function(arg)
    -- "15" ou "15 <id da tarefa>"
    local m, id = string.match(tostring(arg or "5"), "^(%d+)%s*(%S*)$")
    startFocus(m or 5, (id ~= nil and id ~= "") and id or nil)
  end,
  ["focus-stop"] = function()
    focus = nil
    publish()
  end,
  refresh = function()
    refresh()
  end,
}

noctalia.state.watch("sb.cmd", function(cmd)
  if type(cmd) ~= "table" then
    return
  end
  local fn = commands[cmd.op]
  if fn then
    fn(cmd.arg)
  end
end)

function onIpc(event, payload)
  local fn = commands[event]
  if fn then
    fn(payload)
  else
    noctalia.log(`comando desconhecido: {event}`)
  end
end

function onConfigChanged()
  refresh()
end

function update()
  tick += 1
  if tick % POLL_EVERY == 1 then
    refresh()
  end
  checkAlarms()
  checkFocus()
  if ringing then
    ringElapsed += 1
    local limit = noctalia.getConfig("ring_seconds") or 45
    if ringElapsed >= limit or isDone(ringing.id) then
      stopRinging()
    elseif soundReady and ringElapsed % SOUND_LEN == 0 then
      noctalia.sound.play(SOUND)
    end
  end
end

noctalia.sound.load(SOUND, "sounds/girassol.wav", function(ok, err)
  soundReady = ok
  if not ok then
    noctalia.log(`som do alarme não carregou: {err}`)
  end
end)

noctalia.state.set("sb.online", false)
publish()
refresh()
