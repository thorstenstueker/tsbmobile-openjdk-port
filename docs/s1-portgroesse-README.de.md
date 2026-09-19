# S1 — Wie groß ist ein `java.desktop`-Port wirklich?

Gemessen am 11.09.2026, auf Liberica JDK 21, gegen FlatLaf 3.7.2.

## Die Frage

tsbMobile soll AWT/Swing auf Android und iOS zeichnen. Beide Laufzeiten bringen davon
**nichts** mit — nachgezählt in `~/.rfxmobile/robovm-3.0.0-SNAPSHOT/lib/robovm-rt.jar`:
`java/awt` 0 Klassen, `javax/swing` 0, `sun/java2d` 0. Die einzigen `java.awt.*`-Klassen
dort sind `NumericShaper` und `TextAttribute`, Erbe von Androids libcore.

Also muss `java.desktop` mitgeliefert werden. Die Frage ist: **wie viel davon, und was
daran ist nativ?** Vorher gab es dazu keine Zahl, nur Ehrfurcht vor der Größe von Swing.

## Wie gemessen wurde

Nicht statisch, sondern empirisch. `Maskenprobe.java` baut eine Maske aus **allen 22
Bausteinen des Desktop-Katalogs** — die Liste stammt aus
`docs/ai/generated/widgets-desktop.md`, also aus dem Katalog selbst —, richtet FlatLaf ein,
legt sie aus und malt sie mit `paint(Graphics)` in ein `BufferedImage`.

Ohne Fenster, auf demselben Weg wie `SwingApp.snapshot()` heute schon geht. Ein echtes
Fenster hätte die Messung mit dem Fenstersystem des Mac verfälscht, das auf einem Telefon
ohnehin nicht vorkommt.

```bash
javac -cp flatlaf-3.7.2.jar -d . Maskenprobe.java
java  -cp .:flatlaf-3.7.2.jar -verbose:class Maskenprobe > verbose.txt
```

`-verbose:class` nennt zu jeder geladenen Klasse **das Modul**, aus dem sie stammt. Damit
muss man keine Paketliste raten; die JVM sagt es selbst.

Die Maske wurde wirklich gezeichnet, nicht nur gebaut — Beleg aus dem Lauf:

```
PROBE breite=68 hoehe=17 bausteine=22
```

Das ist eine echte Schriftmetrik (`stringWidth("Hallo Welt")`), also lief `sun.font` mit.

## Das Ergebnis

### Umfang

| | |
|---|---|
| geladene Klassen insgesamt | 2804 |
| davon aus **`java.desktop`** | **1055** |
| aus `java.base` | 218 |

Verteilung der 1055:

```
161  javax.swing            82  java.awt              45  sun.font
140  javax.swing.text       53  sun.awt               40  java.awt.event
102  javax.swing.plaf.basic 50  sun.java2d.marlin     39  javax.swing.text.html
                            37  sun.java2d.loops      32  javax.swing.event
                            33  sun.java2d.pipe
```

**1055 Klassen für 22 Bausteine.** Das ist viel, aber es ist kein *java.desktop* — das
Modul hat rund 3400 Klassen. Es ist ein knappes Drittel, und es ist eine *Liste*, keine
Schätzung: `desktop.txt`.

### Nativer Anteil — der eigentliche Befund

| | |
|---|---|
| native Methoden in den 1055 Klassen | **115** |
| davon `initIDs` / `registerNatives` | **30** (reine JNI-Feld-Registrierung, beim Port ersatzlos) |
| **native Methoden in `javax.swing`** | **0** |

Wo die 85 echten sitzen:

```
25  sun.font            ← Schriftrasterung
21  sun.java2d.loops    ← Blit, DrawLine, FillRect, DrawGlyphList …
14  sun.lwawt.macosx    ← die macOS-Rückseite
12  apple.laf           ← Aqua
 6  sun.awt
 2  sun.java2d.pipe · 2 sun.java2d · 2 java.awt
```

**Das ist die ganze Nachricht dieses Experiments.** Alles Native steckt in genau der
Schicht, die Skia ohnehin ersetzen soll: Rasterung, Schrift, Plattform-Rückseite. Die 1040
portablen Klassen darüber — `javax.swing`, `javax.swing.text`, `javax.swing.plaf.basic`,
`java.awt` — sind reines Java.

Die verbleibenden Einzelfälle in `java.awt` sind zwei: `AWTEvent.nativeSetSource` (braucht
Peers, die es nicht geben wird) und `Cursor.finalizeImpl` (ein Zeiger, den es nicht geben
wird). Beide sind beim Port zu streichen, nicht zu ersetzen.

### Zeichenketten-Ladungen

| | |
|---|---|
| `javax.swing.UIManager` | **5** Stellen mit `Class.forName`/`newInstance` |
| `java.awt.Toolkit` | 2 |
| `sun.font.FontManagerFactory`, `sun.awt.AppContext` | 0 |

Wenig — aber genau die Stellen, die MobiVMs AOT nicht von allein findet (er folgt
Referenzen und sonst nichts; dreimal bereits zugeschlagen, siehe
`CompilerEnhancementHints.md` Abschnitt D) **und** die eine Bytecode-Umbenennung
`java/awt/…` → `tsb/awt/…` nicht von allein mitnimmt. Sie sind einzeln zu behandeln, nicht
zu umgehen — aber es sind sieben, nicht siebzig.

## Was das für den Plan heißt

Die Größenordnung verschiebt sich **nach unten**. Der Port ist nicht „Swing
nachimplementieren", sondern:

1. **1040 Klassen reines Java übernehmen** — mechanisch, aus OpenJDK, mit der
   Classpath Exception gedeckt.
2. **85 native Methoden nicht portieren, sondern ersetzen** — durch eine
   `Graphics2D`-Fassung auf Skia. Auf Android kostenlos, weil `android.graphics.Canvas`
   selbst Skia ist.
3. **Sieben Reflexionsstellen** von Hand behandeln.

---

# Messung 2 — Lädt Android Klassen in `java.*`?

**Ja. Die Sperre, die ich erwartet hatte, gibt es nicht.**

Ich war davon ausgegangen, dass Androids Laufzeit Klassen in `java.*` abweist
(„Prohibited package name") und ein mitgelieferter Port deshalb umbenannt werden müsste —
`java/awt/…` → `tsb/awt/…`, im Bytecode, für Formular, Port und FlatLaf zugleich. Das wäre
ein eigener Arbeitsblock gewesen, samt der Frage, was mit Klassennamen in Zeichenketten
passiert.

Gemessen auf **Android 16 (API 36)**, Emulator `Medium_Phone_API_36.0`. Ein APK von 8,6 kB
mit vier Klassen, die es dort nicht geben dürfte, und einer Activity, die sie über
`Class.forName` sucht:

```
GELADEN   java.awt.Farbprobe wurde geladen
GELADEN   java.lang.Farbprobe wurde geladen
GELADEN   java.util.Farbprobe wurde geladen
GELADEN   javax.swing.Farbprobe wurde geladen
```

Alle vier. Auch `java.lang`.

**Die Grenze des Befundes, damit niemand zu viel hineinliest:** geprüft sind *neue* Namen,
die es im Bootclasspath nicht gibt. Eine Klasse, die eine vorhandene **überschatten** soll
— etwa ein eigenes `java.lang.String` — verliert weiterhin gegen den Bootclasspath, weil
der Klassenlader zuerst den Elternteil fragt. Für den Port ist das gleichgültig: `java.awt`
und `javax.swing` gibt es auf Android nicht, es kollidiert also nichts.

Interessant war auch, was *javac* sagt: er weigert sich, in ein Paket zu übersetzen, das im
JDK existiert („Package ist in einem anderen Modul vorhanden: java.desktop"). Umgangen mit
`--patch-module`. `d8` hatte keinerlei Einwand.

**Folge für den Plan:** keine Umbenennung, kein Relokationsschritt, und die
Zeichenketten-Frage stellt sich nur noch für MobiVMs AOT — nicht mehr zusätzlich für die
Umbenennung.

Dateien: `javaprobe/` (Quellen, Manifest, `fertig.apk`).

---

# Messung 3 — Verträgt MobiVMs AOT den Klassenberg?

> **Warnung zum ersten Anlauf, der hier stand.** Ich hatte gemeldet: „74 MB Mach-O, null
> harte Fehler". Diese Zahl war nicht belastbar. Der Lauf war nach zehn Minuten
> abgebrochen worden — aber nur die aufrufende Hülle, nicht der Prozess dahinter. Der
> schrieb weiter, während ich einen zweiten Lauf in **dasselbe** Verzeichnis startete.
> Ergebnis: ein `classes0.jar` von **78 Gigabyte**, von zwei Läufen gleichzeitig gefüllt.
> Dass eine Binärdatei dalag, hieß nicht, dass sie fertig war.
>
> Unten steht der saubere Lauf: einer, unangetastet bis zum Ende.

## Die Quelle: JDK 8, nicht JDK 21

Das ist die erste Entscheidung, und sie fällt gegen die Gewohnheit. MobiVMs
Klassenbibliothek steht auf **Java-8/9-Niveau** (gemessen in `m9-mobivm3`: `List.of` ja,
`String.strip` nein, keine Records, kein `StringConcatFactory`). Ein `java.desktop` aus
JDK 21 würde gegen Methoden linken, die es dort nicht gibt — und Records brechen MobiVMs
AOT ohnehin, weil Soot älter ist als sie (hat schon einmal *jedes* `Try/Catch` auf iOS
gekostet, `rfxc-mobile/DELTA.md` Änderung 4).

`liberica-jdk-8/jre/lib/rt.jar` enthält genau den passenden Stand:

| | |
|---|---|
| `javax/swing` | 1951 Klassen |
| `java/awt` | 599 |
| `sun/java2d` | 457 |
| `sun/font` | 172 |
| `java/beans` | 165 |
| `javax/accessibility` | 28 |

## Der Lauf

```bash
robovm -home ~/.rfxmobile/robovm-3.0.0-SNAPSHOT \
       -cp .:desktop8.jar -os ios -arch arm64-simulator -target ios \
       -d aot -o Zeichenprobe Zeichenprobe
```

`Zeichenprobe.java` ist absichtlich winzig: ein `JPanel`, ein `JLabel`, ein `JButton`,
`doLayout()`. Die Frage ist nicht, ob es zeichnet, sondern ob der AOT den Berg übersetzt.

**Erster Lauf** (3786 Klassen, ohne `sun.swing`): ein **74 MB großes `Mach-O 64-bit
executable arm64`**, 0 harte Fehler, 82 Warnungen — allesamt `phantom class`, und allesamt
Pakete, die ich beim Entpacken vergessen hatte:

```
22 sun.swing          11 javax.sound.sampled     5 javax.print
 5 sun.misc            5 javax.imageio           4 com.sun.beans
 2 java.applet         1 com.sun.java.swing.plaf.windows
```

Die Hälfte davon will ein Telefon gar nicht — Drucken, Klang, Applets, Windows-LAF. Der
zweite Lauf nimmt `sun.swing`, `javax.imageio`, `com.sun.beans`, `com.sun.java.swing` dazu
und lässt die anderen bewusst weg.

**MobiVM meldet fehlende Klassen nur als Warnung und bricht nicht ab** (`m1-ios/README.md`
Z. 62-64). Eine Warnung ist hier also kein Freispruch, sondern eine offene Rechnung: was
phantom bleibt, fliegt erst zur Laufzeit auf. Die Liste oben ist deshalb Teil des Befundes,
nicht Beiwerk.

## Die Größe

74 MB unstripped für den Simulator. Zum Vergleich: das ERP ohne Swing liegt bei 56 MB
(`m9-mobivm3`). Swing kostet also rund **18 MB**, bevor `-treeshaker` überhaupt
eingeschaltet wurde — der steht heute auf `none` und war bisher nie nötig.

---

## Zwei Fallen im Aufbau, beide selbstgemacht

Sie stehen hier, weil sie jeden treffen, der das nachbaut.

**Der Klassenpfad darf das Ausgabeverzeichnis nicht enthalten.** Mit `-cp "."` und
`-d aot`, wobei `aot` in `.` liegt, packt robovm den Klassenpfad ein, findet dabei die
wachsende `lib/classes0.jar` und packt sie in sich selbst. Ergebnis: **78 GB**, mit
Lese- *und* Schreibkennung auf derselben Datei (`lsof` zeigt `11w` und `12r`). Getrennt —
Klassen in `klassen/`, Ausgabe nach `/tmp` — ist dieselbe Datei **22 Byte** groß.

**Einen AOT-Lauf abbrechen bricht nur die Hülle ab.** Der Java-Prozess dahinter lief weiter
und schrieb in dasselbe Verzeichnis wie sein Nachfolger. Ein Zwischenstand von „74 MB, null
Fehler" stammte aus dieser Überlagerung und war wertlos. Wer abbricht, muss den Prozess
suchen und erlegen.

---

## Der Lauf auf dem Simulator

Das Bündel: die Binärdatei, `lib/`, ein minimales `Info.plist`, `codesign -f -s -`,
`simctl install` + `simctl launch --console-pty`. 77 MB.

### Erster Start

```
java.lang.NoClassDefFoundError: sun/util/CoreResourceBundleControl
    at java.awt.Toolkit$5.run(Toolkit.java:1657)
    at java.awt.Toolkit.<clinit>(Toolkit.java:1649)
    at java.awt.Component.<clinit>(Component.java:593)
    at Zeichenprobe.main(Zeichenprobe.java:12)
```

**Kein Scheitern, sondern ein Lebenszeichen.** Das Programm startet, lädt
`java.awt.Component`, arbeitet `Toolkit`s statischen Initialisierer ab. Die erste der 58
Phantom-Klassen ist fällig geworden — genau wie oben angekündigt.

Zwölf Klassen einzeln nachgelegt, **nicht paketweise**: MobiVM bringt eigene `sun.util`
(47 Klassen), `sun.misc` (55) und `sun.security.util` (48) mit, und die dürfen nicht
überschrieben werden. Danach 4736 Klassen, 34 statt 58 Phantome.

### Zweiter Start — und hier liegt die Grenze

```
java.lang.UnsatisfiedLinkError: dlopen(libawt.dylib) — no such file
    at java.lang.System.loadLibrary(System.java:1807)
    at java.awt.Toolkit.loadLibraries(Toolkit.java:1629)
    at java.awt.Toolkit.<clinit>(Toolkit.java:1666)
    at java.awt.Component.<clinit>(Component.java:593)
```

Der vorige Fehler ist weg. Es scheitert **eine Zeile später** — und nicht mehr an Java.

**Das ist der Befund, auf den dieses Experiment hinauslief.** Die Java-Schicht trägt: 4736
Klassen aus Swing und AWT werden geladen, verknüpft und ausgeführt, bis `java.awt.Toolkit`
in seinem statischen Initialisierer `System.loadLibrary("awt")` ruft. Dort endet das reine
Java und beginnt `libawt` — dieselbe Schicht, die Messung 1 als 85 native Methoden in
`sun.java2d`, `sun.font` und der Plattform-Rückseite ausgewiesen hat.

**Die Naht sitzt genau dort, wo Skia hingehört.** Nicht irgendwo verstreut in Swing, sondern
an einer benennbaren Stelle: `Toolkit.loadLibraries`.

---

## Fazit von S1

| Frage | Antwort |
|---|---|
| Wie groß ist der Port? | **1055 Klassen** für 22 Bausteine (von ~3400 im Modul) |
| Wie viel davon ist nativ? | **115 Methoden**, davon 30 `initIDs`; **`javax.swing`: null** |
| Wo sitzt das Native? | `sun.font` (25), `sun.java2d.loops` (21), Plattform-Rückseite (26) |
| Braucht Android eine Umbenennung? | **Nein.** `java.awt`, `java.lang`, `javax.swing` laden alle |
| Verträgt MobiVMs AOT den Berg? | **Ja.** 4736 Klassen, 0 harte Fehler, 79 MB arm64 |
| Läuft es? | **Bis `Toolkit.loadLibraries`.** Dann fehlt `libawt` |

Vier von fünf Sorgen aus dem Plan sind entschärft. Die fünfte — `Graphics2D` auf Skia — war
nie strittig, und sie ist jetzt **lokalisiert** statt vermutet.

---

# Messung 4 — Wie weit kommt Swing wirklich?

Nachdem der AOT stand, ging es Schritt für Schritt weiter. Jede Zeile hier ist ein eigener
Lauf über den Simulator, jede eine gefundene und behobene Ursache.

| # | Gescheitert an | Ursache | Behebung |
|---|---|---|---|
| 1 | `Toolkit.<clinit>` | `sun.util.CoreResourceBundleControl` fehlte | 12 Klassen **einzeln** nachgelegt |
| 2 | `Toolkit.loadLibraries` | `System.loadLibrary("awt")` | Quelle geflickt, **beide** Stellen |
| 3 | `Component.initIDs` | native JNI-Registrierung | 438 native Methoden entnativisiert |
| 4 | `AppContext.<clinit>` | `SharedSecrets.setJavaAWTAccess` | Applet-Haken gestrichen |
| 5 | `EventQueue.<clinit>` | `SharedSecrets.getJavaSecurityAccess` | vier Nutzer umgestellt |
| 6 | `JPanel.updateUI` | LAF über `Class.forName` | `-forcelinkclasses` |
| 7 | `UIManager.setLookAndFeel` | **offen** — siehe unten | |

## Was dabei gelernt wurde

**Die zweite `libawt`-Stelle steht in der Quelle angekündigt.** Der Kommentar über
`Toolkit.loadLibraries` sagt wörtlich: *„If you change loadLibraries(), please add the
change to java.awt.image.ColorModel.loadLibraries()"* — weil Klassen in `java.awt.image`
geladen werden können, ohne dass `Toolkit` je angefasst wurde. Wer nur `Toolkit` flickt,
fällt später darauf herein.

**Die Unverträglichkeiten sind zählbar.** `sun.misc.SharedSecrets` schlug dreimal zu — und
als ich statt weiterzuflicken einmal nachzählte, waren es **vier Klassen im ganzen Port**:
`EventQueue`, `RepaintManager`, `TransferHandler`, `DocumentHandler`. Alle vier holen sich
dasselbe: einen Zugang, um eine Aktion unter dem *Schnitt* zweier Sicherheitskontexte
laufen zu lassen. Auf einem Telefon ohne Sicherheitsverwalter ist der Schnitt zweier
gleicher Mengen die Menge selbst — `tsb.port.Sicherheitszugang` führt die Aktion aus, und
das war es.

**`-forcelinkclasses` allein genügt nicht.** Nach dem Force-Linking wuchs die Binärdatei von
79 auf 88 MB, die Klassen waren also drin — und der Fehler blieb wörtlich derselbe. Das lag
daran, dass `UIManager` die Ursache verschluckt:
`catch (Exception e) { throw new Error("Cannot load " + name); }`. Erst eine Probe, die
jede Ursache der Kette ausgibt, zeigte, dass es gar nicht am Finden lag.

**Ein Verdacht, der sich als falsch erwies.** Ich hielt MobiVMs Kontext-Klassenlader für
`null`, weil Swings `loadSystemClass` ihn benutzt. Isoliert geprüft — eine Sonde ohne
Swing, eine Minute statt zehn:

```
Kontextlader:  java.lang.PathClassLoader[…]
Class.forName(name, true, kontext):  OK
```

Falsch geraten, billig widerlegt. Die isolierte Sonde (`ladeprobe/`) ist dafür das richtige
Werkzeug, nicht ein weiterer Lauf über den ganzen Berg.

## Wo es jetzt steht

```
OK      LAF ueber Reflexion finden: javax.swing.plaf.metal.MetalLookAndFeel
OK      LAF direkt bauen: Metal
HALT    Look-and-Feel setzen
        java.lang.NullPointerException bei java.lang.Class.classForName(Native Method)
```

Die LAF-Klasse wird **gefunden und gebaut**. Es scheitert, wenn `UIManager` die
Vorgabetabelle füllt: `UIDefaults.getUIClass` lädt die UI-Delegaten über ihren Namen. Dass
dieselbe `Class.forName` in der isolierten Sonde funktioniert, lässt nur einen Schluss zu —
**der übergebene Name ist null**, die Vorgabetabelle also leer. Dazu passt, dass alle
folgenden Schritte in `MultiUIDefaults.getUIError` landen.

*Das ist ein Verdacht, keine Messung.* Er ist als Nächstes zu prüfen, und zwar wieder
isoliert: `new MetalLookAndFeel().getDefaults()` aufrufen und die Tabellengröße ausgeben.

## Das Werkzeug, das dabei entstand

`Entnativisierer.java` (ASM) nimmt jeder nativen Methode das `native` und gibt ihr einen
Rumpf — in **zwei Sorten**, und der Unterschied ist der ganze Punkt:

| | |
|---|---|
| `initIDs`, `registerNatives` | **66**, leerer Rumpf — sie registrieren JNI-Kennungen für eine Bibliothek, die es nicht gibt |
| alles andere | **372**, wirft `UnsatisfiedLinkError` **mit dem eigenen Namen darin** |

Keine Attrappe, sondern ein Messinstrument. Eine `Blit`-Methode, die still nichts tut,
ergäbe ein leeres Fenster und niemand wüsste warum. So nennt jeder Lauf die nächste
wirklich gebrauchte native Methode, in der Reihenfolge, in der sie gebraucht wird — und
diese Liste ist die Arbeitsliste für den Skia-Unterbau.

## Ehrliche Bilanz

**Nicht erreicht:** Swing zeichnet auf iOS. Dazu fehlt mindestens die Vorgabetabelle des
Look-and-Feel, und danach der ganze Skia-Unterbau.

**Erreicht, und das ist das eigentliche Ergebnis:** 4736 Klassen aus Swing und AWT
übersetzen durch MobiVMs AOT ohne harten Fehler, starten auf einem iPhone-Simulator, und
initialisieren `Toolkit`, `Component`, `AppContext`, `EventQueue` und `UIManager`. Ein
`MetalLookAndFeel` wird gefunden und gebaut. **Die Hindernisse auf diesem Weg waren
durchweg klein, benennbar und einzeln behebbar** — fehlende Einzelklassen, zwei
`loadLibrary`-Aufrufe, ein Applet-Haken, vier Nutzer einer Sicherheitsschnittstelle.

Keines war struktureller Art. Das war die Frage, und sie ist beantwortet.

---

# Messung 5 — Swing läuft auf iOS, bis zur Schriftmetrik

Der Stand nach weiteren sieben Läufen:

```
OK      Toolkit besorgen:        tsb.port.TsbToolkit
OK      Grafikumgebung besorgen: tsb.port.TsbGraphicsEnvironment
OK      LAF ueber Reflexion:     javax.swing.plaf.metal.MetalLookAndFeel
OK      LAF direkt bauen:        Metal
OK      Vorgabetabelle fuellen:  Eintraege: 640
OK      Look-and-Feel setzen:    Metal
OK      JPanel bauen:            Behaelter mit 0 Kindern
OK      JLabel und JButton:      2 Bausteine
HALT    auslegen
        UnsatisfiedLinkError: tsbMobile: sun.font.StrikeCache.getGlyphCacheDescription([J)V
                              braucht einen Unterbau
          bei sun.font.SunFontManager.<clinit>
          bei sun.swing.SwingUtilities2.getFontMetrics
          bei javax.swing.JComponent.getFontMetrics
```

**Echte Swing-Bausteine, gebaut auf einem iPhone-Simulator, mit ihren UI-Delegaten.** Der
Halt liegt genau dort, wo Messung 1 ihn vorhergesagt hatte: in `sun.font`.

## Die Hindernisse, und warum sie in Familien kommen

| # | Hindernis | Familie |
|---|---|---|
| 8 | `awt.toolkit` nicht gesetzt → `Class.forName(null)` | **Plattform per Systemeigenschaft** |
| 9 | `java.awt.graphicsenv` nicht gesetzt | dieselbe |
| 10 | `KeyboardFocusManagerPeerProvider` nicht implementiert | Einzelfall, eine Methode |
| 11 | `MethodUtil`-Trampolin: `bouncer cannot be found` | Applet-Sandkasten |
| 12 | `Unsafe.ensureClassInitialized` fehlt | **JDK-8-Interna gegen Androids libcore** |
| 13 | `java.awt.event.NativeLibLoader` lädt `libawt` | **native Bibliothek** |

**Zwei Familien tragen fast alles.** „Das JDK sucht seine Plattformklassen über
Systemeigenschaften" — auf dem Desktop setzt sie der Starter, auf dem Telefon niemand. Und
„JDK-8-Interna treffen auf Androids libcore" — MobiVMs `sun.misc`, `sun.util` sind anders
geschnitten als Oracles; bisher vier Vorfälle (`SharedSecrets` zweimal, `Unsafe`,
`PlatformLogger` auf Android).

## Die Lehre: beim dritten Vorfall zählen statt flicken

Zweimal hat sich dasselbe bewährt, und zweimal habe ich es zu spät getan.

`SharedSecrets` schlug dreimal einzeln zu. Dann gezählt: **vier Nutzer im ganzen Port**
(`EventQueue`, `RepaintManager`, `TransferHandler`, `DocumentHandler`), eine gemeinsame
Ersatzfassung, fertig.

`System.loadLibrary` schlug dreimal einzeln zu — `Toolkit`, `ColorModel`,
`java.awt.event.NativeLibLoader`. Dann gezählt: **zehn Stellen**, davon eine (`X11GraphicsEnvironment`)
die nie vorkommt. Sieben in einem Zug behandelt.

Die Regel für den Rest des Ports: **beim dritten Vorfall derselben Art aufhören zu flicken
und zählen.**

## Der Entnativisierer hat geliefert, wofür er gebaut wurde

Die erste wirklich gebrauchte native Methode heißt beim Namen:
`sun.font.StrikeCache.getGlyphCacheDescription`. Kein leeres Fenster, kein Rätsel — eine
Adresse. Und sie sagt zugleich, **womit der Skia-Unterbau anfangen muss**: nicht mit dem
Zeichnen, sondern mit der **Schriftmetrik**.

Das ist eine Präzisierung des Plans. `JLabel` kann ohne `FontMetrics` keine bevorzugte Größe
nennen, also scheitert schon das Auslegen — lange bevor irgendetwas gemalt wird.

## Zwei Fehler, die dazugehören

**Ein halber Flicken.** `MethodUtil.invoke` war ersetzt und als erledigt gemeldet — das
Trampolin wurde aber im statischen Feld gebaut (`private static final Method bounce =
getTrampoline();`). Die Klasse warf unverändert weiter.

**Eine Erfolgsmeldung ohne Prüfung.** Eine Simulator-Ausgabe wurde gezeigt, ohne zu prüfen,
dass der AOT mit `RUECKGABE=1` abgebrochen war; zu sehen war der alte Stand. Ursache:
`xcode-select` lieferte im Hintergrundlauf einen leeren Pfad, worauf robovm seine Hilfeseite
druckt — dieselbe Falle wie in `m9-mobivm3`. Seitdem wird `DEVELOPER_DIR` ausdrücklich
gesetzt.

## Was als Nächstes zu klären ist

1. **Schriftmetrik auf Skia.** Der erste Baustein des Unterbaus, nicht das Zeichnen.
   `sun.font.StrikeCache` und `SunFontManager` sind die Adressen.
2. **Android nachziehen.** Dort hält `sun.util.logging.PlatformLogger` den Start auf —
   Androids Fassung überschattet unsere und kennt `getLogger(String)` nicht. Der
   `Umbenenner` in `../s2-swing-android/` ist dafür geschrieben, aber noch nicht gelaufen.
3. **Native Bibliotheken einbinden** (S3a): `IosTarget.aot()` reicht weder `-libs` noch
   `-frameworks` durch.
4. **Die Bildrate.** Dazu gibt es im Repositorium bis heute keine einzige Zahl.

## Dateien dieses Abschnitts

| | |
|---|---|
| `Zeichenprobe.java` | die Sonde, die jede Ursache der Kette ausgibt |
| `Entnativisierer.java` | das ASM-Werkzeug, plus `nativ-werfend.txt` / `nativ-geleert.txt` |
| `patch/` | die geflickten JDK-Quellen, jede Änderung mit `tsbMobile:` kommentiert |
| `ladeprobe/` | die isolierte Sonde für den Klassenlader |
| `desktop8.jar` | der Port; `desktop8-ohne-nativ.jar` derselbe, entnativisiert |

## Dateien

| | |
|---|---|
| `Maskenprobe.java` | die Maske aus 22 Bausteinen |
| `verbose.txt` | das rohe Ladeprotokoll der JVM |
| `desktop.txt` | die 1055 Klassen, eine je Zeile |
| `nativ.txt` | Klassen mit nativen Methoden, mit Anzahl |
| `namen.txt` | jede native Methode mit Signatur |

Nachvollziehen:

```bash
cd mobile/experiments/s1-portgroesse
javac -cp ../../../compiler/intellij-plugin/src/main/resources/lib/flatlaf-3.7.2.jar -d . Maskenprobe.java
java -cp .:../../../compiler/intellij-plugin/src/main/resources/lib/flatlaf-3.7.2.jar \
     -verbose:class Maskenprobe > verbose.txt
grep "jrt:/java.desktop" verbose.txt | wc -l
```
