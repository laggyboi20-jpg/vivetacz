# ViveTaCZ

VR support for **[TaCZ Refabricated]** (Timeless & Classics Zero, Fabric port) via
**[Vivecraft]**, for Minecraft **1.20.1 / Fabric**.

## Goals

1. **Gun models visible & animated in VR** — draw TaCZ's animated first-person gun
   attached to your controller (Vivecraft normally only shows the static model).
2. **Reload animations play in VR** — button-triggered for now; the physical/gesture
   reload is a planned follow-up.
3. **Scopes actually zoom** — planned; see *Roadmap*.

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
- [ ] **Verify the render seam in-game** — the first VR gun render logs the exact
      Vivecraft `ModelTransformationMode`; confirm and tighten `isHandContext` to it.
- [ ] Strip TaCZ's camera-relative view-sway when in VR (it double-applies over the
      controller pose; see `renderFirstPerson` lines that read player pitch/yaw).
- [ ] **Physical aiming** — fire along the gun barrel (main-hand, or main→off-hand for
      two-handed hold) instead of the camera look vector (`LocalPlayerShoot.shoot`).
- [ ] **Scope zoom in VR** (goal #3) — TaCZ scopes are only an FOV multiplier
      (`CameraSetupEvent.applyScopeMagnification`), which Vivecraft ignores per-eye.
      Route through Vivecraft's telescope/zoom or a scope render target instead.
- [ ] Recoil haptics via `VivecraftBridge` / `triggerHapticPulse`.
- [ ] Full physical reload (grab magazine with off-hand, insert, rack bolt).

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
