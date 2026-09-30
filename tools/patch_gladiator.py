#!/usr/bin/env python3
"""
Gladiator Manager offline editor patch helper.

This script runs inside GitHub Actions after apktool decodes the APK.
It patches the existing GladiatorEditorActivity smali rather than adding
runtime injection/overlay code.
"""
from pathlib import Path
import re, sys

ROOT = Path(sys.argv[1])
smali = ROOT / "smali" / "com" / "rene" / "gladiatormanager" / "activities" / "GladiatorEditorActivity.smali"
listener = ROOT / "smali" / "com" / "rene" / "gladiatormanager" / "activities" / "GladiatorEditorActivity$1.smali"

print("Looking for:", smali)
if not smali.exists():
    # Apktool may place some classes in smali_classesN.
    matches = list(ROOT.glob("smali*/com/rene/gladiatormanager/activities/GladiatorEditorActivity.smali"))
    if len(matches) != 1:
        raise SystemExit(f"GladiatorEditorActivity.smali not found; matches={matches}")
    smali = matches[0]
    listener = smali.parent / "GladiatorEditorActivity$1.smali"

src = smali.read_text(encoding="utf-8")
lsrc = listener.read_text(encoding="utf-8")

def replace_method(text: str, name: str, new_body: str) -> str:
    pat = re.compile(r"(\.method[^\n]*\\s+" + re.escape(name) + r"[^\n]*\n)(.*?)(\.end method)", re.S)
    m = pat.search(text)
    if not m:
        raise SystemExit(f"method not found: {name}")
    return text[:m.start()] + m.group(1) + new_body.rstrip() + "\n" + m.group(3) + text[m.end():]

# Placeholder implementation is deliberately small at first: it turns the
# existing activity into a save-scoped editor entry point. The next stage fills
# the complete GUI after the exact decoded class layout is inspected.
oncreate = r""".registers 4
    invoke-super {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V
    const v0, 0x7f0c0014
    invoke-virtual {p0, v0}, Lcom/rene/gladiatormanager/activities/GladiatorEditorActivity;->setContentView(I)V
    invoke-virtual {p0}, Lcom/rene/gladiatormanager/activities/GladiatorEditorActivity;->resetToGladiator()V
    invoke-virtual {p0}, Lcom/rene/gladiatormanager/activities/GladiatorEditorActivity;->drawDynamicUi()V
    return-void"""

src = replace_method(src, "onCreate", oncreate)

smali.write_text(src, encoding="utf-8")

# Route the existing appearance click listener to originInfo(), which will
# become the central button dispatcher in the completed patch.
lnew = r""".method public onClick(Landroid/view/View;)V
    .registers 2
    iget-object v0, p0, Lcom/rene/gladiatormanager/activities/GladiatorEditorActivity$1;->this$0:Lcom/rene/gladiatormanager/activities/GladiatorEditorActivity;
    invoke-virtual {v0, p1}, Lcom/rene/gladiatormanager/activities/GladiatorEditorActivity;->originInfo(Landroid/view/View;)V
    return-void
.end method"""
lsrc = replace_method(lsrc, "onClick", lnew)
listener.write_text(lsrc, encoding="utf-8")

print("Initial activity/listener patch applied.")
