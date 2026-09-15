# meta-raspberrypi's linux-raspberrypi.inc hard-assigns
#     UBOOT_LOADADDRESS = "0x00008000"
# which is right for 32-bit Pis, where the entry point is 0x8000 too. On arm64
# sota_raspberrypi.bbclass moves UBOOT_ENTRYPOINT to 0x00080000 and nothing
# moves the load address with it, so the FIT image ends up with
#     Load Address: 0x00008000
#     Entry Point:  0x00080000
# bootm then unpacks the kernel at 0x8000, jumps to 0x80000 and lands on zeros:
#     Starting kernel ...
#     "Synchronous Abort" handler, esr 0x02000000 ... Code: ... (00000000)
#
# Invisible on meta-raspberrypi's normal booti path, which uses neither value.
# A recipe-level "=" cannot be overridden from local.conf, hence this bbappend.
UBOOT_LOADADDRESS = "${UBOOT_ENTRYPOINT}"
