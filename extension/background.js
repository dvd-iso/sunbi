// SunBi no navegador: roda em segundo plano (service worker), sem janela aberta.
//
// * guarda a rotina em chrome.storage.local (registros last-writer-wins, como no app);
// * sincroniza como cliente do celular: um POST /v1/merge troca os dados nos dois sentidos,
//   a cada minuto e logo depois de cada mudança feita aqui (docs/PROTOCOL.md);
// * avisa de cada tarefa na hora, por notificação do sistema, com "Concluído";
// * se o celular mudar de endereço na rede (o roteador troca o IP), procura por ele sozinho.
//
// O service worker é desligado pelo navegador quando fica à toa: nada mora em variáveis,
// tudo é lido do storage a cada evento, e quem acorda o trabalho é o chrome.alarms.

import * as S from './lib/store.js';

const api = globalThis.browser ?? globalThis.chrome;
const FIREFOX = typeof globalThis.browser !== 'undefined' && /firefox/i.test(navigator.userAgent);
const TICK = 'tick';
const RESCAN_EVERY = 15 * 60_000; // procurar o celular na rede no máximo a cada 15 min
const FAILS_BEFORE_RESCAN = 3;

// ── storage ─────────────────────────────────────────────────────────────────

const hex = n => [...crypto.getRandomValues(new Uint8Array(n))].map(b => b.toString(16).padStart(2, '0')).join('');

function browserName() {
  const ua = navigator.userAgent;
  const b = /firefox/i.test(ua) ? 'Firefox' : /edg\//i.test(ua) ? 'Edge' : /opr\//i.test(ua) ? 'Opera' : /brave/i.test(ua) ? 'Brave' : 'Chrome';
  const os = /windows/i.test(ua) ? 'Windows' : /mac os/i.test(ua) ? 'Mac' : /cros/i.test(ua) ? 'ChromeOS' : 'Linux';
  return `${b} no ${os}`;
}

async function load() {
  const st = await api.storage.local.get(['cfg', 'entries', 'sync', 'fired']);
  const cfg = { addr: '', key: '', notify: true, peerDevice: '', peerName: '', ...st.cfg };
  if (!cfg.device) {
    cfg.device = `pc-${hex(3)}`;
    cfg.name = browserName();
    await api.storage.local.set({ cfg });
  }
  return { cfg, entries: st.entries ?? {}, sync: { ok: null, at: 0, error: '', fails: 0, scanAt: 0, ...st.sync }, fired: st.fired ?? {} };
}

// uma mudança por vez: sync, clique e alarme podem chegar juntos
let chain = Promise.resolve();
const exclusive = fn => (chain = chain.then(fn, fn));

// ── sincronização ───────────────────────────────────────────────────────────

async function call(addr, path, key, body, timeout) {
  const res = await fetch(`http://${addr}${path}`, {
    method: body ? 'POST' : 'GET',
    headers: { 'X-SunBi-Key': key, ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(timeout),
    cache: 'no-store',
  });
  // 429: o celular parou de atender este endereço depois de vários códigos errados
  if (res.status === 401 || res.status === 429) throw new Error('key');
  if (!res.ok) throw new Error(`http ${res.status}`);
  return res.json();
}

const ERRORS = {
  key: 'O código de pareamento não bate com o do celular.',
  offline: 'Não achei o celular. Ele precisa estar ligado, com o SunBi instalado e na mesma rede Wi-Fi.',
};

/**
 * Procura o celular nos outros endereços da mesma rede. Devolve o endereço novo ou null.
 * Pergunta só "quem é você?" (/v1/ping, sem o código): o código de pareamento nunca é mandado
 * a um endereço que não se identificou como o celular já pareado.
 */
async function rediscover(cfg) {
  if (!cfg.peerDevice) return null;
  const list = S.neighbors(cfg.addr);
  for (let i = 0; i < list.length; i += 32) {
    const found = await Promise.all(list.slice(i, i + 32).map(async addr => {
      try {
        const res = await fetch(`http://${addr}/v1/ping`, { signal: AbortSignal.timeout(1500), cache: 'no-store' });
        const who = res.ok ? await res.json() : null;
        return who && who.v === 1 && who.device === cfg.peerDevice ? addr : null;
      } catch { return null; }
    }));
    const hit = found.find(Boolean);
    if (hit) return hit;
  }
  return null;
}

// Uma sincronização por vez. A rede fica fora da fila de gravação, então marcar uma tarefa
// nunca espera o celular responder; se algo mudou durante a chamada, roda mais uma em seguida.
let syncing = null, again = false;

/** Uma ida e volta com o celular. Devolve true se sincronizou. */
function syncNow(opts) {
  if (syncing) { again = true; return syncing; }
  syncing = doSync(opts).finally(() => {
    syncing = null;
    if (again) { again = false; syncNow().then(refresh); }
  });
  return syncing;
}

async function doSync({ scan = false } = {}) {
  const { cfg, entries, sync } = await load();
  if (!cfg.addr || !cfg.key) return false;
  const attempt = async addr => {
    const res = await call(addr, '/v1/merge', cfg.key, { device: cfg.device, name: cfg.name, listen: false, entries }, 10_000);
    // o que mudou aqui enquanto a chamada estava no ar continua valendo: a mesclagem é por registro
    await exclusive(async () => {
      if ((await load()).cfg.key !== cfg.key) return; // esqueceram o celular no meio da chamada
      const fresh = (await api.storage.local.get('entries')).entries ?? {};
      if (S.merge(fresh, res.entries).length) await api.storage.local.set({ entries: fresh });
    });
    return res;
  };
  const saveCfg = patch => exclusive(async () => {
    const cur = (await load()).cfg;
    if (cur.key === cfg.key) await api.storage.local.set({ cfg: { ...cur, ...patch } }); // não desfaz um "esquecer" no meio
  });
  try {
    const res = await attempt(cfg.addr);
    if (res.device && (res.device !== cfg.peerDevice || !cfg.peerName)) {
      const peerName = await call(cfg.addr, '/v1/hello', cfg.key, null, 4000).then(h => h.name || '', () => '');
      await saveCfg({ peerDevice: res.device, peerName });
    }
    await api.storage.local.set({ sync: { ...sync, ok: true, at: Date.now(), error: '', fails: 0 } });
    return true;
  } catch (e) {
    const wrongKey = e.message === 'key';
    const next = { ...sync, ok: false, error: wrongKey ? ERRORS.key : ERRORS.offline, fails: (sync.fails || 0) + 1 };
    // o roteador pode ter dado outro IP ao celular: procura na mesma rede, sem insistir demais
    if (!wrongKey && (scan || (next.fails >= FAILS_BEFORE_RESCAN && Date.now() - (sync.scanAt || 0) > RESCAN_EVERY))) {
      next.scanAt = Date.now();
      const addr = await rediscover(cfg);
      if (addr) {
        try {
          await attempt(addr);
          await saveCfg({ addr });
          await api.storage.local.set({ sync: { ...next, ok: true, at: Date.now(), error: '', fails: 0 } });
          return true;
        } catch { /* cai no erro abaixo */ }
      }
    }
    await api.storage.local.set({ sync: next });
    return false;
  }
}

/** Grava registros locais (com a hora de agora) e avisa o celular em seguida. */
function write(pairs) {
  return exclusive(async () => {
    const { cfg, entries } = await load();
    const t = Date.now();
    for (const [key, value] of pairs) S.set(entries, key, value, t, cfg.device);
    await api.storage.local.set({ entries });
  }).then(() => { syncNow(); return refresh(); });
}

// ── avisos ──────────────────────────────────────────────────────────────────

async function notify(day, task) {
  const id = `task|${day}|${task.id}`;
  const base = {
    type: 'basic',
    iconUrl: api.runtime.getURL('icons/128.png'),
    title: `${task.time} · ${task.label}`,
    message: task.step ? `Comece só por: ${task.step}` : task.med ? 'Hora do remédio.' : 'Está na hora.',
  };
  try {
    // o Chrome mostra o botão e segura o aviso na tela; o Firefox não aceita essas opções
    if (FIREFOX) throw new Error('simples');
    await api.notifications.create(id, { ...base, buttons: [{ title: 'Concluído' }], requireInteraction: true, priority: 2 });
  } catch {
    await api.notifications.create(id, { ...base, message: `${base.message} Clique para marcar como feito.` });
  }
}

/** Avisa das tarefas que acabaram de chegar na hora e atualiza o selo do ícone. */
async function refresh() {
  const { cfg, entries, fired } = await load();
  const now = new Date();
  const day = S.dayOf(now), nowMin = now.getHours() * 60 + now.getMinutes();
  const paired = Boolean(cfg.addr && cfg.key) && S.hasRoutine(entries);
  const view = S.dayView(entries, day);

  if (paired && cfg.notify && S.ringsHere(entries, cfg.device)) {
    // dentro da fila: o relógio e um clique podem chegar juntos, e cada tarefa avisa uma vez só
    await exclusive(async () => {
      const today = ((await api.storage.local.get('fired')).fired ?? {})[day] ?? [];
      const due = S.due(view, nowMin, today);
      if (!due.length) return;
      await api.storage.local.set({ fired: { [day]: [...today, ...due.map(t => t.id)] } }); // só hoje: os dias antigos saem
      for (const t of due) await notify(day, t).catch(() => {});
    });
  }
  // tarefa feita (aqui ou no celular): o aviso dela some
  const open = await api.notifications.getAll().catch(() => ({}));
  for (const id of Object.keys(open || {})) {
    const [kind, d, taskId] = id.split('|');
    if (kind === 'task' && d !== 'teste' && (d !== day || view.done[taskId])) api.notifications.clear(id);
  }

  const cur = paired ? S.nowOrNext(view, nowMin) : null;
  const left = view.tasks.length - view.doneCount;
  await api.action.setBadgeBackgroundColor({ color: cur?.late ? '#C26A00' : '#7A5900' }).catch(() => {});
  await api.action.setBadgeText({ text: !paired ? '' : cur?.late ? '!' : left > 0 ? String(left) : '' }).catch(() => {});
  await api.action.setTitle({
    title: !paired ? 'SunBi: conecte ao celular' : cur ? `SunBi · ${cur.task.time} ${cur.task.label}` : 'SunBi · rotina de hoje completa',
  }).catch(() => {});
}

async function markDone(day, taskId, done) {
  await write([[`${day}/done/${taskId}`, done ? S.hm(new Date()) : false]]);
}

function onNotification(id, viaButton) {
  const [kind, day, taskId] = id.split('|');
  if (kind !== 'task') return;
  api.notifications.clear(id);
  if (day === 'teste') return;
  // Chrome: o corpo do aviso abre a janela, o botão conclui. Firefox (sem botão): clicar conclui.
  if (viaButton || FIREFOX) markDone(day, taskId, true);
  else api.action.openPopup?.().catch(() => {});
}
api.notifications.onClicked.addListener(id => onNotification(id, false));
api.notifications.onButtonClicked?.addListener(id => onNotification(id, true));

// ── relógio ─────────────────────────────────────────────────────────────────

/** Um alarme por minuto, alinhado ao começo do minuto, para o aviso sair na hora certa. */
async function ensureTick() {
  const cur = await api.alarms.get(TICK);
  if (!cur || cur.periodInMinutes !== 1) {
    await api.alarms.create(TICK, { when: Math.ceil((Date.now() + 1000) / 60_000) * 60_000 + 500, periodInMinutes: 1 });
  }
}

api.alarms.onAlarm.addListener(async alarm => {
  if (alarm.name !== TICK) return;
  await refresh(); // primeiro o aviso, que não depende da rede
  await syncNow();
  await refresh();
});

api.runtime.onInstalled.addListener(() => { ensureTick(); syncNow().then(refresh); });
api.runtime.onStartup.addListener(() => { ensureTick(); syncNow().then(refresh); });
ensureTick();

// ── pedidos da janela ───────────────────────────────────────────────────────

async function snapshot() {
  const { cfg, entries, sync } = await load();
  const now = new Date();
  const view = S.dayView(entries, S.dayOf(now));
  return {
    cfg: { addr: cfg.addr, key: cfg.key, notify: cfg.notify, name: cfg.name, peerName: cfg.peerName },
    paired: Boolean(cfg.addr && cfg.key), hasRoutine: S.hasRoutine(entries), ringsHere: S.ringsHere(entries, cfg.device),
    view, current: S.nowOrNext(view, now.getHours() * 60 + now.getMinutes()), sync, firefox: FIREFOX,
  };
}

const handlers = {
  state: () => snapshot(),
  async sync() { await syncNow({ scan: true }); await refresh(); return snapshot(); },
  async done({ day, id, done }) { await markDone(day, id, done); return snapshot(); },
  async humor({ day, value }) { await write([[`${day}/humor`, value]]); return snapshot(); },
  async pair({ addr, key }) {
    const a = S.normalizeAddr(addr), k = String(key || '').replace(/\s/g, '').toUpperCase();
    if (!a) return { error: 'Endereço inválido. É o que aparece no celular, como 192.168.0.20:47321.' };
    if (k.length < 4) return { error: 'Digite o código de pareamento que aparece no celular.' };
    try {
      const hello = await call(a, '/v1/hello', k, null, 6000);
      await exclusive(async () => {
        const { cfg } = await load();
        await api.storage.local.set({ cfg: { ...cfg, addr: a, key: k, peerDevice: hello.device || '', peerName: hello.name || '' } });
      });
    } catch (e) {
      return { error: e.message === 'key' ? ERRORS.key : ERRORS.offline };
    }
    await syncNow();
    await refresh();
    return snapshot();
  },
  async unpair() {
    // esquece o celular e os dados guardados neste navegador (no celular nada muda)
    await exclusive(async () => {
      const { cfg } = await load();
      await api.storage.local.set({ cfg: { ...cfg, addr: '', key: '', peerDevice: '', peerName: '' }, entries: {}, sync: {}, fired: {} });
    });
    await refresh();
    return snapshot();
  },
  async notify({ on }) {
    await exclusive(async () => { const { cfg } = await load(); await api.storage.local.set({ cfg: { ...cfg, notify: Boolean(on) } }); });
    return snapshot();
  },
  async test() {
    await notify('teste', { id: 'teste', time: S.hm(new Date()), label: 'Aviso de teste do SunBi', step: 'Ver se o aviso apareceu' });
    return { ok: true };
  },
};

api.runtime.onMessage.addListener((msg, _sender, reply) => {
  const h = handlers[msg?.type];
  if (!h) return false;
  Promise.resolve(h(msg)).then(reply, e => reply({ error: String(e?.message || e) }));
  return true; // resposta assíncrona
});
