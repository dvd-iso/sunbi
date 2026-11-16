// SunBi: modelo de dados (registros last-writer-wins) e visão do dia.
//
// Módulo puro: não usa chrome.*, então roda também nos testes (tests/store.test.mjs).
// O formato e as regras são os mesmos do app Android; veja docs/PROTOCOL.md.

// Rotinas de reserva, iguais às do app (Repo.DEFAULT_TASKS). Só aparecem enquanto a rotina
// de verdade ainda não chegou do celular; nunca são gravadas nem enviadas.
export const DEFAULT_TASKS = [
  { id: 'wake', time: '07:00', label: 'Levantar da cama', anchor: true, step: 'Sentar na cama e abrir a cortina' },
  { id: 'medam', time: '07:15', label: 'Remédio da manhã', med: true, step: "Pegar o copo d'água" },
  { id: 'breakfast', time: '07:30', label: 'Café da manhã', step: 'Ir até a cozinha' },
  { id: 'contact', time: '08:30', label: 'Falar com alguém (mensagem, ligação ou pessoalmente)', anchor: true, step: 'Mandar um "bom dia"' },
  { id: 'start', time: '09:00', label: 'Começar o trabalho, estudo ou tarefa do dia', anchor: true, step: 'Abrir só a primeira coisa da lista' },
  { id: 'water1', time: '10:30', label: 'Beber água' },
  { id: 'lunch', time: '12:30', label: 'Almoço: pare e coma', step: 'Levantar da cadeira' },
  { id: 'water2', time: '15:00', label: 'Lanche e água' },
  { id: 'move', time: '17:30', label: 'Movimento leve ou luz do dia', step: 'Calçar o tênis' },
  { id: 'dinner', time: '19:30', label: 'Jantar', anchor: true, step: 'Pôr a mesa ou esquentar a comida' },
  { id: 'dim', time: '21:30', label: 'Luz baixa e telas no modo noturno', step: 'Apagar a luz principal' },
  { id: 'medpm', time: '22:00', label: 'Remédio da noite', med: true, step: "Pegar o copo d'água" },
  { id: 'bed', time: '22:30', label: 'Deitar para dormir', anchor: true, step: 'Deixar o celular fora da cama' },
];

export const DEFAULT_OFF_TASKS = [
  { id: 'wake', time: '08:00', label: 'Levantar da cama', anchor: true, step: 'Abrir a cortina e beber água' },
  { id: 'medam', time: '08:15', label: 'Remédio da manhã', med: true, step: "Pegar o copo d'água" },
  { id: 'breakfast', time: '08:30', label: 'Café da manhã sem pressa' },
  { id: 'contact', time: '10:00', label: 'Falar com alguém ou sair de casa', anchor: true, step: 'Mandar uma mensagem' },
  { id: 'lunch', time: '13:00', label: 'Almoço' },
  { id: 'fun', time: '15:00', label: 'Algo que você gosta de fazer', step: 'Escolher uma coisa só' },
  { id: 'move', time: '17:00', label: 'Luz do dia ou caminhada', step: 'Calçar o tênis' },
  { id: 'dinner', time: '20:00', label: 'Jantar', anchor: true },
  { id: 'dim', time: '22:00', label: 'Luz baixa e telas no modo noturno', step: 'Apagar a luz principal' },
  { id: 'medpm', time: '22:00', label: 'Remédio da noite', med: true, step: "Pegar o copo d'água" },
  { id: 'bed', time: '23:00', label: 'Deitar para dormir', anchor: true, step: 'Deixar o celular fora da cama' },
];

export const HUMOR = {
  '-3': 'Muito para baixo', '-2': 'Para baixo', '-1': 'Um pouco para baixo', '0': 'Estável',
  '1': 'Um pouco acelerado', '2': 'Acelerado', '3': 'Muito acelerado',
};

const recT = r => Number(r && r.t) || 0;
const isObj = x => x !== null && typeof x === 'object' && !Array.isArray(x);

/** true se o registro b deve substituir a (maior t; empate decidido pelo id do aparelho). */
export function newer(a, b) {
  if (!isObj(b) || b.t == null) return false;
  if (!isObj(a)) return true;
  const ta = recT(a), tb = recT(b);
  if (tb !== ta) return tb > ta;
  return String(b.d ?? '') > String(a.d ?? '');
}

/** Mescla remote em entries (no lugar). Devolve a lista de chaves que mudaram. */
export function merge(entries, remote) {
  const changed = [];
  if (!isObj(remote)) return changed;
  for (const [key, rec] of Object.entries(remote)) {
    if (newer(entries[key], rec)) {
      entries[key] = { v: rec.v ?? null, t: recT(rec), d: String(rec.d ?? '') };
      changed.push(key);
    }
  }
  return changed;
}

export function set(entries, key, value, t, device) {
  entries[key] = { v: value ?? null, t, d: device };
}

export function get(entries, key, fallback) {
  const r = entries[key];
  return r == null || r.v == null ? fallback : r.v;
}

/** Feito é `true` ou "HH:MM" (horário real); `false`/ausente é não feito. */
export const isDone = v => v === true || typeof v === 'string';

export function minutes(hm) {
  const m = typeof hm === 'string' ? /^(\d{1,2}):(\d{2})$/.exec(hm) : null;
  return m ? Number(m[1]) * 60 + Number(m[2]) : null;
}

export const hm = date => `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;

/** "AAAA-MM-DD" no fuso do computador. */
export const dayOf = date =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

/** Horas de sono entre deitar e acordar, atravessando a meia-noite. Uma casa decimal. */
export function sleepHours(bed, wake) {
  const b = minutes(bed), w = minutes(wake);
  if (b == null || w == null) return null;
  let mins = w - b;
  if (mins <= 0) mins += 24 * 60;
  return Math.round(mins / 6) / 10;
}

/** Humor -3..+3; lê a escala antiga (1..5) quando não há a nova. */
export function humor(entries, day) {
  const v = get(entries, `${day}/humor`);
  if (typeof v === 'number') return Math.max(-3, Math.min(3, Math.trunc(v)));
  const old = get(entries, `${day}/mood`);
  if (typeof old === 'number') return Math.max(-2, Math.min(2, Math.trunc(old) - 3));
  return null;
}

// ── folga: fins de semana, feriados e modo férias (mesmas regras do app) ─────

const parse = day => {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(day || '');
  return m ? new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]), 12) : null;
};

export function addDays(day, n) {
  const d = parse(day);
  d.setDate(d.getDate() + n);
  return dayOf(d);
}

/** Domingo de Páscoa (algoritmo anônimo gregoriano). */
export function easter(year) {
  const a = year % 19, b = Math.floor(year / 100), c = year % 100;
  const d = Math.floor(b / 4), e = b % 4, f = Math.floor((b + 8) / 25), g = Math.floor((b - f + 1) / 3);
  const h = (19 * a + b - d - g + 15) % 30, i = Math.floor(c / 4), k = c % 4;
  const l = (32 + 2 * e + 2 * i - h - k) % 7, m = Math.floor((a + 11 * h + 22 * l) / 451);
  const month = Math.floor((h + l - 7 * m + 114) / 31), dd = ((h + l - 7 * m + 114) % 31) + 1;
  return `${year}-${String(month).padStart(2, '0')}-${String(dd).padStart(2, '0')}`;
}

const FIXED = {
  '01-01': 'Confraternização Universal', '04-21': 'Tiradentes', '05-01': 'Dia do Trabalho',
  '09-07': 'Independência', '10-12': 'Nossa Senhora Aparecida', '11-02': 'Finados',
  '11-15': 'Proclamação da República', '11-20': 'Consciência Negra', '12-25': 'Natal',
};

export function holiday(day) {
  if (!parse(day)) return null;
  const fixed = FIXED[day.slice(5)];
  if (fixed) return fixed;
  const e = easter(Number(day.slice(0, 4)));
  if (day === addDays(e, -48) || day === addDays(e, -47)) return 'Carnaval';
  if (day === addDays(e, -2)) return 'Paixão de Cristo';
  if (day === addDays(e, 60)) return 'Corpus Christi';
  return null;
}

export function vacationSince(entries) {
  const v = get(entries, 'vacation');
  return isObj(v) && typeof v.since === 'string' && v.since ? v.since : null;
}

/** "vacation" | "manual" | "holiday" | "weekend" | null (rotina de semana). */
export function offReason(entries, day) {
  const explicit = get(entries, `${day}/off`);
  const since = vacationSince(entries);
  if (explicit === true) return since && day >= since ? 'vacation' : 'manual';
  if (explicit === false) return null;
  if (since && day >= since) return 'vacation';
  if (get(entries, 'off/holidays') !== false && holiday(day)) return 'holiday';
  const d = parse(day);
  if (d && get(entries, 'off/weekends') !== false && (d.getDay() === 0 || d.getDay() === 6)) return 'weekend';
  return null;
}

const stored = (entries, key) => { const v = get(entries, key); return Array.isArray(v) && v.length > 0 ? v : null; };

/** Alguma rotina já chegou de algum aparelho (ou só temos a de reserva)? */
export const hasRoutine = entries => Boolean(stored(entries, 'tasks') || stored(entries, 'tasks_off'));

/** A rotina que vale no dia, em ordem de horário. Dia difícil: só âncoras e remédios. */
export function tasks(entries, day) {
  const off = day && offReason(entries, day);
  // a rotina de reserva só vale enquanto nenhuma chegou do celular; depois, uma rotina vazia
  // (folga sem tarefas, por exemplo) é vazia mesmo, igual ao app
  const base = hasRoutine(entries) ? stored(entries, off ? 'tasks_off' : 'tasks') ?? [] : off ? DEFAULT_OFF_TASKS : DEFAULT_TASKS;
  const list = base
    .filter(t => isObj(t) && t.id)
    .slice()
    .sort((a, b) => String(a.time).localeCompare(String(b.time)));
  return day && get(entries, `${day}/hard`) === true ? list.filter(t => t.anchor || t.med) : list;
}

/** Este aparelho avisa das tarefas? (`alarm_device` vazio = todos avisam.) */
export function ringsHere(entries, device) {
  const d = get(entries, 'alarm_device');
  return typeof d !== 'string' || d === '' || d === device;
}

/** Visão pronta de um dia para a janela da extensão. */
export function dayView(entries, day) {
  const list = tasks(entries, day);
  const done = {}, doneAt = {};
  let count = 0;
  for (const t of list) {
    const v = get(entries, `${day}/done/${t.id}`);
    done[t.id] = isDone(v);
    if (done[t.id]) count++;
    if (typeof v === 'string') doneAt[t.id] = v;
  }
  const bed = get(entries, `${day}/bed`, null), wake = get(entries, `${day}/wake`, null);
  const h = humor(entries, day);
  return {
    date: day, tasks: list, off: offReason(entries, day), holiday: holiday(day), hard: get(entries, `${day}/hard`) === true,
    done, doneAt, doneCount: count, humor: h, humorLabel: h == null ? null : HUMOR[String(h)],
    sleep: sleepHours(bed, wake) ?? get(entries, `${day}/sleep`, null), bed, wake, note: get(entries, `${day}/note`, ''),
  };
}

/** Tarefa atrasada (até 3 h, ainda não feita) ou a próxima: { task, minutes, late } ou null. */
export function nowOrNext(view, nowMin) {
  let late = null, next = null;
  for (const t of view.tasks) {
    const m = minutes(t.time);
    if (m == null || view.done[t.id]) continue;
    if (m <= nowMin && nowMin - m <= 180) late = t;
    else if (m > nowMin && !next) next = t;
  }
  if (late) return { task: late, minutes: nowMin - minutes(late.time), late: true };
  if (next) return { task: next, minutes: minutes(next.time) - nowMin, late: false };
  return null;
}

/**
 * Tarefas que chegaram na hora e ainda não foram avisadas: horário entre [windowMin] minutos
 * atrás e agora, não feitas e fora de [fired] (ids já avisados hoje).
 */
export function due(view, nowMin, fired, windowMin = 10) {
  return view.tasks.filter(t => {
    const m = minutes(t.time);
    return m != null && !view.done[t.id] && !fired.includes(t.id) && m <= nowMin && nowMin - m <= windowMin;
  });
}

// ── endereço do celular ─────────────────────────────────────────────────────

export const DEFAULT_PORT = 47321;

/**
 * Aceita o endereço como a pessoa digitar: "192.168.0.20", "192.168.0.20:47321",
 * "http://192.168.0.20:47321/". Devolve "host:porta" ou null.
 */
export function normalizeAddr(raw) {
  let s = String(raw || '').trim().replace(/^https?:\/\//i, '').replace(/\/.*$/, '');
  if (!s || /\s/.test(s)) return null;
  if (s.startsWith('[')) return /\]:\d+$/.test(s) ? s : /\]$/.test(s) ? `${s}:${DEFAULT_PORT}` : null; // IPv6
  const m = /^([^:]+)(?::(\d{1,5}))?$/.exec(s);
  if (!m) return null;
  const port = m[2] ? Number(m[2]) : DEFAULT_PORT;
  return port > 0 && port < 65536 ? `${m[1]}:${port}` : null;
}

/**
 * Os outros endereços da mesma rede (/24) de um "a.b.c.d:porta", para reencontrar o celular
 * quando o roteador troca o IP dele. Devolve [] se o endereço não for IPv4.
 */
export function neighbors(addr) {
  const m = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3}):(\d+)$/.exec(addr || '');
  if (!m) return [];
  const out = [];
  for (let i = 1; i < 255; i++) if (i !== Number(m[4])) out.push(`${m[1]}.${m[2]}.${m[3]}.${i}:${m[5]}`);
  return out;
}
