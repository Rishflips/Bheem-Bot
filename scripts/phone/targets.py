"""Lists clickable or focusable elements of the screen in front that are smaller than 48 dp, and clickable ones with no text or description."""
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ADB = r"C:\Users\Gowrishankar\AppData\Local\Android\Sdk\platform-tools\adb.exe"


def sh(*a):
    return subprocess.run([ADB, *a], capture_output=True, text=True, encoding="utf-8").stdout


density = int(re.search(r"(\d+)", sh("shell", "wm", "density").split("Physical density:")[-1]).group(1))
sh("shell", "uiautomator", "dump", "/sdcard/ui.xml")
root = ET.fromstring(sh("shell", "cat", "/sdcard/ui.xml"))
small, unnamed, total = [], [], 0
for n in root.iter("node"):
    if n.get("package") != "app.bheem.bench" or n.get("clickable") != "true":
        continue
    total += 1
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
    w, h = (x2 - x1) * 160 / density, (y2 - y1) * 160 / density
    label = n.get("text") or n.get("content-desc") or ""
    if not label:
        unnamed.append(n.get("bounds"))
    if w < 48 or h < 48:
        small.append((label, round(w), round(h)))
print("density", density, "clickable", total)
print("smaller than 48dp:", small)
print("clickable without text or description (children may carry it):", unnamed)
