--!nonstrict
-- Testes do módulo de dados. Rodar da raiz do plugin:  luau tests/store_test.luau
local S = require("../lib/store")

local passed, failed = 0, 0
local function test(name, fn)
  local ok, err = pcall(fn)
  if ok then
    passed += 1
  else
    failed += 1
    print("FALHOU: " .. name .. "\n  " .. tostring(err))
  end
end
local function eq(a, b, msg)
  if a ~= b then
    error(`{msg or ""} esperado {tostring(b)}, veio {tostring(a)}`, 2)
  end
end

test("registro mais novo vence", function()
  local e = { k = { v = 1, t = 10, d = "a" } }
  local ch = S.merge(e, { k = { v = 2, t = 20, d = "b" } })
  eq(e.k.v, 2)
  eq(#ch, 1)
end)

test("registro mais velho perde", function()
  local e = { k = { v = 1, t = 30, d = "a" } }
  eq(#S.merge(e, { k = { v = 2, t = 20, d = "b" } }), 0)
  eq(e.k.v, 1)
end)

test("empate decidido pelo aparelho", function()
  local e = { k = { v = 1, t = 10, d = "a" } }
  S.merge(e, { k = { v = 2, t = 10, d = "b" } })
  eq(e.k.v, 2)
  S.merge(e, { k = { v = 3, t = 10, d = "a" } })
  eq(e.k.v, 2)
end)

test("ignora lixo", function()
  local e = {}
  S.merge(e, { x = "lixo", y = { v = 1 } })
  eq(next(e), nil)
end)

test("convergência em qualquer ordem", function()
  local ups = {}
  local seed = 7
  local function rnd(n)
    seed = (seed * 1103515245 + 12345) % 2147483648
    return seed % n
  end
  for _ = 1, 200 do
    -- (t, d) identifica uma escrita: o valor é função dele, como no mundo real
    local t, d = rnd(50), string.char(97 + rnd(3))
    table.insert(ups, { ["k" .. rnd(6)] = { v = t * 10 + string.byte(d), t = t, d = d } })
  end
  local function run(order)
    local st = {}
    for _, i in ipairs(order) do
      S.merge(st, ups[i])
    end
    local keys = {}
    for k, r in st do
      table.insert(keys, `{k}={r.v}@{r.t}{r.d}`)
    end
    table.sort(keys)
    return table.concat(keys, ",")
  end
  local fwd, rev = {}, {}
  for i = 1, #ups do
    table.insert(fwd, i)
    table.insert(rev, 1, i)
  end
  eq(run(fwd), run(rev))
end)

test("feito aceita horário ou true", function()
  local e = {}
  S.set(e, "2026-11-15/done/wake", "07:20", 1, "x")
  S.set(e, "2026-11-15/done/bed", false, 1, "x")
  local d = S.day(e, "2026-11-15")
  eq(d.done.wake, true)
  eq(d.done.bed, false)
  eq(d.doneAt.wake, "07:20")
  eq(d.doneCount, 1)
end)

test("humor nova e antiga escala", function()
  local e = {}
  S.set(e, "2026-11-14/mood", 1, 1, "x")
  eq(S.humor(e, "2026-11-14"), -2)
  S.set(e, "2026-11-15/humor", 3, 1, "x")
  eq(S.day(e, "2026-11-15").humorLabel, "Muito acelerado")
end)

test("sono atravessa a meia-noite", function()
  eq(S.sleepHours("23:30", "07:00"), 7.5)
  eq(S.sleepHours("01:15", "06:45"), 5.5)
  eq(S.sleepHours(nil, "06:45"), nil)
end)

test("rotina padrão tem cinco âncoras e fica ordenada", function()
  local n = 0
  for _, t in ipairs(S.DEFAULT_TASKS) do
    if t.anchor then
      n += 1
    end
  end
  eq(n, 5)
  local e = {}
  S.set(e, "tasks", { { id = "b", time = "09:00", label = "B" }, { id = "a", time = "08:00", label = "A" } }, 5, "x")
  eq(S.tasks(e)[1].id, "a")
end)

test("agora ou próxima", function()
  local e = {}
  S.set(e, "tasks", {
    { id = "a", time = "08:00", label = "A" },
    { id = "b", time = "10:00", label = "B" },
  }, 5, "x")
  local v = S.day(e, "d")
  local t, m, late = S.nowOrNext(v, 9 * 60)
  eq(t.id, "a")
  eq(m, 60)
  eq(late, true)
  S.set(e, "d/done/a", "08:05", 6, "x")
  t, m, late = S.nowOrNext(S.day(e, "d"), 9 * 60)
  eq(t.id, "b")
  eq(m, 60)
  eq(late, false)
end)

test("lê a saída do avahi-browse", function()
  local out = table.concat({
    "+;wlp2s0;IPv4;quest-7a21;_sunbi._tcp;local",
    '=;wlp2s0;IPv4;quest-7a21;_sunbi._tcp;local;Quest-3.local;192.168.1.23;47321;"name=Quest\\0323" "v=1" "id=quest-7a21"',
    '=;wlp2s0;IPv6;quest-7a21;_sunbi._tcp;local;Quest-3.local;fe80::1;47321;"id=quest-7a21"',
  }, "\n")
  local p = S.parseAvahi(out)
  eq(p["quest-7a21"].addr, "192.168.1.23:47321")
  eq(p["quest-7a21"].name, "Quest 3")
  -- celular real visto aqui: linha "IPv6" com endereço IPv4
  local q = S.parseAvahi('=;enp7s0;IPv6;android-4fb5f3;_sunbi._tcp;local;Android.local;192.168.2.214;47321;"name=SM-A366E" "id=android-4fb5f3" "v=1"')
  eq(q["android-4fb5f3"].addr, "192.168.2.214:47321")
  eq(q["android-4fb5f3"].name, "SM-A366E")
end)

print(`{passed} ok, {failed} falharam`)
if failed > 0 then
  error("testes falharam")
end
