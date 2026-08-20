# ViveTaCZ

VR support for **[TaCZ Refabricated]** (Timeless & Classics Zero, Fabric port) via
**[Vivecraft]**, for Minecraft **1.20.1 / Fabric**.

## Goals

1. **Gun models visible & animated in VR** — draw TaCZ's animated first-person gun
   attached to your controller (Vivecraft normally only shows the static model).
2. **Reload animations play in VR** — button-triggered for now; the physical/gesture
   reload is a planned follow-up.
3. **Scopes actually zoom** — planned; see *Roadmap*.

## Features

What ViveTaCZ does once you're in VR with TaCZ installed:

**Rendering**
- **Animated gun in your hand** — the first-person gun is drawn at your main-hand
  controller with its animation state machine ticking, so reload / fire / inspect / bolt
  animations actually play (Vivecraft alone shows only the static model).
- **Correctly-placed ammo items** — magazines and ammo boxes held in VR also get proper
  in-hand placement, not the default flat-item pose.
- **Static-body option** — freeze the gun's idle/run sway so only the moving parts
  (magazine, bolt, slide, charging handle…) animate.
- **Force high-poly model** — keeps the animated model on screen instead of TaCZ's
  distant static LOD.

**Shooting**
- **Physical controller aiming** — bullets leave the actual barrel along the direction
  your controller points, with a per-gun yaw/pitch/roll calibration, instead of firing
  where your head looks. No more eye-to-gun parallax.
- **Muzzle bullet origin** — the bullet spawns at the muzzle, not your eye.
- **Recoil** — the muzzle climbs (and drifts slightly) per shot and recovers over time;
  strength is adjustable.
- **Muzzle tracers** — an optional short-lived beam drawn from the barrel to the impact
  point, matched to the recoil-adjusted bullet path.
- **Recoil haptics** — the gun-hand controller buzzes on each shot.

**HUD**
- **Floating gun HUD** — ammo count, fire mode, and (for heat guns like the minigun) a
  heat/overheat bar, drawn next to the gun. The ammo count matches TaCZ's own desktop HUD,
  including the chambered "+1" round on closed-bolt guns.

**Reloading** (two modes — pick one in the config)
- **Proximity gesture** — bring your off hand to the gun to trigger a reload.
- **Physical magazine** — a magazine sits by your gun; reach your off hand to grab it, then
  bring it up to the gun's mag well to seat it and reload.
  - A translucent **ghost of the gun's real magazine** marks the insert zone (green when
    ready to seat).
  - Optional **two-stage reload**: press *Drop Magazine* to eject the old mag first, then
    insert a fresh one.
  - **Per-gun reload zones and mag placement**, so each gun's mag well lines up.
  - Wrong-gun magazines are **auto-dropped when you switch weapons**; inventory-fed guns
    (e.g. the minigun) are left to TaCZ's own reload so they aren't corrupted.
  - A **grab cooldown** stops accidental reload spam.

**Quality-of-life**
- **Keep the gun visible while the config menu is open**, so you can align placements live.
- **Full in-game configuration** via Mod Menu (Cloth Config) — every option below is
  live-tunable and most support per-gun overrides.
- **Opt-in debug logging** to `logs/vivetacz-debug.log` for tuning aim/placement from real
  numbers, plus a *Dump State* key for a one-shot snapshot.
- **Safe when idle** — the Vivecraft link is reflection-only, so the mod loads inert with no
  Vivecraft installed or in desktop mode and never affects normal (non-VR) TaCZ.

## Controls

Two keybinds (bind them to controller buttons in Vivecraft's controls; defaults are for
desktop testing):

| Action | Default | What it does |
| --- | --- | --- |
| **Drop Magazine** | `V` | Ejects the current mag in two-stage reload mode. |
| **Debug: Dump State** | `P` | Writes a full state snapshot to the debug log (needs debug logging on). |

## Configuration

Everything is editable in-game via **Mod Menu → ViveTaCZ**, and persisted to
`config/vivetacz.json`.

- **Master:** enable/disable the whole mod.
- **Gun rendering:** static gun body toggle; default gun placement (position / rotation /
  scale) + per-gun overrides; ammo-item placement.
- **HUD:** enable toggle + HUD placement.
- **Aiming:** controller aim toggle; aim offset (yaw/pitch/roll) + per-gun aim overrides;
  recoil strength; barrel tracer toggle; haptic feedback toggle.
- **Reloading:** proximity-gesture toggle + trigger distance; physical-magazine toggle;
  magazine-pouch placement; held-magazine placement; grab distance; two-stage toggle;
  reload-zone placement + per-gun reload zones; show-reload-zone marker toggle; reload
  cooldown.
- **Menu:** keep the gun rendered while a config screen is open.
- **Debug:** verbose logging toggle.

## How it works

TaCZ ticks its gun animation state machine and draws the animated model only inside
`GunItemRendererWrapper.renderFirstPerson(...)`, which runs from TaCZ's Forge
hand-render hook. That hook **does not fire under Vivecraft**, so in VR the gun is
drawn by the built-in item renderer entry point (`render` / `method_3166`), which
renders only the *static* model — hence "the gun shows but never animates".

ViveTaCZ mixes into that entry point: when VR is active and a gun is being drawn in a
hand context, it cancels the static path and instead calls `renderFirstPerson(...)` on
the PoseStack Vivecraft already positioned at the controller. The state machine ticks,
so animations play, and the model sits in your hand.

The Vivecraft link is made entirely by **reflection** (`com.vivetacz.vr.VivecraftBridge`)
so the mod loads and stays inert when Vivecraft is absent or in desktop mode — it never
affects normal (non-VR) TaCZ.

## Roadmap

- [x] Animated gun renders at the controller in VR (goal #1, reload-anim half of #2)
- [x] **Physical aiming** — bullets fire along the controller barrel (per-gun aim offset)
      instead of the camera look vector.
- [x] Recoil + recoil haptics.
- [x] Physical reload (grab magazine with off-hand, insert; optional two-stage drop).
- [x] Freeze TaCZ's idle/run sway in VR (the *static gun body* option).
- [ ] **Verify the render seam in-game** — the first VR gun render logs the exact
      Vivecraft `ModelTransformationMode`; confirm and tighten `isHandContext` to it.
- [ ] **Two-handed hold aiming** — aim main→off-hand for two-handed guns.
- [ ] **Scope zoom in VR** (goal #3) — TaCZ scopes are only an FOV multiplier
      (`CameraSetupEvent.applyScopeMagnification`), which Vivecraft ignores per-eye.
      Route through Vivecraft's telescope/zoom or a scope render target instead.
- [ ] Rack the bolt / charging handle as its own reload gesture.
- [ ] Multiplayer / dedicated-server aim (the aim override is singleplayer-only today).

## Build

```
./gradlew build
```

Output jar lands in `build/libs/`. No extra jars are needed to build: TaCZ and Vivecraft
are both referenced only by string/reflection, never as Gradle dependencies. Players must
install TaCZ Refabricated and Vivecraft themselves at runtime.

The build pins JDK 17 via `org.gradle.java.home` in `gradle.properties` (this machine's
default JDK is 25, which Loom can't run under) — adjust that path on other machines.

## Unofficial add-on

ViveTaCZ is an **independent, unofficial** add-on. It is **not affiliated with, endorsed
by, or supported by** the TaCZ (Timeless & Classics Zero) or Vivecraft projects. "TaCZ" and
"Vivecraft" belong to their respective authors; they are used here only to describe what
this mod works with.

Report issues with ViveTaCZ **to this project**, not to the TaCZ or Vivecraft teams.

## Licensing

ViveTaCZ is licensed under the **GNU General Public License v3.0 or later** (`GPL-3.0-or-later`);
see the full text in [`LICENSE`](LICENSE).

Why GPL: ViveTaCZ uses Mixins that weave into TaCZ's classes and reflection to drive it at
runtime, so at load time it forms a combined work with TaCZ (which is GPLv3). Licensing this
add-on under the same GPLv3 keeps that combination unambiguously compliant.

It **bundles no TaCZ or Vivecraft code or assets** — no jars, classes, gun models, or
textures. It references their public class/method names for Mixins and links to them at
runtime only. Players must install **TaCZ Refabricated** and **Vivecraft** themselves.

- **TaCZ Refabricated** — code GPLv3; the default gun **assets** (models/textures) are
  **CC BY-NC-ND 4.0**. This project redistributes none of those assets. Do not bundle or
  modify TaCZ's assets, and note the assets' non-commercial terms if you fork.
- **Vivecraft** — see the Vivecraft project for its terms.

You may redistribute and/or modify ViveTaCZ under the GPLv3. If you distribute a build,
you must also make the corresponding source available (this repository satisfies that) and
keep it under the GPLv3. Charging for it or using platform reward programs is permitted by
the GPL, but you cannot restrict others from redistributing it.

[TaCZ Refabricated]: https://modrinth.com/mod/tacz-refabricated
[Vivecraft]: https://www.vivecraft.org/
