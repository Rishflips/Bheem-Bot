"""Runs the scripted cases of docs/STAGE11-CASE-LIST.md on the connected phone and keeps logcat for each.

usage: python scripts/phone/cases.py C01 C02 ...   (or "all" for every scripted case)
The Bheem offline debug build must be installed. Results go to eval/results/stage11-cases.json and are printed.
"""
import json
import os
import re
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(__file__))
import ui

CHILD_GUARD = "BHEEM_CASES_CHILD"

PKG = "app.bheem.bench"
MAIN = f"{PKG}/app.bheem.app.MainActivity"
BAR = "Ask or tell Bheem"
CRASH = re.compile(r"FATAL EXCEPTION|ANR in|Process " + re.escape(PKG) + r" \(pid \d+\) has died|signal \d+ \(SIG")
OUT = os.path.join(os.path.dirname(__file__), "..", "..", "eval", "results", "stage11-cases.json")


def sh(*a, timeout=120):
    return subprocess.run([ui.adb, *a], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout).stdout


def texts():
    return ui.texts()


def wait_for(pred, timeout=60, step=2):
    end = time.time() + timeout
    while time.time() < end:
        t = texts()
        if pred(t):
            return t
        time.sleep(step)
    return texts()


def has(t, needle):
    return any(needle in x for x in t)


def talk_xy():
    m = re.search(r'content-desc="Hold to talk"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', ui.dump())
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def hold_talk(ms):
    xy = talk_xy()
    if xy is None:
        return False
    sh("shell", "input", "swipe", str(xy[0]), str(xy[1]), str(xy[0]), str(xy[1]), str(ms))
    return True


def tap_anywhere(label):
    """Taps a control on any screen, including a system dialog."""
    sh("shell", "uiautomator", "dump", "/sdcard/any.xml")
    x = sh("exec-out", "cat", "/sdcard/any.xml")
    m = re.search(r'text="' + re.escape(label) + r'"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
    if not m:
        return False
    x1, y1, x2, y2 = map(int, m.groups())
    sh("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    return True


def stop_app():
    sh("shell", "am", "force-stop", PKG)
    time.sleep(1)


def start_app():
    sh("shell", "am", "start", "-n", MAIN)
    wait_for(lambda t: BAR in t, 40)


def alarm_count():
    out = sh("shell", "dumpsys", "alarm")
    return len(re.findall(r"(?:RTC_WAKEUP|RTC) #\d+: Alarm\{\w+ type \d+ origWhen \d+ [^\n]*?" + re.escape(PKG) + r"\}", out))


VERBS = ("Set reminder", "Add task", "Save note", "Save expense", "Add to calendar", "Open WhatsApp", "Open SMS", "Open dialer", "Open maps", "Confirm and open UPI app")


def n_cards(t):
    """How many cards wait for a tap: the screen texts that are exactly a verb button."""
    return sum(1 for x in t if x in VERBS)


def card_in(t):
    return n_cards(t) > 0


def approve():
    """Tap the first verb button on the screen, like the old OK."""
    for label, x, y in ui.nodes():
        if label in VERBS:
            sh("shell", "input", "tap", str(x), str(y))
            return True
    return False


def clear_cards():
    for _ in range(25):
        if not (ui.tap("Dismiss") or ui.tap("Undo")):
            break
        time.sleep(1)


def type_command(text):
    ui.tap(BAR, exact=False)
    time.sleep(0.6)
    sh("shell", "input", "text", text.replace(" ", "%s"))
    time.sleep(0.4)
    ui.tap("Send")


def card_ready(t):
    return card_in(t)


def banner(t):
    """The newest answer or notice: the text after the last "Bheem" label of the conversation."""
    if "Bheem" not in t:
        return None
    index = len(t) - 1 - t[::-1].index("Bheem")
    return t[index + 1] if index + 1 < len(t) else None


def say_and_wait(text, timeout=150):
    type_command(text)
    return wait_for(lambda t: card_in(t) or "Bheem" in t, timeout)


NOISE = ("Today", "Settings", "Notifications for Bheem are off", "Turn on notifications", "Reminders may arrive late", "Allow exact reminders", "Exact alarms are off", "Open battery settings", "Not now")


def screen_text():
    return " | ".join(x for x in texts() if not x.startswith(NOISE))


# Each case returns (passed, note).
def c01():
    clear_cards()
    base = alarm_count()
    t = say_and_wait("remind me to call Arun tomorrow at 5 pm", 150)
    if not card_in(t):
        return False, f"no card: {t[:8]}"
    if alarm_count() != base:
        return False, "alarm scheduled before the tap"
    approve()
    time.sleep(3)
    ok = alarm_count() == base + 1
    return ok, f"alarms {base} -> {alarm_count()}; card text: {[x for x in t if 'Arun' in x][:1]}"


def c02():
    clear_cards()
    t = say_and_wait("remind me in 30 minutes to check the oven", 150)
    return card_in(t) and has(t, "oven"), screen_text()[:200]


def simple_card(command, must):
    def run():
        clear_cards()
        t = say_and_wait(command, 150)
        return card_in(t) and has(t, must), screen_text()[:200]

    return run


def c07():
    clear_cards()
    t = say_and_wait("what did I note about the wifi", 150)
    message = banner(t)
    return message is not None and not card_in(t), f"banner: {message}"


def c08():
    clear_cards()
    type_command("hello there")
    t = wait_for(lambda t: "Bheem" in t, 150)
    message = banner(t)
    return message is not None and "help" in message and not card_in(t), f"banner: {message}"


def c09():
    clear_cards()
    t = say_and_wait("pay Arun 500 rupees", 150)
    message = banner(t)
    return message is not None and "payments come later" in message and not card_in(t), f"banner: {message}"


def c10():
    clear_cards()
    type_command(" ")
    time.sleep(2)
    t1 = texts()
    type_command(" ".join(["word"] * 400))
    t2 = wait_for(lambda t: card_in(t) or has(t, "long") or has(t, "could"), 90)
    alive = bool(sh("shell", "pidof", PKG).strip())
    return alive and not card_in(t1), f"alive={alive}; after long input: {' | '.join(t2)[:160]}"


def c11():
    clear_cards()
    stop_app()
    start_app()
    began = time.time()
    type_command("please remind me to renew the insurance next Monday at 10 am")
    t = wait_for(lambda t: card_in(t), 180)
    return card_in(t), f"card after {round(time.time() - began)} s"


def c12():
    clear_cards()
    began = time.time()
    type_command("please remind me to pay the electricity bill on Friday at 6 pm")
    t = wait_for(lambda t: card_in(t), 60)
    took = round(time.time() - began)
    return card_in(t) and took < 40, f"card after {took} s (includes UI polling)"


def c16():
    clear_cards()
    stop_app()
    start_app()
    type_command("please remind me to book the tickets on Saturday at 9 am")
    time.sleep(6)
    stop_app()
    start_app()
    t = wait_for(lambda t: BAR in t, 40)
    type_command("remind me tomorrow at 9 am to call Ravi")
    t = wait_for(lambda t: card_in(t), 180)
    return BAR in t and card_in(t), "restarted cleanly and answered after a kill mid-load"


def c15():
    clear_cards()
    stop_app()
    start_app()
    type_command("please remind me to water the plants on Sunday at 7 am")
    time.sleep(5)
    sh("shell", "input", "keyevent", "3")
    time.sleep(20)
    start_app()
    t = wait_for(lambda t: card_in(t), 180)
    return card_in(t), screen_text()[:160]


def airplane(on):
    sh("shell", "cmd", "connectivity", "airplane-mode", "enable" if on else "disable")
    time.sleep(4)


def c19():
    clear_cards()
    airplane(True)
    try:
        t = say_and_wait("remind me tomorrow at 8 am to call the plumber", 90)
    finally:
        airplane(False)
    return card_in(t), screen_text()[:160]


def c20():
    clear_cards()
    airplane(True)
    try:
        t = say_and_wait("please remind me to send the report on Thursday at 4 pm", 180)
    finally:
        airplane(False)
    return card_in(t), screen_text()[:160]


def c24():
    clear_cards()
    sh("shell", "pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS")
    stop_app()
    start_app()
    before = has(texts(), "Notifications for Bheem are off")
    sh("shell", "pm", "revoke", PKG, "android.permission.POST_NOTIFICATIONS")
    time.sleep(2)
    stop_app()
    start_app()
    t = say_and_wait("remind me tomorrow at 7 am to take the medicine", 120)
    banner_on = has(t, "Notifications for Bheem are off")
    sh("shell", "pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS")
    return (not before) and banner_on and card_in(t), f"banner before revoke={before}, after revoke={banner_on}, card={card_in(t)}"


def c26():
    clear_cards()
    sh("shell", "pm", "revoke", PKG, "android.permission.RECORD_AUDIO")
    time.sleep(1)
    stop_app()
    start_app()
    held = hold_talk(800)
    time.sleep(1.5)
    dialog = tap_anywhere("Don't allow")
    time.sleep(1.5)
    start_app()
    held_again = hold_talk(800)
    t = wait_for(lambda t: "Close" in t or "Bheem" in t, 10)
    alive = bool(sh("shell", "pidof", PKG).strip())
    sh("shell", "pm", "grant", PKG, "android.permission.RECORD_AUDIO")
    message = banner(t)
    return held and alive and message is not None and "microphone" in message, f"system dialog shown={dialog} (it is only offered until the user refuses twice); alive={alive}; a press after refusing shows: {message}"


def c29():
    clear_cards()
    base = alarm_count()
    t = say_and_wait("remind me tomorrow at 6 am to go for a run", 120)
    approve()
    time.sleep(3)
    t2 = texts()
    return alarm_count() == base + 1 and has(t2, "Next:"), f"alarms {base} -> {alarm_count()}"


def c30():
    clear_cards()
    base = alarm_count()
    say_and_wait("remind me tomorrow at 6 pm to feed the cat", 120)
    ui.tap("Dismiss")
    time.sleep(2)
    return alarm_count() == base and not card_in(texts()), f"alarms {base} -> {alarm_count()}"


def c32():
    clear_cards()
    base = alarm_count()
    say_and_wait("remind me tomorrow at 5 am to catch the train", 120)
    approve()
    time.sleep(3)
    scheduled = alarm_count()
    undone = ui.tap("Undo")
    time.sleep(3)
    return scheduled == base + 1 and undone and alarm_count() == base, f"alarms {base} -> {scheduled} -> {alarm_count()}; undo tapped={undone}"


def c33():
    clear_cards()
    base = alarm_count()
    say_and_wait("remind me tomorrow at 4 am to check the flight", 120)
    for n in ui.nodes():
        if n[0] in VERBS:
            sh("shell", "input", "tap", str(n[1]), str(n[2]))
            sh("shell", "input", "tap", str(n[1]), str(n[2]))
            break
    time.sleep(4)
    return alarm_count() == base + 1, f"alarms {base} -> {alarm_count()}"


def c34():
    clear_cards()
    say_and_wait("remind me tomorrow at 3 am to renew the pass", 120)
    sh("shell", "settings", "put", "system", "accelerometer_rotation", "0")
    sh("shell", "settings", "put", "system", "user_rotation", "1")
    time.sleep(3)
    t1 = texts()
    sh("shell", "settings", "put", "system", "user_rotation", "0")
    time.sleep(3)
    t2 = texts()
    return card_in(t1) and card_in(t2), f"landscape card={card_in(t1)}, portrait card={card_in(t2)}"


def c35():
    clear_cards()
    say_and_wait("remind me tomorrow at 2 am to lock the gate", 120)
    base = alarm_count()
    stop_app()
    start_app()
    t = wait_for(lambda t: card_in(t), 40)
    return card_in(t) and alarm_count() == base, f"card back={card_in(t)}; alarms unchanged={alarm_count() == base}"


def run_as(*cmd):
    return sh("shell", "run-as", PKG, *cmd)


def c13():
    clear_cards()
    stop_app()
    start_app()
    type_command("please remind me to buy flowers on Sunday at 8 am")
    time.sleep(1.5)
    t1 = texts()
    locked = has(t1, "Getting ready") or has(t1, "Thinking")
    second_possible = ui.tap(BAR, exact=False)
    t = wait_for(lambda t: card_in(t), 200)
    time.sleep(10)
    t = texts()
    alive = bool(sh("shell", "pidof", PKG).strip())
    return alive and locked and not second_possible and n_cards(t) == 1, f"loading note shown={locked}; a second command could be typed={second_possible}; cards waiting={n_cards(t)}; alive={alive}"


def c17():
    clear_cards()
    run_as("mv", "files/models", "files/models.away")
    try:
        stop_app()
        start_app()
        t = say_and_wait("remind me tomorrow at 9 am to call Ravi", 60)
        rules_ok = card_in(t)
        clear_cards()
        t2 = say_and_wait("please summarise my week", 90)
        message = banner(t2) or screen_text()[:160]
    finally:
        stop_app()
        run_as("mv", "files/models.away", "files/models")
    return rules_ok, f"rules path card={rules_ok}; free-form: {message}"


def c18():
    clear_cards()
    run_as("mv", "files/models/gemma-4-E2B-it.litertlm", "files/gemma.bak")
    try:
        run_as("dd", "if=files/gemma.bak", "of=files/models/gemma-4-E2B-it.litertlm", "bs=1048576", "count=1")
        stop_app()
        start_app()
        t = say_and_wait("please summarise my week", 120)
        message = banner(t) or screen_text()[:200]
        alive = bool(sh("shell", "pidof", PKG).strip())
    finally:
        stop_app()
        run_as("mv", "files/gemma.bak", "files/models/gemma-4-E2B-it.litertlm")
    restored = "gemma-4-E2B-it.litertlm" in run_as("ls", "files/models")
    return alive and restored, f"alive={alive}; restored={restored}; screen: {message}"


def c21():
    clear_cards()
    stop_app()
    start_app()
    type_command("please remind me to call the dentist on Monday at 11 am")
    time.sleep(3)
    airplane(True)
    time.sleep(5)
    airplane(False)
    t = wait_for(lambda t: card_in(t), 200)
    return card_in(t), f"card={card_in(t)}"


def c22():
    clear_cards()
    out = sh("shell", "df", "-k", "/data")
    avail_kb = int(out.strip().splitlines()[-1].split()[3])
    fill_kb = max(avail_kb - 400 * 1024, 0)
    filler = "/data/local/tmp/stage11-filler"
    try:
        sh("shell", "fallocate", "-l", f"{fill_kb}K", filler, timeout=300)
        left_kb = int(sh("shell", "df", "-k", "/data").strip().splitlines()[-1].split()[3])
        base = alarm_count()
        t = say_and_wait("remind me tomorrow at 7 pm to call the bank", 120)
        shown = card_in(t)
        approve()
        time.sleep(3)
        alive = bool(sh("shell", "pidof", PKG).strip())
    finally:
        sh("shell", "rm", "-f", filler)
    return shown and alive, f"free {left_kb // 1024} MB during the test; card={shown}; alarms {base} -> {alarm_count()}; alive={alive}; banner={banner(texts())}"


def c23():
    clear_cards()
    sh("shell", "settings", "put", "global", "low_power", "1")
    try:
        base = alarm_count()
        say_and_wait("remind me tomorrow at 10 am to call the tailor", 120)
        approve()
        time.sleep(3)
        after = alarm_count()
        t = texts()
    finally:
        sh("shell", "settings", "put", "global", "low_power", "0")
    return after == base + 1, f"alarms {base} -> {after}; notes: {[x for x in t if 'late' in x or 'battery' in x.lower()][:2]}"


def c25():
    clear_cards()
    sh("shell", "cmd", "appops", "set", "--uid", PKG, "SCHEDULE_EXACT_ALARM", "allow")
    stop_app()
    start_app()
    before = has(texts(), "Reminders may arrive late")
    sh("shell", "cmd", "appops", "set", "--uid", PKG, "SCHEDULE_EXACT_ALARM", "deny")
    try:
        stop_app()
        start_app()
        banner_on = has(texts(), "Reminders may arrive late")
        warnings = sum("Exact alarms are off" in x for x in texts())
        say_and_wait("remind me tomorrow at 11 am to call the vet", 120)
        approve()
        time.sleep(3)
        more = sum("Exact alarms are off" in x for x in texts()) - warnings
    finally:
        sh("shell", "cmd", "appops", "set", "--uid", PKG, "SCHEDULE_EXACT_ALARM", "allow")
    return (not before) and banner_on, f"banner when allowed={before}, when denied={banner_on} (the per-card warning is checked by eye in the screenshots, not here)"


def c27():
    clear_cards()
    stop_app()
    start_app()
    xy = talk_xy()
    if xy is None:
        return False, "no Talk button"
    holder = subprocess.Popen([ui.adb, "shell", "input", "swipe", str(xy[0]), str(xy[1]), str(xy[0]), str(xy[1]), "6000"])
    time.sleep(2)
    sh("shell", "pm", "revoke", PKG, "android.permission.RECORD_AUDIO")
    holder.wait(timeout=30)
    time.sleep(2)
    start_app()
    sh("shell", "pm", "grant", PKG, "android.permission.RECORD_AUDIO")
    t = texts()
    return BAR in t, f"app reopened cleanly after the revoke (Android restarts the app on a revoke): {BAR in t}"


def c31():
    clear_cards()
    say_and_wait("remind me tomorrow at 9 am to call Ravi", 120)
    if not ui.tap("Edit"):
        return False, "no Edit button"
    time.sleep(1.5)
    for _ in range(70):
        sh("shell", "input", "keyevent", "KEYCODE_DEL")
    sh("shell", "input", "text", "remind%sme%stomorrow%sat%s10%sam%sto%scall%sRavi")
    ui.tap("Send")
    t = wait_for(lambda t: card_in(t), 90)
    time.sleep(2)
    t = texts()
    return n_cards(t) == 1 and has(t, "10:00 AM"), f"cards waiting={n_cards(t)}; has 10 AM={has(t, '10:00 AM')}"


def c14():
    clear_cards()
    stop_app()
    start_app()
    type_command("please remind me to pick up the parcel on Saturday at 5 pm")
    time.sleep(2)
    sh("shell", "input", "keyevent", "KEYCODE_POWER")
    time.sleep(30)
    sh("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    time.sleep(1)
    sh("shell", "wm", "dismiss-keyguard")
    time.sleep(2)
    locked = "mDreamingLockscreen=true" in sh("shell", "dumpsys", "window") or not ui.front()
    if locked and not ui.front():
        return None, "not run: the phone has a screen lock and did not unlock from adb"
    t = wait_for(lambda t: card_in(t) or "Close" in t, 150)
    alive = bool(sh("shell", "pidof", PKG).strip())
    return alive and card_in(t), f"alive={alive}; card after unlock={card_in(t)}"


def c28():
    clear_cards()
    when = time.strftime("%H:%M", time.localtime(time.time() + 150))
    say_and_wait(f"remind me today at {when} to stretch", 120)
    approve()
    time.sleep(2)
    sh("shell", "input", "keyevent", "KEYCODE_POWER")
    sh("shell", "dumpsys", "deviceidle", "force-idle")
    time.sleep(170)
    notes = sh("shell", "dumpsys", "notification", "--noredact")
    fired = "stretch" in notes
    sh("shell", "dumpsys", "deviceidle", "unforce")
    sh("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    sh("shell", "wm", "dismiss-keyguard")
    return fired, f"reminder notification present while idle was forced: {fired}"


def newest_answer(t):
    """The newest Bheem bubble (plain or from the model): its label and its text."""
    for i in range(len(t) - 1, -1, -1):
        if t[i] == "Bheem" or t[i].startswith("Bheem, from the model"):
            return t[i], (t[i + 1] if i + 1 < len(t) else "")
    return None, None


def ask_and_read(text, wait=60, want=None):
    c0 = sum(1 for x in texts() if x == "Bheem" or x.startswith("Bheem, from the model"))
    type_command(text)
    end = time.time() + wait
    while time.time() < end:
        time.sleep(2)
        t = texts()
        label, answer = newest_answer(t)
        count = sum(1 for x in t if x == "Bheem" or x.startswith("Bheem, from the model"))
        if label and (count > c0 or want) and "Stop" not in t and (want is None or want in (answer or "")):
            return label, answer
    return newest_answer(texts())


def c41():
    label, answer = ask_and_read("hi", 30)
    return label == "Bheem" and "How can I help" in (answer or ""), f"{label}: {(answer or '')[:80]}"


def c42():
    label, answer = ask_and_read("thanks", 30, want="welcome")
    return "welcome" in (answer or ""), f"{label}: {answer}"


def c43():
    label, answer = ask_and_read("what is on tomorrow", 30)
    return label == "Bheem" and (answer.startswith("Tomorrow,") or answer.startswith("You have nothing")), f"{label}: {(answer or '')[:80]}"


def c44():
    label, answer = ask_and_read("give me my morning brief", 30, want="Your day")
    return "Your day" in (answer or ""), f"{(answer or '')[:80]}"


def c45():
    label, answer = ask_and_read("explain what GST is", 120)
    return bool(label) and label.startswith("Bheem, from the model") and len(answer or "") > 20, f"{label}: {(answer or '')[:100]}"


def c46():
    type_command("write me a long story about a lighthouse")
    end = time.time() + 60
    tapped = False
    while time.time() < end:
        if ui.tap("Stop"):
            tapped = True
            break
    time.sleep(8)
    t = texts()
    alive = bool(sh("shell", "pidof", PKG).strip())
    long_story = any(len(x) > 150 and "lighthouse" in x.lower() for x in t)
    return tapped and alive and not long_story and BAR in t, f"tapped={tapped}; alive={alive}; long story stored={long_story}; bar usable={BAR in t}"


def c47():
    label, answer = ask_and_read("call Zorro", 30, want="number")
    return "no number saved for Zorro" in (answer or ""), f"{(answer or '')[:100]}"


def c48():
    clear_cards()
    type_command("note Mohan number is 98765 11111")
    wait_for(lambda t: card_in(t), 90)
    approve()
    time.sleep(3)
    clear_cards()
    type_command("call Mohan")
    t = wait_for(lambda t: card_in(t), 60)
    card = has(t, "Call Mohan")
    approve()
    time.sleep(4)
    top = ""
    for l in sh("shell", "dumpsys", "activity", "activities").splitlines():
        if "topResumedActivity" in l:
            top = l.strip()
    opened = "dialer" in top.lower()
    sh("shell", "input", "keyevent", "BACK")
    time.sleep(1)
    start_app()
    return card and opened, f"card={card}; dialer opened={opened}"


def c49():
    clear_cards()
    type_command("remind me tomorrow at 9 to call Arun and add buy salt to my tasks")
    t = wait_for(lambda t: "Do all" in t, 120)
    plan = has(t, "2 steps") and n_cards(t) == 2
    ui.tap("Do all")
    time.sleep(5)
    t2 = texts()
    done = has(t2, "Done: 2 of 2") and n_cards(t2) == 0
    return plan and done, f"plan shown={plan}; all done={done}"


def c50():
    clear_cards()
    type_command("remind me to call Ravi")
    wait_for(lambda t: has(t, "What time should I use for that?"), 90)
    asked = has(texts(), "What time should I use for that?")
    type_command("5 pm")
    t = wait_for(lambda t: card_in(t), 90)
    card = card_in(t) and has(t, "5:00 PM")
    type_command("yes")
    time.sleep(5)
    approved = n_cards(texts()) == 0
    return asked and card and approved, f"asked={asked}; card={card}; yes approved={approved}"


def simple(cid, command, must):
    return simple_card(command, must)


CASES = {
    "C01": c01, "C02": c02,
    "C03": simple("C03", "task buy milk", "milk"),
    "C04": simple("C04", "note the wifi password is on the fridge", "wifi"),
    "C05": simple("C05", "schedule a meeting with Meena on Friday at 3 pm", "Meena"),
    "C06": simple("C06", "tell Ravi I will be late", "Ravi"),
    "C07": c07, "C08": c08, "C09": c09, "C10": c10,
    "C11": c11, "C12": c12, "C15": c15, "C16": c16,
    "C19": c19, "C20": c20, "C24": c24, "C26": c26,
    "C13": c13, "C14": c14, "C28": c28, "C17": c17, "C18": c18, "C21": c21, "C22": c22, "C23": c23, "C25": c25, "C27": c27,
    "C41": c41, "C42": c42, "C43": c43, "C44": c44, "C45": c45, "C46": c46, "C47": c47, "C48": c48, "C49": c49, "C50": c50,
    "C29": c29, "C30": c30, "C32": c32, "C33": c33, "C34": c34, "C35": c35,
}


def run(cid):
    if not ui.front():
        start_app()
    sh("logcat", "-c")
    began = time.time()
    try:
        passed, note = CASES[cid]()
    except Exception as e:
        passed, note = False, f"script error: {e!r}"
    log = sh("logcat", "-d", "-v", "threadtime", timeout=120)
    crashes = [l for l in log.splitlines() if CRASH.search(l)]
    result = {"id": cid, "passed": None if passed is None else (bool(passed) and not crashes), "crashes": crashes[:3], "note": note, "seconds": round(time.time() - began)}
    print(json.dumps(result), flush=True)
    return result


def grant_all():
    sh("shell", "pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS")
    sh("shell", "pm", "grant", PKG, "android.permission.RECORD_AUDIO")
    sh("shell", "cmd", "appops", "set", "--uid", PKG, "SCHEDULE_EXACT_ALARM", "allow")
    sh("shell", "settings", "put", "system", "accelerometer_rotation", "0")
    sh("shell", "settings", "put", "system", "user_rotation", "0")
    sh("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    sh("shell", "wm", "dismiss-keyguard")
    sh("shell", "svc", "power", "stayon", "true")


OWNER = {"C14", "C28"}


def save(result):
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    old = json.load(open(OUT)) if os.path.exists(OUT) else {}
    old[result["id"]] = result
    json.dump(old, open(OUT, "w"), indent=1)


if __name__ == "__main__":
    args = [a.upper() if not a.startswith("--") else a for a in sys.argv[1:]]
    if args[:1] == ["--one"]:
        grant_all()
        run(args[1].upper())
        sys.exit(0)
    if os.environ.get(CHILD_GUARD):
        sys.exit("refusing to start: this process was started by the case runner and must not start more")
    ids = sorted(k for k in CASES if k not in OWNER) if args == ["ALL"] else args
    ids = [cid for cid in ids if cid in CASES]
    if len(ids) > len(CASES):
        sys.exit("refusing to start: more cases requested than exist")
    child_env = {**os.environ, CHILD_GUARD: "1"}
    for cid in ids:
        try:
            out = subprocess.run([sys.executable, __file__, "--one", cid], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=360, env=child_env).stdout
            line = [l for l in out.splitlines() if l.startswith("{")]
            result = json.loads(line[-1]) if line else {"id": cid, "passed": False, "crashes": [], "note": "no result", "seconds": 0}
        except subprocess.TimeoutExpired:
            result = {"id": cid, "passed": False, "crashes": [], "note": "timed out after 360 s", "seconds": 360}
            stop_app()
        save(result)
        print(json.dumps(result), flush=True)
