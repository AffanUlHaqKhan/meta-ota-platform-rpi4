SUMMARY = "Default wired network configuration for OTA devices"
DESCRIPTION = "systemd-networkd is enabled in poky but ships no active .network file, \
so an image built from core-image-minimal comes up with no address. A device with no \
network cannot be updated, so this is required, not cosmetic."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://20-wired.network"
S = "${WORKDIR}"

inherit allarch systemd

RDEPENDS:${PN} = "systemd"

do_install() {
    # /usr/lib, not /etc: shipped defaults belong in the read-only commit, which
    # keeps OSTree's 3-way merge of /etc free for genuine local overrides.
    install -d ${D}${nonarch_libdir}/systemd/network
    install -m 0644 ${WORKDIR}/20-wired.network ${D}${nonarch_libdir}/systemd/network/
}

FILES:${PN} = "${nonarch_libdir}/systemd/network"
