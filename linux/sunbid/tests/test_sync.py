import json
import os
import random
import sys
import tempfile
import threading
import time
import unittest
import urllib.request
from http.server import ThreadingHTTPServer
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import sunbid as sf  # noqa: E402


def rec(v, t, d):
    return {"v": v, "t": t, "d": d}


class MergeTests(unittest.TestCase):
    def test_newer_wins(self):
        a = {"k": rec(1, 10, "a")}
        sf.merge_entries(a, {"k": rec(2, 20, "b")})
        self.assertEqual(a["k"]["v"], 2)

    def test_older_loses(self):
        a = {"k": rec(1, 30, "a")}
        self.assertEqual(sf.merge_entries(a, {"k": rec(2, 20, "b")}), [])
        self.assertEqual(a["k"]["v"], 1)

    def test_tie_broken_by_device(self):
        a = {"k": rec(1, 10, "a")}
        sf.merge_entries(a, {"k": rec(2, 10, "b")})
        self.assertEqual(a["k"]["v"], 2)

    def test_convergence_any_order(self):
        rnd = random.Random(7)
        updates = [{f"k{rnd.randint(0, 5)}": rec(rnd.randint(0, 99), rnd.randint(0, 50), rnd.choice("abc"))}
                   for _ in range(200)]
        results = []
        for _ in range(5):
            order = updates[:]
            rnd.shuffle(order)
            st = {}
            for u in order:
                sf.merge_entries(st, u)
            results.append(json.dumps(st, sort_keys=True))
        self.assertEqual(len(set(results)), 1)

    def test_ignores_garbage(self):
        a = {}
        sf.merge_entries(a, {"x": "lixo", "y": {"v": 1}})
        self.assertEqual(a, {})


class TwoDaemonsTest(unittest.TestCase):
    """Dois daemons na mesma máquina, pareados manualmente, sem mDNS."""

    def start(self, name, port, peer_port):
        tmp = Path(tempfile.mkdtemp())
        cfg = {"device": name, "name": name, "key": "SEGREDO1", "port": port,
               "peers": [f"127.0.0.2:{peer_port}"]}
        store = sf.Store(tmp / "state.json", name)
        peers = sf.Peers(store, cfg)
        store.listeners.append(peers.push_soon)
        httpd = ThreadingHTTPServer(("127.0.0.2", port), sf.make_handler(store, peers, cfg))
        threading.Thread(target=httpd.serve_forever, daemon=True).start()
        self.addCleanup(httpd.shutdown)
        return store, peers, cfg

    def wait(self, fn, timeout=5):
        end = time.time() + timeout
        while time.time() < end:
            if fn():
                return True
            time.sleep(0.05)
        return False

    def test_mark_on_one_shows_on_other(self):
        a, pa, _ = self.start("linux-a", 47401, 47402)
        b, pb, _ = self.start("linux-b", 47402, 47401)
        a.set("2026-11-15/done/wake", True)
        self.assertTrue(self.wait(lambda: b.get("2026-11-15/done/wake") is True))
        b.set("2026-11-15/humor", -1)
        self.assertTrue(self.wait(lambda: a.get("2026-11-15/humor") == -1))
        self.assertTrue(b.day("2026-11-15")["done"]["wake"])

    def test_wrong_key_rejected(self):
        import http.client
        self.start("linux-c", 47403, 47404)

        def status(key, path="/v1/state"):
            # origem 127.0.0.3: não conta como localhost
            c = http.client.HTTPConnection("127.0.0.2", 47403, timeout=3, source_address=("127.0.0.3", 0))
            c.request("GET", path, headers={"X-SunBi-Key": key})
            code = c.getresponse().status
            c.close()
            return code

        self.assertEqual(status("ERRADO"), 401)
        self.assertEqual(status("segredo1"), 200)  # não diferencia maiúsculas
        self.assertEqual(status("SEGREDO1", "/v1/day"), 404)  # rota só local

    def test_persistence(self):
        tmp = Path(tempfile.mkdtemp()) / "state.json"
        s = sf.Store(tmp, "linux-p")
        s.set("2026-11-15/note", "dia bom")
        self.assertEqual(sf.Store(tmp, "linux-p").get("2026-11-15/note"), "dia bom")


class DayViewTests(unittest.TestCase):
    def store(self):
        return sf.Store(Path(tempfile.mkdtemp()) / "state.json", "linux-t")

    def test_done_accepts_time_string(self):
        s = self.store()
        s.set("2026-11-15/done/wake", "07:20")
        s.set("2026-11-15/done/bed", False)
        d = s.day("2026-11-15")
        self.assertTrue(d["done"]["wake"])
        self.assertFalse(d["done"]["bed"])
        self.assertEqual(d["doneAt"], {"wake": "07:20"})

    def test_humor_new_and_old_scale(self):
        s = self.store()
        s.set("2026-11-14/mood", 1)
        self.assertEqual(s.day("2026-11-14")["humor"], -2)
        s.set("2026-11-15/humor", 3)
        self.assertEqual(s.day("2026-11-15")["humorLabel"], "Muito acelerado")

    def test_sleep_from_times_across_midnight(self):
        s = self.store()
        s.set("2026-11-15/bed", "23:30")
        s.set("2026-11-15/wake", "07:00")
        self.assertEqual(s.day("2026-11-15")["sleep"], 7.5)
        self.assertEqual(sf.sleep_hours("01:15", "06:45"), 5.5)

    def test_default_tasks_have_five_anchors(self):
        self.assertEqual(sum(1 for t in sf.DEFAULT_TASKS if t.get("anchor")), 5)


class MigrationTest(unittest.TestCase):
    def test_copies_sunflower_dirs_once(self):
        home = Path(tempfile.mkdtemp())
        (home / "cfg" / "sunflower-clocks").mkdir(parents=True)
        (home / "cfg" / "sunflower-clocks" / "config.json").write_text('{"device": "linux-old", "key": "ABC"}')
        (home / "data" / "sunflower-clocks").mkdir(parents=True)
        (home / "data" / "sunflower-clocks" / "state.json").write_text('{"entries": {}}')
        old = dict(os.environ)
        os.environ["XDG_CONFIG_HOME"] = str(home / "cfg")
        os.environ["XDG_DATA_HOME"] = str(home / "data")
        try:
            cfg = sf.load_config()
            self.assertEqual(cfg["device"], "linux-old")
            self.assertTrue((home / "data" / "sunbi" / "state.json").exists())
        finally:
            os.environ.clear()
            os.environ.update(old)


if __name__ == "__main__":
    unittest.main()
