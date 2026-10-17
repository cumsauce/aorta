#!/usr/bin/env python3
"""Generate Aorta's sound set from physical models, not recordings.

Every sound Aorta plays is synthesized here from a small model of the thing
that makes it, so the asset is a few kilobytes of numbers with an explanation
rather than a recording of unknown provenance. The output is deterministic:
same script, same bytes.

Synthesis model
---------------
* `lub` (S1) -- the atrioventricular valves snap shut and the whole heart
  jolts. Three damped modes (~52/29/104 Hz) plus a short band-limited noise
  transient at closure, softly saturated so it reads as a body, not a sine.
* `dub` (S2) -- the semilunar valves close higher and shorter (~84/47/162 Hz),
  plus a brief, brighter closure tick.
* `breath_in` / `breath_out` -- turbulent air through the airway.

The breath is the interesting one. Measurements say quiet adult breathing is
~12-15/min on a ~5 s cycle (~2 s in, ~3 s out, I:E about 1:1.5); its sound
pressure peaks at 63-250 Hz, is around -10 dB by 1 kHz and near the floor by
2-4 kHz, and normal breath sounds have a median around 355-367 Hz.

A single low-pass sounds like wind and a high shelf sounds like a gasp, so the
breath is built the way Foley builds moving air: as a *filter*, not a colour.
The air is brown-ish noise (turbulence, not white hiss) driven through a
Kelly-Lochbaum digital waveguide tract -- 44 sections of 3.9 mm, scattering at
area changes, reflections at the glottis and lips, and a nasal branch -- after
Foley's `voice/tract.rs`. That gives real vocal-tract formants, and the tract
length sets where they sit. A low-mid body layer (chest ~170 Hz, airway
~430 Hz) is mixed underneath so the result keeps the low-frequency weight a
chest recording has. The tract output is gently low-passed at 2 kHz so the top
octave stays air, not hiss.

Each event is rendered several times with different seeds, tract lengths and
envelopes, and `sounds.json` lists them together so the engine picks one at
random per play: no two breaths are the same sample.

All are mono, 16-bit, 44.1 kHz. The engine schedules the one-shots at the
model's heart/breath rate; the assets themselves are static and deterministic.
"""

import argparse
import os

import numpy as np

SR = 44100

# Kelly-Lochbaum tract constants, from Foley's voice/tract.rs.
C = 343.0
SECTION = C / (2.0 * SR)
GLOTTIS = 0.75
LIPS = -0.85
NOSTRILS = -0.85
FADE = 0.999


def normalize(x, peak):
    m = np.max(np.abs(x))
    if m < 1e-9:
        return x
    return x * (peak / m)


class Tract:
    """A simplified Kelly-Lochbaum waveguide with a nasal branch.

    Sections are half a sample of sound travel long. Pressure waves scatter at
    every area change, reflect at the glottis (0.75) and lips/nostrils (-0.85),
    and radiate from the lips and nose. A noise source injected near the
    glottis is shaped by the tract's formants.
    """

    def __init__(self, length=0.20):
        self.n = max(8, min(180, int(round(length / SECTION))))
        n = self.n
        d = np.empty(n)
        for i in range(n):
            x = i / n
            d[i] = 0.6 if x < 7 / 44 else (1.1 if x < 12 / 44 else 1.5)
        self.area = np.maximum(d * d, 1e-4)

        self.m = max(4, int(round(n * 28 / 44)))
        m = self.m
        na = np.empty(m)
        for i in range(m):
            dd = 2.0 * i / m
            dd = 0.4 + 1.6 * dd if dd < 1.0 else 0.5 + 1.5 * (2.0 - dd)
            na[i] = min(dd, 1.9) ** 2
        self.nose_area = na
        self.velum_at = n - m + 1
        self.nose_area[0] = 0.4  # velum fully open

        self.right = np.zeros(n)
        self.left = np.zeros(n)
        self.out_right = np.zeros(n + 1)
        self.out_left = np.zeros(n + 1)
        self.nright = np.zeros(m)
        self.nleft = np.zeros(m)
        self.nout_right = np.zeros(m + 1)
        self.nout_left = np.zeros(m + 1)

    def step(self, glottis, noise_at, noise_amp):
        n, m, v = self.n, self.m, self.velum_at

        self.out_right[0] = self.left[0] * GLOTTIS + glottis
        self.out_left[n] = self.right[n - 1] * LIPS

        a1 = self.area[:-1]
        a2 = self.area[1:]
        p = 2.0 * (a1 * self.right[:-1] + a2 * self.left[1:]) / (a1 + a2)
        self.out_right[1:n] = p - self.left[1:n]
        self.out_left[1:n] = p - self.right[:-1]

        # Three-way junction at the velum.
        al, ar, an = self.area[v - 1], self.area[v], self.nose_area[0]
        pv = (
            2.0
            * (al * self.right[v - 1] + ar * self.left[v] + an * self.nleft[0])
            / (al + ar + an)
        )
        self.out_right[v] = pv - self.left[v]
        self.out_left[v] = pv - self.right[v - 1]
        self.nout_right[0] = pv - self.nleft[0]

        if noise_amp:
            i = min(max(noise_at, 0), n - 1)
            self.out_right[i] += 0.5 * noise_amp
            self.out_left[i] += 0.5 * noise_amp

        self.right[:] = self.out_right[:n] * FADE
        self.left[:] = self.out_left[1 : n + 1] * FADE

        self.nout_left[m] = self.nright[m - 1] * NOSTRILS
        na1 = self.nose_area[:-1]
        na2 = self.nose_area[1:]
        q = 2.0 * (na1 * self.nright[:-1] + na2 * self.nleft[1:]) / (na1 + na2)
        self.nout_right[1:m] = q - self.nleft[1:m]
        self.nout_left[1:m] = q - self.nright[:-1]
        self.nright[:] = self.nout_right[:m] * FADE
        self.nleft[:] = self.nout_left[1 : m + 1] * FADE

        return (self.right[n - 1] * (1.0 + LIPS), self.nright[m - 1] * (1.0 + NOSTRILS))

    def run(self, glottis, noise_at, noise_amp):
        oral = 0.0
        nasal = 0.0
        for _ in range(2):
            o, a = self.step(glottis, noise_at, noise_amp)
            oral += o
            nasal += a
        return 0.5 * oral, 0.5 * nasal


def smoothstep(x):
    x = np.clip(x, 0.0, 1.0)
    return x * x * (3.0 - 2.0 * x)


def smooth_noise(n, cutoff, seed):
    """Low-passed noise in [-1, 1], for slow, organic amplitude movement."""
    rng = np.random.default_rng(seed)
    x = rng.standard_normal(n)
    a = np.exp(-2.0 * np.pi * cutoff / SR)
    y = np.empty(n)
    acc = 0.0
    for i in range(n):
        acc = a * acc + (1.0 - a) * x[i]
        y[i] = acc
    peak = np.max(np.abs(y))
    return y / peak if peak > 1e-9 else y


def band_noise(dur, lo, hi, seed, sigma=0.6):
    """Noise through a smooth log-Gaussian band, for short transient ticks."""
    rng = np.random.default_rng(seed)
    n = int(dur * SR)
    x = rng.standard_normal(n)
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1.0 / SR)
    centre = np.sqrt(lo * hi)
    width = sigma * np.log(hi / lo)
    X *= np.exp(-0.5 * (np.log(np.maximum(f, 1.0) / centre) / width) ** 2)
    return np.fft.irfft(X, n=n)


def brown_noise(n, seed):
    """Turbulence-ish excitation: 1/f, not white. White drove the tract too bright."""
    rng = np.random.default_rng(seed)
    x = rng.standard_normal(n)
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1.0 / SR)
    X /= np.maximum(f, 20.0)
    y = np.fft.irfft(X, n=n)
    return y / (np.max(np.abs(y)) + 1e-9)


def lowpass(x, cutoff):
    """One-pole low-pass, 6 dB/octave."""
    a = np.exp(-2.0 * np.pi * cutoff / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc = a * acc + (1.0 - a) * x[i]
        y[i] = acc
    return y


def resonant_gain(f, layers):
    """Sum of second-order band-pass magnitudes: the airway's low-mid body."""
    g = np.zeros_like(f)
    for centre, q, amp in layers:
        ratio = np.where(f > 0.0, f / centre, 0.0)
        r = np.where(f > 0.0, ratio - 1.0 / np.maximum(ratio, 1e-9), 0.0)
        g += amp / (1.0 + (q * r) ** 2)
    return g


# Low-mid body layers mixed under the tract output. The tract supplies the
# formants near 500-650 Hz; these supply the chest weight below them.
BREATH_IN = [
    (165.0, 0.85, 1.00),
    (430.0, 1.35, 0.60),
    (1000.0, 1.80, 0.15),
    (2200.0, 0.80, 0.045),
]
BREATH_OUT = [
    (180.0, 0.85, 1.00),
    (430.0, 1.35, 0.62),
    (1000.0, 1.80, 0.14),
    (2300.0, 0.80, 0.045),
]


def breath_envelope(dur, attack, release):
    """A soft, smooth swell: no click at the onset, no abrupt cut at the end."""
    n = int(dur * SR)
    e = np.ones(n)
    a = max(1, int(attack * SR))
    r = max(1, int(release * SR))
    e[:a] = smoothstep(np.linspace(0.0, 1.0, a))
    e[-r:] = smoothstep(np.linspace(1.0, 0.0, r))
    return e


def shaped_breath(dur, layers, seed):
    """The low-mid body: noise through a few broad airway bands."""
    rng = np.random.default_rng(seed)
    n = int(dur * SR)
    x = rng.standard_normal(n)
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1.0 / SR)

    g = resonant_gain(f, layers)
    g *= 1.0 - np.exp(-((f / 55.0) ** 2))  # drop sub-bass rumble
    X *= g
    y = np.fft.irfft(X, n=n)

    y *= 1.0 + 0.07 * smooth_noise(n, 3.0, seed + 7)  # slow turbulence
    return y


def tract_breath(
    dur,
    oral_gain,
    nasal_gain,
    weight,
    layers,
    seed,
    peak,
    attack,
    release,
    tract_length,
):
    """Noise through the tract, blended with the low-mid body and enveloped."""
    n = int(dur * SR)
    exc = brown_noise(n, seed)

    tract = Tract(tract_length)
    at = max(1, int(tract.n * 0.05))
    oral = np.empty(n)
    nasal = np.empty(n)
    for i in range(n):
        o, a = tract.run(0.0, at, exc[i])
        oral[i] = o
        nasal[i] = a

    voiced = oral_gain * oral + nasal_gain * nasal
    voiced -= np.mean(voiced)
    voiced /= np.max(np.abs(voiced)) + 1e-9
    voiced = lowpass(voiced, 2000.0)  # keep the top octave as air, not hiss
    voiced /= np.max(np.abs(voiced)) + 1e-9

    body = shaped_breath(dur, layers, seed + 31)
    body /= np.max(np.abs(body)) + 1e-9

    y = (1.0 - weight) * body + weight * voiced
    return normalize(y * breath_envelope(dur, attack, release), peak)


def heartbeat_lub(seed=11, detune=1.0):
    dur = 0.34
    t = np.linspace(0.0, dur, int(dur * SR), endpoint=False)
    body = (
        0.95 * np.exp(-t / 0.060) * np.sin(2 * np.pi * 52.0 * detune * t)
        + 0.45 * np.exp(-t / 0.042) * np.sin(2 * np.pi * 29.0 * detune * t)
        + 0.22 * np.exp(-t / 0.026) * np.sin(2 * np.pi * 104.0 * detune * t)
    )

    # Valve closure is broadband as well as low: a very short filtered noise
    # transient is what stops the sound reading as a pure sine.
    n = int(0.028 * SR)
    tick = band_noise(0.028, 120.0, 850.0, seed=seed)
    tick *= np.exp(-np.linspace(0.0, 7.0, n))
    body[:n] += 0.30 * tick

    body = np.tanh(body * 1.5) / np.tanh(1.5)
    return normalize(body, peak=0.72)


def heartbeat_dub(seed=12, detune=1.0):
    dur = 0.26
    t = np.linspace(0.0, dur, int(dur * SR), endpoint=False)
    body = (
        0.85 * np.exp(-t / 0.040) * np.sin(2 * np.pi * 84.0 * detune * t)
        + 0.35 * np.exp(-t / 0.030) * np.sin(2 * np.pi * 47.0 * detune * t)
        + 0.18 * np.exp(-t / 0.020) * np.sin(2 * np.pi * 162.0 * detune * t)
    )

    n = int(0.020 * SR)
    tick = band_noise(0.020, 220.0, 1500.0, seed=seed)
    tick *= np.exp(-np.linspace(0.0, 7.0, n))
    body[:n] += 0.22 * tick

    body = np.tanh(body * 1.4) / np.tanh(1.4)
    return normalize(body, peak=0.56)


def breath_in(seed=1, tract_length=0.20, weight=0.55, attack=0.35, release=0.75):
    # 2 s inhale; mostly nasal (oral gain low), matching a quiet in-breath.
    return tract_breath(
        2.0,
        oral_gain=0.20,
        nasal_gain=1.00,
        weight=weight,
        layers=BREATH_IN,
        seed=seed,
        peak=0.28,
        attack=attack,
        release=release,
        tract_length=tract_length,
    )


def breath_out(seed=2, tract_length=0.20, weight=0.55, attack=0.40, release=1.20):
    # 3 s exhale; more oral, matching the longer, more open out-breath.
    return tract_breath(
        3.0,
        oral_gain=0.75,
        nasal_gain=0.50,
        weight=weight,
        layers=BREATH_OUT,
        seed=seed,
        peak=0.32,
        attack=attack,
        release=release,
        tract_length=tract_length,
    )


def generate():
    """Every event's variants, each rendered with its own seed and shape."""
    return {
        "lub": [
            heartbeat_lub(seed=11, detune=0.985),
            heartbeat_lub(seed=21, detune=1.015),
        ],
        "dub": [
            heartbeat_dub(seed=12, detune=0.985),
            heartbeat_dub(seed=22, detune=1.015),
        ],
        "breath_in": [
            breath_in(
                seed=1, tract_length=0.19, weight=0.53, attack=0.34, release=0.72
            ),
            breath_in(
                seed=101, tract_length=0.20, weight=0.55, attack=0.35, release=0.75
            ),
            breath_in(
                seed=201, tract_length=0.21, weight=0.57, attack=0.36, release=0.78
            ),
        ],
        "breath_out": [
            breath_out(
                seed=2, tract_length=0.19, weight=0.53, attack=0.39, release=1.14
            ),
            breath_out(
                seed=102, tract_length=0.20, weight=0.55, attack=0.40, release=1.20
            ),
            breath_out(
                seed=202, tract_length=0.21, weight=0.57, attack=0.41, release=1.26
            ),
        ],
    }


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument(
        "--out",
        default="src/main/resources/assets/aorta/sounds",
        help="directory to write the .ogg files into",
    )
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)
    import soundfile as sf

    for event, renders in generate().items():
        for index, data in enumerate(renders, start=1):
            name = f"{event}_{index}"
            pcm = (np.clip(data, -1.0, 1.0) * 32767.0).astype(np.int16)
            wav_path = os.path.join(args.out, name + ".wav")
            ogg_path = os.path.join(args.out, name + ".ogg")
            # The WAV is for inspection; the game loads the OGG.
            sf.write(wav_path, pcm, SR, format="WAV", subtype="PCM_16")
            sf.write(ogg_path, pcm, SR, format="OGG", subtype="VORBIS")
            os.remove(wav_path)
            print(f"wrote {ogg_path} ({os.path.getsize(ogg_path)} bytes)")


if __name__ == "__main__":
    main()
