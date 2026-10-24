import datetime
import os
import re
import subprocess

adb = os.path.join(os.environ["LOCALAPPDATA"], "Android", "Sdk", "platform-tools", "adb.exe")
out = subprocess.run([adb, "shell", "dumpsys", "alarm"], capture_output=True, text=True).stdout
n = 0
for m in re.finditer(r"(RTC_WAKEUP|RTC) #\d+: Alarm\{\w+ type \d+ origWhen (\d+) [^\n]*?app\.bheem\.bench\}", out):
    t = datetime.datetime.fromtimestamp(int(m.group(2)) / 1000)
    print(m.group(1), t.strftime("%H:%M:%S"))
    n += 1
print("pending bheem alarms:", n)
