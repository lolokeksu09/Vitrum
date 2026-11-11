#!/usr/bin/env python3
"""Verify that R8 did not remove WorkManager's reflection-required Room constructor."""
import re
import struct
import sys
import zipfile
from pathlib import Path

TARGET = "Landroidx/work/impl/WorkDatabase_Impl;"


def u32(data, offset):
    return struct.unpack_from("<I", data, offset)[0]


def uleb(data, offset):
    value = shift = 0
    while True:
        byte = data[offset]
        offset += 1
        value |= (byte & 127) << shift
        if byte < 128:
            return value, offset
        shift += 7
        if shift > 35:
            raise ValueError("Invalid DEX ULEB128")


def constructor_present(data):
    if not data.startswith(b"dex\n"):
        raise ValueError("Invalid DEX header")
    nstr, ostr = struct.unpack_from("<II", data, 0x38)
    ntypes, otypes = struct.unpack_from("<II", data, 0x40)
    nprotos, oprotos = struct.unpack_from("<II", data, 0x48)
    nmethods, omethods = struct.unpack_from("<II", data, 0x58)
    nclasses, oclasses = struct.unpack_from("<II", data, 0x60)
    strings = []
    for i in range(nstr):
        offset = u32(data, ostr + i * 4)
        _, offset = uleb(data, offset)
        end = data.index(0, offset)
        strings.append(data[offset:end].decode("utf-8", "replace"))
    types = [strings[u32(data, otypes + i * 4)] for i in range(ntypes)]
    if TARGET not in types:
        return None
    target_type = types.index(TARGET)
    for i in range(nclasses):
        definition = struct.unpack_from("<8I", data, oclasses + i * 32)
        if definition[0] != target_type:
            continue
        offset = definition[6]
        if not offset:
            return False
        nstatic, offset = uleb(data, offset)
        ninstance, offset = uleb(data, offset)
        ndirect, offset = uleb(data, offset)
        _, offset = uleb(data, offset)
        for _ in range(nstatic + ninstance):
            _, offset = uleb(data, offset)
            _, offset = uleb(data, offset)
        method_id = 0
        for _ in range(ndirect):
            delta, offset = uleb(data, offset)
            method_id += delta
            _, offset = uleb(data, offset)
            code_offset, offset = uleb(data, offset)
            if method_id >= nmethods:
                raise ValueError("DEX method index out of bounds")
            owner, proto, name = struct.unpack_from("<HHI", data, omethods + method_id * 8)
            if owner != target_type or strings[name] != "<init>" or not code_offset:
                continue
            if proto >= nprotos:
                raise ValueError("DEX prototype index out of bounds")
            parameters = u32(data, oprotos + proto * 12 + 8)
            if not parameters or u32(data, parameters) == 0:
                return True
        return False
    return None


def main():
    if len(sys.argv) != 2:
        sys.exit("Usage: check-workdatabase-r8.py path/to/release.apk")
    apk_path = Path(sys.argv[1])
    if not apk_path.is_file():
        sys.exit(f"Release APK not found: {apk_path}")
    try:
        with zipfile.ZipFile(apk_path) as archive:
            for name in archive.namelist():
                if not re.fullmatch(r"classes\\d*\\.dex", name):
                    continue
                result = constructor_present(archive.read(name))
                if result is None:
                    continue
                if not result:
                    sys.exit("FAIL: R8 stripped androidx.work.impl.WorkDatabase_Impl.<init>()")
                print("PASS: WorkDatabase_Impl no-argument constructor is present")
                return
    except (ValueError, OSError, zipfile.BadZipFile, struct.error) as exc:
        sys.exit(f"FAIL: Could not inspect release DEX: {exc}")
    sys.exit("FAIL: WorkDatabase_Impl definition is missing from release APK")


if __name__ == "__main__":
    main()
