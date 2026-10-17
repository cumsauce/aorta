#!/usr/bin/env python3
"""Generate Aorta's sound set from physical models, not recordings.

Every sound Aorta plays is synthesized here from a small model of the thing
that makes it, so the asset is a few kilobytes of numbers with an explanation
rather than a recording of unknown provenance. The output is deterministic:
same script, same bytes.

Synthesis model
---------------
* `lub` (S1) -- the atrioventricular valves snap shut and the whole heart
  jolts. Three damped modes (a ~52 Hz pressure wave, a ~29 Hz sub, a ~104 Hz
  ring) plus a short band-limited noise transient at closure, softly saturated
  so it reads as a body rather than a sine. Real S1 energy sits at 20-120 Hz.
* `dub` (S2) -- the semilunar valves close higher and shorter: ~84 Hz with a
  ~47 Hz sub and a ~162 Hz edge, plus a brief, brighter closure tick.
* `breath_in` / `breath_out` -- turbulent air through the airway.

The breath follows measurements and the approach Foley uses for moving air:
it is *several resonant bands*, not one low-passed noise. Quiet adult
breathing is ~12-15/min on a ~5 s cycle (~2 s in, ~3 s out, I:E about 1:1.5);
its sound pressure peaks at 63-250 Hz, is around -10 dB by 1 kHz and near the
floor by 2-4 kHz, and normal breath sounds have a median around 355-367 Hz.
So the asset is a chest/tracheal body near 170 Hz, a main airway resonance
near 430 Hz, a nasal/formant band near 1 kHz and a trace of air near
2.2 kHz -- with a slow, soft envelope and a few percent of turbulence wobble.
The high shelf of the first attempt made it hiss like a gasp; the single
low-pass of the second made it sound like wind. Resonances are the difference.

All are mono, 16-bit, 44.1 kHz. The engine schedules the one-shots at the
model's heart/breath rate; the assets themselves are static and deterministic.
"""

import argparse
import os

import numpy as np

SR = 44100


def normalize(x, peak):
    m = np.max(np.abs(x))
    if m < 1e-9:
        return x
    return x * (peak / m)


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


def resonant_gain(f, layers):
    """Sum of second-order band-pass magnitudes: the airway's resonances."""
    g = np.zeros_like(f)
    for centre, q, amp in layers:
        ratio = np.where(f > 0.0, f / centre, 0.0)
        # r - 1/r is the detuning; 1/(1 + (Q r)^2) is a 0 dB-peak band-pass.
        r = np.where(f > 0.0, ratio - 1.0 / np.maximum(ratio, 1e-9), 0.0)
        g += amp / (1.0 + (q * r) ** 2)
    return g


# Airway resonances for each phase. The inhale is a touch darker and more
# nasal; the exhale is slightly more open and brighter.
BREATH_IN = [
    (165.0, 0.85, 1.00),  # chest / tracheal body
    (430.0, 1.35, 0.60),  # main airway
    (1000.0, 1.80, 0.15),  # nasal / formant
    (2200.0, 0.80, 0.045),  # air
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
    """Turbulent air shaped by the airway's resonances, not a single low-pass."""
    rng = np.random.default_rng(seed)
    n = int(dur * SR)
    x = rng.standard_normal(n)
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1.0 / SR)

    g = resonant_gain(f, layers)
    g *= 1.0 - np.exp(-((f / 55.0) ** 2))  # drop sub-bass rumble
    X *= g
    y = np.fft.irfft(X, n=n)

    # A few percent of slow turbulence wobble: breath is never a steady sweep.
    y *= 1.0 + 0.07 * smooth_noise(n, 3.0, seed + 7)
    return y


def heartbeat_lub():
    dur = 0.34
    t = np.linspace(0.0, dur, int(dur * SR), endpoint=False)
    body = (
        0.95 * np.exp(-t / 0.060) * np.sin(2 * np.pi * 52.0 * t)
        + 0.45 * np.exp(-t / 0.042) * np.sin(2 * np.pi * 29.0 * t)
        + 0.22 * np.exp(-t / 0.026) * np.sin(2 * np.pi * 104.0 * t)
    )

    # Valve closure is broadband as well as low: a very short filtered noise
    # transient is what stops the sound reading as a pure sine.
    n = int(0.028 * SR)
    tick = band_noise(0.028, 120.0, 850.0, seed=11)
    tick *= np.exp(-np.linspace(0.0, 7.0, n))
    body[:n] += 0.30 * tick

    body = np.tanh(body * 1.5) / np.tanh(1.5)
    return normalize(body, peak=0.72)


def heartbeat_dub():
    dur = 0.26
    t = np.linspace(0.0, dur, int(dur * SR), endpoint=False)
    body = (
        0.85 * np.exp(-t / 0.040) * np.sin(2 * np.pi * 84.0 * t)
        + 0.35 * np.exp(-t / 0.030) * np.sin(2 * np.pi * 47.0 * t)
        + 0.18 * np.exp(-t / 0.020) * np.sin(2 * np.pi * 162.0 * t)
    )

    n = int(0.020 * SR)
    tick = band_noise(0.020, 220.0, 1500.0, seed=12)
    tick *= np.exp(-np.linspace(0.0, 7.0, n))
    body[:n] += 0.22 * tick

    body = np.tanh(body * 1.4) / np.tanh(1.4)
    return normalize(body, peak=0.56)


def breath_in():
    noise = shaped_breath(2.0, BREATH_IN, seed=1)
    return normalize(noise * breath_envelope(2.0, attack=0.35, release=0.75), peak=0.28)


def breath_out():
    noise = shaped_breath(3.0, BREATH_OUT, seed=2)
    return normalize(noise * breath_envelope(3.0, attack=0.40, release=1.20), peak=0.32)


SOUNDS = {
    "lub": heartbeat_lub,
    "dub": heartbeat_dub,
    "breath_in": breath_in,
    "breath_out": breath_out,
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

    for name, make in SOUNDS.items():
        data = make()
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
