import os
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(__file__))
import ui

adb = ui.adb


def sh(*a):
    return subprocess.run([adb, *a], capture_output=True, text=True).stdout


def say(text, wait_for=None, timeout=90):
    ui.tap("Tell Bheem what you need", exact=False)
    time.sleep(0.5)
    sh("shell", "input", "text", text.replace(" ", "%s"))
    ui.tap("Send")
    end = time.time() + timeout
    while time.time() < end:
        time.sleep(3)
        t = ui.texts()
        if wait_for and any(wait_for in x for x in t):
            return t
    return ui.texts()


def tap_first(label):
    for t, x, y in ui.nodes():
        if t == label:
            sh("shell", "input", "tap", str(x), str(y))
            return True
    return False


def alarms():
    out = sh("shell", "dumpsys", "alarm")
    lines = [l.strip() for l in out.splitlines() if "app.bheem.bench" in l and ("ReminderReceiver" in l or "bheem-reminder" in l or "RTC" in l)]
    return lines


def bheem_alarm_block():
    out = sh("shell", "dumpsys", "alarm")
    keep = []
    lines = out.splitlines()
    for i, l in enumerate(lines):
        if "app.bheem.bench" in l and ("RTC" in l):
            keep.append(l.strip())
    return keep


def notifications():
    out = sh("shell", "dumpsys", "notification", "--noredact")
    res = []
    for blk in out.split("NotificationRecord")[1:]:
        if "pkg=app.bheem.bench" in blk and "bheem-model" not in blk:
            title = ""
            text = ""
            for l in blk.splitlines():
                if "android.title=" in l:
                    title = l.split("android.title=", 1)[1].strip()
                if "android.text=" in l:
                    text = l.split("android.text=", 1)[1].strip()
            res.append((title, text))
    return res


if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "say":
        print(say(sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None))
    elif cmd == "ok":
        print(tap_first("OK"))
    elif cmd == "alarms":
        for l in bheem_alarm_block():
            print(l)
    elif cmd == "notes":
        for n in notifications():
            print(n)
    elif cmd == "texts":
        print(ui.texts())
    elif cmd == "tap":
        print(tap_first(sys.argv[2]))
