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
* `breath_in` / `breath_out` -- turbulent air through the airway.

The breath is shaped from measurements rather than guessed. Quiet adult
breathing is ~12-15 breaths/min on a ~5 s cycle (~2 s in, ~3 s out, I:E about
1:1.5); its sound pressure peaks at 63-250 Hz, is already around -10 dB by
1 kHz, and sits near the noise floor by 2-4 kHz; the median frequency of normal
breath sounds is ~350-390 Hz, and the whole thing is only ~10 dB SPL. So the
asset is low-mid dominated with a smooth -12 dB/oct rolloff above ~400 Hz and
a slow, soft envelope -- not the bright, short hiss that reads as a gasp.

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


def smoothstep(x):
    x = np.clip(x, 0.0, 1.0)
    return x * x * (3.0 - 2.0 * x)


def breath_envelope(dur, attack, release):
    """A soft, smooth swell: no click at the onset, no abrupt cut at the end."""
    n = int(dur * SR)
    e = np.ones(n)
    a = max(1, int(attack * SR))
    r = max(1, int(release * SR))
    e[:a] = smoothstep(np.linspace(0.0, 1.0, a))
    e[-r:] = smoothstep(np.linspace(1.0, 0.0, r))
    return e


def shaped_breath(dur, fc, seed):
    """Quiet breath: low-mid dominant, smoothly rolled off above ~400 Hz.

    A single -12 dB/oct low-pass with its knee near the measured median
    frequency of normal breath sounds. No high shelf: boosting the top octaves
    is what made the earlier asset sound like a gasp.
    """
    rng = np.random.default_rng(seed)
    n = int(dur * SR)
    x = rng.standard_normal(n)
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1.0 / SR)

    # Drop sub-bass rumble, then the gentle rolloff.
    low = 1.0 - np.exp(-((f / 70.0) ** 2))
    rolloff = 1.0 / (1.0 + (f / fc) ** 2)
    X *= low * rolloff
    return np.fft.irfft(X, n=n)


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
    # 2 s inhale, matching the inspiratory phase of a ~5 s resting cycle.
    noise = shaped_breath(2.0, 650.0, seed=1)
    return normalize(noise * breath_envelope(2.0, attack=0.35, release=0.75), peak=0.28)


def breath_out():
    # 3 s exhale, matching the longer expiratory phase.
    noise = shaped_breath(3.0, 600.0, seed=2)
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
