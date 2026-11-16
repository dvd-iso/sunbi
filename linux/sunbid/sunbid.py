#!/usr/bin/env python3
"""sunbid: daemon de sincronização P2P do SunBi para Linux.

Guarda o estado em ~/.local/share/sunbi/state.json, anuncia-se via mDNS
(_sunbi._tcp) e troca registros LWW com os outros aparelhos. O plugin do
Noctalia conversa com ele em 127.0.0.1. Veja docs/PROTOCOL.md.

Na primeira execução, copia os dados do antigo Sunflower Clocks
(~/.local/share/sunflower-clocks e ~/.config/sunflower-clocks), se existirem.
"""
from __future__ import annotations

import argparse
import json
import os
import secrets
import socket
import sys
import threading
import time
import urllib.error
import urllib.request
from datetime import date
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

PROTOCOL_VERSION = 1
DEFAULT_PORT = 47321
SERVICE_TYPE = "_sunbi._tcp.local."
SYNC_INTERVAL = 60

# Mesma rotina inicial do app Android (Repo.DEFAULT_TASKS): as cinco âncoras do ritmo
# social (IPSRT), remédios e pausas. "step" é o primeiro passo (TDAH), "min" a duração.
DEFAULT_TASKS = [
    {"id": "wake", "time": "07:00", "label": "Levantar da cama", "anchor": True, "step": "Sentar na cama e abrir a cortina", "min": 5},
    {"id": "medam", "time": "07:15", "label": "Remédio da manhã", "med": True, "step": "Pegar o copo d'água", "min": 2},
    {"id": "breakfast", "time": "07:30", "label": "Café da manhã", "step": "Ir até a cozinha", "min": 20},
    {"id": "contact", "time": "08:30", "label": "Falar com alguém (mensagem, ligação ou pessoalmente)", "anchor": True, "step": "Mandar um \"bom dia\"", "min": 5},
    {"id": "start", "time": "09:00", "label": "Começar o trabalho, estudo ou tarefa do dia", "anchor": True, "step": "Abrir só a primeira coisa da lista", "min": 25},
    {"id": "water1", "time": "10:30", "label": "Beber água", "min": 1},
    {"id": "lunch", "time": "12:30", "label": "Almoço: pare e coma", "step": "Levantar da cadeira", "min": 30},
    {"id": "water2", "time": "15:00", "label": "Lanche e água", "min": 10},
    {"id": "move", "time": "17:30", "label": "Movimento leve ou luz do dia", "step": "Calçar o tênis", "min": 20},
    {"id": "dinner", "time": "19:30", "label": "Jantar", "anchor": True, "step": "Pôr a mesa ou esquentar a comida", "min": 30},
    {"id": "dim", "time": "21:30", "label": "Luz baixa e telas no modo noturno", "step": "Apagar a luz principal", "min": 5},
    {"id": "medpm", "time": "22:00", "label": "Remédio da noite", "med": True, "step": "Pegar o copo d'água", "min": 2},
    {"id": "bed", "time": "22:30", "label": "Deitar para dormir", "anchor": True, "step": "Deixar o celular fora da cama", "min": 10},
]

HUMOR = {-3: "Muito para baixo", -2: "Para baixo", -1: "Um pouco para baixo", 0: "Estável",
         1: "Um pouco acelerado", 2: "Acelerado", 3: "Muito acelerado"}


def now_ms() -> int:
    return int(time.time() * 1000)


# ── modelo LWW ──────────────────────────────────────────────────────────────

def newer(a: dict | None, b: dict | None) -> bool:
    """True se o registro b deve substituir a."""
    if b is None:
        return False
    if a is None:
        return True
    return (b.get("t", 0), str(b.get("d", ""))) > (a.get("t", 0), str(a.get("d", "")))


def merge_entries(local: dict, remote: dict) -> list[str]:
    """Mescla remote em local (in place). Devolve as chaves que mudaram."""
    changed = []
    for key, rec in remote.items():
        if not isinstance(rec, dict) or "t" not in rec:
            continue
        if newer(local.get(key), rec):
            local[key] = {"v": rec.get("v"), "t": int(rec["t"]), "d": str(rec.get("d", ""))}
            changed.append(key)
    return changed


def is_done(v) -> bool:
    """Feito é `true` ou "HH:MM" (horário real); `false`/ausente é não feito."""
    return v is True or isinstance(v, str)


def hm_minutes(hm) -> int | None:
    try:
        h, m = str(hm).split(":")
        return int(h) * 60 + int(m)
    except (ValueError, AttributeError):
        return None


def sleep_hours(bed, wake) -> float | None:
    b, w = hm_minutes(bed), hm_minutes(wake)
    if b is None or w is None:
        return None
    mins = w - b
    if mins <= 0:
        mins += 24 * 60
    return round(mins / 60, 1)


class Store:
    def __init__(self, path: Path, device: str):
        self.path = path
        self.device = device
        self.lock = threading.RLock()
        self.entries: dict = {}
        self.listeners: list = []
        if path.exists():
            try:
                self.entries = json.loads(path.read_text("utf-8")).get("entries", {})
            except (OSError, ValueError):
                self.entries = {}
        if "tasks" not in self.entries:
            # t=1: qualquer lista editada em outro aparelho vence a padrão
            self.entries["tasks"] = {"v": DEFAULT_TASKS, "t": 1, "d": ""}

    def save(self) -> None:
        with self.lock:
            self.path.parent.mkdir(parents=True, exist_ok=True)
            tmp = self.path.with_suffix(".tmp")
            tmp.write_text(json.dumps({"device": self.device, "entries": self.entries},
                                      ensure_ascii=False), "utf-8")
            os.replace(tmp, self.path)

    def snapshot(self) -> dict:
        with self.lock:
            return {"device": self.device, "entries": json.loads(json.dumps(self.entries))}

    def set(self, key: str, value) -> None:
        with self.lock:
            self.entries[key] = {"v": value, "t": now_ms(), "d": self.device}
            self.save()
        for fn in self.listeners:
            fn([key])

    def merge(self, remote_entries: dict) -> list[str]:
        with self.lock:
            changed = merge_entries(self.entries, remote_entries)
            if changed:
                self.save()
        if changed:
            for fn in self.listeners:
                fn(changed)
        return changed

    def get(self, key: str, default=None):
        with self.lock:
            rec = self.entries.get(key)
            return default if rec is None else rec.get("v", default)

    def humor(self, day: str):
        v = self.get(f"{day}/humor")
        if isinstance(v, (int, float)):
            return max(-3, min(3, int(v)))
        old = self.get(f"{day}/mood")  # escala antiga 1..5
        if isinstance(old, (int, float)):
            return max(-2, min(2, int(old) - 3))
        return None

    def day(self, day: str) -> dict:
        tasks = self.get("tasks", DEFAULT_TASKS) or []
        raw = {t["id"]: self.get(f"{day}/done/{t['id']}") for t in tasks}
        bed, wake = self.get(f"{day}/bed"), self.get(f"{day}/wake")
        sleep = sleep_hours(bed, wake)
        if sleep is None:
            sleep = self.get(f"{day}/sleep")
        humor = self.humor(day)
        return {
            "date": day,
            "tasks": tasks,
            "done": {k: is_done(v) for k, v in raw.items()},
            "doneAt": {k: v for k, v in raw.items() if isinstance(v, str)},
            "humor": humor,
            "humorLabel": HUMOR.get(humor) if humor is not None else None,
            "energy": self.get(f"{day}/energy"),
            "focus": self.get(f"{day}/focus"),
            "sleep": sleep,
            "bed": bed,
            "wake": wake,
            "signs": self.get(f"{day}/signs") or [],
            "events": self.get(f"{day}/events") or [],
            "note": self.get(f"{day}/note", ""),
        }


# ── configuração ────────────────────────────────────────────────────────────

def _config_base() -> Path:
    return Path(os.environ.get("XDG_CONFIG_HOME") or str(Path.home() / ".config"))


def _data_base() -> Path:
    return Path(os.environ.get("XDG_DATA_HOME") or str(Path.home() / ".local" / "share"))


def config_dir() -> Path:
    return _config_base() / "sunbi"


def data_dir() -> Path:
    return _data_base() / "sunbi"


def migrate_from_sunflower() -> None:
    """Copia config e dados do Sunflower Clocks na primeira execução do SunBi."""
    import shutil
    for old, new in ((_config_base() / "sunflower-clocks", config_dir()), (_data_base() / "sunflower-clocks", data_dir())):
        if old.is_dir() and not new.exists():
            shutil.copytree(old, new)
            log(f"dados do Sunflower Clocks copiados de {old} para {new}")


def load_config() -> dict:
    migrate_from_sunflower()
    path = config_dir() / "config.json"
    cfg = {}
    if path.exists():
        try:
            cfg = json.loads(path.read_text("utf-8"))
        except ValueError:
            cfg = {}
    dirty = False
    if not cfg.get("device"):
        cfg["device"] = "linux-" + secrets.token_hex(3)
        dirty = True
    if not cfg.get("key"):
        cfg["key"] = secrets.token_hex(4).upper()
        dirty = True
    cfg.setdefault("name", socket.gethostname())
    cfg.setdefault("port", DEFAULT_PORT)
    cfg.setdefault("peers", [])
    if dirty:
        save_config(cfg)
    return cfg


def save_config(cfg: dict) -> None:
    path = config_dir() / "config.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(cfg, indent=2, ensure_ascii=False), "utf-8")
    try:
        os.chmod(path, 0o600)
    except OSError:
        pass


# ── pares ───────────────────────────────────────────────────────────────────

class Peers:
    def __init__(self, store: Store, cfg: dict):
        self.store = store
        self.cfg = cfg
        self.lock = threading.Lock()
        self.found: dict[str, dict] = {}  # device -> {addr, name, seen, ok}
        for manual in cfg.get("peers", []):
            self.found[f"manual:{manual}"] = {"addr": manual, "name": manual, "seen": 0, "ok": None}

    def add(self, device: str, addr: str, name: str) -> None:
        if device == self.cfg["device"]:
            return
        with self.lock:
            is_new = device not in self.found or self.found[device]["addr"] != addr
            self.found[device] = {"addr": addr, "name": name, "seen": time.time(), "ok": None}
        if is_new:
            threading.Thread(target=self.sync_one, args=(device,), daemon=True).start()

    def remove(self, device: str) -> None:
        with self.lock:
            self.found.pop(device, None)

    def list(self) -> list[dict]:
        with self.lock:
            return [{"device": d, **info} for d, info in self.found.items()]

    def sync_one(self, device: str) -> bool:
        with self.lock:
            info = self.found.get(device)
        if not info:
            return False
        body = json.dumps(self.store.snapshot(), ensure_ascii=False).encode("utf-8")
        req = urllib.request.Request(
            f"http://{info['addr']}/v1/merge", data=body, method="POST",
            headers={"Content-Type": "application/json", "X-SunBi-Key": self.cfg["key"]},
        )
        ok = False
        try:
            with urllib.request.urlopen(req, timeout=5) as resp:
                remote = json.loads(resp.read().decode("utf-8"))
            self.store.merge(remote.get("entries", {}))
            ok = True
        except (urllib.error.URLError, OSError, ValueError) as exc:
            log(f"sync com {info['name']} falhou: {exc}")
        with self.lock:
            if device in self.found:
                self.found[device]["ok"] = ok
                if ok:
                    self.found[device]["seen"] = time.time()
        return ok

    def sync_all(self) -> None:
        for p in self.list():
            self.sync_one(p["device"])

    def push_soon(self, _changed=None) -> None:
        threading.Thread(target=self.sync_all, daemon=True).start()

    def loop(self) -> None:
        while True:
            time.sleep(SYNC_INTERVAL)
            self.sync_all()


def start_mdns(cfg: dict, peers: Peers):
    try:
        from zeroconf import ServiceBrowser, ServiceInfo, Zeroconf
    except ImportError:
        log("python-zeroconf não instalado: só pares manuais funcionam")
        return None

    zc = Zeroconf()
    ip = local_ip()
    info = ServiceInfo(
        SERVICE_TYPE,
        f"{cfg['device']}.{SERVICE_TYPE}",
        addresses=[socket.inet_aton(ip)],
        port=int(cfg["port"]),
        properties={"id": cfg["device"], "v": str(PROTOCOL_VERSION), "name": cfg["name"]},
    )
    zc.register_service(info)

    class Listener:
        def add_service(self, zc_, type_, name):
            self.update_service(zc_, type_, name)

        def update_service(self, zc_, type_, name):
            si = zc_.get_service_info(type_, name, timeout=3000)
            if not si or not si.addresses:
                return
            props = {k.decode(): (v.decode() if v else "") for k, v in si.properties.items()}
            dev = props.get("id") or name
            addr = f"{socket.inet_ntoa(si.addresses[0])}:{si.port}"
            peers.add(dev, addr, props.get("name", dev))

        def remove_service(self, zc_, type_, name):
            pass  # mantém o par; se sumir de vez, a sincronização só falha

    ServiceBrowser(zc, SERVICE_TYPE, Listener())
    log(f"anunciando {cfg['device']} em {ip}:{cfg['port']} via mDNS")
    return zc


def local_ip() -> str:
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("10.255.255.255", 1))
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


# ── HTTP ────────────────────────────────────────────────────────────────────

def make_handler(store: Store, peers: Peers, cfg: dict):
    class Handler(BaseHTTPRequestHandler):
        server_version = "sunbid/1"

        def log_message(self, fmt, *args):
            if os.environ.get("SUNBI_DEBUG"):
                log(fmt % args)

        def _local(self) -> bool:
            return self.client_address[0] in ("127.0.0.1", "::1")

        def _authorized(self) -> bool:
            if self._local():
                return True
            given = self.headers.get("X-SunBi-Key", "")
            return secrets.compare_digest(given.upper(), str(cfg["key"]).upper())

        def _send(self, code: int, obj) -> None:
            data = json.dumps(obj, ensure_ascii=False).encode("utf-8")
            self.send_response(code)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def _body(self) -> dict:
            n = int(self.headers.get("Content-Length") or 0)
            if n > 5_000_000:
                raise ValueError("corpo grande demais")
            return json.loads(self.rfile.read(n).decode("utf-8") or "{}")

        def do_GET(self):
            if not self._authorized():
                return self._send(401, {"error": "código de pareamento inválido"})
            url = urlparse(self.path)
            if url.path == "/v1/hello":
                return self._send(200, {"device": cfg["device"], "name": cfg["name"], "v": PROTOCOL_VERSION})
            if url.path == "/v1/state":
                return self._send(200, store.snapshot())
            if url.path == "/v1/day" and self._local():
                d = parse_qs(url.query).get("date", [date.today().isoformat()])[0]
                out = store.day(d)
                out["peers"] = [{"name": p["name"], "ok": p["ok"]} for p in peers.list()]
                out["device"] = cfg["device"]
                return self._send(200, out)
            return self._send(404, {"error": "rota desconhecida"})

        def do_POST(self):
            if not self._authorized():
                return self._send(401, {"error": "código de pareamento inválido"})
            url = urlparse(self.path)
            try:
                body = self._body()
            except ValueError as exc:
                return self._send(400, {"error": str(exc)})
            if url.path == "/v1/merge":
                store.merge(body.get("entries", {}))
                return self._send(200, store.snapshot())
            if url.path == "/v1/set" and self._local():
                key = body.get("key")
                if not isinstance(key, str) or not key:
                    return self._send(400, {"error": "key obrigatória"})
                store.set(key, body.get("value"))
                return self._send(200, {"ok": True})
            return self._send(404, {"error": "rota desconhecida"})

    return Handler


def log(msg: str) -> None:
    print(f"[sunbid] {msg}", file=sys.stderr, flush=True)


def serve(cfg: dict) -> None:
    store = Store(data_dir() / "state.json", cfg["device"])
    peers = Peers(store, cfg)
    store.listeners.append(peers.push_soon)
    httpd = ThreadingHTTPServer(("0.0.0.0", int(cfg["port"])), make_handler(store, peers, cfg))
    zc = start_mdns(cfg, peers)
    threading.Thread(target=peers.loop, daemon=True).start()
    threading.Thread(target=peers.sync_all, daemon=True).start()
    log(f"ouvindo na porta {cfg['port']}; código de pareamento: {cfg['key']}")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        if zc:
            zc.unregister_all_services()
            zc.close()


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(prog="sunbid", description="Sincronização P2P do SunBi")
    sub = ap.add_subparsers(dest="cmd")
    sub.add_parser("run", help="roda o daemon (padrão)")
    sub.add_parser("key", help="mostra o código de pareamento")
    sk = sub.add_parser("set-key", help="define o código de pareamento (igual em todos os aparelhos)")
    sk.add_argument("key")
    ap_ = sub.add_parser("add-peer", help="adiciona um par manual, ex. 100.64.0.5:47321")
    ap_.add_argument("addr")
    sub.add_parser("status", help="mostra o dia de hoje e os pares (daemon precisa estar rodando)")
    args = ap.parse_args(argv)
    cfg = load_config()

    if args.cmd == "key":
        print(cfg["key"])
    elif args.cmd == "set-key":
        cfg["key"] = args.key.strip().upper()
        save_config(cfg)
        print("código salvo; reinicie o daemon")
    elif args.cmd == "add-peer":
        if args.addr not in cfg["peers"]:
            cfg["peers"].append(args.addr)
        save_config(cfg)
        print("par adicionado; reinicie o daemon")
    elif args.cmd == "status":
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{cfg['port']}/v1/day", timeout=3) as r:
                d = json.loads(r.read().decode("utf-8"))
        except OSError as exc:
            print(f"daemon não respondeu: {exc}")
            return 1
        n = sum(1 for v in d["done"].values() if v)
        print(f"{d['date']}: {n}/{len(d['tasks'])} concluídas")
        if d.get("humor") is not None:
            print(f"humor: {d['humor']:+d} ({d['humorLabel']})")
        if d.get("sleep") is not None:
            print(f"sono: {d['sleep']} h")
        for t in d["tasks"]:
            print(f"  [{'x' if d['done'].get(t['id']) else ' '}] {t['time']} {t['label']}")
        print("pares:", ", ".join(f"{p['name']} ({'ok' if p['ok'] else '?'})" for p in d["peers"]) or "nenhum")
    else:
        serve(cfg)
    return 0


if __name__ == "__main__":
    sys.exit(main())
