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

Resting: 58 bpm, 12 breaths/min.

| Driver | Heart (bpm) | Breath (/min) |
| ------ | ----------- | ------------- |
| low health (0→1 frac) | up to +50 | up to +8 |
| starving / hungry | +16 / +5 | — |
| sprinting / moving | +50 / +18 | +20 / +7 |
| drowning (air 1→0) | up to +45 | 0 (holds breath) |
| threat (0→1) | up to +34 | up to +10 |
| falling | +12 | — |

Depth of breath and force of heartbeat are similarly derived and clamped to
0..1. Playback volume is `master * strength^2`, so the calm state falls away
much faster than an exerted one.

## Rhythm and inertia

Both clocks schedule on integer game ticks (20 tps). A heart beat is 1200/HR
ticks; the S2 lands ~28% of the way through the cycle. A breath is 1200/BR ticks,
inhale ~42% then exhale.

The model's instantaneous rates are fed through a first-order smoother with a
quick rise and a slow fall, so the heart eases into exertion and takes its time
coming back down. Underwater the breath rate is zero: the breath clock stops and
the body holds its breath, resuming on surfacing.

## Settings

A small vanilla `Screen` (F9, or `/aorta menu`) holds the volume slider and the
heartbeat on/off toggle. Volume previews live through an in-memory apply and is
persisted on close, so dragging does not rewrite the JSON on every pixel.

## Audio

`tools/gen_sounds.py` synthesizes four mono 44.1 kHz OGGs from physical models:

- `lub` (S1): two damped sinusoids (50 Hz body + 28 Hz sub) plus an onset
  transient.
- `dub` (S2): an 80 Hz damped sinusoid at 75% amplitude.
- `breath_in` / `breath_out`: wideband shaped noise (up to ~8 kHz) with a gentle
  high shelf, so the hiss and sibilance survive; a narrow band sounds lo-fi.

The engine varies rate via the clocks and volume/pitch from the model; the assets
themselves are static and deterministic.
