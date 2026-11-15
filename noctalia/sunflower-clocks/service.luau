--!nonstrict
-- Sunflower Clocks: serviço em segundo plano.
--
-- Lê o dia do daemon sunflowerd a cada poucos segundos e publica em
-- noctalia.state para a barra e o painel. Toca o alarme quando chega a hora
-- de uma tarefa ainda não feita. Comandos chegam por noctalia.state("sf.cmd")
-- ou por IPC.

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
  noctalia.state.set("sf.day", day or {})
  noctalia.state.set("sf.ringing", ringing or {})
end

local function refresh()
  noctalia.http({ url = base() .. "/v1/day?date=" .. today() }, function(resp)
    if resp.ok and resp.status == 200 then
      local ok, data = pcall(noctalia.json.decode, resp.body)
      if ok and type(data) == "table" then
        day = data
        noctalia.state.set("sf.online", true)
        publish()
        return
      end
    end
    noctalia.state.set("sf.online", false)
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
      noctalia.notifyError("Sunflower Clocks", noctalia.tr("ui.offline"))
    end
    refresh()
  end)
end

local function isDone(id)
  return day and day.done and day.done[id] == true
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
  setKey(`{today()}/done/{id}`, value)
end

local function ring(task)
  ringing = task
  ringElapsed = 0
  publish()
  noctalia.notify("Sunflower Clocks · " .. task.time, task.label)
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

local commands = {
  toggle = function(arg)
    setDone(arg, not isDone(arg))
  end,
  done = function(arg)
    local id = arg or (ringing and ringing.id)
    if id then
      setDone(id, true)
    end
    stopRinging()
  end,
  stop = function()
    stopRinging()
  end,
  mood = function(arg)
    setKey(`{today()}/mood`, tonumber(arg))
  end,
  note = function(arg)
    setKey(`{today()}/note`, arg or "")
  end,
  refresh = function()
    refresh()
  end,
}

noctalia.state.watch("sf.cmd", function(cmd)
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

noctalia.state.set("sf.online", false)
publish()
refresh()
