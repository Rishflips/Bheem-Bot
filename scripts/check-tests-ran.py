#!/usr/bin/env python3
"""Fails if a test class declares more @Test methods than its result file reports.

JUnit silently skips a test method whose return type is not Unit (for example
`fun x() = runBlocking { ... }` ending in an assertion), so a green build can hide it.
"""
import glob
import os
import re
import sys

root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
declared = {}
for path in glob.glob(os.path.join(root, "*", "src", "test", "**", "*.kt"), recursive=True):
    text = open(path, encoding="utf-8").read()
    package = re.search(r"^package ([\w.]+)", text, re.M)
    for cls in re.finditer(r"^class (\w+)", text, re.M):
        body = text[cls.end():]
        nxt = re.search(r"^class \w+", body, re.M)
        body = body[: nxt.start()] if nxt else body
        count = len(re.findall(r"^\s*@Test\b", body, re.M))
        if count:
            declared[f"{package.group(1)}.{cls.group(1)}"] = (count, path)

ran = {}
for path in glob.glob(os.path.join(root, "*", "build", "test-results", "**", "TEST-*.xml"), recursive=True):
    head = open(path, encoding="utf-8").read(1500)
    name = re.search(r'<testsuite name="([^"]+)" tests="(\d+)"', head)
    if name:
        ran[name.group(1)] = int(name.group(2))

problems = []
for name, (count, path) in sorted(declared.items()):
    if name not in ran:
        continue
    if ran[name] != count:
        problems.append(f"{name}: {count} @Test declared, {ran[name]} ran ({os.path.relpath(path, root)})")
if problems:
    print("tests declared but not run (a test function must return Unit):")
    print("\n".join(problems))
    sys.exit(1)
print(f"all declared tests ran ({len(ran)} classes checked)")
