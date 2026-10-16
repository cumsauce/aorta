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
* `breath_in` / `breath_out` -- turbulent air through the airway. White noise,
  band-passed (in ~300-2500 Hz, out ~200-1800 Hz), with an asymmetric
  envelope: inspiration is a slow swell, expiration a long decay.

All are mono, 16-bit, 44.1 kHz. The engine pitch-shifts them to match the
instantaneous heart/respiratory rate at runtime.
"""

import argparse
import os

import numpy as np

SR = 44100


def normalize(x, peak=0.92):
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


def bandpass_noise(dur, lo, hi, seed):
    rng = np.random.default_rng(seed)
    n = int(dur * SR)
    x = rng.standard_normal(n)
    X = np.fft.rfft(x)
    freqs = np.fft.rfftfreq(n, 1 / SR)
    X[(freqs < lo) | (freqs > hi)] = 0.0
    return np.fft.irfft(X, n=n)


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
    return normalize(out)


def heartbeat_dub():
    return normalize(damped_sine(0.22, 80.0, 0.045, amp=0.75))


def breath_in():
    noise = bandpass_noise(1.1, 300.0, 2500.0, seed=1)
    return normalize(noise * envelope_swell(1.1, 0.35, 0.30), peak=0.45)


def breath_out():
    noise = bandpass_noise(1.4, 200.0, 1800.0, seed=2)
    return normalize(noise * envelope_decay(1.4, 0.18), peak=0.45)


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
    for name, make in SOUNDS.items():
        data = make()
        pcm = (data * 32767.0).astype(np.int16)
        wav_path = os.path.join(args.out, name + ".wav")
        ogg_path = os.path.join(args.out, name + ".ogg")
        # The WAV is for inspection; the game loads the OGG.
        import soundfile as sf

        sf.write(wav_path, pcm, SR, format="WAV", subtype="PCM_16")
        sf.write(ogg_path, pcm, SR, format="OGG", subtype="VORBIS")
        os.remove(wav_path)
        print(f"wrote {ogg_path} ({os.path.getsize(ogg_path)} bytes)")


if __name__ == "__main__":
    main()
