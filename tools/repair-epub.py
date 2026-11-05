#!/usr/bin/env python3
"""Repair EPUB files whose end-of-central-directory record points to the wrong place.

A damaged file has two EOCD records. The last one is broken: it names a central
directory offset that holds no central header. The earlier record is correct.
Cutting the file back to the earlier record produces a valid archive.

The tool only removes trailing bytes. It never rewrites content. It verifies the
result before it replaces the original.

Usage:
    repair-epub.py PATH [PATH ...] [--apply] [--quiet]

Exit codes:
    0  every file is valid or was repaired
    1  a file could not be repaired
    2  a path does not exist
"""

import argparse
import os
import struct
import sys
import tempfile
import zipfile

EOCD = b"PK\x05\x06"
CENTRAL = b"PK\x01\x02"


def eocd_offsets(data):
    """Offsets of every end-of-central-directory signature in the file."""
    offsets = []
    position = 0
    while True:
        position = data.find(EOCD, position)
        if position < 0:
            return offsets
        offsets.append(position)
        position += 1


def is_consistent(data, offset):
    """True when the EOCD points at a central directory that ends at the EOCD."""
    if len(data) < offset + 22:
        return False
    _, _, _, _, size, start, comment = struct.unpack("<HHHHIIH", data[offset + 4:offset + 22])
    return (
        data[start:start + 4] == CENTRAL
        and start + size == offset
        and offset + 22 + comment <= len(data)
    )


def opens_cleanly(data):
    """True when a ZIP reader can list and verify every entry."""
    with tempfile.NamedTemporaryFile(suffix=".epub") as handle:
        handle.write(data)
        handle.flush()
        try:
            with zipfile.ZipFile(handle.name) as archive:
                return archive.testzip() is None
        except (zipfile.BadZipFile, OSError):
            return False


def repair(path, apply):
    """Return (status, detail). Status is ok, repaired or failed."""
    try:
        with open(path, "rb") as handle:
            data = handle.read()
    except OSError as error:
        return "failed", f"unreadable: {error}"

    offsets = eocd_offsets(data)
    if not offsets:
        return "failed", "no EOCD record"

    if is_consistent(data, offsets[-1]):
        return "ok", ""

    valid = [offset for offset in offsets if is_consistent(data, offset)]
    if not valid:
        return "failed", f"{len(offsets)} EOCD record(s), none consistent"

    chosen = valid[-1]
    comment_length = struct.unpack("<H", data[chosen + 20:chosen + 22])[0]
    cut = chosen + 22 + comment_length
    removed = len(data) - cut
    if removed <= 0:
        return "failed", "inconsistent but nothing to remove"

    repaired = data[:cut]
    if not opens_cleanly(repaired):
        return "failed", "repaired bytes do not open"

    if apply:
        directory = os.path.dirname(os.path.abspath(path))
        descriptor, temporary = tempfile.mkstemp(dir=directory, suffix=".epub.tmp")
        try:
            with os.fdopen(descriptor, "wb") as handle:
                handle.write(repaired)
                handle.flush()
                os.fsync(handle.fileno())
            os.replace(temporary, path)
        except OSError as error:
            os.unlink(temporary)
            return "failed", f"write failed: {error}"

    return "repaired", f"removed {removed} bytes ({len(offsets)} EOCD records)"


def main():
    parser = argparse.ArgumentParser(description="Repair EPUB files with a broken trailing EOCD record.")
    parser.add_argument("paths", nargs="+", help="files or directories")
    parser.add_argument("--apply", action="store_true", help="write the repairs (default: report only)")
    parser.add_argument("--quiet", action="store_true", help="print failures and repairs only")
    arguments = parser.parse_args()

    targets = []
    for entry in arguments.paths:
        if os.path.isdir(entry):
            for directory, _, names in os.walk(entry):
                targets += [os.path.join(directory, name) for name in names if name.lower().endswith(".epub")]
        elif os.path.isfile(entry):
            targets.append(entry)
        else:
            print(f"missing: {entry}", file=sys.stderr)
            return 2

    counts = {"ok": 0, "repaired": 0, "failed": 0}
    failures = []
    for path in sorted(targets):
        status, detail = repair(path, arguments.apply)
        counts[status] += 1
        if status == "failed":
            failures.append(path)
            print(f"FAILED   {path}: {detail}")
        elif status == "repaired" and not arguments.quiet:
            verb = "REPAIRED" if arguments.apply else "WOULD REPAIR"
            print(f"{verb} {path}: {detail}")
        elif status == "ok" and not arguments.quiet:
            print(f"ok       {path}")

    print()
    print(f"{counts['ok']} ok, {counts['repaired']} {'repaired' if arguments.apply else 'repairable'}, {counts['failed']} failed")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
