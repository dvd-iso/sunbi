--!nonstrict
-- SunBi: traduções com idioma escolhido no próprio plugin.
--
-- noctalia.tr segue o idioma do Noctalia; num sistema em inglês isso misturava textos em
-- inglês com as tarefas em português. Aqui o padrão é português, com opção de inglês ou de
-- seguir o Noctalia (configuração "language": "pt-BR" | "en" | "auto").

local M = {}
local cache = {}

local function load(lang)
  if cache[lang] == nil then
    local raw = noctalia.readFile(`translations/{lang}.json`) -- caminho relativo ao plugin
    local ok, obj = pcall(noctalia.json.decode, raw or "")
    cache[lang] = (ok and type(obj) == "table") and obj or false
  end
  return cache[lang]
end

local function lookup(tbl, key)
  for part in string.gmatch(key, "[^%.]+") do
    if type(tbl) ~= "table" then
      return nil
    end
    tbl = tbl[part]
  end
  return tbl
end

local function subst(s, vars)
  return (string.gsub(s, "{(%w+)}", function(k)
    local v = vars and vars[k]
    return v ~= nil and tostring(v) or ("{" .. k .. "}")
  end))
end

--- Idioma escolhido, ou nil para seguir o Noctalia.
function M.lang()
  local l = noctalia.getConfig("language")
  if l == "auto" then
    return nil
  end
  if l == "en" then
    return "en"
  end
  return "pt-BR"
end

function M.t(key, vars)
  local l = M.lang()
  local tbl = l and load(l)
  local s = tbl and lookup(tbl, key)
  if type(s) ~= "string" then
    return noctalia.tr(key, vars)
  end
  return subst(s, vars)
end

--- Plural: usa <key>.one quando n == 1 e <key>.other no resto ({n} e {count} disponíveis).
function M.tp(key, n, vars)
  local l = M.lang()
  local tbl = l and load(l)
  local node = tbl and lookup(tbl, key)
  if type(node) ~= "table" then
    return noctalia.trp(key, n, vars)
  end
  local s = (n == 1 and node.one) or node.other or node.one
  local v = vars or {}
  v.count = n
  return subst(tostring(s or key), v)
end

return M
