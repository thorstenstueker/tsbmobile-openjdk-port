# S2 — Swing auf Android

Gemessen am 11.09.2026, Emulator `Medium_Phone_API_36.0` (Android 16, API 36).

## Die Frage

S1 hat gezeigt, dass 4736 Klassen aus JDK 8s Swing und AWT durch MobiVMs AOT gehen und auf
iOS bis zur Schriftmetrik laufen. **Gilt dasselbe auf Android?** Und iteriert es dort
wirklich schneller?

Antwort auf die zweite Frage vorweg: **ja, drastisch.** Ein Durchlauf dauert auf Android
etwa eine Minute (`d8` braucht 7 Sekunden für 4736 Klassen), auf iOS neun. Deshalb gehört
jede Ursachensuche hierher.

## Das Ergebnis

```
OK      Toolkit besorgen:        sun.awt.HeadlessToolkit
OK      Klasse java.awt.Component finden: java.awt.Component
OK      Toolkit initialisieren:  headless=true
OK      LAF direkt bauen:        Metal
OK      Vorgabetabelle fuellen:  Eintraege: 640
OK      Look-and-Feel setzen:    Metal
OK      JPanel bauen:            Kinder: 0
HALT    JButton bauen und auslegen
        ExceptionInInitializerError bei sun.font.SunFontManager$1.run
        SecurityException: Unsafe access denied bei sun.misc.Unsafe.getUnsafe
```

**Android steht damit an derselben Grenze wie iOS: `sun.font`.** Zwei verschiedene Wege, zwei
verschiedene Symptome — auf iOS eine fehlende native Methode
(`StrikeCache.getGlyphCacheDescription`), hier ein verwehrter Zugriff auf `Unsafe` — und
dieselbe Ursache dahinter: die Schriftschicht hat keinen Unterbau.

APK: 3,3 MB. Dex: 7,4 MB für 4795 Klassen.

## Was Android anders macht als iOS

### Der Bootclasspath gewinnt

Androids Laufzeit lädt Klassen in `java.*` und `javax.*` bereitwillig — das war S1,
Messung 2. **Eine Klasse, die eine vorhandene überschattet, verliert aber.** Das traf uns
dreimal:

| | |
|---|---|
| `sun.util.logging.PlatformLogger` | Android hat sie **ohne** `getLogger(String)`; JDK 8s `Component.<clinit>` ruft genau die |
| `java.beans.PropertyChange*` | Androids Fassung ist eine Teilmenge |
| `sun.misc.Unsafe.getUnsafe()` | Android erlaubt das **nur Klassen vom Bootclasspath** — sonst `SecurityException` |

Die ersten beiden lösen sich durch Umbenennen (`Umbenenner.java`), die dritte durch
Entfernen.

### Umfang der Umbenennung, gemessen

Androids Bootclasspath wurde vom Gerät geholt (`core-oj.jar`, `core-libart.jar`,
`okhttp.jar`, `bouncycastle.jar`), ausgelesen und mit dem Port geschnitten:

```
Klassen im Bootclasspath:  5901
Klassen im Port:           3589
Kollisionen:                  7
```

Sieben. Nicht der Baum, wie zunächst befürchtet — aber auch nicht sieben *Klassen*: die
Kollisionsliste nennt `java.beans.PropertyChangeSupport$PropertyChangeListenerMap`, also
eine **innere** Klasse, und wer nur die äußeren umbenennt, zerreißt das Paar.

## Ein Fehler, der im Kommentar schon stand

Ich hatte fünf `java.beans`-Klassen einzeln umbenannt. Ergebnis:

```
NoSuchMethodError: No virtual method
  addPropertyChangeListener(Ljava/lang/String;Ltsb/port/beans/PropertyChangeListener;)
  bei java.awt.Toolkit.addPropertyChangeListener(Toolkit.java:1875)
```

Der Aufrufer benutzte die umgezogene Schnittstelle, der Empfänger — Androids
`PropertyChangeSupport` — die alte. **Ein halb umgezogenes Paket ist schlimmer als keines.**

Genau dieser Satz stand da bereits, zwei Absätze weiter oben im selben `Umbenenner`, als
Begründung dafür, `sun/util/logging` vollständig umzuziehen. Ich habe ihn geschrieben und
im nächsten Block dagegen gehandelt. Jetzt zieht `java/beans/` als Ganzes um: 165 Klassen,
190 verschoben insgesamt.

## Die Regel, die sich durchzieht

**Beim dritten Vorfall derselben Art aufhören zu flicken und zählen.** In S1 und S2 hat sich
das viermal bestätigt:

| Familie | einzeln entdeckt | dann gezählt |
|---|---|---|
| `SharedSecrets` | 3× | **4 Nutzer** |
| `System.loadLibrary` | 3× | **10 Stellen** |
| `java.beans`-Kollision | 1× (nach Teillösung) | **ganzes Paket** |
| `Unsafe.getUnsafe` | 2× | **10 Nutzer**, davon 8 in `sun.font`/`sun.java2d.marlin` — also in der Schicht, die Skia ohnehin ersetzt |

Die letzte Zeile ist die nützlichste: von zehn `Unsafe`-Nutzern mussten nur zwei repariert
werden (`AWTAccessor`, `SwingAccessor`). Die anderen acht liegen unterhalb der Grenze, an
der wir jetzt stehen — sie werden nicht geflickt, sondern ersetzt.

## Dateien

| | |
|---|---|
| `src/tsb/probe/HauptActivity.java` | die Sonde, gleiche Messlogik wie iOS |
| `Umbenenner.java` | ASM-Umbenennung nach Präfix, mit Begründung je Umzug |
| `desktop8-android.jar` | der Port nach Entnativisierung **und** Umbenennung |
| `fertig.apk` | 3,3 MB, signiert |

Der Port selbst und seine Flicken liegen in `../s1-portgroesse/` — beide Plattformen teilen
ihn, und das ist der Punkt: **eine Arbeit, zwei Ziele.**

---

# Die Schriftmetrik — und warum sie nicht repariert wurde

```
OK      Schriftwerk angemeldet:  ja, AndroidSchriftwerk
OK      Toolkit besorgen:        sun.awt.HeadlessToolkit
OK      Klasse java.awt.Component finden: java.awt.Component
OK      Toolkit initialisieren:  headless=true
OK      LAF direkt bauen:        Metal
OK      Vorgabetabelle fuellen:  Eintraege: 640
OK      Look-and-Feel setzen:    Metal
OK      JPanel bauen:            Kinder: 0
OK      Schriftmetrik:           Breite von 'Hallo Welt': 62, Hoehe: 18
OK      JButton bauen:           Knopf bei 181,5  Groesse 37x16
```

**Swing legt auf Android aus.** Ein `JButton`, von Skia vermessen und von Swings
Layoutmanager platziert.

## Der Entschluss: abschneiden statt flicken

Der Halt lag in `sun.font.SunFontManager` → `StrikeCache` → `Unsafe.getUnsafe()`. Dem
`StrikeCache` das `Unsafe` zu nehmen wäre naheliegend gewesen — und falsch. Danach käme
`getGlyphCacheDescription`, dann der Glyphen-Cache, dann FreeType. **Am Ende stünde eine
nachgebaute Schriftmaschine neben einer, die im Betriebssystem schon liegt.**

Der Schnitt gehört darüber, und er ist eine Zeile:

```java
// sun/swing/SwingUtilities2.java
public static FontMetrics getFontMetrics(JComponent c, Font font) {
    return tsb.port.Schrift.metrik(font);     // war: FontDesignMetrics.getMetrics(font, frc)
}
```

Dahinter drei kleine Stücke:

| | |
|---|---|
| `tsb.port.Schriftwerk` | eine Schnittstelle, **fünf Methoden** — Breite, Ober-, Unterlänge, Zeilenabstand, Zeichenbreite |
| `tsb.port.Schrift` | wo die Plattform ihr Werk hinterlegt, plus ein `FontMetrics`, das dorthin fragt |
| `tsb.android.AndroidSchriftwerk` | `android.graphics.Paint.measureText`, ~40 Zeilen |

Mehr braucht Swing nicht, um eine Maske auszulegen.

## Der Beleg, dass wirklich gemessen wird

`Hallo Welt` in 14 Punkt ergibt **62**. Die Notfassung in `Schrift.Schaetzung` — feste
Verhältnisse für den Fall, dass keine Plattform gemeldet ist — hätte 77 geraten
(zehn Zeichen × 0,55 × 14). Die Zahl kommt also aus Skia, nicht aus der Faustformel.

Die Notfassung bleibt trotzdem drin, und zwar absichtlich: eine Maske, die mit ungenauen
Breiten erscheint, lässt sich ansehen und beurteilen; eine, die gar nicht erscheint, nicht.
`Schrift.istEcht()` sagt, welche von beiden gerade rechnet.

## Warum Android zuerst richtig war

`android.graphics` **ist** Skia. Kein NDK, kein JNI, keine Bauerei — `Paint` misst Text von
Haus aus. Die vierzig Zeilen `AndroidSchriftwerk` sind die ganze Android-Hälfte der
Schriftfrage.

Auf iOS steht an derselben Stelle später CoreText oder Skia; **die Schnittstelle bleibt
dieselbe.** Das ist der Punkt der Aufteilung: ab hier ist die Arbeit geteilt, und was hier
entsteht, gilt der Form nach für beide Ziele.

---

# Das erste Bild

```
GEMALT   1080x1418 in 4890 Mikrosekunden
LUECKEN  1: [drawImage(Ausschnitt)]
```

Auf dem Bildschirm steht eine Swing-Maske, gemalt von Metal durch ein eigenes
`Graphics2D` auf Skia: **„Willkommen" in 28 Punkt fett, eine Beschriftung, ein Textfeld mit
Inhalt, ein Knopf mit mittig gesetzter Aufschrift, ein Fortschrittsbalken bei 60 %.**

**4,9 Millisekunden** für die ganze Maske bei 1080 × 1418.

## Die Bauform

`java.awt.Graphics2D` hat **75 abstrakte Methoden**. Davon sind die allermeisten Zustand und
Rechnung — Farbe merken, Formen auf Rechtecke zurückführen, Verschiebungen verketten. Das
steht in `tsb.port.Portgrafik`, einmal für beide Plattformen.

Was übrig bleibt, ist `tsb.port.Zeichenwerk`: **zwölf Methoden**, die Farbe auf eine Fläche
bringen.

| | |
|---|---|
| `Portgrafik` | 75 Methoden, reines Java, plattformfrei |
| `Zeichenwerk` | **12** Methoden, je Plattform |
| `AndroidZeichenwerk` | `Canvas.drawRect/drawRoundRect/drawText/…`, ~150 Zeilen |

Verschiebung, Maßstab und Schnitt gehen **unverändert** an die Plattform — Canvas wie Skia
rechnen das in Hardware. Hier nochmal zu multiplizieren hieße, es doppelt zu tun und doppelt
falsch machen zu können.

## Buch führen statt werfen

`Portgrafik` wirft nicht, wenn ihr etwas fehlt: sie **vermerkt**. Der Unterschied ist
entscheidend — eine Ausnahme mitten im Malen bricht die Maske ab, und man sieht nichts, auch
nicht das, was schon geht. Ein Vermerk lässt fertig malen und liefert hinterher die Liste.

Das Ergebnis nach dem ersten vollständigen Durchlauf: **eine Lücke von 75 Methoden**,
`drawImage(Ausschnitt)` — das Zurückkopieren einer Hilfsfläche.

Dieselbe Haltung bekam die Ansicht: ein Baustein, der beim Malen scheitert, darf nicht die
App mitnehmen. Vorher tat er es — die Ankreuzfläche mit ihrem `null`-Symbol riss alles ab,
und weil **Swing Kinder in umgekehrter Reihenfolge malt**, fehlte danach alles außer dem
Fortschrittsbalken. Ein Fehler, der aussah wie „nichts funktioniert", und in Wahrheit hieß
„alles funktioniert außer einem".

## Drei Schnitte oberhalb von `sun.font`

Immer dasselbe Muster: nicht reparieren, sondern abschneiden.

| Wo | Was dahinter lag | Was stattdessen geschieht |
|---|---|---|
| `SwingUtilities2.getFontMetrics` | `FontDesignMetrics` → `SunFontManager` → FreeType | `tsb.port.Schrift` fragt die Plattform |
| `SwingUtilities2.getLeftSideBearing` | `Font.createGlyphVector` → `FontManagerFactory` → `sun.awt.X11FontManager` | **0** |
| `GraphicsEnvironment.createGraphics` | eine Bildfläche mit Java2D-Rasterung | eine Fläche, die nichts aufnimmt |

Die mittlere ist die lehrreichste. Die **linke Seitenlage** ist die Strecke, um die ein „W"
optisch weiter links beginnt als sein Kästchen; Swing rückt Beschriftungen darum ein paar
Pixel zurecht. Dafür baut es einen Glyphenvektor — und landet in einer Schriftmaschine, die
es auf dem Telefon nicht gibt.

**Für diese Korrektur eine Schriftmaschine mitzuschleppen wäre ein schlechter Handel.** Null
heißt: die Beschriftung sitzt an ihrem Kästchen statt an ihrer Kontur. Auf einem Telefon
unsichtbar — das Alternativangebot war eine Maske, die gar nicht erscheint.

---

# Der Finger auf dem Knopf — Meilenstein A

```
FINGER   0 bei 195,454 (Pixel 540,1256, Massstab 2.77) trifft JButton
FINGER   1 bei 195,454 (Pixel 540,1256, Massstab 2.77) trifft JButton
GEKLICKT Gruss ist jetzt: Hallo, Thorsten!
```

Auf dem Schirm wird aus „Willkommen" ein „Hallo, Thorsten!". **Ein Swing-`JButton`, von
Swing ausgelegt, von Skia gemalt, durch einen Finger ausgelöst.** Damit ist Meilenstein A
des Plans erreicht.

## Wie ein Finger zu einer Maus wird

Swing verteilt Mausereignisse über den `LightweightDispatcher` des obersten Fensters. Ein
Fenster gibt es hier nicht, also wird von Hand getroffen: `getDeepestComponentAt` sucht den
Baustein, `dispatchEvent` reicht ihm das Ereignis. Drei Ereignisse genügen —
**gedrückt, losgelassen, geklickt**; auf genau diese Folge wartet `BasicButtonListener`.

Die Koordinaten werden durch den Maßstab geteilt: 540 Pixel ÷ 2,77 = 195 Punkte. Das ist
die Rückrechnung aus dem Konzept, und sie stimmt auf den Punkt.

## Ein Paket, das niemand will

Der erste Versuch stürzte ab:

```
NoClassDefFoundError: Failed resolution of: Ljava/applet/Applet;
  bei javax.swing.SwingUtilities.convertPointToScreen(SwingUtilities.java:401)
  bei javax.swing.SwingUtilities.convertPoint(SwingUtilities.java:182)
```

`convertPoint` läuft bis zum obersten Fenster hinauf und fragt unterwegs, ob es ein Applet
ist. Auf einem Telefon. Die Umrechnung selbst ist eine Schleife über Positionen —
**fünf Zeilen gegen ein Paket, das niemand will** (`oertlichIn`).

## Die Zahlen, die im Plan gefordert waren

Der Plan verlangt für Meilenstein A Bildrate und APK-Größe; beides gab es im Repositorium
bis heute nicht. Gemessen über 120 Bilder am Bildtakt:

| | |
|---|---|
| **Malen** (`paint` des ganzen Baums) | Median **0,26 ms** — 2 % eines 60-Hz-Bildes |
| **Auslegen** (`doLayout` rekursiv) | Median **0,02 ms** |
| **Bildrate** | 36 je Sekunde |
| **APK** | 3,3 MB, davon 7,4 MB Dex für 4795 Klassen |

Die Bildrate passt nicht zu den Malzeiten, und das ist der eigentliche Befund: **unser
Anteil ist 0,3 ms von 27.** Die übrigen 98 % gehören dem Emulator —

```
GLES: Android Emulator OpenGL ES Translator (ANGLE, Vulkan 1.3.0 (SwiftShader))
gfxinfo: 50th gpu percentile: 23ms
```

**SwiftShader rastert auf der CPU.** Die 36 Bilder je Sekunde sind seine Grenze, nicht die
von Swing. Der Schirm meldet 60 Hz, unser Code braucht 2 % davon.

**Offen und ehrlich gesagt: auf echter Hardware ist das noch nicht gemessen.** Die
Reservezahl (0,26 ms) trägt die Aussage, die Bildrate nicht — die muss an einem Gerät mit
GPU wiederholt werden, bevor sie irgendwo zitiert wird.

## `bauen.sh`

Der Weg von der Quelle zur laufenden App ist sechs Schritte lang — javac, d8, aapt2, zip,
zipalign, apksigner. Solange er nur im Sitzungsprotokoll stand, war nach einem verlorenen
Aufruf nicht mehr feststellbar, ob die APK den neuen Stand enthielt oder den alten. Genau
das ist passiert: ein Klick wurde dreimal geprüft und scheiterte dreimal an derselben
Ausnahme, obwohl die Reparatur längst in der Quelle stand.

Zwei Dinge, die man beim Nachbauen ohne Gradle nicht ahnt:

| | |
|---|---|
| `core-lambda-stubs.jar` | Weder `android.jar` noch der Port haben `LambdaMetafactory` — Android hat keines (d8 löst Lambdas selbst auf), der Port ist auf `java.desktop` beschnitten. **Ohne die Stubs übersetzt kein einziges Lambda.** Gradle legt sie still dazu |
| `--min-sdk-version` / `--target-sdk-version` | Fehlen sie bei `aapt2 link`, steht `targetSdk 0` in der APK und das Gerät lehnt sie ab: *„must target at least SDK version 24, but found 0"* |

---

# FlatLaf statt Metal — Punkt 1.3

```
OK      LAF direkt bauen:        FlatLaf Light
OK      Vorgabetabelle fuellen:  Eintraege: 1064
OK      Look-and-Feel setzen:    FlatLaf Light
OK      JButton bauen:           Knopf bei 164,5  Groesse 72x23
GEMALT  390x650 bei Massstab 2.77 in 1036 Mikrosekunden
LUECKEN keine
```

**FlatLaf zeichnet auf Android.** Zwei Zahlen belegen, dass es wirklich FlatLaf ist und
nicht ein Metal mit anderem Namen: die Vorgabetabelle hat **1064** Einträge statt Metals
640 — die `.properties`-Dateien aus dem Jar werden also gefunden —, und derselbe `JButton`
misst jetzt **72×23** statt 37×16.

Und zum ersten Mal: **`LUECKEN keine`.**

## Fünf Hindernisse, vier davon Familien

| # | Symptom | Ursache | Antwort |
|---|---|---|---|
| 1 | `Package com.formdev.flatlaf ist nicht vorhanden` | **zsh**, nicht Java | `${PLATTFORM}` statt `$PLATTFORM` |
| 2 | `NoSuchMethodError addPropertyChangeListener(String, java.beans.…)` | FlatLaf ruft `java.beans`, der Port hat es verschoben | FlatLaf durch denselben `Umbenenner` |
| 3 | `InternalError: ClassNotFoundException: sun.awt.X11FontManager` | `sun.font.fontmanager` — die **dritte** Plattformklasse per Systemeigenschaft | `TsbSchriftverwaltung` |
| 4 | `ClassNotFoundException: tsb.port.TsbSchriftverwaltung` — obwohl im Dex | Androids Systemlader sieht die APK nicht | `Laderwechsel`, **18 Stellen** |
| 5 | `NullPointerException` in `Font.getFamily()` | `findFont2D` gab `null`, und `Font.getFont2D()` prüft nicht | `sun.font.TsbFont2D` |

### 1 — Ein Fehler, der nach Java aussah und keiner war

```
Warnung: Ungültiges Pfadelement ".../android.jaratlaf.jar": nicht vorhanden
```

Aus `"$PLATTFORM:flatlaf.jar"` wurde `.../android.jaratlaf.jar`. **zsh liest `:f` als
History-Modifikator** („wiederhole den folgenden"), `l` als „klein schreiben" — daher auch
der kleingeschriebene Pfad in der Warnung. Auch in Anführungszeichen.

Dieselbe Familie wie das Wortsplitting, das in diesem Verzeichnis schon einmal zugeschlagen
hat und beim Einbau der Nähte gleich nochmal (`NAEHTE` als Zeichenkette statt als Feld:
zwei Pfade wurden **ein** Dateiname). Die Regel für `bauen.sh` lautet jetzt: **geschweifte
Klammern um jede Variable, auf die ein `:` folgt; Listen sind Felder.**

### 3 und 4 — beim dritten Mal zählen

`awt.toolkit`, `java.awt.graphicsenv`, `sun.font.fontmanager`: dreimal dasselbe Muster. Also
gezählt statt weiter geflickt — sieben Eigenschaften im Port laden eine Klasse nach Namen:

| | |
|---|---|
| `awt.toolkit`, `java.awt.graphicsenv`, `sun.font.fontmanager` | **gesetzt** |
| `java.awt.printerjob`, `javax.accessibility.assistive_technologies`, `swing.defaultlaf`, `sun.java2d.renderer` | unterhalb der Schnittlinie, bisher nicht erreicht |

Hindernis 4 war das wertvollste des ganzen Tages, weil es **nicht nach Schrift aussah**:

```
dalvik.system.PathClassLoader[DexPathList[[directory "."]]]   (ANDERER als unserer)
```

Androids `ClassLoader.getSystemClassLoader()` entsteht aus `java.class.path`, und das ist im
App-Prozess schlicht `"."`. **Er kann keine Klasse aus der APK laden.** Das JDK benutzt ihn
an 18 Stellen in 17 Klassen — darunter `javax.swing.UIDefaults`, und damit gilt:

> Ohne diese Umleitung kann auf Android **kein** Look-and-Feel laden, das seine
> UI-Delegierten über Klassennamen einträgt — also keines.

`Laderwechsel.java` schreibt jeden dieser Aufrufe auf `tsb.port.Lader.system()` um. Eine
Regel statt siebzehn Flicken, und sie bleibt richtig, wenn der nächste JDK-Stand achtzehn
Stellen hat.

### 5 — der Schnitt, der zu tief lag

FlatLaf hält Android für Linux (`os.name` ist „Linux") und fragt über `LinuxFontPolicy` nach
der GNOME-Schrift. Der naheliegende Ausweg wäre, `os.name` zu ändern — **das geht nicht:**

```
OSNAME   vorher=Linux  setProperty gab zurueck=Linux  danach=Linux
```

Androids libcore hält die Eigenschaft unveränderlich. Gemessen, nicht vermutet.

Damit blieb der ehrliche Weg, und er korrigiert eine Annahme aus S1: **ein `Font2D` muss
sein.** `java.awt.Font.getFont2D()` ist der Engpass der ganzen Klasse — `getFamily`,
`getFontName`, `canDisplay` laufen alle dort hindurch, und das Ergebnis wird ohne Prüfung
dereferenziert. Kein Sonderweg, sondern die öffentliche API einer Kernklasse.

Der Preis ist klein: **`Font2D` hat 45 Methoden, aber nur zwei abstrakte** —
`getMapper()` und `createStrike()`, beide reine Glyphenarbeit. Die werfen mit eigenem Text.
`TsbFont2D` liegt in `sun.font`, weil beide Methoden paketprivat sind; das ist der einzige
Grund.

## Pfade — die dreizehnte Plattformmethode

Nach FlatLafs erstem Durchlauf stand in der Lückenliste `fill(Float)`. Eine Form einzeln
nachzuziehen hätte die nächste offen gelassen, also kam die allgemeine Antwort:

```java
void pfad(List<float[]> teilzuege, boolean geradeUngerade, boolean fuellen, int argb, float dicke);
```

`Portgrafik` zerlegt jede unbekannte Form über `getPathIterator(null, 0.5)` in Strecken —
**Kurven kommen bei der Plattform nie an.** Ein Streckenzug bedeutet überall dasselbe, eine
Bézier-Kurve hat drei Schreibweisen.

Das schloss in einem Zug: `fill(Path2D)`, `drawArc`, `fillArc`, `fillPolygon`. Auf Android
sind es zwölf Zeilen `Canvas.drawPath`.

*Nebenbei ein Mangel der Diagnose selbst:* `fill(Float)` nannte nur den kurzen Klassennamen,
und `Float` heißen `Rectangle2D.Float`, `RoundRectangle2D.Float`, `Path2D.Float` und vier
weitere. Eine Lückenliste, die nicht sagt, welche Lücke gemeint ist, kostet einen Durchgang.

## Der Bau hat jetzt eine Stufe mehr

`tsb.port.Portgrafik` und `Zeichenwerk` liegen **im Port-Jar**. Eine Quelländerung allein
wirkt also nicht, und eine zweite Fassung danebenzulegen gäbe eine doppelte Klasse im Dex.
`bauen.sh` übersetzt die geänderten Nähte deshalb und **ersetzt die Einträge im Jar**.

Ausdrücklich aufgezählt, nicht „alles unter `patch/`": `TsbToolkit` verweist auf
`java.awt.font.TextAttribute`, das der Umbenenner verschoben hat — die Quelle passt nicht
mehr zu dem Jar, in das sie zurück soll. Wer eine solche Naht ändert, muss sie durch den
Umbenenner schicken.

Damit läuft der Port durch drei Umschreibungen:

| | |
|---|---|
| `Umbenenner` | `java.beans` → `tsb.port.beans`, `sun.util.logging` → … (190 Klassen) |
| `Laderwechsel` | `getSystemClassLoader()` → `tsb.port.Lader.system()` (18 Stellen) |
| `Kurzschluss` | benannte Methoden auf feste Ergebnisse (1 Schnitt) |

`Kurzschluss` meldet, wenn eine Regel **nicht** greift, und bricht dann ab — eine Regel, die
ins Leere läuft, ist gefährlicher als keine, weil sie nach Absicherung aussieht.

## Zahlen mit FlatLaf

| | Metal | FlatLaf |
|---|---|---|
| Einträge in der Vorgabetabelle | 640 | **1064** |
| `JButton("Button")` | 37×16 | **72×23** |
| Malen, Median | 0,26 ms | **0,34 ms** |
| APK | 3,3 MB | **3,8 MB** |
| Lücken | 1 | **0** |

Der Knopf reagiert unverändert (`GEKLICKT`, keine Abstürze). Die Bildrate bleibt die des
Emulators und wird hier weiter nicht zitiert — siehe oben, SwiftShader.

## Die sechs ERP-Bausteine, gemessen

Das ERP benutzt sechs der zwölf Bausteine — gezählt in den sieben Masken: Label 38,
TextField 24, Button 20, Dropdown 9, Table 4, TextArea 2. Alle sechs in eine Maske gelegt:

```
OK      Dropdown bauen:    Eintraege: 3, gewaehlt: Kunde A
OK      Tabelle bauen:     Zeilen: 2, Spalten: 3
OK      Textbereich bauen: Zeilen: 2
GEMALT  390x650 in 4882 Mikrosekunden
LUECKEN keine
```

**Alle sechs bauen, legen aus und malen ohne eine einzige Lücke.** Bauen ist aber nicht
dasselbe wie aussehen, und das Bild zeigt zwei Fehlstellen:

| | |
|---|---|
| **Dropdown** | Rahmen und Pfeil stehen, **die Beschriftung „Kunde A" fehlt** |
| **Tabelle** | die Zeilen stehen, **der Spaltenkopf fehlt** |
| TextArea | vollständig |

Beide Fehlstellen meldeten **keine** Lücke. Die erste Vermutung — „kein fehlender Unterbau,
sondern ein nicht begangener Malweg" — war bei beiden falsch; der nächste Abschnitt sagt,
was es wirklich war. *Dass* sie nicht in der Lückenliste standen, war trotzdem richtig: die
Liste nennt fehlende Plattformmethoden, und es fehlte keine.

---

# Die zwei Fehlstellen — behoben

Beide sind behoben, und beide hatten eine Ursache, die man nicht geraten hätte.

## Das Dropdown: `setClip` heißt ersetzen, nicht verengen

Die Beschriftung fehlte. Der erste Schritt war nicht, in `JComboBox` zu lesen, sondern zu
protokollieren, **welche Texte überhaupt bei der Plattform ankommen**:

```
Kunde A @6,41 Farbe=ff000000 Schnitt=Rect(0, 0 - 0, 0)
```

Der Text kam an. Der Schnitt war leer. Damit war es kein fehlender Malweg, sondern ein
Unterbau-Fehler — und die Schnittkette zeigte, wo:

```
schneiden 368,0 22x76  -> Rect(368,0 - 390,76)    der Aufklapppfeil
schneiden 0,0 390x76   -> Rect(368,0 - 390,76)    sollte zuruecksetzen, tat nichts
schneiden 0,0 367x74   -> Rect(0,0 - 0,0)         leer
```

**Swings `setClip` ersetzt den Schnitt. Skia kann Schnitte nur verengen** — Android hat
`Region.Op.REPLACE` mit API 26 entfernt, Metal kennt es ebenso wenig. Der Rücksetzer lief
ins Leere, und der nächste Schnitt machte die Fläche leer.

Deshalb steht in `Zeichenwerk` bewusst **kein** „Schnitt ersetzen": keine der beiden
Plattformen kann es. Stattdessen geht `Portgrafik.setClip` auf den Grundzustand des eigenen
`Graphics` zurück und baut neu auf.

*Und ein Fehler dabei, der sofort messbar war:* die erste Fassung merkte sich jeden
Verschiebeschritt einzeln und spielte die Liste bei jedem `setClip` ab. `BasicTableUI`
verschiebt je Zelle hin und zurück — die Liste wuchs mit jeder Zelle, das Abspielen wurde
quadratisch. Eine Matrix statt einer Liste macht daraus zwei Aufrufe.

## Die Tabelle: ein Baum ohne Fenster bekommt kein `addNotify`

Der Spaltenkopf fehlte, und der Grund stand im Bytecode:

```
public void addNotify();
   0: invokespecial javax/swing/JComponent.addNotify
   4: invokevirtual configureEnclosingScrollPane     ← setzt den Kopf
```

`addNotify()` läuft nur, wenn ein Baum in eine anzeigbare Hierarchie kommt. Unserer hat kein
Fenster. Der Versuch, es von Hand zu rufen, scheiterte an —

```
NoClassDefFoundError: java.applet.Applet
  bei javax.swing.RepaintManager.addDirtyRegion0
```

**`java.applet` zum zweiten Mal**, nach `SwingUtilities.convertPoint` bei den Berührungen.
Also gezählt statt umgangen:

| | |
|---|---|
| Klassen im Paket `java.applet` | **5** |
| Klassen im Port, die darauf verweisen | **17** — darunter `JComponent`, `RepaintManager`, `SwingUtilities`, `PopupFactory`, `JTable$CellEditorRemover` |

Fünf gegen siebzehn ist keine Abwägung. Das Paket ging durch dieselbe Kette wie alles andere
(`Umbenenner`, `Laderwechsel`) und liegt jetzt im Port. Danach: `addNotify durchgelaufen,
displayable=true`, und der Spaltenkopf steht.

## Marken statt Stapeltiefe

Dabei kam heraus:

```
IllegalStateException: Underflow in restore - more restores than saves
  bei tsb.port.Portgrafik.dispose
```

**Swing entsorgt ein `Graphics` gelegentlich zweimal.** Auf einem echten `Graphics` ist das
folgenlos; auf einem Speicherstapel bricht das Malen mitten im Bild ab.

`Zeichenwerk.sichern()` gibt deshalb jetzt eine **Marke** zurück, und
`wiederherstellen(marke)` geht auf sie zurück statt „einen Schritt". Beide Plattformen
bieten das an (`Canvas.restoreToCount`, Skia `restoreToCount`). Es kostet nichts und nimmt
eine ganze Fehlerfamilie weg — Reihenfolge egal, zweites Entsorgen wirkungslos.

## Was die Malzeit wirklich kostet

Mit sieben Bausteinen stieg die Malzeit von 0,26 ms auf 46 ms. Bevor daraus ein Befund
wurde, zwei Messungen:

**Erstens: nicht die Plattform.** Aufrufe und Zeit je Art, ein volles Bild:

```
sichern             78 Aufrufe   0.02 ms
fuelleRechteck      14 Aufrufe   0.05 ms
wiederherstellen    78 Aufrufe   0.04 ms
schneiden           26 Aufrufe   0.03 ms
Schrift.breite      17 Aufrufe   0.23 ms
text                16 Aufrufe   0.33 ms
pfad                 5 Aufrufe   0.03 ms
zusammen                         0.78 ms
```

**0,78 ms von 46.** Skia ist unschuldig; die Zeit steckt in Swings eigenem Java.

**Zweitens: wie schnell rechnet dieses Gerät überhaupt?** Dieselbe Rechenarbeit hier und
dort:

| | |
|---|---|
| Mac, JVM 21 | **21 ms** |
| Emulator, ART | **132 ms** |

**Der Emulator ist 6,3× langsamer bei reinem Java.** Die 46 ms entsprechen damit rund
**7 ms** auf einem gewöhnlichen Rechner — für einen *vollständigen* Neuaufbau aller sieben
Bausteine, den echtes Swing so nie macht: es malt nur schmutzige Bereiche. Unsere Messstrecke
erzwingt 120 volle Neuaufbauten.

Der Befund lautet deshalb nicht „zu langsam", sondern: **bevor Rollen flüssig wird, braucht
es Teilbereichs-Malen.** Das ist Punkt 2.3 und war ohnehin geplant.

## Ein Grau, das keines war

Zwischendurch sah die Maske grau aus, obwohl jede gemessene Farbe hell war und jeder Schnitt
stimmte. Erst das Auslesen der Pixel aus dem Bildschirmfoto brachte es:

```
#f5f5f7 -> #d7d7d9     Faktor 0.878
#2285e1 -> #1e75c6     Faktor 0.878
```

Ein **gleichmäßiger** Faktor über alle Kanäle und alle Farben ist kein Malfehler, sondern
Bildschirmhelligkeit — der Emulator dimmt, weil ihn niemand anfasst. Bei voller Helligkeit
misst der Schirm exakt die gezeichneten Werte.

Zwei Stunden lang wäre das eine Fehlersuche in der falschen Hälfte gewesen. **Bildschirmfotos
gehören ausgelesen, nicht angesehen** — jedenfalls dann, wenn die Messung dem Auge
widerspricht.

---

# Die Tastatur — Punkt 2.2

Der größte Einzelposten des Plans (10–20 PT veranschlagt) und der einzige, der ausufern
konnte. Er läuft:

```
FINGER   0 bei 181,199 trifft JTextField
  → Systemtastatur erscheint, mIsInputViewShown=true
  → getippt, 8x Ruecktaste, "Moin" getippt
GEKLICKT Gruss ist jetzt: Hallo, Moin!
```

Antippen, Tastatur, tippen, löschen, Knopf — der ganze Ablauf einer Anmeldemaske.

## Zuerst die billigere Hälfte

Bevor irgendetwas an Androids Eingabemethode angeschlossen wurde, die Frage, die alles
andere entscheidet: **nimmt ein `JTextField` ohne Fenster überhaupt Tastenereignisse an?**

Beim ersten Versuch nein — und lautlos:

```
OK  Taste ins Textfeld: vorher 'Thorsten' danach 'Thorsten'
```

`DefaultKeyboardFocusManager.dispatchKeyEvent` verlangt `focusOwner.isShowing()`, und
`isShowing()` verlangt einen Peer. Den gibt es erst nach `addNotify` — das wir tags zuvor
wegen des Spaltenkopfs eingebaut hatten. **Die Probe stand nur an der falschen Stelle.**
Hinter den `addNotify`-Schritt verschoben:

```
OK  Taste ins Textfeld: vorher 'Thorsten' danach 'ThorstenX'
OK  Ruecktaste: jetzt 'ThorsteX'
```

## Der Schreibzeiger, und was wirklich dahinter steckte

`ThorsteX` ist falsch: die Rücktaste löschte das `n`, nicht das `X`. Der Schreibzeiger war
nach dem Einfügen stehengeblieben.

`DefaultCaret` hat die Vorgabe `UPDATE_WHEN_ON_EDT` — es folgt Einfügungen nur, wenn es auf
dem Ereignisfaden läuft. Wir laufen auf Androids Hauptfaden.

Man hätte `ALWAYS_UPDATE` setzen können, an jedem Textfeld, für immer. Die ehrlichere Antwort
ist ein Schnitt, und er sagt die Wahrheit über diese Plattform:

```java
// Kurzschluss-Regel
java/awt/EventQueue.isDispatchThread ()Z  =  true
```

> Auf dieser Plattform gibt es genau **einen** Faden, der auslegt, malt und zustellt:
> Androids Hauptfaden, später iOS' Hauptfaden. Er *ist* der Ereignisfaden — es gibt keinen
> zweiten, von dem man ihn unterscheiden könnte.

Swing fragt an vielen Stellen danach, und `false` lässt es Arbeit auf einen Faden verschieben,
den niemand abarbeitet. Danach: `ThorstenX` → Rücktaste → `Thorsten`.

## Keine zweite Wahrheit

Der übliche Android-Weg ist ein verstecktes `EditText` als Stellvertreter, dessen Inhalt man
abgleicht. Das wurde **nicht** gemacht: es gäbe zwei Wahrheiten über denselben Text — eine im
`Document` des `JTextField`, eine im `Editable` des Stellvertreters. Zwei Wahrheiten laufen
auseinander, sobald Swing selbst etwas ändert: bei einer Eingabeprüfung, einer Formatierung,
einem `setText` aus dem Programm.

Stattdessen ist die Ansicht selbst das Eingabefeld: `onCreateInputConnection` liefert eine
eigene `InputConnection`, die `commitText`/`deleteSurroundingText` unmittelbar in
AWT-Tastenereignisse übersetzt. **Das `Document` bleibt die einzige Wahrheit.**

Den Fokusbesitzer führen wir selbst — ohne Fenster gibt es keinen `KeyboardFocusManager`,
der das täte. Wer den Finger bekommt, bekommt die Tastatur; ist es kein Textfeld, verschwindet
sie.

## Tasten kommen auf zwei Wegen

Vier Rücktasten über `adb` löschten nichts, obwohl die Bildschirmtastatur einwandfrei
funktionierte. Der Grund: **`adb shell input keyevent` geht an der Eingabemethode vorbei**
und landet in `View.onKeyDown`. Denselben Weg nehmen eine angesteckte Tastatur und ein
Barcodeleser — für ein ERP keine Randfälle.

Beide Wege sind jetzt bedient und teilen sich dieselbe Übersetzung.

## Ein Folgefehler von gestern

Der erste Fingerdruck stürzte ab:

```
NullPointerException
  bei java.awt.Component.getLocationOnScreen_NoTreeLock
  bei java.awt.event.MouseEvent.<init>(MouseEvent.java:576)
```

Der kurze `MouseEvent`-Konstruktor holt sich Bildschirmkoordinaten selbst — **aber nur, wenn
die Komponente `isShowing()` ist.** Solange der Baum kein `addNotify` gesehen hatte, blieb der
Zweig kalt. Seit `addNotify` ist er heiß.

Der lange Konstruktor nimmt `xAbs`/`yAbs` entgegen, und die kennen wir genauer als jeder
Peer: es sind die Gerätepixel des Fingers.

*Das Muster ist bemerkenswert:* `addNotify` hat an drei Stellen etwas verändert — den
Spaltenkopf gebracht, die Tastatur ermöglicht, den Mausweg gebrochen. Ein Schalter, der
„displayable" heißt, schaltet mehr um, als der Name sagt.

## Was an der Tastatur noch fehlt

| | |
|---|---|
| **Zwischenzustand** | Vorschläge und tote Tasten werden sofort übernommen statt unterstrichen. Für Latein brauchbar, für CJK nicht |
| **Auswahl** | kein Markieren, keine Greifer, kein Kopieren/Einfügen |
| ~~Sichtbar halten~~ | *erledigt* — siehe „Rollen" |
| **Blinken** | der Schreibzeiger steht, er blinkt nicht — `javax.swing.Timer` braucht eine laufende `EventQueue` |
| `IME_ACTION_DONE` | die Haken-Taste macht noch nichts |

---

# Rollen — Punkt 2.3

```
in der Tabelle gewischt   → Tabelle rollt, Maske steht     (4714–4717)
über der Maske gewischt   → Maske rollt, Tabelle steht     ("Willkommen" verschwindet)
```

Zwei Rollflächen ineinander, jede für sich, mit Schwung. Der Spaltenkopf bleibt stehen.

## Die eigentliche Frage ist nicht „rollen", sondern „ziehen oder drücken?"

Auf einer Maus ist ein Klick ein Klick. Auf einem Telefon **beginnt jedes Rollen als Druck**,
und erst nach ein paar Pixeln stellt sich heraus, dass es keiner war.

Deshalb geht der Druck zunächst doch an Swing — ein Knopf soll sich eindrücken, während der
Finger liegt. Überschreitet die Bewegung dann `getScaledTouchSlop`, wird er zurückgenommen:
**losgelassen, aber nicht geklickt.**

Die Probe dafür ist dreiteilig, und alle drei müssen stimmen:

| | |
|---|---|
| auf „Weiter" tippen | löst aus |
| auf „Weiter" drücken und wegwischen | löst **nicht** aus |
| danach wieder tippen | löst aus |

Der mittlere Fall stimmte zunächst **aus dem falschen Grund**: das Loslassen ging an den
Baustein *unter dem Finger*, nicht an den gedrückten. Das ist kein Detail —

## Mausfang, und zwei Regeln, die daran hängen

> Solange der Finger liegt, gehören alle Ereignisse dem Baustein, der den Druck bekommen hat.

Jede Maus macht das so. Ohne diese Regel bekommt ein Knopf sein Loslassen nie und **bleibt
eingedrückt stehen**. Mit ihr allein löste er dann aber beim Wegwischen aus — also fehlten
noch zwei Stücke, die echtes Swing über den `LightweightDispatcher` bekommt:

| | |
|---|---|
| `MOUSE_EXITED` | verlässt der Finger den gedrückten Baustein, entwaffnet sich ein `JButton` und zeichnet sich wieder ungedrückt |
| `MOUSE_CLICKED` nur innen | geklickt heißt: losgelassen, wo gedrückt wurde. Sonst ist es eine abgebrochene Geste |

Erst mit beidem stimmen alle drei Fälle *und* ihre Begründung.

## Welche Rollfläche gewinnt

Von innen nach außen gesucht, nicht umgekehrt: liegt eine Tabelle in einer Rollfläche, die
wiederum in einer liegt, rollt die innere — so wie überall. Das sind vier Zeilen
(`rollfeldUeber`), und sie sind der ganze Unterschied.

Das Anhalten am Rand macht nur **eine** Stelle: `rolle()` meldet, ob es noch ging, und der
Schwung hört auf, wenn nicht. Zwei Stellen, die den Rand kennen, wären eine zuviel.

## `adjustResize` gibt es nicht mehr

Ein Feld unter der Tastatur muss nach oben. `scrollRectToVisible` erledigt das — es ist
gewöhnliches Swing und läuft selbst bis zur nächsten `JScrollPane` hinauf. Es passierte
trotzdem nichts.

`android:windowSoftInputMode="adjustResize"` war gesetzt. **Seit API 35 wirkt es nicht mehr:**
Anwendungen laufen randlos, und die Tastatur ist ein Fensterrand wie die Statusleiste. Wer
will, dass die Ansicht kleiner wird, muss ihn selbst auswerten:

```java
wurzel.setOnApplyWindowInsetsListener((ansicht, raender) -> {
    int unten = raender.getInsets(WindowInsets.Type.ime()
            | WindowInsets.Type.systemBars()).bottom;
    ansicht.setPadding(0, 0, 0, unten);
    return raender;
});
```

Danach schrumpft die Ansicht, `onSizeChanged` feuert, und das Feld kommt hoch.

*Bemerkenswert:* von den vier Hindernissen dieses Abschnitts kam **keines** aus Swing. Drei
kamen aus Android (Touch Slop, Fensterränder, Schwungrechner), eines aus der Bedienung
selbst (ziehen gegen drücken). Der Port trägt; was Arbeit macht, ist die Plattform darunter.

## Was noch fehlt

| | |
|---|---|
| **Kein Ereignisfaden** | es läuft keine `EventQueue`: gemalt wird aus `onDraw`, zugestellt aus `onTouchEvent`. `EventQueue.isDispatchThread()` sagt zwar jetzt die Wahrheit, aber `invokeLater` und `javax.swing.Timer` haben weiterhin keinen Antrieb. Punkt 1.2, weiterhin halb |
| **Nicht im Werkzeug** | das alles läuft über `bauen.sh` in einem Versuchsverzeichnis. `AndroidTarget` und `Toolchain` wissen nichts davon — `rfxmobile android` baut weiter über `android.widget` |
| Bildrate am Gerät | der Emulator kann sie nicht beantworten (SwiftShader) |
| Tastatur | der größte Einzelposten, Punkt 2.2 des Plans — *läuft inzwischen, siehe unten* |
| Ankreuzfläche | `CheckBox.icon` war in Metals Tabelle `null` — unter FlatLaf noch ungeprüft |
| Fortschrittsbalken | erscheint eckig, FlatLaf zeichnet ihn rund. Keine Lücke gemeldet, also eine Frage der Form, nicht des Unterbaus |
| `drawImage` | nicht mehr gemeldet, aber `Leerwerk` nimmt noch immer nichts auf |
| iOS | dieselben fünf Hindernisse stehen dort noch bevor (S3) |

Erledigt: **Berührungen**, **Maßstab**, **FlatLaf**, **Pfade**.
