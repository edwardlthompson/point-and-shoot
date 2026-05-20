"""Grep UTF-8-ish strings from APK (zip) and .so inside for Camera2 needles."""
import re
import sys
import zipfile
from pathlib import Path

NEEDLES = [
    b"DngCreator",
    b"RAW_SENSOR",
    b"setPhysicalCameraId",
    b"LOGICAL_MULTI_CAMERA",
    b"LENS_SHADING",
    b"physicalCamera",
]


def scan_blob(data: bytes, label: str, out_lines: list) -> None:
    for n in NEEDLES:
        if n in data:
            out_lines.append(f"{label}: {n.decode('ascii', errors='ignore')}")


def main():
    if len(sys.argv) < 2:
        print("usage: apk_strings_grep.py file.apk [file2.apk ...]")
        sys.exit(2)
    for path in sys.argv[1:]:
        p = Path(path)
        print(f"=== {p.name} ===")
        lines: list[str] = []
        if p.suffix.lower() == ".apk":
            with zipfile.ZipFile(p, "r") as zf:
                for name in zf.namelist():
                    if name.endswith((".dex", ".so")):
                        try:
                            scan_blob(zf.read(name), name, lines)
                        except Exception:
                            pass
        else:
            scan_blob(p.read_bytes(), str(p), lines)
        if lines:
            for ln in sorted(set(lines)):
                print(ln)
        else:
            print("(no needles in dex/so)")


if __name__ == "__main__":
    main()
