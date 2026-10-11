# Synthesises the UI sound effects and lays them on a 44.5 s timeline (48 kHz stereo).
import numpy as np, wave, sys, subprocess, os
SR = 48000; DUR = 44.5
rng = np.random.default_rng(3)
def env(n, a, d):  # attack/decay exponential envelope, seconds
    t = np.arange(n) / SR
    return np.minimum(1, t / max(a, 1e-4)) * np.exp(-t / d)
def bp(x, lo, hi):  # crude FFT band-pass
    X = np.fft.rfft(x); f = np.fft.rfftfreq(len(x), 1 / SR)
    X[(f < lo) | (f > hi)] = 0; return np.fft.irfft(X, len(x))
def tone(freq, dur, a=0.003, d=0.08, harm=(1, .3, .1)):
    n = int(dur * SR); t = np.arange(n) / SR
    f = freq(t) if callable(freq) else np.full(n, freq)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return sum(h * np.sin(ph * (i + 1)) for i, h in enumerate(harm)) * env(n, a, d)
def tap():
    n = int(0.05 * SR); return 0.5 * bp(rng.standard_normal(n), 1500, 6000) * env(n, 0.001, 0.008) + 0.25 * tone(900, 0.05, 0.001, 0.012, (1,))
def tick():
    return 0.35 * tone(2300, 0.08, 0.001, 0.018, (1, .2))
def pop(big=1.0):
    p = tone(lambda t: 260 + 900 * (1 - np.exp(-t / 0.03)), 0.14, 0.002, 0.04, (1, .25)) * big
    s = 0.18 * tone(2640, 0.5, 0.004, 0.18, (1, .4, .1)) * big
    m = np.zeros(max(len(p), len(s))); m[:len(p)] += p; m[:len(s)] += s; return m
def whoosh(dur, lo, hi, vol=0.5):
    n = int(dur * SR); x = rng.standard_normal(n); out = np.zeros(n); step = 1024
    for i in range(0, n, step):
        k = i / n; c = lo * (hi / lo) ** k
        seg = bp(x[i:i + step * 2] if i + step * 2 <= n else x[i:], c * 0.7, c * 1.4)
        out[i:i + len(seg)] += seg[:len(out[i:i + len(seg)])] * np.hanning(len(seg))[:len(out[i:i + len(seg)])]
    t = np.arange(n) / n; return vol * out * np.sin(np.pi * t) ** 1.5
def chime():
    a = tone(1318.5, 1.2, 0.004, 0.45, (1, .35, .12)); b = tone(1975.5, 1.2, 0.004, 0.5, (1, .3, .1))
    m = np.zeros(len(a) + int(0.11 * SR)); m[:len(a)] += 0.3 * a; m[int(0.11 * SR):] += 0.28 * b; return m
def thud(vol=1.0):
    n = int(0.25 * SR)
    return vol * (0.6 * tone(lambda t: 150 - 60 * t / 0.25, 0.25, 0.002, 0.05, (1, .3)) + 0.25 * bp(rng.standard_normal(n), 200, 2500) * env(n, 0.001, 0.02))
def paper():
    n = int(0.12 * SR); return 0.35 * bp(rng.standard_normal(n), 800, 5000) * env(n, 0.002, 0.025) + 0.3 * tone(320, 0.12, 0.002, 0.03, (1,))
def pen(dur):
    n = int(dur * SR); x = bp(rng.standard_normal(n), 2500, 9000)
    am = 0.6 + 0.4 * np.sin(2 * np.pi * 7 * np.arange(n) / SR) ** 2
    t = np.arange(n) / n; return 0.12 * x * am * np.sin(np.pi * t) ** 0.5
def shutter():
    if os.path.exists('audio/shutter.ogg.mp3'):
        raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', 'audio/shutter.ogg.mp3', '-ac', '1', '-ar', str(SR), '-f', 'f32le', '-'], capture_output=True).stdout
        x = np.frombuffer(raw, np.float32).astype(float)
        nz = np.where(np.abs(x) > 0.05 * np.abs(x).max())[0]
        x = x[max(0, nz[0] - 200): nz[-1] + 2000]
        return 0.7 * x / np.abs(x).max()
    n = int(0.12 * SR); return 0.6 * bp(rng.standard_normal(n), 800, 8000) * env(n, 0.001, 0.02)

track = np.zeros(int(DUR * SR) + SR)
def put(t, x, g=1.0):
    i = int(t * SR); track[i:i + len(x)] += g * x[:len(track) - i]
# timeline (seconds), matched to site/app.js
put(4.0, tap(), .7)                         # tap to focus
put(5.0, tap(), .6); put(5.06, shutter(), .9)  # shutter
put(5.4, whoosh(1.45, 600, 3500, .18))      # scan sweep
for i in range(3): put(6.8 + 0.26 * i, tick(), .8)
put(8.1, pop(1.0), 1.0)                     # sticker pops
put(9.2, tap(), .5); put(9.55, whoosh(0.5, 500, 4000, .45))  # peel off
put(11.3, chime(), .9)                      # identified
put(13.55, tap(), .6); put(13.8, pop(0.5), .5)
put(15.45, tap(), .6)
put(16.9, tap(), .6); put(17.2, whoosh(0.35, 800, 2500, .15))
put(18.9, whoosh(1.1, 300, 1200, .12))      # scroll to the timeline
put(22.1, tap(), .6); put(22.4, whoosh(0.35, 800, 2500, .15))
put(23.3, whoosh(0.8, 300, 1200, .10))
put(24.5, whoosh(0.35, 800, 2500, .15))
for i in range(11): put(25.0 + i * 0.07, pop(0.35), .45)  # markers
put(27.0, tap(), .6); put(27.15, whoosh(0.4, 400, 1800, .25))
put(29.55, whoosh(0.3, 800, 2500, .12)); put(30.95, tap(), .6)
put(32.6, whoosh(0.3, 800, 2500, .12))
for t in (33.05, 33.35, 33.65, 33.95): put(t + 0.06, paper(), .9)
put(34.55, tick(), .4)
put(35.2, pen(0.75), 1.0)
put(36.22, thud(), .9)                      # stamp
put(37.15, whoosh(0.6, 300, 2500, .35))     # share card
put(40.8, chime(), .8)
track = track[:int(DUR * SR)]
peak = np.abs(track).max(); track = track / peak * 0.7
pcm = (np.clip(track, -1, 1) * 32767).astype(np.int16)
with wave.open('audio/sfx.wav', 'wb') as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
print('sfx ok', peak)
