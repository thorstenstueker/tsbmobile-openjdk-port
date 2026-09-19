# tsbmobile-openjdk-port

Source code of the port of OpenJDK's `java.desktop` (Swing and AWT) that tsb.mobile uses to render Swing forms on
Android and iOS — `desktop8-*.jar`.

The port is **modified OpenJDK** and therefore licensed under the **GNU General Public License, version 2, with the
Classpath Exception** ([LICENSE](LICENSE)), like OpenJDK itself. This repository makes its complete corresponding
source available to everybody who receives the port — in the tsbDesignerMobile plugin or inside an app built with it.

The Classpath Exception allows independent code — tsb.mobile, the designers, and every application built with them —
to link with the port without becoming subject to the GPL.

## Where it comes from

| | |
|---|---|
| Base | `jre/lib/rt.jar` of **BellSoft Liberica JDK 1.8.0_492-b09** — the `java.desktop` classes, 4,766 of them, and five classes of `java.applet` |
| Unmodified source of that base | `bellsoft-jdk8u492+9-src.tar.gz`, attached to the [releases](../../releases) of this repository (original: `https://download.bell-sw.com/java/8u492+9/bellsoft-jdk8u492+9-src.tar.gz`) |
| Copyright of the OpenJDK code | Oracle and/or its affiliates |
| Modifications and tools | Copyright (C) 2026 tsb Thorsten Stueker Buero for Technology development |

## What was changed, and how

The port does not come out of a compiler run over the OpenJDK sources but out of four rewrites of the compiled
classes, plus a set of changed and added classes played back into the jar. In this order:

| Step | Tool | What it does |
|---|---|---|
| 1 | [`tools/Entnativisierer.java`](tools/Entnativisierer.java) | 438 native methods: 66 emptied, 372 made to throw |
| 2 | [`tools/Umbenenner.java`](tools/Umbenenner.java) | `java.beans` → `tsb.port.beans`, `sun.util.logging` → `tsb.port.logging` (190 classes) |
| 3 | [`tools/Laderwechsel.java`](tools/Laderwechsel.java) | `ClassLoader.getSystemClassLoader()` → `tsb.port.Lader.system()` (18 places) |
| 4 | [`tools/Kurzschluss.java`](tools/Kurzschluss.java) | `FontUtilities.fontSupportsDefaultEncoding` → `true`, `EventQueue.isDispatchThread` → `true` |
| 5 | [`patch/`](patch) | changed OpenJDK classes (`javax.swing.RepaintManager`, `java.awt.EventQueue`, `sun.swing.SwingUtilities2`, …) and the added seams in `tsb.port` and `sun.font.TsbFont2D`, compiled with `-source 8 -target 8` against the rewritten jar and replaced inside it |

The tools use ASM (BSD-3-Clause). [`tools/bauen.sh.template`](tools/bauen.sh.template) shows the compile-and-replace
step for the seams in executable form. The background and the measurements behind every step are in
[`docs/`](docs) (the experiment write-ups are in German).

FlatLaf for Android (`flatlaf-android-*.jar`) goes through steps 2 and 3 as well. FlatLaf is Apache-2.0 licensed, and
this sentence is the notice of that modification.

## Contact

tsb Thorsten Stueker Buero for Technology development, Lemgoer Str. 7, 32683 Barntrup, Germany;
licensequestions@stueker.org
