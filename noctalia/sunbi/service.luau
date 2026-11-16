--!nonstrict
-- SunBi: serviço em segundo plano. Roda tudo dentro do plugin, sem daemon:
--
-- * guarda a rotina em <pluginDataDir>/state.json (registros last-writer-wins, como no app);
-- * acha o celular e o Quest na rede com `avahi-browse` (mDNS) ou pelos pares em Ajustes;
-- * sincroniza como cliente: um POST /v1/merge por aparelho troca os dados nos dois
--   sentidos, a cada 30 s e logo depois de cada mudança feita aqui;
-- * toca os alarmes, controla o bloco de foco (TDAH) e os sons.
--
-- Publica para a barra e o painel em noctalia.state: sb.day, sb.ringing, sb.focus, sb.sync.
-- Comandos chegam por noctalia.state("sb.cmd") ou por IPC.

local S = require("./lib/store.luau")
local I = require("./lib/i18n.luau")

noctalia.setUpdateInterval(1000)

local SYNC_EVERY = 30 -- segundos
local DISCOVER_EVERY = 120
local DEFAULT_PORT = 47321
local SOUND_LEN = { amanhecer = 5, girassol = 14 } -- segundos até repetir o toque

local dataDir = noctalia.pluginDataDir() or noctalia.expandPath("~/.local/share/sunbi-noctalia")
local STATE = dataDir .. "/state.json"
local IDENT = dataDir .. "/device.json"

local entries = {}
local ident = {} -- { device, key }
local hostname = "PC"
local dirty, saveAt = false, 0
local peers = {} -- id -> { id, name, addr, ok, seen, why, manual, busy }
local tick = 0
local lastSync, lastDiscover = -SYNC_EVERY, -DISCOVER_EVERY
local pushAt = nil
local fired = {} -- ["AAAA-MM-DD/id"] = true
local ringing = nil
local ringElapsed = 0
local focus = nil -- { endsAt, total, task }
local soundsReady = {}
local view = nil
local wasComplete = false

-- ── utilidades ───────────────────────────────────────────────────────────────

local function today()
  return os.date("%Y-%m-%d")
end

local function nowMin()
  return tonumber(os.date("%H")) * 60 + tonumber(os.date("%M"))
end

local function hex(n)
  local s = {}
  for _ = 1, n do
    table.insert(s, string.format("%02x", math.random(0, 255)))
  end
  return table.concat(s)
end

local function play(name)
  if soundsReady[name] then
    noctalia.sound.play(name)
  end
end

local function uiSound(name)
  if noctalia.getConfig("sounds") ~= false then
    play(name)
  end
end

local function pairingKey()
  local k = noctalia.getConfig("key")
  if type(k) == "string" and string.match(k, "%S") then
    return string.upper((string.gsub(k, "%s", "")))
  end
  return ident.key
end

-- ── persistência ────────────────────────────────────────────────────────────

local function load()
  noctalia.mkdirAll(dataDir)
  local raw = noctalia.readFile(IDENT)
  local ok, obj = pcall(noctalia.json.decode, raw or "")
  ident = (ok and type(obj) == "table") and obj or {}
  if not ident.device or not ident.key then
    math.randomseed(noctalia.nowMs() % 2147483647)
    ident.device = ident.device or ("linux-" .. hex(3))
    ident.key = ident.key or string.upper(hex(4))
    noctalia.writeFile(IDENT, noctalia.json.encode(ident))
  end

  raw = noctalia.readFile(STATE)
  ok, obj = pcall(noctalia.json.decode, raw or "")
  entries = (ok and type(obj) == "table" and type(obj.entries) == "table") and obj.entries or {}
  if entries.tasks == nil then
    -- t = 1: qualquer rotina editada em outro aparelho vence a padrão
    entries.tasks = { v = S.DEFAULT_TASKS, t = 1, d = "" }
  end

  local h = noctalia.readFile("/etc/hostname")
  if h and string.match(h, "%S") then
    hostname = string.match(h, "^%s*(.-)%s*$")
  end
end

local function save()
  local tmp = STATE .. ".tmp"
  local ok, err = noctalia.writeFile(tmp, noctalia.json.encode({ device = ident.device, entries = entries }))
  if ok then
    noctalia.renameFile(tmp, STATE)
    dirty = false
  else
    noctalia.log("não consegui salvar: " .. tostring(err))
  end
end

local function markDirty()
  dirty = true
  saveAt = os.time() + 2
end

-- ── publicação ──────────────────────────────────────────────────────────────

local function publishSync()
  local list = {}
  for _, p in peers do
    table.insert(list, { name = p.name, addr = p.addr, ok = p.ok, why = p.why, seen = p.seen })
  end
  table.sort(list, function(a, b)
    return tostring(a.name) < tostring(b.name)
  end)
  noctalia.state.set("sb.sync", { key = pairingKey(), device = ident.device, name = hostname, peers = list })
end

local function publish()
  view = S.day(entries, today())
  noctalia.state.set("sb.day", view)
  noctalia.state.set("sb.ringing", ringing or {})
  noctalia.state.set("sb.focus", focus or {})
  noctalia.state.set("sb.online", true)
  local complete = #view.tasks > 0 and view.doneCount == #view.tasks
  if complete and not wasComplete then
    uiSound("chime")
  end
  wasComplete = complete
end

-- ── sincronização ───────────────────────────────────────────────────────────

local function syncPeer(p)
  if p.busy then
    return
  end
  p.busy = true
  local body = noctalia.json.encode({ device = ident.device, name = hostname, listen = false, entries = entries })
  noctalia.http({
    url = `http://{p.addr}/v1/merge`,
    method = "POST",
    headers = { "Content-Type: application/json; charset=utf-8", "X-SunBi-Key: " .. pairingKey() },
    body = body,
  }, function(resp)
    p.busy = false
    if resp.ok and resp.status == 200 then
      local ok, obj = pcall(noctalia.json.decode, resp.body)
      if ok and type(obj) == "table" then
        local changed = S.merge(entries, obj.entries)
        if #changed > 0 then
          markDirty()
          publish()
        end
        p.ok, p.why, p.seen = true, nil, os.time()
      else
        p.ok, p.why = false, "resposta inválida"
      end
    elseif resp.ok and resp.status == 401 then
      p.ok, p.why = false, "código de pareamento diferente"
    else
      p.ok, p.why = false, "fora do ar"
    end
    publishSync()
  end)
end

local function syncAll()
  lastSync = tick
  for _, p in peers do
    syncPeer(p)
  end
end

local function loadManualPeers()
  for id, p in peers do
    if p.manual then
      peers[id] = nil
    end
  end
  local list = noctalia.getConfig("peers")
  if type(list) == "table" then
    for _, a in ipairs(list) do
      a = string.match(tostring(a), "^%s*(.-)%s*$")
      if a ~= "" then
        if not string.find(a, ":") then
          a = `{a}:{DEFAULT_PORT}`
        end
        peers["manual:" .. a] = { id = "manual:" .. a, name = a, addr = a, manual = true }
      end
    end
  end
end

local function discover()
  lastDiscover = tick
  if not noctalia.commandExists("avahi-browse") then
    return
  end
  noctalia.runAsync({ "avahi-browse", "-rpt", "_sunbi._tcp" }, function(res)
    local found = S.parseAvahi(res.stdout)
    for id, f in found do
      if id ~= ident.device then
        local old = peers[id]
        local isNew = old == nil or old.addr ~= f.addr
        peers[id] = { id = id, name = f.name, addr = f.addr, ok = old and old.ok, seen = old and old.seen, why = old and old.why }
        if isNew then
          syncPeer(peers[id])
        end
      end
    end
    publishSync()
  end, 15000)
end

local function pushSoon()
  pushAt = os.time() + 1
end

local function setKey(key, value)
  S.set(entries, key, value, noctalia.nowMs(), ident.device)
  markDirty()
  publish()
  pushSoon()
end

-- ── rotina, alarmes e foco ──────────────────────────────────────────────────

local function isDone(id)
  return view and view.done and view.done[id] == true
end

local function taskById(id)
  for _, t in ipairs((view and view.tasks) or {}) do
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
  -- feito hoje guarda o horário real (regularidade das âncoras); desfeito é false
  uiSound(value and "pop" or "unpop")
  setKey(`{today()}/done/{id}`, value and os.date("%H:%M") or false)
end

local function alarmSound()
  local s = noctalia.getConfig("alarm_sound")
  return (s == "girassol") and "girassol" or "amanhecer"
end

local function ring(task)
  ringing = task
  ringElapsed = 0
  publish()
  local body = task.label
  if task.step and task.step ~= "" then
    body = body .. "\n" .. I.t("ui.first_step", { s = task.step })
  end
  noctalia.notify("SunBi · " .. task.time, body)
  play(alarmSound())
end

local function checkAlarms()
  if not view or noctalia.getConfig("alarms") == false or not store.ringsHere(entries, ident.device) then
    return
  end
  local hm = os.date("%H:%M")
  local d = today()
  for _, task in ipairs(view.tasks) do
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
  local m = math.clamp(tonumber(minutes) or 5, 1, 180)
  local t = id and taskById(id) or nil
  focus = {
    endsAt = os.time() + m * 60,
    total = m * 60,
    task = t and { id = t.id, label = t.label, step = t.step } or {},
  }
  uiSound("focus_start")
  publish()
end

local function checkFocus()
  if focus and focus.endsAt and os.time() >= focus.endsAt then
    local label = focus.task and focus.task.label
    focus = nil
    publish()
    noctalia.notify(I.t("ui.focus_end"), (label and (label .. " · ") or "") .. I.t("ui.focus_end_body"))
    play("focus_end")
  end
end

-- ── comandos ────────────────────────────────────────────────────────────────

local commands = {
  toggle = function(arg)
    if arg then
      setDone(arg, not isDone(arg))
    end
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
    -- "-3".."3" marca; "clear" limpa; qualquer outra coisa é ignorada
    local n = tonumber(arg)
    if n then
      uiSound("tick")
      setKey(`{today()}/humor`, math.clamp(math.floor(n), -3, 3))
    elseif arg == "clear" then
      setKey(`{today()}/humor`, nil)
    end
  end,
  vacation = function()
    -- toque na flor: liga ou desliga o modo férias (mesma regra do app)
    local writes = S.toggleOff(entries, today(), today())
    local t = noctalia.nowMs()
    for _, w in ipairs(writes) do
      S.set(entries, w[1], w[2], t, ident.device)
    end
    markDirty()
    uiSound(S.offReason(entries, today()) and "pop" or "unpop")
    publish()
    pushSoon()
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
  extend = function(arg)
    if focus and focus.endsAt then
      local m = tonumber(arg) or 5
      focus.endsAt += m * 60
      focus.total += m * 60
      publish()
    end
  end,
  sync = function()
    discover()
    syncAll()
  end,
  refresh = function()
    publish()
    publishSync()
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
  loadManualPeers()
  publishSync()
  syncAll()
end

function update()
  tick += 1
  if tick - lastDiscover >= DISCOVER_EVERY then
    discover()
  end
  if tick - lastSync >= SYNC_EVERY or (pushAt and os.time() >= pushAt) then
    pushAt = nil
    syncAll()
  end
  if dirty and os.time() >= saveAt then
    save()
  end
  if tick % 30 == 0 then
    publish() -- vira o dia e atualiza "em X min"
  end
  checkAlarms()
  checkFocus()
  if ringing then
    ringElapsed += 1
    local limit = noctalia.getConfig("ring_seconds") or 60
    if ringElapsed >= limit or isDone(ringing.id) then
      stopRinging()
    elseif ringElapsed % (SOUND_LEN[alarmSound()] or 5) == 0 then
      play(alarmSound())
    end
  end
end

function onExit()
  if dirty then
    save()
  end
end

-- ── início ──────────────────────────────────────────────────────────────────

for _, name in { "pop", "unpop", "tick", "chime", "focus_start", "focus_end", "amanhecer", "girassol" } do
  noctalia.sound.load(name, `sounds/{name}.wav`, function(ok, err)
    soundsReady[name] = ok
    if not ok then
      noctalia.log(`som {name} não carregou: {err}`)
    end
  end)
end

load()
loadManualPeers()
publish()
wasComplete = #view.tasks > 0 and view.doneCount == #view.tasks
publishSync()
