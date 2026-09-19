# awt — `java.desktop` for phones

Files that go onto the device, and not a single line of Java code of our own. The code for it
sits in `../swing-android/` (Android) and `../swing-ios/` (iOS).

| | |
|---|---|
| `libs/desktop8-0.1.0.jar` | 8.1 MB — JDK 8's `java.desktop`, trimmed and rewritten |
| `libs/flatlaf-android-3.7.2.jar` | 1.0 MB — FlatLaf 3.7.2, through the same rewrites |
| `libs/flatlaf-3.7.2.jar` | the same version **unchanged**, for the desktop — see below |
| `libs/flatlaf-intellij-themes-*.jar` | 46 themes, once rewritten and once not |
| `libs/flatlaf-res/` | FlatLaf's 12 `.properties` — its appearance is there, not in the code |
| `libs/fonts/` | four cuts of Inter, SIL OFL |

**Two copies of FlatLaf, and that is not an oversight.** The rewritten one demands
`tsb.port.beans.PropertyChangeListener`, because it is meant to run against the port. On a real
JVM that class does not exist and `UIManager.setLookAndFeel` ends in a `NoClassDefFoundError` —
measured, after the attempt to simply put the Android version on the desktop class path failed on
exactly that. One megabyte duplicated is the price of the fastest round showing the same thing as
the device.

**The fonts are the reason the same form has the same widths on Android and iOS.** Without them
every target measures with its own system font: 65 points against 62 for the same two-word sample,
and two points more leading. That is 4.8 % of tracking and, on twenty table rows, forty points of
total height.

## Why as files and not as dependencies

The same as with ConstraintLayout (`Toolchain.constraintLayout()`): the Android branch of this
project keeps to one rule — **no Gradle on the device route, no AAR resolver** — and a jar beside
the experiment that shows why it is needed is cheaper than a dependency resolver inside a
command-line tool.

Both jars are **derived** anyway: they do not come out of a source but out of four rewrites of
somebody else's jar. There is no repository they could be loaded from.

## How they came about

All four tools sit in the experiments and are justified there. In this order:

| Step | Tool | what it does |
|---|---|---|
| 1 | `s1-portgroesse/Entnativisierer.java` | 438 native methods: empty 66, make 372 throw — with an error text of their own, so that the work list falls out in call order |
| 2 | `s2-swing-android/Umbenenner.java` | `java.beans` → `tsb.port.beans`, `sun.util.logging` → `tsb.port.logging` (190 classes). Android's own versions are subsets, and the bootclasspath wins |
| 3 | `s2-swing-android/Laderwechsel.java` | `ClassLoader.getSystemClassLoader()` → `tsb.port.Lader.system()` in **18 places**. Android's system loader does not see the APK |
| 4 | `s2-swing-android/Kurzschluss.java` | two methods pinned to fixed results: `FontUtilities.fontSupportsDefaultEncoding` → `true`, `EventQueue.isDispatchThread` → `true` |

On top of that come the seams in `s1-portgroesse/patch/`, which exist as source and are played
back into the jar — `tsb.port.Portgrafik`, `Zeichenwerk`, `Schrift`, `TsbToolkit`,
`TsbGraphicsEnvironment`, `TsbSchriftverwaltung`, `Lader`, `sun.font.TsbFont2D`.

And `java.applet`: five classes from JDK 8, unchanged apart from the same rewrites. Seventeen
classes in the port refer to it, among them `JComponent`, `RepaintManager` and `PopupFactory`.

**To rebuild it, `s2-swing-android/bauen.sh` is the template** — the order stands there in
executable form.

> The names inside the port are German — `Zeichenwerk`, `Schriftwerk`, `Schrift`, `Portgrafik`,
> `Bildschirm`. That is deliberate and it is the one exception to "everything in English": those
> names live inside `desktop8-0.1.0.jar`, which does not come out of the Gradle build, so renaming
> them means rebuilding the port through all four steps above. Every class that implements one of
> those interfaces says so in its javadoc, so a reader meets the explanation where they meet the
> name.

## The check that keeps this honest

`swing-android/src/test/.../SeamsUpToDateTest` compares the timestamp of the jar against the seam
sources in `patch/`. Change a seam and forget the jar, and you notice nothing: the build is green,
the app starts, and it behaves as before — the worst case, because it looks like a mistake in
reasoning and is not one. That happened several times while building this route.

`PortCompatibilityTest` answers the other question: `:swing` and `:swing-android` compile against
the **building** JDK, and run against the port, which is JDK 8. Use a method that has only existed
since Java 9 and the compiler says nothing. The test reads the constant pool of every compiled
class and holds it against what is actually in the jar.

## Licence

The port is trimmed OpenJDK 8: **GPLv2 with Classpath Exception**. FlatLaf is **Apache-2.0**. Both
permit linking into a closed program; the Classpath Exception exists for exactly that. The rewrites
change nothing about it — they are modifications of the GPL part and remain under the GPL, which
is why the tools that produce them sit in the repository and are described.

Inter is under the **SIL Open Font License**, which permits bundling.

The `LICENSE`/`NOTICE` point from stage 0 of the plan is therefore **still open**: the files belong
in the root of the repository, with OpenJDK named explicitly.
