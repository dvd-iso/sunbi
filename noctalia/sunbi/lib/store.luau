--!nonstrict
-- SunBi: modelo de dados (registros last-writer-wins) e visão do dia.
--
-- Módulo puro: não usa noctalia.*, então roda também nos testes (tests/store_test.luau).
-- O formato é o mesmo do app Android; veja docs/PROTOCOL.md.

local M = {}

-- Rotina inicial igual à do app (Repo.DEFAULT_TASKS): as cinco âncoras do ritmo social,
-- remédios e pausas. "step" é o primeiro passo (TDAH), "min" a duração estimada.
M.DEFAULT_TASKS = {
  { id = "wake", time = "07:00", label = "Levantar da cama", anchor = true, step = "Sentar na cama e abrir a cortina", min = 5 },
  { id = "medam", time = "07:15", label = "Remédio da manhã", med = true, step = "Pegar o copo d'água", min = 2 },
  { id = "breakfast", time = "07:30", label = "Café da manhã", step = "Ir até a cozinha", min = 20 },
  { id = "contact", time = "08:30", label = "Falar com alguém (mensagem, ligação ou pessoalmente)", anchor = true, step = "Mandar um \"bom dia\"", min = 5 },
  { id = "start", time = "09:00", label = "Começar o trabalho, estudo ou tarefa do dia", anchor = true, step = "Abrir só a primeira coisa da lista", min = 25 },
  { id = "water1", time = "10:30", label = "Beber água", min = 1 },
  { id = "lunch", time = "12:30", label = "Almoço: pare e coma", step = "Levantar da cadeira", min = 30 },
  { id = "water2", time = "15:00", label = "Lanche e água", min = 10 },
  { id = "move", time = "17:30", label = "Movimento leve ou luz do dia", step = "Calçar o tênis", min = 20 },
  { id = "dinner", time = "19:30", label = "Jantar", anchor = true, step = "Pôr a mesa ou esquentar a comida", min = 30 },
  { id = "dim", time = "21:30", label = "Luz baixa e telas no modo noturno", step = "Apagar a luz principal", min = 5 },
  { id = "medpm", time = "22:00", label = "Remédio da noite", med = true, step = "Pegar o copo d'água", min = 2 },
  { id = "bed", time = "22:30", label = "Deitar para dormir", anchor = true, step = "Deixar o celular fora da cama", min = 10 },
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

function M.tasks(entries)
  local t = M.get(entries, "tasks")
  if type(t) ~= "table" or #t == 0 then
    return sortedTasks(M.DEFAULT_TASKS)
  end
  return sortedTasks(t)
end

--- Visão pronta de um dia para a barra e o painel.
function M.day(entries, day)
  local tasks = M.tasks(entries)
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
