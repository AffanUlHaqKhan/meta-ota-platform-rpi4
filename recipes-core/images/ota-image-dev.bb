SUMMARY = "Development variant: debug tools, no read-only rootfs"
LICENSE = "MIT"

require ota-image.bb

IMAGE_FEATURES += "debug-tweaks tools-debug tools-profile"

IMAGE_INSTALL:append = " \
    gdbserver \
    strace \
    curl \
    vim \
    "

# The HMI recipe ships /usr/share/homeautomationgui/vnc.conf. Enable it on a
# running dev board to view the UI from a laptop with no monitor attached:
#   mkdir -p /etc/systemd/system/homeautomationgui.service.d
#   cp /usr/share/homeautomationgui/vnc.conf \
#      /etc/systemd/system/homeautomationgui.service.d/
#   systemctl daemon-reload && systemctl restart homeautomationgui
