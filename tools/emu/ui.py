#!/usr/bin/env python3
"""Drive the emulator for manual walkthroughs: tap by visible text or description, screenshot.

    python3 tools/emu/ui.py tap "Try the example trip"     # first node whose text/desc contains it
    python3 tools/emu/ui.py longdrag "Hold and drag to reorder Feirinha" 0 600
    python3 tools/emu/ui.py shot out.png
    python3 tools/emu/ui.py texts                           # list visible texts
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ADB = "/opt/homebrew/share/android-commandlinetools/platform-tools/adb"


def adb(*args, out=False):
    r = subprocess.run([ADB, *args], capture_output=True)
    return r.stdout if out else None


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb("exec-out", "cat", "/sdcard/ui.xml", out=True).decode("utf-8", "replace")
    root = ET.fromstring(xml[xml.index("<"):])
    for n in root.iter("node"):
        b = re.findall(r"\d+", n.get("bounds", ""))
        if len(b) == 4:
            x1, y1, x2, y2 = map(int, b)
            yield n.get("text", ""), n.get("content-desc", ""), ((x1 + x2) // 2, (y1 + y2) // 2), n.get("clickable") == "true"


def find(label, index=0):
    """A label starting with '=' matches the whole text exactly; otherwise any substring."""
    exact = label.startswith("=")
    want = label[1:] if exact else label
    hits = [c for t, d, c, _ in nodes() if (t == want or d == want) if exact] if exact else \
        [c for t, d, c, _ in nodes() if want in t or want in d]
    if len(hits) <= index:
        sys.exit(f"not found: {label!r}")
    return hits[index]


def main():
    cmd, *rest = sys.argv[1:]
    if cmd == "tap":
        x, y = find(rest[0], int(rest[1]) if len(rest) > 1 else 0)
        adb("shell", "input", "tap", str(x), str(y))
        time.sleep(1.2)
    elif cmd == "longdrag":
        # Long-press then move: `input draganddrop` holds before moving, which the long-press handle needs.
        x, y = find(rest[0])
        adb("shell", "input", "draganddrop", str(x), str(y), str(x + int(rest[1])), str(y + int(rest[2])), rest[3] if len(rest) > 3 else "2500")
        time.sleep(1.5)
    elif cmd == "texts":
        for t, d, c, _ in nodes():
            if t or d:
                print(c, t or "", f"[{d}]" if d else "")
    elif cmd == "shot":
        open(rest[0], "wb").write(adb("exec-out", "screencap", "-p", out=True))
    elif cmd == "scroll":
        adb("shell", "input", "swipe", "540", rest[0] if rest else "1700", "540", rest[1] if len(rest) > 1 else "600", "400")
        time.sleep(0.8)
    elif cmd == "back":
        adb("shell", "input", "keyevent", "4")
        time.sleep(1)


if __name__ == "__main__":
    main()
