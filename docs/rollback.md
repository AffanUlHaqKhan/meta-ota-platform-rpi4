# Rollback design

> **This board can actually do it.** The Pi 4 has upstream u-boot, so everything below is the
> live design rather than a target. The Pi 5 variant of this platform cannot — it has no
> bootloader that counts failed boots, and falls back to userspace-only rejection. That is the
> main reason the two boards have separate layers. See `DECISIONS.md` D4.

OSTree gives atomic deploy + a previous deployment kept on disk. It does **not** decide when
to roll back. That is bootloader + agent.

## u-boot boards

`meta-updater` ships `u-boot-ostree` integration using two env vars:

| Var | Meaning |
|---|---|
| `bootcount` | incremented by u-boot each boot attempt |
| `bootlimit` | when `bootcount > bootlimit`, u-boot boots the *previous* deployment |
| `upgrade_available` | set to 1 by the agent right before rebooting into a new deploy |

Sequence:

1. Agent: `ostree admin deploy <commit>` → `fw_setenv upgrade_available 1` → `reboot`.
2. u-boot boots new deployment, bootcount++.
3. Agent starts, runs healthcheck (app alive, network up, server reachable).
4. Pass → `fw_setenv bootcount 0; fw_setenv upgrade_available 0`, report success.
5. Fail / no confirm before watchdog → reboot → u-boot sees limit exceeded → boots old deployment.
6. Agent on old deployment reports `rollback` to the server; server marks the target bad and
   halts the rollout.

## EFI/grub boards

`ostree admin status` ordering plus `ostree admin undeploy 0` on healthcheck failure, driven
by a systemd `OnFailure=` unit and a hardware watchdog. Weaker than u-boot bootcount — a
kernel that fails to boot at all cannot self-recover without bootloader help.

## Non-negotiable

- Hardware watchdog enabled, kicked only after healthcheck passes.
- `/var` and `/etc` persist across deployments (OSTree 3-way-merges `/etc`). App state must
  live in `/var`, never in `/usr`.
- Test rollback in CI on real hardware before every release.
