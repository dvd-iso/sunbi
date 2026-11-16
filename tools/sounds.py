"""Sintetiza os sons do SunBi (WAV 44,1 kHz mono, 16 bits). Só usa a biblioteca padrão.

Uso:  python3 tools/sounds.py <pasta de saída>

Sons:
  pop        marcar tarefa como feita (bolha curta, tom descendo)
  unpop      desmarcar (mais grave)
  tick       escolher humor e opções (clique bem leve)
  chime      rotina do dia completa (arpejo de sino)
  focus_start / focus_end   começo e fim do bloco de foco
  amanhecer  alarme: frase de kalimba em pentatônica, em loop (o app aumenta o volume aos poucos)
"""
import math
import random
import struct
import sys
import wave
from pathlib import Path

RATE = 44100


def silence(sec):
    return [0.0] * int(RATE * sec)


def env_exp(n, decay):
    """Envelope com ataque de 4 ms e queda exponencial (decay em segundos até ~-60 dB)."""
    att = int(0.004 * RATE)
    k = 6.9 / max(decay, 1e-3)
    return [(i / att if i < att else 1.0) * math.exp(-k * (i / RATE)) for i in range(n)]


def tone(freqs, dur, decay, partials=((1.0, 1.0),), glide=None):
    """Soma de parciais (multiplicador de frequência, amplitude) com envelope exponencial.
    glide=(f0, f1): a frequência base desliza de f0 para f1."""
    n = int(dur * RATE)
    env = env_exp(n, decay)
    out = [0.0] * n
    for f in freqs:
        for mult, amp in partials:
            ph = 0.0
            for i in range(n):
                base = f
                if glide:
                    a = i / n
                    base = glide[0] * (1 - a) + glide[1] * a
                ph += 2 * math.pi * base * mult / RATE
                out[i] += amp * math.sin(ph) * env[i]
    return out


def mix(dst, src, at):
    start = int(at * RATE)
    need = start + len(src)
    if len(dst) < need:
        dst.extend([0.0] * (need - len(dst)))
    for i, s in enumerate(src):
        dst[start + i] += s
    return dst


def normalize(x, peak_db):
    peak = max(abs(s) for s in x) or 1.0
    g = 10 ** (peak_db / 20) / peak
    return [s * g for s in x]


def fade_out(x, sec):
    n = int(sec * RATE)
    for i in range(max(0, len(x) - n), len(x)):
        x[i] *= (len(x) - i) / n
    return x


def write(path, x):
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s)) * 32767)) for s in x))


def note(name):
    names = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}
    semis = names[name[0]] + (1 if "#" in name else 0)
    octave = int(name[-1])
    return 440.0 * 2 ** ((semis - 9 + 12 * (octave - 4)) / 12)


# timbres
BELL = ((1.0, 1.0), (2.76, 0.35), (5.4, 0.12))       # sino suave (parciais inarmônicos)
KALIMBA = ((1.0, 1.0), (2.0, 0.18), (5.9, 0.08))     # dente de kalimba
MARIMBA = ((1.0, 1.0), (4.0, 0.25), (9.9, 0.04))


def pop():
    x = tone([1], 0.09, 0.07, glide=(980, 560))
    return normalize(fade_out(x, 0.01), -10)


def unpop():
    x = tone([1], 0.09, 0.07, glide=(620, 380))
    return normalize(fade_out(x, 0.01), -12)


def tick():
    random.seed(3)
    n = int(0.018 * RATE)
    x = [random.uniform(-1, 1) * math.exp(-i / (0.003 * RATE)) for i in range(n)]
    x = mix(x, tone([2400], 0.018, 0.012), 0)
    return normalize(x, -18)


def chime():
    x = []
    for i, nm in enumerate(["C6", "E6", "G6", "C7"]):
        mix(x, tone([note(nm)], 1.4, 1.2 - i * 0.15, BELL), i * 0.09)
    return normalize(fade_out(x, 0.2), -9)


def focus_start():
    x = []
    mix(x, tone([note("G5")], 0.5, 0.45, MARIMBA), 0)
    mix(x, tone([note("C6")], 0.7, 0.6, MARIMBA), 0.12)
    return normalize(fade_out(x, 0.1), -10)


def focus_end():
    x = []
    mix(x, tone([note("E6")], 1.6, 1.5, BELL), 0)
    mix(x, tone([note("C6")], 2.0, 1.9, BELL), 0.35)
    return normalize(fade_out(x, 0.3), -8)


def amanhecer():
    """Frase de ~4,8 s que fecha no tom e volta ao começo sem emenda."""
    phrase = [
        ("C5", 0.0), ("E5", 0.3), ("G5", 0.6), ("A5", 0.9), ("G5", 1.2),
        ("E5", 1.5), ("D5", 1.8), ("E5", 2.1), ("G5", 2.4), ("C6", 2.7),
        ("A5", 3.0), ("G5", 3.3), ("E5", 3.6), ("D5", 3.9), ("C5", 4.2),
    ]
    x = silence(4.8)
    for nm, at in phrase:
        mix(x, tone([note(nm)], 1.3, 1.1, KALIMBA), at)
    # cama suave de quinta (C3+G3) para encorpar sem assustar
    pad = tone([note("C3"), note("G3")], 4.8, 9.0, ((1.0, 0.18), (2.0, 0.05)))
    mix(x, pad, 0)
    # o que passa de 4,8 s volta para o começo, para o loop ficar contínuo
    tail = x[int(4.8 * RATE):]
    x = x[: int(4.8 * RATE)]
    for i, s in enumerate(tail):
        if i < len(x):
            x[i] += s
    return normalize(x, -3)


SOUNDS = {
    "pop": pop, "unpop": unpop, "tick": tick, "chime": chime,
    "focus_start": focus_start, "focus_end": focus_end, "amanhecer": amanhecer,
}

if __name__ == "__main__":
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    for name, fn in SOUNDS.items():
        write(out / f"{name}.wav", fn())
        print("ok", name)
