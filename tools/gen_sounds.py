#!/usr/bin/env python3
"""Generate Aorta's sound set from physical models, not recordings.

Every sound Aorta plays is synthesized here from a small model of the thing
that makes it, so the asset is a few kilobytes of numbers with an explanation
rather than a recording of unknown provenance. The output is deterministic:
same script, same bytes.

Synthesis model
---------------
* `lub` (S1, the first heart sound) -- the atrioventricular valves snap shut
  and the whole heart jolts. Energy is concentrated low (real S1 sits around
  20-120 Hz), so it is two damped sinusoids (a ~50 Hz body and a ~28 Hz sub)
  with a broadband onset transient.
* `dub` (S2, the second heart sound) -- the semilunar valves close a little
  later, higher and shorter: a ~80 Hz damped sinusoid at ~75% the amplitude.
* `breath_in` / `breath_out` -- turbulent air through the airway. White noise
  shaped with a wide band (up to ~8 kHz) and a gentle high shelf, because the
  hiss and sibilance that make a breath read as a breath live in the top
  octaves; a narrow 200-2500 Hz band sounds like a telephone. A slow random
  flutter keeps it from sounding like a static sweep.

All are mono, 16-bit, 44.1 kHz. The engine pitch-shifts them to match the
instantaneous heart/respiratory rate at runtime.
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


def damped_sine(dur, freq, tau, amp=1.0, sub=None, sub_amp=0.0):
    """A damped sinusoid (optionally plus a sub-harmonic), an exponential decay."""
    t = np.linspace(0.0, dur, int(dur * SR), endpoint=False)
    env = np.exp(-t / tau)
    out = amp * env * np.sin(2 * np.pi * freq * t)
    if sub is not None:
        out += sub_amp * env * np.sin(2 * np.pi * sub * t)
    return out


def onset_click(dur=0.008):
    """A short broadband transient: a few samples of noise shaped by a fast decay."""
    n = int(dur * SR)
    rng = np.random.default_rng(0)
    return np.exp(-np.linspace(0, 6.0, n)) * rng.standard_normal(n)


def smooth_noise(n, cutoff, seed):
    """Low-passed noise in [-1, 1], used for slow, organic amplitude flutter."""
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


def shaped_breath(dur, lo, hi, seed, shelf=1.3):
    """Broadband turbulent air: soft-edged band + a high shelf so it stays airy."""
    rng = np.random.default_rng(seed)
    n = int(dur * SR)
    x = rng.standard_normal(n)
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1.0 / SR)

    # Soft band edges (raised over roughly an octave) rather than a hard brick
    # wall, which rings and sounds synthetic.
    low = np.clip((f - lo * 0.4) / (lo * 1.2 + 1e-9), 0.0, 1.0)
    high = np.clip((hi * 1.4 - f) / (hi * 0.6 + 1e-9), 0.0, 1.0)
    mask = low * high

    # Lift the top octaves: the hiss and sibilance are what stop a breath from
    # sounding muffled and lo-fi.
    shelf_gain = 1.0 + shelf * np.clip((f - lo) / (hi - lo + 1e-9), 0.0, 1.0) ** 1.4

    X *= mask * shelf_gain
    y = np.fft.irfft(X, n=n)

    # Slow random amplitude flutter: real breath is not a steady sweep.
    y *= 1.0 + 0.12 * smooth_noise(n, 25.0, seed + 100)
    return y


def envelope_swell(dur, attack, release):
    """A smooth attack-sustain-release envelope (fractions of dur)."""
    n = int(dur * SR)
    na = int(n * attack)
    nr = int(n * release)
    e = np.ones(n)
    if na > 0:
        e[:na] = np.linspace(0.0, 1.0, na, endpoint=False)
    if nr > 0:
        e[-nr:] = np.linspace(1.0, 0.0, nr, endpoint=True)
    return e


def envelope_decay(dur, attack):
    """Attack then a long exponential decay to zero."""
    n = int(dur * SR)
    na = int(n * attack)
    t = np.arange(n) / SR
    e = np.exp(-3.5 * np.maximum(0.0, t - attack))
    if na > 0:
        e[:na] *= np.linspace(0.0, 1.0, na, endpoint=False)
    return e


def heartbeat_lub():
    body = damped_sine(0.30, 50.0, 0.06, amp=1.0, sub=28.0, sub_amp=0.4)
    click = onset_click()
    out = np.zeros_like(body)
    out[: len(click)] += click * 0.25
    out += body
    return normalize(out, peak=0.75)


def heartbeat_dub():
    return normalize(damped_sine(0.22, 80.0, 0.045, amp=0.75), peak=0.60)


def breath_in():
    noise = shaped_breath(1.15, 250.0, 8000.0, seed=1)
    return normalize(noise * envelope_swell(1.15, 0.32, 0.30), peak=0.40)


def breath_out():
    noise = shaped_breath(1.5, 180.0, 6500.0, seed=2)
    return normalize(noise * envelope_decay(1.5, 0.14), peak=0.40)


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
