# meta-ota-platform-rpi4

Yocto/OpenEmbedded layer producing an **OSTree-managed** Raspberry Pi 4 image containing the
HomeAuto HMI (`HomeAutomationGUI`) and the OTA update client (`ota-agent`).

Sibling of `meta-ota-platform`, which targets the Raspberry Pi 5.

> **Never add both layers to one build.** They ship recipes with the same names — `ota-image`,
> `homeautomationgui`, `ota-agent`, `ota-network-config`, `ota-rootdirs`. bitbake would see
> duplicates. Use one build directory per board.

---

## Why this layer exists separately

The two boards differ in the one place that matters for OTA: the bootloader.

| | Pi 4 (this layer) | Pi 5 (`meta-ota-platform`) |
|---|---|---|
| u-boot upstream | **yes** (`bcm2711`) | none — no `bcm2712` target, still absent at u-boot v2025.07 |
| meta-updater class | `sota_raspberrypi.bbclass`, upstream | hand-written `sota_raspberrypi5.bbclass` |
| `OSTREE_BOOTLOADER` | `u-boot` | `none` |
| Kernel format | `fitImage` | raw arm64 `Image` |
| Boot-failure recovery | **bootcount/bootlimit — real** | nothing; userspace-only |
| Custom boot machinery | none | `ostree-boot-sync`, generated `cmdline.txt` |

So this layer is the *simpler* of the two. Everything the Pi 5 layer adds by hand — a class to
replace the bootloader integration, a script to sync the FAT partition after every deploy, a
build-time `cmdline.txt` generator — exists only because the Pi 5 has no u-boot. None of it is
here, because u-boot reads OSTree's boot loader entries directly.

**This is the board on which you can demonstrate rollback.** Ship a deliberately broken commit,
watch the device recover unattended, see the server record a `rollback`. That is not possible on
a Pi 5. See `DECISIONS.md` D3/D4 in the workspace root.

---

## Layer dependencies

Same as the Pi 5 layer: `poky`, `meta-openembedded` (oe, python, networking, **filesystems**),
`meta-qt6`, `meta-updater` (layer name `sota`), `meta-raspberrypi`. All pinned to `scarthgap` —
which is a ceiling, not a preference: `meta-updater` and `meta-raspberrypi` both declare
`LAYERSERIES_COMPAT = "nanbield scarthgap"`.

`meta-filesystems` is a hard dependency of `sota`, not optional.

---

## Setup

```bash
mkdir -p ~/rpi4-build && cd ~/rpi4-build     # separate from any Pi 5 build dir

git clone -b scarthgap https://git.yoctoproject.org/poky
git clone -b scarthgap https://git.openembedded.org/meta-openembedded
git clone -b 6.8       https://code.qt.io/yocto/meta-qt6.git
git clone -b scarthgap https://github.com/uptane/meta-updater
git clone -b scarthgap https://github.com/agherzan/meta-raspberrypi
ln -s /path/to/meta-ota-platform-rpi4 meta-ota-platform-rpi4   # symlink, do not copy

source poky/oe-init-build-env build

bitbake-layers add-layer \
  ../meta-openembedded/meta-oe \
  ../meta-openembedded/meta-python \
  ../meta-openembedded/meta-networking \
  ../meta-openembedded/meta-filesystems \
  ../meta-qt6 \
  ../meta-updater \
  ../meta-raspberrypi \
  ../meta-ota-platform-rpi4

cat ../meta-ota-platform-rpi4/conf/ota.conf >> conf/local.conf
# edit conf/local.conf: OSTREE_REMOTE_URL, SSTATE_DIR, DL_DIR

bitbake -p          # parse only -- catches config errors in ~1 min instead of hours
bitbake ota-image
```

Point `SSTATE_DIR` and `DL_DIR` at the **same** directories your Pi 5 build uses. Most of what
gets built is machine-independent, so the second board's first build is far cheaper than the
first board's was.

Verify the OSTree wiring before building:

```bash
bitbake -e ota-image | grep -E '^(IMAGE_FSTYPES|OSTREE_BOOTLOADER|WKS_FILE|SOTA_MACHINE)='
```

Expect `OSTREE_BOOTLOADER=u-boot`, `SOTA_MACHINE=raspberrypi`, `WKS_FILE=sdimage-sota.wks`,
and `ostreepush`/`garagesign`/`garagecheck` **absent** from `IMAGE_FSTYPES`.

---

## Configuration surface (`conf/ota.conf`)

| Variable | Meaning |
|---|---|
| `MACHINE` | `raspberrypi4-64` |
| `INHERIT += "sota"` | **required** — meta-updater's layer.conf does not inherit its own class |
| `OSTREE_BRANCHNAME` | `raspberrypi4-64-main`; renaming after shipping means migrating every device |
| `OSTREE_REMOTE_URL` | baked into the agent config; must be reachable **from the device** |
| `IMAGE_FSTYPES:remove` | strips the aktualizr/garage-sign image types |
| `KERNEL_DEVICETREE:…:sota` | drops meta-updater's dead `uart0-rpi4.dtbo` reference |
| `QT_HMI_PLATFORM` | substituted into the HMI unit's `QT_QPA_PLATFORM` |
| `BB_NUMBER_THREADS` / `PARALLEL_MAKE` | capped at 8 — do not remove, see below |

Notably **absent** compared to the Pi 5 layer: no `SOTA_MACHINE` override. meta-updater's
`SOTA_MACHINE:rpi ?= "raspberrypi"` is exactly what a Pi 4 wants.

---

## Rollback

Real, bootloader-level, and documented in [docs/rollback.md](docs/rollback.md) — which on this
board describes what actually happens rather than an aspiration.

Summary: u-boot counts boot attempts; the agent clears the counter only after its healthcheck
passes; a device that cannot pass falls back to the previous deployment on its own, including
when the kernel never reaches userspace.

Test it by shipping a commit whose HMI unit fails to start. Do this before trusting any of it.

---

## Common failures

| Symptom | Cause |
|---|---|
| `Layer 'sota' depends on layer 'filesystems-layer'` | add `meta-openembedded/meta-filesystems` |
| `No rule to make target '.../overlays/uart0-rpi4.dtbo'` | meta-updater names an overlay the RPi kernel no longer ships — overridden in `conf/ota.conf` |
| `No IMAGE_CMD defined for IMAGE_FSTYPES entry 'ostreepush'` on every image | `ostreepush` set globally; it only exists under `sota.bbclass` |
| `Could not inherit file classes/cmake_qt6.bbclass` | meta-qt6's class is `qt6-cmake` |
| `qtbase-native do_configure` fails with `QT_FEATURE_egl = "OFF"` | a `PACKAGECONFIG:append` reached the native recipe — use `:class-target` |
| HMI exits: `no Qt platform plugin could be initialized` | `QT_QPA_PLATFORM` names a plugin never built; check `/usr/lib/plugins/platforms/` |
| `User namespaces are not usable by BitBake` | Ubuntu 24.04 AppArmor restriction — see the Pi 5 layer's README |
| Duplicate recipe warnings for `ota-image` etc. | both OTA layers added to one build; remove one |
| `undefined symbol: …` on a symbol that clearly exists | OOM truncated object files to 0 bytes — `find <workdir> -name '*.o' -size 0`, then `cleansstate` |
