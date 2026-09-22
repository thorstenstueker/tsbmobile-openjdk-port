# tsbmobile-openjdk-port

Source code of the port of OpenJDK's `java.desktop` (Swing and AWT) that tsb.mobile uses to render Swing forms on
Android and iOS — `desktop17-*.jar` (until 22.09.2026 `desktop8-*.jar`).

The port is **modified OpenJDK** and therefore licensed under the **GNU General Public License, version 2, with the
Classpath Exception** ([LICENSE](LICENSE)), like OpenJDK itself. This repository makes its complete corresponding
source available to everybody who receives the port — in the tsbDesignerMobile plugin or inside an app built with it.

The Classpath Exception allows independent code — tsb.mobile, the designers, and every application built with them —
to link with the port without becoming subject to the GPL.

## Where it comes from

Since 22.09.2026 the port is built from **JDK 17** — `desktop17-*.jar`. The JDK 8 port
(`desktop8-*.jar`, `patch/`, `tools/bauen.sh.template`) stays in this repository as the record of how
it came about; the sections on it are marked as history below.

| | |
|---|---|
| Base | `jmods/java.desktop.jmod` and `java.datatransfer.jmod` of **BellSoft Liberica JDK 17.0.12** (macOS build), without the macOS layer (`sun.lwawt`, `com.apple`, Metal, OpenGL, `CFont…`), plus a handful of `java.base` classes the desktop names and the phones lack |
| Unmodified source of that base | `lib/src.zip` of the same JDK (original: `https://download.bell-sw.com/java/17.0.12+10/`) |
| Copyright of the OpenJDK code | Oracle and/or its affiliates |
| Modifications and tools | Copyright (C) 2026 tsb Thorsten Stueker Buero for Technology development |

## What is changed, and how

One script, [`tools/port17.sh`](tools/port17.sh), in this order:

| Step | Tool | What it does |
|---|---|---|
| 0 | `jmod extract` | the platform-free classes of `java.desktop` and `java.datatransfer`; from `java.base` the `sun.security.action`, `sun.reflect.misc`, `sun.reflect.generics.reflectiveObjects` classes, the `jdk.internal.access` interfaces and `SecurityConstants` |
| 1 | `javac --patch-module` | [`src17/`](src17): `SwingUtilities2` (font metrics from the platform), `MethodUtil` (no trampoline), `PlatformGraphicsInfo` (hands out the seams — JDK 9+ no longer reads `awt.toolkit`), a `SharedSecrets` and a `CleanerFactory` of our own, and the seams `tsb.port.*` and `sun.font.TsbFont2D` |
| 2 | [`tools/Modulfrei.java`](tools/Modulfrei.java) | `Class.getModule` → `tsb.port.Modul`, `Class.forName(Module, …)`, `ResourceBundle.getBundle(…, Module)`, `ClassLoader.getUnnamedModule/getPlatformClassLoader`, `Thread(…, inheritThreadLocals)` → four arguments, `MethodHandles.lookup().ensureInitialized` → `Class.forName`, `System.loadLibrary` → nothing: one rule each instead of one patch each |
| 3 | [`tools/Entnativisierer.java`](tools/Entnativisierer.java) | every native method gets a body: `initIDs`/`registerNatives` empty (63), the rest throw with their name (198) |
| 4 | [`tools/Umbenenner.java`](tools/Umbenenner.java) | `java.beans` → `tsb.port.beans`, `sun.util.logging` → `tsb.port.logging`, `java.lang.Module` → `tsb.port.Modul`, `sun.security.action` → `tsb.port.action`, `TextAttribute` and `JavaAWTFontAccessImpl` → `tsb.port` (163 classes) — what collides with the phones' boot class path or does not exist there |
| 5 | [`tools/Laderwechsel.java`](tools/Laderwechsel.java) | `ClassLoader.getSystemClassLoader()` → `tsb.port.Lader.system()` (15 places) |
| 6 | [`tools/Kurzschluss.java`](tools/Kurzschluss.java) | 14 cuts: the six of the JDK 8 port, plus `Font.getStringBounds/getLineMetrics/getMaxCharBounds` → `tsb.port.Schrift`, because JDK 17's `PlainView` measures through `Font` and that path leads into `sun.font` |
| 7 | `desktop8-0.1.0.jar` | `tsb.port.logging` is still the JDK 8 `PlatformLogger`, because JDK 17's hangs on `jdk.internal.logger` |

What the phones must bring: `java.lang.ref.Cleaner`, `Method.getParameterCount`, the limited
`AccessController.doPrivileged` — Android 14 has them, the RoboVM fork
(`github.com/thorstenstueker/robovm`, branch `TSjvm17`) since release `3.0.0-tsb.20260922.2`.

Measured on 22.09.2026 with `examples/ERPMobile` of RapidFX: the sign-in form renders with FlatLaf
Arc Dark on the iOS simulator and on the Android 14 emulator, sign-in and the article list (SQLite,
JTable, JComboBox) work on Android.

FlatLaf for Android (`flatlaf-android-*.jar`) goes through steps 4 and 5 as well. FlatLaf is Apache-2.0
licensed, and this sentence is the notice of that modification.

## History: the JDK 8 port (until 22.09.2026)

The port did not come out of a compiler run over the OpenJDK sources but out of four rewrites of the compiled
classes of `jre/lib/rt.jar` of **BellSoft Liberica JDK 1.8.0_492-b09** (4,766 `java.desktop` classes and
five of `java.applet`; unmodified source `bellsoft-jdk8u492+9-src.tar.gz` in the [releases](../../releases)),
plus a set of changed and added classes played back into the jar:

| Step | Tool | What it does |
|---|---|---|
| 1 | `tools/Entnativisierer.java` | 438 native methods: 66 emptied, 372 made to throw |
| 2 | `tools/Umbenenner.java` | `java.beans` → `tsb.port.beans`, `sun.util.logging` → `tsb.port.logging` (190 classes) |
| 3 | `tools/Laderwechsel.java` | `ClassLoader.getSystemClassLoader()` → `tsb.port.Lader.system()` (18 places) |
| 4 | `tools/Kurzschluss.java` | `FontUtilities.fontSupportsDefaultEncoding` → `true`, `EventQueue.isDispatchThread` → `true`, … |
| 5 | [`patch/`](patch) | nineteen changed OpenJDK classes and the seams, compiled with `-source 8 -target 8` against the rewritten jar and replaced inside it ([`tools/bauen.sh.template`](tools/bauen.sh.template)) |

Nine of the nineteen patches only removed a `System.loadLibrary`, four only redirected `SharedSecrets`
— that is what steps 1 and 2 of the JDK 17 pipeline replace. The background and the measurements behind
every cut are in [`docs/`](docs) (the experiment write-ups are in German).

## Contact

tsb Thorsten Stueker Buero for Technology development, Lemgoer Str. 7, 32683 Barntrup, Germany;
licensequestions@stueker.org
