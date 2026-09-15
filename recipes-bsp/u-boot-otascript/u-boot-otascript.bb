SUMMARY = "U-Boot boot script for launching OSTree deployments"
DESCRIPTION = "meta-updater expects an OSTree-aware boot.scr in the boot partition, but \
ships none -- it lives in meta-updater-raspberrypi, which only supports a newer Yocto \
release than this layer targets. Vendored here and adapted to scarthgap."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

DEPENDS = "u-boot-mkimage-native"
COMPATIBLE_MACHINE = "^rpi$"

SRC_URI = " \
    file://boot.scr \
    file://uEnv.txt \
    "

# Upstream uses ${UNPACKDIR}, which does not exist before styhead.
S = "${WORKDIR}"
B = "${WORKDIR}/build"

inherit deploy

do_configure[noexec] = "1"

do_compile() {
    # U-Boot needs the script wrapped in a mkimage header to run it.
    mkimage -A arm64 -O linux -T script -C none -a 0 -e 0 \
            -n "OSTree boot script" -d ${S}/boot.scr ${B}/boot.scr
}

do_install[noexec] = "1"

do_deploy() {
    install -d ${DEPLOYDIR}
    install -m 0644 ${B}/boot.scr ${DEPLOYDIR}/boot.scr
    # Plain text, read by boot.scr with `env import`; must NOT be mkimage-wrapped.
    install -m 0644 ${S}/uEnv.txt ${DEPLOYDIR}/uEnv.txt
}
addtask deploy after do_compile before do_build

do_compile[dirs] = "${B}"

PACKAGE_ARCH = "${MACHINE_ARCH}"

# Replaces meta-raspberrypi's rpi-u-boot-scr, which boots a fitImage straight
# from the vfat partition and knows nothing about OSTree deployments.
PROVIDES += "u-boot-default-script"
RPROVIDES:${PN} += "u-boot-default-script"
