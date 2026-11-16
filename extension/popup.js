// Janela da extensão: mostra o dia e manda os pedidos para o background.js, que é quem guarda,
// sincroniza e avisa. Aqui não se grava nada direto.
import { HUMOR } from './lib/store.js';

const api = globalThis.browser ?? globalThis.chrome;
const $ = id => document.getElementById(id);
const send = msg => api.runtime.sendMessage(msg);
const el = (tag, cls, text) => { const e = document.createElement(tag); if (cls) e.className = cls; if (text != null) e.textContent = text; return e; };

let state = null;
let screen = null; // 'settings' enquanto os ajustes estão abertos

const DATE = new Intl.DateTimeFormat('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' });
const OFF = { vacation: 'Férias', manual: 'Folga', holiday: 'Feriado', weekend: 'Fim de semana' };

function ago(ms) {
  const m = Math.floor((Date.now() - ms) / 60_000);
  return m < 1 ? 'agora' : m < 60 ? `há ${m} min` : `há ${Math.floor(m / 60)} h`;
}

function show(name) {
  for (const id of ['setup', 'today', 'settings']) $(id).hidden = id !== name;
}

function render() {
  if (!state) return;
  if (!state.paired) return show('setup');
  if (screen === 'settings') { renderSettings(); return show('settings'); }
  show('today');
  const v = state.view, cur = state.current;

  $('date').textContent = DATE.format(new Date());
  const chips = $('chips');
  chips.replaceChildren();
  if (v.off) chips.append(el('span', 'chip', v.off === 'holiday' && v.holiday ? v.holiday : OFF[v.off]));
  if (v.hard) chips.append(el('span', 'chip hard', 'Dia difícil: só o essencial'));
  if (!state.hasRoutine) chips.append(el('span', 'chip', 'Esperando a rotina chegar do celular'));
  $('bar').style.width = v.tasks.length ? `${Math.round((v.doneCount / v.tasks.length) * 100)}%` : '0';

  const allDone = v.tasks.length > 0 && v.doneCount === v.tasks.length;
  $('now').hidden = !cur;
  $('allDone').hidden = !allDone;
  if (cur) {
    $('now').classList.toggle('late', cur.late);
    $('nowKicker').textContent = cur.late
      ? (cur.minutes < 2 ? 'Agora' : `Agora · há ${cur.minutes} min`)
      : `Em seguida · em ${cur.minutes >= 60 ? `${Math.floor(cur.minutes / 60)} h ${cur.minutes % 60} min` : `${cur.minutes} min`}`;
    $('nowTitle').textContent = `${cur.task.time} · ${cur.task.label}`;
    $('nowStep').textContent = cur.task.step ? `Comece só por: ${cur.task.step}` : '';
    $('nowDone').onclick = () => act({ type: 'done', day: v.date, id: cur.task.id, done: true });
  }

  const list = $('tasks');
  const top = list.scrollTop;
  list.replaceChildren(...v.tasks.map(t => {
    const li = el('li', v.done[t.id] ? 'done' : '');
    li.setAttribute('role', 'checkbox');
    li.setAttribute('aria-checked', String(Boolean(v.done[t.id])));
    li.tabIndex = 0;
    const check = el('span', 'check');
    check.innerHTML = '<svg viewBox="0 0 16 16"><path d="M3 8.5l3.2 3.2L13 5"/></svg>';
    li.append(check, el('span', 'time', v.doneAt[t.id] || t.time), el('span', 'label', t.label));
    li.title = t.label + (t.step ? `\nPrimeiro passo: ${t.step}` : '');
    if (t.med) li.append(el('span', 'tag med', 'remédio'));
    else if (t.anchor) li.append(el('span', 'tag', 'âncora'));
    const toggle = () => act({ type: 'done', day: v.date, id: t.id, done: !v.done[t.id] });
    li.onclick = toggle;
    li.onkeydown = e => { if (e.key === ' ' || e.key === 'Enter') { e.preventDefault(); toggle(); } };
    return li;
  }));
  list.scrollTop = top;

  $('humorLabel').textContent = v.humorLabel ? `· ${v.humorLabel}` : '';
  $('scale').replaceChildren(...[-3, -2, -1, 0, 1, 2, 3].map(n => {
    const b = el('button', `${n < 0 ? 'down' : n > 0 ? 'up' : 'zero'}${v.humor === n ? ' on' : ''}`, n > 0 ? `+${n}` : String(n));
    b.title = HUMOR[String(n)];
    b.setAttribute('role', 'radio');
    b.setAttribute('aria-checked', String(v.humor === n));
    b.setAttribute('aria-label', HUMOR[String(n)]);
    b.onclick = () => act({ type: 'humor', day: v.date, value: v.humor === n ? null : n });
    return b;
  }));

  const s = state.sync;
  $('dot').className = `dot ${s.ok === true ? 'ok' : s.ok === false ? 'bad' : ''}`;
  $('syncText').textContent = s.ok === true ? `Em dia com ${state.cfg.peerName || 'o celular'} · ${ago(s.at)}`
    : s.ok === false ? (s.at ? `Sem o celular por perto (última vez ${ago(s.at)}). O que você marcar aqui vai quando ele voltar.` : s.error)
    : 'Conectando…';
}

function renderSettings() {
  $('notify').checked = state.cfg.notify;
  $('notifyHint').textContent = !state.ringsHere
    ? 'No app, os alarmes estão em "só neste aparelho" (o celular), então o computador fica quieto.'
    : state.firefox ? 'Aviso do sistema, com o Firefox aberto. Clicar no aviso marca como feito.'
    : 'Aviso do sistema com o botão Concluído, mesmo com esta janela fechada.';
  $('peer').textContent = state.cfg.peerName || 'Celular';
  $('peerAddr').textContent = `${state.cfg.addr} · este navegador aparece no app como "${state.cfg.name}"`;
  $('about').textContent = `SunBi para navegador ${api.runtime.getManifest().version} · organiza e observa; não diagnostica nem substitui seu tratamento.`;
}

async function act(msg) {
  const res = await send(msg);
  if (res && !res.error && res.view) { state = res; render(); }
  return res;
}

$('pairForm').onsubmit = async e => {
  e.preventDefault();
  $('pairBtn').disabled = true;
  $('pairBtn').textContent = 'Procurando o celular…';
  $('pairError').hidden = true;
  const res = await act({ type: 'pair', addr: $('addr').value, key: $('key').value });
  $('pairBtn').disabled = false;
  $('pairBtn').textContent = 'Conectar';
  if (res?.error) { $('pairError').textContent = res.error; $('pairError').hidden = false; }
};
// colar "192.168.0.20:47321 AB12CD34" de uma vez preenche os dois campos
$('addr').addEventListener('paste', e => {
  const m = /^\s*(\S+)[\s#,;]+([0-9a-f]{4,})\s*$/i.exec(e.clipboardData.getData('text'));
  if (m) { e.preventDefault(); $('addr').value = m[1]; $('key').value = m[2].toUpperCase(); }
});
$('key').addEventListener('input', () => { $('key').value = $('key').value.toUpperCase(); });

$('syncBtn').onclick = async () => {
  $('syncBtn').disabled = true;
  $('syncText').textContent = 'Sincronizando…';
  await act({ type: 'sync' });
  $('syncBtn').disabled = false;
};
$('openSettings').onclick = () => { screen = 'settings'; render(); };
$('closeSettings').onclick = () => { screen = null; render(); };
$('notify').onchange = () => act({ type: 'notify', on: $('notify').checked });
$('testBtn').onclick = () => send({ type: 'test' });
$('advanced').onclick = () => api.tabs.create({ url: `http://${state.cfg.addr}/` });
$('forget').onclick = async () => {
  if (!confirm('Esquecer o celular e apagar os dados guardados neste navegador? No celular nada muda.')) return;
  screen = null;
  await act({ type: 'unpair' });
};

// o background sincroniza sozinho: a janela aberta acompanha
api.storage.onChanged.addListener(async () => { state = await send({ type: 'state' }); render(); });

state = await send({ type: 'state' });
render();
if (state?.paired) act({ type: 'sync' });
