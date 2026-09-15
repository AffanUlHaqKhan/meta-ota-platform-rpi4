SUMMARY = "HomeAuto — Qt 6 / QML home automation HMI"
DESCRIPTION = "Device HMI: lighting, watering and climate control pages, QML Canvas visuals, \
C++ QAbstractListModel backends. Installed as /usr/bin/appHomeAuto."
HOMEPAGE = "https://github.com/AffanUlHaqKhan/HomeAutomationGUI"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=ea4c82615fa45f1b037a425e28147b3d"

# Qt 6 ships Quick Controls inside qtdeclarative; no separate qtquickcontrols2 recipe.
# No qtsvg / qtcharts — icons and charts are drawn with QML Canvas.
DEPENDS = "qtbase qtdeclarative qtdeclarative-native"

SRC_URI = "git://github.com/AffanUlHaqKhan/HomeAutomationGUI.git;protocol=https;branch=main \
           file://homeautomationgui.service \
           file://vnc.conf \
           "

# Pin for releases. AUTOREV breaks reproducibility, which breaks small OSTree deltas.
SRCREV = "b0ed1335e5857eaeeef53f90b8ea6caa03e210bf"
PV = "1.0+git${SRCREV}"

S = "${WORKDIR}/git"

inherit qt6-cmake systemd

EXTRA_OECMAKE = "-DCMAKE_BUILD_TYPE=Release"

# Upstream target is 'appHomeAuto'; the recipe/package is named after the repo.
SYSTEMD_SERVICE:${PN} = "homeautomationgui.service"
SYSTEMD_AUTO_ENABLE = "enable"

do_install:append() {
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/homeautomationgui.service ${D}${systemd_system_unitdir}/

    # Graphics platform comes from the build config, not the app repo.
    sed -i -e 's|@QT_QPA_PLATFORM@|${QT_HMI_PLATFORM}|g' \
           ${D}${systemd_system_unitdir}/homeautomationgui.service

    # Optional systemd drop-in for viewing the UI over VNC during bring-up.
    # Shipped but NOT enabled -- copy it into
    # /etc/systemd/system/homeautomationgui.service.d/ to activate.
    install -d ${D}${datadir}/homeautomationgui
    install -m 0644 ${WORKDIR}/vnc.conf ${D}${datadir}/homeautomationgui/

    # Writable state lives in /var — /usr is read-only under OSTree.
    install -d ${D}${localstatedir}/lib/homeautomationgui
}

FILES:${PN} += " \
    ${systemd_system_unitdir} \
    ${datadir}/homeautomationgui \
    ${localstatedir}/lib/homeautomationgui \
    "

# QML is compiled into the executable's resources by qt_add_qml_module, so the
# binary is the only installed artifact. If that changes upstream, extend FILES.
