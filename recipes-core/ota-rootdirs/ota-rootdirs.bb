SUMMARY = "Recreate /var-backed directories that OSTree does not carry in the commit"
DESCRIPTION = "OSTree commits contain /usr; /var is per-deployment and starts empty, so \
anything the image build wrote there is silently dropped. This restores what the running \
system expects via systemd-tmpfiles."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://ota-rootdirs.conf"
S = "${WORKDIR}"

inherit allarch systemd
RDEPENDS:${PN} = "systemd"

do_install() {
    install -d ${D}${nonarch_libdir}/tmpfiles.d
    install -m 0644 ${WORKDIR}/ota-rootdirs.conf ${D}${nonarch_libdir}/tmpfiles.d/
}

FILES:${PN} = "${nonarch_libdir}/tmpfiles.d"
