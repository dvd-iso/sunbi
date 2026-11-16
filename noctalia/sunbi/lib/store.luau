--!nonstrict
-- SunBi: modelo de dados (registros last-writer-wins) e visão do dia.
--
-- Módulo puro: não usa noctalia.*, então roda também nos testes (tests/store_test.luau).
-- O formato é o mesmo do app Android; veja docs/PROTOCOL.md.

local M = {}

-- Rotina inicial igual à do app (Repo.DEFAULT_TASKS): as cinco âncoras do ritmo social,
-- remédios e pausas. "step" é o primeiro passo (TDAH).
M.DEFAULT_TASKS = {
  { id = "wake", time = "07:00", label = "Levantar da cama", anchor = true, step = "Sentar na cama e abrir a cortina" },
  { id = "medam", time = "07:15", label = "Remédio da manhã", med = true, step = "Pegar o copo d'água" },
  { id = "breakfast", time = "07:30", label = "Café da manhã", step = "Ir até a cozinha" },
  { id = "contact", time = "08:30", label = "Falar com alguém (mensagem, ligação ou pessoalmente)", anchor = true, step = "Mandar um \"bom dia\"" },
  { id = "start", time = "09:00", label = "Começar o trabalho, estudo ou tarefa do dia", anchor = true, step = "Abrir só a primeira coisa da lista" },
  { id = "water1", time = "10:30", label = "Beber água" },
  { id = "lunch", time = "12:30", label = "Almoço: pare e coma", step = "Levantar da cadeira" },
  { id = "water2", time = "15:00", label = "Lanche e água" },
  { id = "move", time = "17:30", label = "Movimento leve ou luz do dia", step = "Calçar o tênis" },
  { id = "dinner", time = "19:30", label = "Jantar", anchor = true, step = "Pôr a mesa ou esquentar a comida" },
  { id = "dim", time = "21:30", label = "Luz baixa e telas no modo noturno", step = "Apagar a luz principal" },
  { id = "medpm", time = "22:00", label = "Remédio da noite", med = true, step = "Pegar o copo d'água" },
  { id = "bed", time = "22:30", label = "Deitar para dormir", anchor = true, step = "Deixar o celular fora da cama" },
}

-- Rotina de folga (fins de semana, feriados, férias), igual à do app (Repo.DEFAULT_OFF_TASKS).
M.DEFAULT_OFF_TASKS = {
  { id = "wake", time = "08:00", label = "Levantar da cama", anchor = true, step = "Abrir a cortina e beber água" },
  { id = "medam", time = "08:15", label = "Remédio da manhã", med = true, step = "Pegar o copo d'água" },
  { id = "breakfast", time = "08:30", label = "Café da manhã sem pressa" },
  { id = "contact", time = "10:00", label = "Falar com alguém ou sair de casa", anchor = true, step = "Mandar uma mensagem" },
  { id = "lunch", time = "13:00", label = "Almoço" },
  { id = "fun", time = "15:00", label = "Algo que você gosta de fazer", step = "Escolher uma coisa só" },
  { id = "move", time = "17:00", label = "Luz do dia ou caminhada", step = "Calçar o tênis" },
  { id = "dinner", time = "20:00", label = "Jantar", anchor = true },
  { id = "dim", time = "22:00", label = "Luz baixa e telas no modo noturno", step = "Apagar a luz principal" },
  { id = "medpm", time = "22:00", label = "Remédio da noite", med = true, step = "Pegar o copo d'água" },
  { id = "bed", time = "23:00", label = "Deitar para dormir", anchor = true, step = "Deixar o celular fora da cama" },
}

M.HUMOR = {
  [-3] = "Muito para baixo", [-2] = "Para baixo", [-1] = "Um pouco para baixo", [0] = "Estável",
  [1] = "Um pouco acelerado", [2] = "Acelerado", [3] = "Muito acelerado",
}

local function recT(r)
  return tonumber(r.t) or 0
end

--- true se o registro b deve substituir a (maior t; empate decidido pelo id do aparelho).
function M.newer(a, b)
  if type(b) ~= "table" or b.t == nil then
    return false
  end
  if type(a) ~= "table" then
    return true
  end
  local ta, tb = recT(a), recT(b)
  if tb ~= ta then
    return tb > ta
  end
  return tostring(b.d or "") > tostring(a.d or "")
end

--- Mescla remote em entries (no lugar). Devolve a lista de chaves que mudaram.
function M.merge(entries, remote)
  local changed = {}
  if type(remote) ~= "table" then
    return changed
  end
  for key, rec in remote do
    if type(key) == "string" and M.newer(entries[key], rec) then
      entries[key] = { v = rec.v, t = recT(rec), d = tostring(rec.d or "") }
      table.insert(changed, key)
    end
  end
  return changed
end

function M.set(entries, key, value, t, device)
  entries[key] = { v = value, t = t, d = device }
end

function M.get(entries, key, default)
  local r = entries[key]
  if r == nil or r.v == nil then
    return default
  end
  return r.v
end

--- Feito é `true` ou "HH:MM" (horário real); `false`/ausente é não feito.
function M.isDone(v)
  return v == true or type(v) == "string"
end

function M.minutes(hm)
  if type(hm) ~= "string" then
    return nil
  end
  local h, m = string.match(hm, "^(%d+):(%d+)$")
  if not h then
    return nil
  end
  return tonumber(h) * 60 + tonumber(m)
end

--- Horas de sono entre deitar e acordar, atravessando a meia-noite. Uma casa decimal.
function M.sleepHours(bed, wake)
  local b, w = M.minutes(bed), M.minutes(wake)
  if not b or not w then
    return nil
  end
  local mins = w - b
  if mins <= 0 then
    mins += 24 * 60
  end
  return math.floor(mins / 6 + 0.5) / 10
end

--- Humor -3..+3; lê a escala antiga do Sunflower Clocks (1..5) quando não há a nova.
function M.humor(entries, day)
  local v = M.get(entries, day .. "/humor")
  if type(v) == "number" then
    return math.clamp(math.floor(v), -3, 3)
  end
  local old = M.get(entries, day .. "/mood")
  if type(old) == "number" then
    return math.clamp(math.floor(old) - 3, -2, 2)
  end
  return nil
end

local function sortedTasks(tasks)
  local out = {}
  for _, t in ipairs(tasks) do
    if type(t) == "table" and t.id then
      table.insert(out, t)
    end
  end
  table.sort(out, function(a, b)
    return tostring(a.time) < tostring(b.time)
  end)
  return out
end

-- ── folga: fins de semana, feriados e modo férias (mesmas regras do app) ──────

local function ymd(day)
  local y, m, d = string.match(day or "", "^(%d+)-(%d+)-(%d+)$")
  return tonumber(y), tonumber(m), tonumber(d)
end

local function dateOf(y, m, d)
  return string.format("%04d-%02d-%02d", y, m, d)
end

local function addDays(day, n)
  local y, m, d = ymd(day)
  local t = os.time({ year = y, month = m, day = d + n, hour = 12 })
  return os.date("%Y-%m-%d", t)
end
M.addDays = addDays

--- Domingo de Páscoa (algoritmo anônimo gregoriano).
function M.easter(year)
  local a = year % 19
  local b = year // 100
  local c = year % 100
  local d = b // 4
  local e = b % 4
  local f = (b + 8) // 25
  local g = (b - f + 1) // 3
  local h = (19 * a + b - d - g + 15) % 30
  local i = c // 4
  local k = c % 4
  local l = (32 + 2 * e + 2 * i - h - k) % 7
  local m = (a + 11 * h + 22 * l) // 451
  local month = (h + l - 7 * m + 114) // 31
  local dd = ((h + l - 7 * m + 114) % 31) + 1
  return dateOf(year, month, dd)
end

local FIXED = {
  ["01-01"] = "Confraternização Universal", ["04-21"] = "Tiradentes", ["05-01"] = "Dia do Trabalho",
  ["09-07"] = "Independência", ["10-12"] = "Nossa Senhora Aparecida", ["11-02"] = "Finados",
  ["11-15"] = "Proclamação da República", ["11-20"] = "Consciência Negra", ["12-25"] = "Natal",
}

function M.holiday(day)
  local y = ymd(day)
  if not y then
    return nil
  end
  local f = FIXED[string.sub(day, 6, 10)]
  if f then
    return f
  end
  local e = M.easter(y)
  if day == addDays(e, -48) or day == addDays(e, -47) then
    return "Carnaval"
  elseif day == addDays(e, -2) then
    return "Paixão de Cristo"
  elseif day == addDays(e, 60) then
    return "Corpus Christi"
  end
  return nil
end

local function weekday(day) -- 1 = domingo ... 7 = sábado; nil se a data não for válida
  local y, m, d = ymd(day)
  if not y then
    return nil
  end
  return os.date("*t", os.time({ year = y, month = m, day = d, hour = 12 })).wday
end

function M.vacationSince(entries)
  local v = M.get(entries, "vacation")
  return type(v) == "table" and type(v.since) == "string" and v.since ~= "" and v.since or nil
end

--- "vacation" | "manual" | "holiday" | "weekend" | nil (rotina de semana).
function M.offReason(entries, day)
  local explicit = M.get(entries, day .. "/off")
  local since = M.vacationSince(entries)
  if explicit == true then
    return (since and day >= since) and "vacation" or "manual"
  elseif explicit == false then
    return nil
  end
  if since and day >= since then
    return "vacation"
  end
  if M.get(entries, "off/holidays") ~= false and M.holiday(day) then
    return "holiday"
  end
  local w = weekday(day)
  if M.get(entries, "off/weekends") ~= false and (w == 1 or w == 7) then
    return "weekend"
  end
  return nil
end

--- A rotina que vale no dia (sem dia: a de semana).
function M.tasks(entries, day)
  local key, default = "tasks", M.DEFAULT_TASKS
  if day and M.offReason(entries, day) then
    key, default = "tasks_off", M.DEFAULT_OFF_TASKS
  end
  local t = M.get(entries, key)
  if type(t) ~= "table" or #t == 0 then
    return sortedTasks(default)
  end
  return sortedTasks(t)
end

--- Toque na flor (mesma regra do app). Devolve a lista de (chave, valor) a gravar.
function M.toggleOff(entries, day, today)
  local writes = {}
  local since = M.vacationSince(entries)
  local function isOffNow()
    return M.offReason(entries, day) ~= nil
  end
  if day ~= today then
    table.insert(writes, { day .. "/off", not isOffNow() })
    return writes
  end
  if since then
    local d = since
    while d < today do
      if M.get(entries, d .. "/off") == nil then
        table.insert(writes, { d .. "/off", true })
      end
      d = addDays(d, 1)
    end
    table.insert(writes, { "vacation", nil })
    -- hoje: se ainda cairia em folga (fim de semana/feriado), volta ao normal
    local tmp = table.clone(entries)
    tmp.vacation = nil
    tmp[today .. "/off"] = nil
    if M.offReason(tmp, today) then
      table.insert(writes, { today .. "/off", false })
    elseif M.get(entries, today .. "/off") ~= nil then
      table.insert(writes, { today .. "/off", nil })
    end
    return writes
  end
  if isOffNow() then
    table.insert(writes, { today .. "/off", false })
    return writes
  end
  if M.get(entries, today .. "/off") == false then
    table.insert(writes, { today .. "/off", nil })
  end
  table.insert(writes, { "vacation", { since = today } })
  return writes
end

--- Visão pronta de um dia para a barra e o painel.
function M.day(entries, day)
  local tasks = M.tasks(entries, day)
  local done, doneAt, count = {}, {}, 0
  for _, t in ipairs(tasks) do
    local v = M.get(entries, `{day}/done/{t.id}`)
    done[t.id] = M.isDone(v)
    if done[t.id] then
      count += 1
    end
    if type(v) == "string" then
      doneAt[t.id] = v
    end
  end
  local bed, wake = M.get(entries, day .. "/bed"), M.get(entries, day .. "/wake")
  local sleep = M.sleepHours(bed, wake) or M.get(entries, day .. "/sleep")
  local humor = M.humor(entries, day)
  return {
    date = day,
    tasks = tasks,
    off = M.offReason(entries, day),
    holiday = M.holiday(day),
    done = done,
    doneAt = doneAt,
    doneCount = count,
    humor = humor,
    humorLabel = humor ~= nil and M.HUMOR[humor] or nil,
    energy = M.get(entries, day .. "/energy"),
    focus = M.get(entries, day .. "/focus"),
    sleep = sleep,
    bed = bed,
    wake = wake,
    note = M.get(entries, day .. "/note", ""),
  }
end

--- Tarefa atrasada (até 3 h, ainda não feita) ou a próxima. Devolve (tarefa, minutos, atrasada).
function M.nowOrNext(view, nowMin)
  local late, nxt = nil, nil
  for _, t in ipairs(view.tasks or {}) do
    local m = M.minutes(t.time)
    if m and not view.done[t.id] then
      if m <= nowMin and nowMin - m <= 180 then
        late = t
      elseif m > nowMin and not nxt then
        nxt = t
      end
    end
  end
  if late then
    return late, nowMin - M.minutes(late.time), true
  end
  if nxt then
    return nxt, M.minutes(nxt.time) - nowMin, false
  end
  return nil, nil, false
end

--- Linhas de `avahi-browse -rpt _sunbi._tcp` → pares { id, name, addr }. Só IPv4 resolvido.
function M.parseAvahi(stdout)
  local found = {}
  for line in string.gmatch(stdout or "", "[^\n]+") do
    if string.sub(line, 1, 1) == "=" then
      local f = string.split(line, ";")
      -- =;iface;proto;nome;tipo;domínio;host;ip;porta;txt
      -- o campo proto é o da interface: um endereço IPv4 pode vir numa linha "IPv6"
      if f[8] and f[9] and string.match(f[8], "^%d+%.%d+%.%d+%.%d+$") then
        local txt = f[10] or ""
        local id = string.match(txt, '"id=([^"]*)"') or f[4]
        local name = string.match(txt, '"name=([^"]*)"') or f[4]
        name = string.gsub(name, "\\(%d%d%d)", function(n)
          return string.char(tonumber(n))
        end)
        found[id] = { id = id, name = name, addr = `{f[8]}:{f[9]}` }
      end
    end
  end
  return found
end

return M
