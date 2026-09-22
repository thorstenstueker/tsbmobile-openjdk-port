import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Benennt die Klassen um, die Androids Bootclasspath uns wegnimmt.
 *
 * <h2>Warum das nötig ist — und warum nur für eine Handvoll</h2>
 *
 * <p>Androids Laufzeit lädt Klassen in {@code java.*} und {@code javax.*} ohne Weiteres; das
 * wurde gemessen (S1, Messung 2: {@code java.awt}, {@code java.lang}, {@code java.util},
 * {@code javax.swing} — alle vier geladen). <b>Eine Klasse, die eine vorhandene überschattet,
 * verliert aber</b>, denn der Klassenlader fragt zuerst den Elternteil.
 *
 * <p>Das traf uns an genau einer Stelle, und sie war zunächst unsichtbar, weil sie <i>nicht</i>
 * in der Kollisionsliste steht: {@code sun.util.logging.PlatformLogger}. Wir liefern sie gar
 * nicht mit — Android hat sie, in einer Fassung ohne {@code getLogger(String)}. JDK 8s
 * {@code java.awt.Component} ruft genau die, im statischen Initialisierer:
 *
 * <pre>
 * NoSuchMethodError: No static method getLogger(Ljava/lang/String;)…
 *   (declaration of 'sun.util.logging.PlatformLogger' appears in
 *    /apex/com.android.art/javalib/core-oj.jar)
 *   at java.awt.Component.&lt;clinit&gt;(Component.java:190)
 * </pre>
 *
 * <p>Danach lehnte die Laufzeit alles darunter ab — {@code VerifyError: Rejecting class
 * javax.swing.JPanel that attempts to sub-type erroneous class javax.swing.JComponent}. Eine
 * Ursache, fünf Folgefehler.
 *
 * <p>Mitliefern hilft nicht: der Bootclasspath gewinnt. <b>Umbenennen ist der einzige Weg</b>
 * — und weil dann auch niemand mehr Androids Fassung sieht, ist es zugleich der saubere.
 *
 * <h2>Umfang</h2>
 *
 * <p>Gemessen, nicht geschätzt: von 3589 Portklassen kollidieren <b>sieben</b> mit Androids
 * Bootclasspath (5 × {@code java.beans}, {@code sun.security.util.SecurityConstants},
 * {@code java.awt.font.TextAttribute}). Dazu die eine fehlende-und-überschattete oben. Das ist
 * die ganze Liste — kein Umbenennen des Baums, wie zunächst befürchtet.
 */
public final class Umbenenner {

    /**
     * Was umbenannt wird, und wohin.
     *
     * <p>Ein eigenes Präfix statt {@code java.*}, damit die Klasse Androids gleichnamiger nicht
     * mehr begegnen kann. Innere Klassen fallen automatisch mit, weil der Remapper Präfixe
     * abgleicht.
     */
    private static final Map<String, String> UMZUEGE = new LinkedHashMap<>();

    static {
        // Die Ursache des Startabbruchs.
        // Das ganze Paket, nicht nur PlatformLogger: LoggingSupport und LoggingProxy
        // gibt es auf Android ebenfalls, und ein halb umgezogenes Paket waere schlimmer
        // als keines.
        UMZUEGE.put("sun/util/logging/", "tsb/port/logging/");

        // Die sieben echten Kollisionen. java.beans ist der dickste Brocken: Swing hängt
        // ueberall an PropertyChangeListener, und Androids Fassung ist eine Teilmenge.
        // Das ganze Paket, 165 Klassen — und das ist eine Korrektur.
        //
        // Zuerst standen hier fuenf einzelne Namen, die Kollisionsliste hatte sie genannt.
        // Prompt scheiterte es an einer sechsten, die dort fehlte:
        // PropertyChangeSupport$PropertyChangeListenerMap, eine innere Klasse. Die Folge war
        // ein NoSuchMethodError auf addPropertyChangeListener(String, tsb/port/beans/...) —
        // der Aufrufer benutzte die umgezogene Schnittstelle, der Empfaenger Androids alte.
        //
        // Ein halb umgezogenes Paket ist schlimmer als keines. Genau das stand schon im
        // Kommentar ueber sun/util/logging, zwei Absaetze weiter oben, und ich habe es hier
        // trotzdem gemacht.
        UMZUEGE.put("java/beans/", "tsb/port/beans/");
        UMZUEGE.put("sun/security/util/SecurityConstants", "tsb/port/SecurityConstants");
        UMZUEGE.put("java/awt/font/TextAttribute", "tsb/port/TextAttribute");

        // JDK 17: das Modul, das es auf dem Telefon nicht gibt. Modulfrei hat die Aufrufe
        // (Class.getModule, ResourceBundle.getBundle(…, Module), …) schon auf tsb.port.Modul
        // umgeschrieben; hier ziehen die restlichen Nennungen des Typs nach — Felder,
        // lokale Variablen, Parameter, Rahmen. Genau der Name, nicht das Praefix: ModuleLayer
        // und ModuleDescriptor bleiben, was sie sind (und werden nicht gebraucht).
        UMZUEGE.put("java/lang/Module", "tsb/port/Modul");

        // JDK 17: sun.security.action.GetPropertyAction & Co. gibt es auf beiden Telefonen —
        // in der Fassung ohne die statischen privilegedGetProperty seit 9. Der Bootclasspath
        // gewinnt, also wuerde unsere mitgelieferte Fassung nie geladen: gemessen auf dem
        // iPhone-Simulator, "Could not initialize class sun.awt.SunToolkit". Das ganze Paket.
        UMZUEGE.put("sun/security/action/", "tsb/port/action/");

        // JDK 17: TextAttribute setzt in seinem statischen Initialisierer den Font-Zugang
        // ueber eine paketprivate Nachbarklasse. Nach dem Umzug von TextAttribute nach
        // tsb.port ist der Nachbar nicht mehr im selben Paket — IllegalAccessError, gemessen
        // auf Android 14 als "Illegal class access: tsb.port.TextAttribute attempting to
        // access java.awt.font.JavaAWTFontAccessImpl". Also zieht der Nachbar mit um.
        UMZUEGE.put("java/awt/font/JavaAWTFontAccessImpl", "tsb/port/JavaAWTFontAccessImpl");
    }

    public static void main(String[] args) throws IOException {
        Path quelle = Path.of(args[0]);
        Path ziel = Path.of(args[1]);

        // Nach Praefix, nicht nach genauem Namen: sun/util/logging hat zehn Klassen, davon
        // fuenf innere von PlatformLogger. Jede einzeln aufzuzaehlen waere eine Liste, die
        // beim naechsten JDK-Stand falsch ist.
        Remapper karte = new Remapper() {
            @Override
            public String map(String name) {
                for (Map.Entry<String, String> u : UMZUEGE.entrySet()) {
                    if (name.equals(u.getKey()) || name.startsWith(u.getKey() + "$")) {
                        return u.getValue() + name.substring(u.getKey().length());
                    }
                    if (u.getKey().endsWith("/") && name.startsWith(u.getKey())) {
                        return u.getValue() + name.substring(u.getKey().length());
                    }
                }
                return name;
            }
        };
        int umgeschrieben = 0;
        int verschoben = 0;

        try (ZipFile ein = new ZipFile(quelle.toFile());
             ZipOutputStream aus = new ZipOutputStream(Files.newOutputStream(ziel))) {

            Enumeration<? extends ZipEntry> eintraege = ein.entries();
            while (eintraege.hasMoreElements()) {
                ZipEntry eintrag = eintraege.nextElement();
                if (eintrag.isDirectory()) continue;

                byte[] inhalt;
                try (InputStream strom = ein.getInputStream(eintrag)) {
                    inhalt = strom.readAllBytes();
                }

                String name = eintrag.getName();
                if (name.endsWith(".class")) {
                    ClassReader leser = new ClassReader(inhalt);
                    ClassWriter schreiber = new ClassWriter(0);
                    leser.accept(new ClassRemapper(schreiber, karte), 0);
                    byte[] neu = schreiber.toByteArray();
                    if (neu.length != inhalt.length) umgeschrieben++;
                    inhalt = neu;

                    String ohne = name.substring(0, name.length() - ".class".length());
                    String zielName = karte.map(ohne);
                    if (zielName != null && !zielName.equals(ohne)) {
                        name = zielName + ".class";
                        verschoben++;
                    }
                }
                aus.putNextEntry(new ZipEntry(name));
                aus.write(inhalt);
                aus.closeEntry();
            }
        }

        System.out.println("  Klassen verschoben:  " + verschoben);
        System.out.println("  Klassen angepasst:   " + umgeschrieben + " (Schaetzung ueber Groesse)");
        System.out.println("  geschrieben:         " + ziel);
    }
}
