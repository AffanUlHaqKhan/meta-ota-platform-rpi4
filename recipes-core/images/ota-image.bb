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

IMAGE_INSTALL:append = " \
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
IMAGE_INSTALL:append = " \
    qtbase \
    qtdeclarative \
    "

IMAGE_ROOTFS_EXTRA_SPACE = "524288"
