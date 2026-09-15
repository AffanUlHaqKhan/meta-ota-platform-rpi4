# Manual OTA update procedure

The update path as it actually works today, end to end, with no agent. Every step here is
something `ota-agent` will eventually do on its own; running it by hand first is what proves
the mechanism, and it stays the recovery procedure when the agent is broken or absent.

For *why* rollback works the way it does, see [rollback.md](rollback.md). This document is the
runbook.

## Bench addresses

Current values. Substitute your own.

| Role | Address | Notes |
|---|---|---|
| Build host (x86) | `192.168.100.34` | runs bitbake and serves the repo |
| Device (Pi 4) | `192.168.100.60` | SSH as `root`, no password |
| Repo HTTP port | `8090` | **not** 8080 — that was already taken on this machine |
| OSTree branch | `raspberrypi4-64-main` | `OSTREE_BRANCHNAME` in `conf/ota.conf` |
| OS name | `ota-platform` | `OSTREE_OSNAME` |

Keep the USB-TTL serial console attached throughout. SSH is for running commands; serial is the
only way to see U-Boot, and U-Boot is where a failed update shows itself.

---

## 1. Make the change

For application changes, the recipe pins an exact commit, so the commit must exist on the remote
**before** the build can fetch it:

```bash
cd HomeAutomationGUI
git commit -am "..."
git push origin main
git rev-parse HEAD
```

Then update `SRCREV` in `recipes-qt/homeautomationgui/homeautomationgui_git.bb`. `AUTOREV` is
deliberately not used: it resolves at parse time, breaks reproducibility, and non-reproducible
builds produce large OSTree deltas instead of small ones.

Layer-only changes (recipes, image contents, config) need no push.

> **Check the layer is a symlink, not a copy.** This has silently swallowed edits twice:
> ```bash
> ls -ld <build>/meta-ota-platform-rpi4      # must be a symlink to your working tree
> ```

## 2. Build

```bash
cd /media/affan/Data1/rpi4-ota-build
source poky/oe-init-build-env build
bitbake ota-image
```

The OSTree commit lands in:

```
build/tmp/deploy/images/raspberrypi4-64/ostree_repo
```

This repo is `archive-z2` mode, which is what makes it servable over plain HTTP — objects are
individually compressed files rather than a packfile. Nothing needs converting.

A useful pre-flight when you have changed a recipe, to catch typos before spending an hour:

```bash
bitbake-layers show-recipes <recipe>
bitbake -e ota-image | grep -E "^IMAGE_INSTALL="
```

## 3. Serve the repo

Check the port is actually yours to take before starting:

```bash
ss -lntp | grep :8090        # expect no output
```

```bash
cd build/tmp/deploy/images/raspberrypi4-64
python3 -m http.server 8090 --protocol HTTP/1.1 --directory ostree_repo --bind 0.0.0.0
```

`OSError: [Errno 98] Address already in use` means something already holds the port — another
copy of this server from an earlier session, or an unrelated container. Find it with the `ss`
command above and either stop it or pick a different port. Keep the server in a terminal you
own: its access log names the exact object a stalled pull died on.

Verify from the build host before touching the device — this takes seconds and saves confusing
failures on the Pi:

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://192.168.100.34:8090/config
curl -s http://192.168.100.34:8090/refs/heads/raspberrypi4-64-main
```

Expect `200` and a 64-character commit hash.

> **`HTTP 404` on the device means something else owns the port.** A `404` proves *a* server
> answered — if nothing were listening you would get a connection refusal. Check with
> `ss -lntp | grep :8090` and `docker ps`. A stray container publishing the same port will
> answer every OSTree request with 404 and look exactly like a broken repo.

## 4. Pull on the device

```bash
ssh root@192.168.100.60

ostree remote delete ota 2>/dev/null
ostree remote add --no-gpg-verify ota http://192.168.100.34:8090/
ostree pull ota raspberrypi4-64-main
```

`ostree remote delete` first: `remote add` will not silently replace an existing remote, and a
stale URL from a previous session is the most likely reason a pull goes to the wrong place.

Typical output for an application-only change:

```
13 metadata, 14 content objects fetched; 372 KiB transferred; 1.3 MB content written
```

372 KiB against a ~1 GB image is the entire point of content-addressed updates — only changed
objects move.

> `--no-gpg-verify` means **this device will install anything that machine serves.** Acceptable
> on a closed bench, unacceptable anywhere else. Closing it is DECISIONS D5.

## 5. Deploy, and inspect before rebooting

```bash
ostree admin deploy --os=ota-platform ota:raspberrypi4-64-main
```

Expect `Transaction complete; bootconfig swap: yes`. Nothing has changed for the running system
yet — the new deployment is written alongside the old one, which is what makes rollback possible.

Confirm what you are about to boot:

```bash
ostree admin status
```

```
  ota-platform bd81b82d….0 (pending)
* ota-platform b418ecd3….0
```

Inspect the actual contents of the update — this is the step that catches "I rebuilt the wrong
thing" before a reboot rather than after:

```bash
ostree --repo=/ostree/repo diff <old-commit> <new-commit>
```

And confirm rollback is wired, which requires the previous deployment to be recorded:

```bash
cat /boot/loader/uEnv.txt
```

`kernel_image2` / `bootargs2` **must** be present. On a first update they will be absent, because
there is no previous deployment to fall back to. If the kernel did not change, `kernel_image` and
`kernel_image2` point at the same file and only the `ostree=` karg differs (`…/0` vs `…/1`) —
that is correct, not a bug.

## 6. Arm the rollback counter

**This is the step that is easiest to forget and hardest to notice.**

```bash
fw_setenv upgrade_available 1
fw_setenv bootcount 0
```

U-Boot's `CONFIG_BOOTCOUNT_ENV` driver gates *everything* on `upgrade_available`:

```c
void bootcount_store(ulong a) {
    int upgrade_available = env_get_ulong("upgrade_available", 10, 0);
    if (upgrade_available) { env_set_ulong("bootcount", a); env_save(); }
}
```

With `upgrade_available` unset, `bootcount` is neither loaded nor stored. It will read `0` or `1`
forever, never reach `bootlimit`, and **no automatic rollback can ever occur**. This is
deliberate — a device you reboot normally four times should not roll itself back — but it means
an unarmed update has no safety net at all, and nothing warns you.

Requires `ota-bootenv` in the image, which supplies `fw_setenv`, `/etc/fw_env.config`, and the
mount of the vfat boot partition. Verify once:

```bash
fw_printenv bootcount bootlimit altbootcmd
```

If `altbootcmd` comes back matching `u-boot-otascript/files/uEnv.txt`, it is reading the real
saved environment rather than a built-in default.

## 7. Reboot and verify

```bash
reboot
```

Watch on serial. Then:

```bash
ostree admin status
```

```
* ota-platform bd81b82d….0            ← now active
  ota-platform b418ecd3….0 (rollback)  ← previous, kept
```

## 8. Confirm health and disarm

Once satisfied the deployment is good:

```bash
fw_setenv upgrade_available 0
fw_setenv bootcount 0
```

Leaving `upgrade_available=1` means the counter keeps climbing on every subsequent normal boot,
and the device rolls back for no reason on the fourth one.

This whole step is the agent's real job: boot, run a healthcheck (app alive, network up, server
reachable), and only then disarm.

---

## Testing rollback deliberately

```bash
fw_setenv upgrade_available 1     # without this, nothing happens
fw_setenv bootcount 3             # next boot makes 4, over bootlimit=3
reboot
```

U-Boot runs `altbootcmd` instead of `bootcmd` and boots the previous deployment. Confirm with
`ostree admin status`.

**Recovery — do not skip.** `altbootcmd` runs `bootcmd_set_rollback`, which sets `rollback=1`
*permanently* in the saved environment. Every later boot then takes the rollback path regardless
of deployment state:

```bash
fw_setenv rollback                # no value deletes the variable
fw_setenv bootcount 0
fw_setenv upgrade_available 0
fw_printenv rollback bootcount upgrade_available
reboot
```

---

## Things that will mislead you

**`/etc` and `/var` survive updates.** OSTree replaces `/usr` atomically, 3-way merges `/etc`,
and does not touch `/var` at all. A debugging hack left in either one persists across every
future update and can mask exactly the change you are testing. A font dropped in
`/var/lib/<app>/fonts` during bring-up made an image built *without* fonts render correctly,
which looked like the update had not applied.

The OSTree symlink farm, so you know what is really in `/var`:

```
/home -> var/rootdirs/home      /srv  -> var/rootdirs/srv
/mnt  -> var/rootdirs/mnt       /root -> var/roothome
/opt  -> var/rootdirs/opt       /usr/local -> ../var/usrlocal
```

**Anything the app needs must be in the commit, not in `/var`.** Content in `/var` is not
versioned with the deployment, does not roll back with it, and disappears if `/var` is ever
reset — the application degrades with no corresponding deployment change to explain it.

**The origin refspec changes.** A deployment pulled from remote `ota` shows
`origin refspec: ota:raspberrypi4-64-main`, while one from the flashed image shows
`ota-platform:…`. Cosmetic, but it is a quick way to tell which deployments arrived over the air.

---

## What the agent replaces

| Step | Today | With `ota-agent` |
|---|---|---|
| 3 | `python3 -m http.server` on a laptop | nginx on the Pi 5, always on |
| 4 | manual `ostree pull` | poll interval + jitter, resumable, static deltas |
| 5 | manual `ostree admin deploy` | applied per rollout policy |
| 6 | `fw_setenv upgrade_available 1` by hand | automatic, before every reboot |
| 8 | eyeballing it | healthcheck units, then disarm and report |
| — | nothing | signature verification (DECISIONS D5) |

Until then this procedure is the whole update mechanism, and it is worth being able to run it
from memory — it is also how you recover a device whose agent has shipped broken.
