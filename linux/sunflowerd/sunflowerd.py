#!/usr/bin/env python3
"""sunflowerd: daemon de sincronização P2P do Sunflower Clocks para Linux.

Guarda o estado em ~/.local/share/sunflower-clocks/state.json, anuncia-se via mDNS
(_sunflower._tcp) e troca registros LWW com os outros aparelhos. O plugin do
Noctalia conversa com ele em 127.0.0.1. Veja docs/PROTOCOL.md.
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
SERVICE_TYPE = "_sunflower._tcp.local."
SYNC_INTERVAL = 60

DEFAULT_TASKS = [
    {"id": "t0700", "time": "07:00", "label": "Comer e tomar remédio", "med": True},
    {"id": "t0715", "time": "07:15", "label": "Escovar os dentes e se arrumar"},
    {"id": "t0930", "time": "09:30", "label": "Beber água"},
    {"id": "t1200", "time": "12:00", "label": "Almoço: pare e coma"},
    {"id": "t1245", "time": "12:45", "label": "Tomar o remédio (depois do almoço)", "med": True},
    {"id": "t1400", "time": "14:00", "label": "Beber água"},
    {"id": "t1530", "time": "15:30", "label": "Lanche e água"},
    {"id": "t1700", "time": "17:00", "label": "Beber água"},
    {"id": "t1900", "time": "19:00", "label": "Jantar"},
    {"id": "t1945", "time": "19:45", "label": "Exercício leve"},
    {"id": "t2030", "time": "20:30", "label": "Tomar banho"},
    {"id": "t2100", "time": "21:00", "label": "Hora do Astral (até 22h)"},
    {"id": "t2230", "time": "22:30", "label": "Desligar telas e tomar remédio", "med": True},
    {"id": "t2240", "time": "22:40", "label": "Escovar os dentes e ir para a cama"},
]


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

    def day(self, day: str) -> dict:
        tasks = self.get("tasks", DEFAULT_TASKS) or []
        done = {t["id"]: bool(self.get(f"{day}/done/{t['id']}", False)) for t in tasks}
        return {
            "date": day,
            "tasks": tasks,
            "done": done,
            "mood": self.get(f"{day}/mood"),
            "sleep": self.get(f"{day}/sleep"),
            "note": self.get(f"{day}/note", ""),
        }


# ── configuração ────────────────────────────────────────────────────────────

def config_dir() -> Path:
    base = os.environ.get("XDG_CONFIG_HOME") or str(Path.home() / ".config")
    return Path(base) / "sunflower-clocks"


def data_dir() -> Path:
    base = os.environ.get("XDG_DATA_HOME") or str(Path.home() / ".local" / "share")
    return Path(base) / "sunflower-clocks"


def load_config() -> dict:
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
            headers={"Content-Type": "application/json", "X-Sunflower-Key": self.cfg["key"]},
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
        server_version = "sunflowerd/1"

        def log_message(self, fmt, *args):
            if os.environ.get("SUNFLOWER_DEBUG"):
                log(fmt % args)

        def _local(self) -> bool:
            return self.client_address[0] in ("127.0.0.1", "::1")

        def _authorized(self) -> bool:
            if self._local():
                return True
            given = self.headers.get("X-Sunflower-Key", "")
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
    print(f"[sunflowerd] {msg}", file=sys.stderr, flush=True)


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
    ap = argparse.ArgumentParser(prog="sunflowerd", description="Sincronização P2P do Sunflower Clocks")
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
        for t in d["tasks"]:
            print(f"  [{'x' if d['done'].get(t['id']) else ' '}] {t['time']} {t['label']}")
        print("pares:", ", ".join(f"{p['name']} ({'ok' if p['ok'] else '?'})" for p in d["peers"]) or "nenhum")
    else:
        serve(cfg)
    return 0


if __name__ == "__main__":
    sys.exit(main())
