#!/usr/bin/env python3
"""Adds a new build to sidestore.json, the source SideStore/AltStore reads.

Usage: make_source.py <version> <build> <ipa path> <download url> <notes>
"""
import datetime
import json
import os
import sys

REPO_RAW = "https://raw.githubusercontent.com/dlegion98/wavremote/main"
PATH = "sidestore.json"

version, build, ipa, url, notes = sys.argv[1:6]
size = os.path.getsize(ipa)
now = datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")

try:
    with open(PATH) as f:
        source = json.load(f)
except (FileNotFoundError, ValueError):
    source = {}

old_versions = []
for app in source.get("apps", []):
    if app.get("bundleIdentifier") == "com.dayo.wavremote":
        old_versions = [v for v in app.get("versions", []) if v.get("version") != version]

entry = {
    "version": version,
    "buildVersion": build,
    "date": now,
    "localizedDescription": notes.strip() or "Improvements and fixes.",
    "downloadURL": url,
    "size": size,
    "minOSVersion": "16.0",
}

app = {
    "name": "WavRemote",
    "bundleIdentifier": "com.dayo.wavremote",
    "developerName": "Dayo",
    "subtitle": "Control your Wavlink mesh Wi-Fi.",
    "localizedDescription": (
        "See every device on your Wavlink mesh, block or speed-limit them, change Wi-Fi names and "
        "passwords, share Wi-Fi with a QR code, switch guest and kids' Wi-Fi, check your mesh points "
        "and restart the router. Works away from home through a VPN to your home network."
    ),
    "iconURL": f"{REPO_RAW}/ios/icon.png",
    "tintColor": "#3D8BFF",
    "category": "utilities",
    "screenshots": [],
    "versions": [entry] + old_versions[:9],
    # Older SideStore/AltStore versions read these top-level fields.
    "version": version,
    "versionDate": now,
    "versionDescription": entry["localizedDescription"],
    "downloadURL": url,
    "size": size,
    "appPermissions": {
        "entitlements": [],
        "privacy": {
            "NSLocalNetworkUsageDescription": "WavRemote talks to your Wavlink router on your home network.",
        },
    },
}

source = {
    "name": "WavRemote",
    "identifier": "com.dayo.wavremote.source",
    "subtitle": "Dayo's apps",
    "iconURL": f"{REPO_RAW}/ios/icon.png",
    "website": "https://github.com/dlegion98/wavremote",
    "tintColor": "#3D8BFF",
    "apps": [app],
    "news": [],
}

with open(PATH, "w") as f:
    json.dump(source, f, indent=2)
    f.write("\n")
print(f"sidestore.json now offers WavRemote {version}")
