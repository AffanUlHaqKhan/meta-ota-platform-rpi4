SUMMARY = "OSTree-updatable Raspberry Pi 4 image with the HomeAuto HMI and OTA agent"
LICENSE = "MIT"

require recipes-core/images/core-image-minimal.bb

inherit core-image

IMAGE_FEATURES += "ssh-server-openssh"

# ota-agent is gated: its repo has no source yet, so a first bring-up image is
# built without it to prove the OSTree pipeline end to end. Set
# OTA_AGENT_ENABLED = "1" in local.conf once the repo is pushed and its SRCREV
# pinned. Without the agent the device can be updated manually with
# `ostree admin deploy`, which is exactly what step 3 of the bring-up verifies.
OTA_AGENT_ENABLED ?= "0"

# kernel-modules is NOT optional here, despite meta-raspberrypi listing it in
# MACHINE_EXTRA_RRECOMMENDS: that variable is consumed by packagegroup-base,
# which belongs to core-image-base. This image derives from core-image-minimal
# (packagegroup-core-boot), which honours only MACHINE_ESSENTIAL_*, so the Pi's
# modules were silently absent -- 14 of them instead of hundreds. Without vc4
# there is no DRM device and the HMI aborts on startup with
#     Could not find DRM device!
# This costs image size, which is update bandwidth: trimming to
# kernel-module-vc4 + kernel-module-v3d and their dependencies is a worthwhile
# follow-up once the display is proven.
IMAGE_INSTALL:append = " \
    kernel-modules \
    homeautomationgui \
    ${@bb.utils.contains('OTA_AGENT_ENABLED', '1', 'ota-agent', '', d)} \
    ostree \
    ota-network-config \
    ota-rootdirs \
    ca-certificates \
    openssl \
    util-linux \
    e2fsprogs \
    "

# Qt 6 runtime for the HMI. HomeAuto links Quick, Gui, Qml and QuickControls2 —
# in Qt 6 all four live in qtbase + qtdeclarative. It draws its icons and charts
# with QML Canvas, so no qtsvg and no qtcharts.
#
# Fonts are not pulled in by qtbase: it RDEPENDS on the fontconfig *library*
# through shlibs, but nothing depends on font files, so core-image-minimal has
# none at all (no /usr/share/fonts). The HMI still lays out and renders
# correctly -- every glyph is just drawn as an empty box. DejaVu Sans covers
# Latin/Greek/Cyrillic and is what the empty Theme.fontFamily resolves to.
IMAGE_INSTALL:append = " \
    qtbase \
    qtdeclarative \
    ttf-dejavu-sans \
    ttf-dejavu-sans-mono \
    "

# The OTA partition is an ext4 image sized at build time by oe_mkext234fs:
# content x IMAGE_OVERHEAD_FACTOR + IMAGE_ROOTFS_EXTRA_SPACE, floored at
# IMAGE_ROOTFS_SIZE. Nothing grows it on first boot, so this number IS the
# device's usable space regardless of the card size.
#
# Left to content sizing it came out at 928 MB, which is too tight: an update
# deploys the new commit alongside the old one (that is what makes rollback
# possible), and /var grows with logs and app state. Running out of space
# mid-deploy is the failure you least want.
#
# 4 GB floor, in KiB. Costs almost nothing to flash -- the free space is zeros,
# which gzip collapses and bmaptool skips entirely. Needs a card of at least
# ~4.2 GB. Growing to fill the card on first boot is the better long-term fix.
IMAGE_ROOTFS_SIZE = "4194304"
IMAGE_ROOTFS_EXTRA_SPACE = "524288"
