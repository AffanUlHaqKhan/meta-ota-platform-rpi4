SUMMARY = "U-Boot environment access from Linux: fw_env.config and the boot partition mount"
DESCRIPTION = "U-Boot increments bootcount on every boot and switches to altbootcmd once it \
exceeds bootlimit. Nothing decrements it -- userspace has to, with `fw_setenv bootcount 0`, \
after it is satisfied the boot was healthy. That is the OTA agent's job, and it needs two \
things this recipe provides: a fw_env.config describing where the saved environment lives, \
and the vfat boot partition mounted so the environment file is reachable. \
Without them bootcount climbs monotonically and the board rolls back on the fourth boot."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://fw_env.config \
           file://var-rootdirs-mnt-bootpart.mount \
           "
S = "${WORKDIR}"

inherit allarch systemd

# fw_printenv/fw_setenv come from libubootenv's -bin package (it inherits
# lib_package, which splits the tools out of the library).
RDEPENDS:${PN} = "libubootenv-bin"

SYSTEMD_SERVICE:${PN} = "var-rootdirs-mnt-bootpart.mount"
SYSTEMD_AUTO_ENABLE = "enable"

do_install() {
    install -d ${D}${sysconfdir}
    install -m 0644 ${WORKDIR}/fw_env.config ${D}${sysconfdir}/fw_env.config

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/var-rootdirs-mnt-bootpart.mount ${D}${systemd_system_unitdir}/
}

FILES:${PN} = "${sysconfdir}/fw_env.config ${systemd_system_unitdir}"
