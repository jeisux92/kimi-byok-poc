#!/usr/bin/env python3
"""Idempotent smali patches applied to an apktool-decoded tree.

Edit A (PairIP): remove the single LicenseClient.checkLicense call from
com/pairip/application/Application.smali. PairIP force-exits any build that was
not installed by Google Play, which would kill the sideloaded POC within ~30 s.

Edit B (router): prepend a ByokRouter branch to
com/moonshot/kimichat/route/ExternalLinkActivity.onCreate, right after
super.onCreate. ExternalLinkActivity is the manifest entry for ALL kimi:// URIs
and provides the Activity as Context (h40.n.f, the alternative chokepoint, has
no Context register). Returns before the rest of onCreate, finishing cleanly.

Usage: patch_smali.py <decoded-tree>
"""
import os
import sys

PAIRIP = "smali_classes2/com/pairip/application/Application.smali"
CHECK = "    invoke-static {p1}, Lcom/pairip/licensecheck/LicenseClient;->checkLicense(Landroid/content/Context;)V\n"
ROUTER = "smali_classes3/com/moonshot/kimichat/route/ExternalLinkActivity.smali"
SUPER = ("    invoke-super {p0, p1}, Lfv/a;->onCreate(Landroid/os/Bundle;)V\n")
HOOK = (
    "\n"
    "    invoke-static {p0, p0}, Lcom/moonshot/kimichat/byok/ByokRouter;"
    "->handleDeepLink(Landroid/content/Context;Landroid/content/Intent;)Z\n"
    "\n"
    "    move-result v2\n"
    "\n"
    "    if-eqz v2, :cond_byok_skip\n"
    "\n"
    "    invoke-virtual {p0}, Lcom/moonshot/kimichat/route/ExternalLinkActivity;->finish()V\n"
    "\n"
    "    return-void\n"
    "\n"
    "    :cond_byok_skip\n"
)


def patch_pairip(root):
    path = os.path.join(root, PAIRIP)
    with open(path, "r", encoding="utf-8") as f:
        text = f.read()
    if CHECK not in text:
        print("pairip: call already removed (or pattern missing) - no change")
        return
    with open(path, "w", encoding="utf-8") as f:
        f.write(text.replace(CHECK, "\n", 1))
    print("pairip: removed checkLicense call")


def patch_router(root):
    path = os.path.join(root, ROUTER)
    with open(path, "r", encoding="utf-8") as f:
        text = f.read()
    if "ByokRouter;->handleDeepLink" in text:
        print("router: hook already present - no change")
        return
    if SUPER not in text:
        sys.exit("router: super.onCreate pattern not found - smali shape changed")
    with open(path, "w", encoding="utf-8") as f:
        f.write(text.replace(SUPER, SUPER + HOOK, 1))
    print("router: ByokRouter hook inserted after super.onCreate")


if __name__ == "__main__":
    tree = sys.argv[1]
    patch_pairip(tree)
    patch_router(tree)
