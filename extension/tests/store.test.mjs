// Testes do módulo de dados: node --test extension/tests/
import test from 'node:test';
import assert from 'node:assert/strict';
import * as S from '../lib/store.js';

test('merge: vence o maior t; empate decide pelo aparelho', () => {
  const e = { a: { v: 1, t: 10, d: 'x' } };
  assert.deepEqual(S.merge(e, { a: { v: 2, t: 9, d: 'z' } }), []);
  assert.deepEqual(S.merge(e, { a: { v: 3, t: 10, d: 'y' } }), ['a']);
  assert.equal(e.a.v, 3);
  assert.deepEqual(S.merge(e, { a: { v: 3, t: 10, d: 'y' } }), []); // idempotente
  assert.deepEqual(S.merge(e, { b: { v: null, t: 5, d: 'y' } }), ['b']);
  assert.equal(S.get(e, 'b', 'padrão'), 'padrão'); // null apaga
  assert.deepEqual(S.merge(e, 'lixo'), []);
});

test('merge é comutativo', () => {
  const r1 = { k: { v: 'a', t: 5, d: 'android-1' } }, r2 = { k: { v: 'b', t: 5, d: 'pc-2' } };
  const x = {}, y = {};
  S.merge(x, r1); S.merge(x, r2);
  S.merge(y, r2); S.merge(y, r1);
  assert.deepEqual(x, y);
  assert.equal(x.k.v, 'b');
});

test('horários e sono', () => {
  assert.equal(S.minutes('07:15'), 435);
  assert.equal(S.minutes('x'), null);
  assert.equal(S.sleepHours('23:30', '07:00'), 7.5);
  assert.equal(S.sleepHours('01:00', '06:12'), 5.2);
  assert.equal(S.sleepHours(null, '06:00'), null);
});

test('feriados móveis e fixos', () => {
  assert.equal(S.easter(2026), '2026-04-05');
  assert.equal(S.holiday('2026-04-03'), 'Paixão de Cristo');
  assert.equal(S.holiday('2026-02-17'), 'Carnaval');
  assert.equal(S.holiday('2026-06-04'), 'Corpus Christi');
  assert.equal(S.holiday('2026-11-15'), 'Proclamação da República');
  assert.equal(S.holiday('2026-11-16'), null);
});

test('qual rotina vale no dia', () => {
  const e = {};
  assert.equal(S.offReason(e, '2026-11-16'), null); // segunda
  assert.equal(S.offReason(e, '2026-11-14'), 'weekend'); // sábado
  assert.equal(S.offReason(e, '2026-11-20'), 'holiday');
  S.set(e, 'off/weekends', false, 1, 'x');
  assert.equal(S.offReason(e, '2026-11-14'), null);
  S.set(e, '2026-11-16/off', true, 1, 'x');
  assert.equal(S.offReason(e, '2026-11-16'), 'manual');
  S.set(e, 'vacation', { since: '2026-11-10' }, 1, 'x');
  assert.equal(S.offReason(e, '2026-11-17'), 'vacation');
  S.set(e, '2026-11-17/off', false, 2, 'x');
  assert.equal(S.offReason(e, '2026-11-17'), null);
  assert.equal(S.tasks(e, '2026-11-18')[0].time, '08:00'); // folga de reserva
});

test('rotina do celular, em ordem; dia difícil só com âncoras e remédios', () => {
  const e = {};
  assert.equal(S.hasRoutine(e), false);
  S.set(e, 'tasks', [
    { id: 'b', time: '12:00', label: 'Almoço' },
    { id: 'a', time: '07:00', label: 'Levantar', anchor: true },
    { id: 'm', time: '08:00', label: 'Remédio', med: true },
  ], 5, 'android-1');
  assert.equal(S.hasRoutine(e), true);
  assert.deepEqual(S.tasks(e, '2026-11-16').map(t => t.id), ['a', 'm', 'b']);
  S.set(e, '2026-11-16/hard', true, 6, 'android-1');
  assert.deepEqual(S.tasks(e, '2026-11-16').map(t => t.id), ['a', 'm']);
});

test('visão do dia, tarefa da vez e avisos', () => {
  const e = {};
  S.set(e, 'tasks', [
    { id: 'a', time: '07:00', label: 'Levantar' },
    { id: 'b', time: '09:00', label: 'Começar' },
    { id: 'c', time: '12:00', label: 'Almoço' },
  ], 5, 'android-1');
  const day = '2026-11-16';
  S.set(e, `${day}/done/a`, '07:10', 6, 'android-1');
  S.set(e, `${day}/humor`, 1, 6, 'android-1');
  let v = S.dayView(e, day);
  assert.equal(v.doneCount, 1);
  assert.equal(v.doneAt.a, '07:10');
  assert.equal(v.humorLabel, 'Um pouco acelerado');
  assert.deepEqual(S.nowOrNext(v, 9 * 60 + 20), { task: v.tasks[1], minutes: 20, late: true });
  assert.equal(S.nowOrNext(v, 8 * 60).task.id, 'b');
  assert.equal(S.nowOrNext(v, 8 * 60).late, false);
  // 09:00 venceu às 09:03: avisa uma vez
  assert.deepEqual(S.due(v, 9 * 60 + 3, []).map(t => t.id), ['b']);
  assert.deepEqual(S.due(v, 9 * 60 + 3, ['b']), []);
  assert.deepEqual(S.due(v, 9 * 60 + 30, []), []); // navegador fechado na hora: não avisa atrasado
  S.set(e, `${day}/done/b`, true, 7, 'pc-1');
  v = S.dayView(e, day);
  assert.deepEqual(S.due(v, 9 * 60 + 3, []), []);
  S.set(e, `${day}/done/b`, false, 8, 'pc-1');
  assert.equal(S.dayView(e, day).done.b, false);
});

test('alarme num aparelho só', () => {
  const e = {};
  assert.equal(S.ringsHere(e, 'pc-1'), true);
  S.set(e, 'alarm_device', 'android-1', 1, 'android-1');
  assert.equal(S.ringsHere(e, 'pc-1'), false);
  assert.equal(S.ringsHere(e, 'android-1'), true);
});

test('endereço do celular', () => {
  assert.equal(S.normalizeAddr(' 192.168.0.20 '), '192.168.0.20:47321');
  assert.equal(S.normalizeAddr('http://192.168.0.20:47321/'), '192.168.0.20:47321');
  assert.equal(S.normalizeAddr('celular.tailnet.ts.net:8080'), 'celular.tailnet.ts.net:8080');
  assert.equal(S.normalizeAddr(''), null);
  assert.equal(S.normalizeAddr('a b'), null);
  assert.equal(S.normalizeAddr('1.2.3.4:99999'), null);
  const n = S.neighbors('192.168.0.20:47321');
  assert.equal(n.length, 253);
  assert.ok(n.includes('192.168.0.1:47321') && !n.includes('192.168.0.20:47321'));
  assert.deepEqual(S.neighbors('celular.local:47321'), []);
});
