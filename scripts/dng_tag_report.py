"""Emit DNG tag summary for forensics (FM, ASN, dimensions). Used by ProShot vs P&S diff."""
import struct
import sys


def read_tags(path):
    with open(path, "rb") as f:
        data = f.read()
    ifd0 = struct.unpack_from("<I", data, 4)[0]
    n = struct.unpack_from("<H", data, ifd0)[0]
    pos = ifd0 + 2
    asn = None
    fm00 = None
    width = None
    height = None
    for _ in range(n):
        tag = struct.unpack_from("<H", data, pos)[0]
        cnt = struct.unpack_from("<I", data, pos + 4)[0]
        off = struct.unpack_from("<I", data, pos + 8)[0]
        if tag == 50728 and cnt >= 3:
            asn = [
                float(struct.unpack_from("<I", data, off + i * 8)[0])
                / max(float(struct.unpack_from("<I", data, off + i * 8 + 4)[0]), 1)
                for i in range(3)
            ]
        if tag == 50964:
            n0 = struct.unpack_from("<i", data, off)[0]
            d0 = struct.unpack_from("<i", data, off + 4)[0]
            fm00 = n0 / max(abs(d0), 1)
        if tag == 256:
            width = struct.unpack_from("<H", data, off)[0] if cnt == 1 else None
        if tag == 257:
            height = struct.unpack_from("<H", data, off)[0] if cnt == 1 else None
        pos += 12
    return {
        "path": path,
        "fm00": fm00,
        "asn_r": (1.0 / asn[0]) if asn else None,
        "asn_b": (1.0 / asn[2]) if asn else None,
        "width": width,
        "height": height,
        "bytes": len(data),
    }


def main():
    paths = sys.argv[1:]
    if not paths:
        print("usage: dng_tag_report.py file1.dng [file2.dng ...]")
        sys.exit(2)
    for p in paths:
        t = read_tags(p)
        print("--- %s ---" % p)
        print("  size_mb=%.1f" % (t["bytes"] / (1024 * 1024)))
        print("  WxH=%s x %s" % (t["width"], t["height"]))
        print("  FM1[0,0]=%s" % ("%.4f" % t["fm00"] if t["fm00"] is not None else "?"))
        if t["asn_r"] is not None:
            print("  ASN_WB R=%.3f B=%.3f" % (t["asn_r"], t["asn_b"]))


if __name__ == "__main__":
    main()
