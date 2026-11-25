#!/usr/bin/env python3
"""Idempotent manifest patch: BYOK activities + "Kimi BYOK" launcher alias.

Inserted directly before </application>. No permissions are added; existing
attributes (including the original launcher activity) are untouched.
Usage: patch_manifest.py <decoded-tree>/AndroidManifest.xml
"""
import sys

ANCHOR = '        <activity android:exported="false" android:name="com.pairip.licensecheck.LicenseActivity"/>\n    </application>'
ADD = '''        <activity android:exported="false" android:name="com.pairip.licensecheck.LicenseActivity"/>
        <activity android:exported="false" android:name="com.moonshot.kimichat.byok.ArtifactPreviewActivity" android:theme="@android:style/Theme.DeviceDefault.NoActionBar"/>
        <activity android:exported="false" android:name="com.moonshot.kimichat.byok.ByokMainActivity" android:theme="@android:style/Theme.DeviceDefault.NoActionBar"/>
        <activity android:exported="true" android:name="com.moonshot.kimichat.byok.ByokSettingsActivity" android:theme="@android:style/Theme.DeviceDefault.NoActionBar">
            <intent-filter>
                <action android:name="android.intent.action.VIEW"/>
                <category android:name="android.intent.category.DEFAULT"/>
                <category android:name="android.intent.category.BROWSABLE"/>
                <data android:scheme="kimibyok"/>
            </intent-filter>
        </activity>
        <activity-alias android:enabled="true" android:exported="true" android:icon="@mipmap/ic_launcher" android:label="Kimi BYOK" android:name="com.moonshot.kimichat.byok.Launcher" android:roundIcon="@mipmap/ic_launcher_round" android:targetActivity="com.moonshot.kimichat.byok.ByokMainActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity-alias>
    </application>'''


def main(path):
    with open(path, "r", encoding="utf-8") as f:
        text = f.read()
    if "com.moonshot.kimichat.byok.Launcher" in text:
        print("manifest: byok entries already present - no change")
        return
    if ANCHOR not in text:
        sys.exit("manifest: anchor (LicenseActivity + </application>) not found")
    with open(path, "w", encoding="utf-8") as f:
        f.write(text.replace(ANCHOR, ADD, 1))
    print("manifest: activities + launcher alias inserted")


if __name__ == "__main__":
    main(sys.argv[1])
