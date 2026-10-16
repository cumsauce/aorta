# Aorta — design notes

## Intent

The suite gives the world a voice (Foley), a room to speak in (Earshot), other
people to hear (Yak VC), and a body to bleed (Gore). What's missing is the sound
of *your own* body. Aorta closes that gap: a heartbeat and breath that are
synthesized from a physiological model of the player's actual state, so your body
becomes part of the soundscape — louder and faster exactly when the game is
harder.

## Goals

- A client-side heartbeat (S1/S2 "lub-dub") and breath, both synthesized, never
  recorded.
- Rate, pitch, and volume driven by a closed-form model of health, hunger,
  drowning, exertion, threat, darkness, and depth.
- Pure, unit-testable core (model + clocks + config) separated from the thin
  client glue.
- Fit the suite's conventions: config-driven, `/aorta` debug command, client
  game-test smoke test, MC 26.3 unobfuscated names.

## Non-goals

- No server-side logic; the mod changes only what one player hears.
- No spatial audio or occlusion (Earshot owns that); body sounds are played
  relative with no attenuation.
- No new entities, blocks, or items.

## Model

All terms are additive on a resting value, so the whole thing stays readable and
pinnable by tests.

Resting: 70 bpm, 14 breaths/min.

| Driver | Heart (bpm) | Breath (/min) |
| ------ | ----------- | ------------- |
| low health (0→1 frac) | up to +60 | up to +10 |
| starving / hungry | +18 / +6 | — |
| sprinting / moving | +55 / +22 | +22 / +8 |
| drowning (air 1→0) | up to +50 | up to +18 |
| threat (0→1) | up to +40 | up to +12 |
| falling | +15 | — |

Depth of breath and force of heartbeat are similarly derived and clamped to
0..1.

## Rhythm

Both clocks schedule on integer game ticks (20 tps). A heart beat is 1200/HR
ticks; the S2 lands ~28% of the way through the cycle. A breath is 1200/BR ticks,
inhale ~42% then exhale.

## Audio

`tools/gen_sounds.py` synthesizes four mono 44.1 kHz OGGs from physical models:

- `lub` (S1): two damped sinusoids (50 Hz body + 28 Hz sub) plus an onset
  transient.
- `dub` (S2): an 80 Hz damped sinusoid at 75% amplitude.
- `breath_in` / `breath_out`: band-passed noise with asymmetric envelopes.

The engine varies rate via the clocks and volume/pitch from the model; the assets
themselves are static and deterministic.
