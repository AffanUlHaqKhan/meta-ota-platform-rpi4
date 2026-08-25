# meta-qt6's default PACKAGECONFIG has no eglfs, so no libqeglfs.so is built and
# QT_QPA_PLATFORM=eglfs fails at startup with "no Qt platform plugin could be
# initialized". eglfs over KMS/GBM is the right embedded path on a Pi 5
# (vc4 KMS, no X server, no Wayland compositor).
#
# :class-target is required. Without it the append also hits qtbase-native,
# which has no EGL, and configure dies with:
#   QT_FEATURE_egl = "OFF" ... Configuring incomplete, errors occurred!
#
# kms pulls in drm + virtual/egl, gbm pulls in virtual/libgbm; both come from
# mesa, which is present because DISTRO_FEATURES includes opengl.
#
# Not listed here: vnc. There is no PACKAGECONFIG[vnc] -- Qt's VNC platform
# plugin is built unconditionally, and libqvnc.so was already in the image.
PACKAGECONFIG:append:class-target = " eglfs kms gbm"
