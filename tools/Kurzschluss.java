import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Ersetzt den Rumpf benannter Methoden durch ein festes Ergebnis — der Schnitt als Werkzeug.
 *
 * <h2>Wozu</h2>
 *
 * <p>Die Grundentscheidung dieses Ports lautet: <b>nicht reparieren, sondern abschneiden.</b>
 * Unterhalb von {@code sun.font} und {@code sun.java2d} liegt eine Schriftmaschine und ein
 * Rasterer, die auf dem Telefon schon im Betriebssystem stehen. Sie nachzubauen waere
 * doppelte Arbeit mit schlechterem Ergebnis.
 *
 * <p>In S1 wurden zwei solche Schnitte von Hand in die JDK-Quellen geschrieben
 * ({@code SwingUtilities2.getFontMetrics}, {@code getLeftSideBearing}). Das geht, solange es
 * zwei sind. Beim dritten lohnt das Werkzeug — nicht weil Handarbeit nicht ginge, sondern
 * weil jede Handarbeit eine Quelle mehr ist, die beim naechsten JDK-Stand nachgezogen werden
 * muss.
 *
 * <h2>Was es kann und was nicht</h2>
 *
 * <p>Nur konstante Rueckgaben: {@code true}, {@code false}, {@code 0}, {@code null}. Das ist
 * Absicht. Wer mehr braucht, braucht keinen Schnitt, sondern eine Naht — und die gehoert
 * nach {@code tsb.port} als lesbares Java, nicht in ein Umschreibewerkzeug.
 *
 * <p>Jeder Schnitt muss <b>begruendet</b> werden: die Regeln unten tragen ihren Grund bei
 * sich, und das Werkzeug meldet am Ende, welche Regel nicht gegriffen hat. Eine Regel, die
 * ins Leere laeuft, ist gefaehrlicher als keine — sie sieht nach Absicherung aus.
 *
 * <p>Aufruf: {@code java -cp .:asm Kurzschluss ein.jar aus.jar}
 */
public final class Kurzschluss {

    /**
     * Klasse, Methode, Signatur, Wert, Grund.
     *
     * <p>{@code wert} ist entweder eine Konstante ({@code "true"}, {@code "0"}, {@code "null"})
     * oder eine <b>Weiterleitung</b> in der Form {@code "-> tsb/port/Ortung.aufDemSchirm"}.
     * Weitergeleitet wird an eine statische Methode, die den Empfaenger als ersten Parameter
     * bekommt und dieselbe Rueckgabe hat.
     *
     * <p>Die zweite Art kam dazu, als eine Konstante nicht mehr genuegte: ein Punkt auf dem
     * Bildschirm ist kein fester Wert. Sie bleibt trotzdem ein Schnitt und keine Reparatur —
     * <b>der Rumpf wird ersetzt, nicht ergaenzt</b>, und was dahinter lag, wird nicht mehr
     * betreten.
     */
    private record Regel(String klasse, String methode, String signatur, String wert, String grund) {
        boolean passt(String k, String m, String s) {
            return klasse.equals(k) && methode.equals(m) && signatur.equals(s);
        }

        boolean istWeiterleitung() {
            return wert.startsWith("-> ");
        }

        String zielBesitzer() {
            String ohne = wert.substring(3);
            return ohne.substring(0, ohne.lastIndexOf('.'));
        }

        String zielName() {
            String ohne = wert.substring(3);
            return ohne.substring(ohne.lastIndexOf('.') + 1);
        }
    }

    /**
     * Regeln je Jar, nicht in einem Topf.
     *
     * <p>Weil der Waechter sonst luegen muesste: das Werkzeug meldet, wenn eine Regel nicht
     * greift — und genau das hat schon zweimal einen Fehler gefunden. Liefe eine
     * FlatLaf-Regel ueber den Port, griffe sie dort nie, und die Meldung waere ab dann
     * Rauschen statt Warnung.
     *
     * <p>Aufruf: {@code java Kurzschluss ein.jar aus.jar [satz]}, Satz ist {@code port}
     * (Vorgabe) oder {@code flatlaf}.
     */
    private static final java.util.Map<String, List<Regel>> SAETZE = new java.util.LinkedHashMap<>();

    private static final List<Regel> PORT = List.of(
            new Regel("sun/font/FontUtilities", "fontSupportsDefaultEncoding",
                    "(Ljava/awt/Font;)Z", "true",
                    "Fragt ueber Font.getFont2D die Schriftmaschine, ob eine Schrift die "
                            + "Standardkodierung traegt. Darunter liegt FreeType. FlatLaf kommt "
                            + "ueber LinuxFontPolicy hier heraus — auf Android ist os.name "
                            + "'Linux'. Die Plattformschriften koennen Latin-1; 'true' ist "
                            + "keine Notluege, sondern die Antwort."),
            new Regel("java/awt/EventQueue", "isDispatchThread", "()Z", "true",
                    "Auf dieser Plattform gibt es genau EINEN Faden, der auslegt, malt und "
                            + "zustellt: Androids Hauptfaden, spaeter iOS' Hauptfaden. Er IST "
                            + "der Ereignisfaden — es gibt keinen zweiten, von dem man ihn "
                            + "unterscheiden koennte. Swing fragt oft danach, und 'false' "
                            + "laesst es Arbeit auf einen Faden verschieben, den niemand "
                            + "abarbeitet. Gefunden an DefaultCaret: dessen Vorgabe "
                            + "UPDATE_WHEN_ON_EDT liess den Schreibzeiger stehen, und die "
                            + "Ruecktaste loeschte daraufhin das falsche Zeichen."),
            new Regel("java/awt/Component", "getLocationOnScreen_NoTreeLock",
                    "()Ljava/awt/Point;", "-> tsb/port/Ortung.aufDemSchirm",
                    "Sucht den naechsten nativen Behaelter und fragt dessen Peer. Ohne Fenster "
                            + "gibt es keinen: getNativeContainer() liefert null, und die "
                            + "naechste Zeile liest darauf ein Feld. Gefunden am Dropdown — "
                            + "JPopupMenu.show braucht Bildschirmkoordinaten, der Pfeil "
                            + "reagierte, die Liste erschien nie. Hier ist der Baum der "
                            + "Bildschirm, also ist die Antwort die Summe der Positionen."),
            new Regel("javax/swing/SwingUtilities", "getRoot",
                    "(Ljava/awt/Component;)Ljava/awt/Component;", "-> tsb/port/Ortung.wurzel",
                    "Sucht ein Window oder ein Applet und gibt sonst null zurueck. Genau "
                            + "dieses null liess jedes Dropdown scheitern: "
                            + "LightWeightPopup.fitsOnScreen() bekommt null, meldet 'passt "
                            + "nicht', und PopupFactory weicht auf ein Fenster aus — das es "
                            + "hier nicht gibt (Toolkit.createFrame). Ohne Fenster ist der "
                            + "oberste Baustein die Wurzel."),
            new Regel("javax/swing/PopupFactory$ContainerPopup", "fitsOnScreen", "()Z", "true",
                    "Prueft, ob ein Aufklappmenue in das Fenster passt — und kennt dabei nur "
                            + "JFrame, JDialog, JWindow und JApplet. Fuer alles andere gibt es "
                            + "keinen else-Zweig: das Ergebnis bleibt false. Ein Baum ohne "
                            + "Fenster kann die Pruefung also gar nicht bestehen, und "
                            + "PopupFactory weicht auf ein Fenster aus, das es hier nicht "
                            + "gibt. Hier ist die Ansicht der Bildschirm; ein Menue faellt "
                            + "nirgends herunter, es wird hoechstens beschnitten — und ein "
                            + "beschnittenes Menue ist besser als keines."),
            new Regel("javax/swing/SwingUtilities", "convertScreenLocationToParent",
                    "(Ljava/awt/Container;II)Ljava/awt/Point;", "-> tsb/port/Ortung.imElter",
                    "Sucht ein Window und wirft sonst ausdruecklich einen Error. Gebraucht "
                            + "wird es, solange ein Aufklappmenue offen ist: jede Bewegung "
                            + "fragt, ueber welchem Eintrag der Finger steht. Ohne Fenster ist "
                            + "die Umrechnung die Summe der Positionen, andersherum.")
    );

    private static final List<Regel> FLATLAF = List.of(
            new Regel("com/formdev/flatlaf/util/JavaCompatibility", "drawStringUnderlineCharAt",
                    "(Ljavax/swing/JComponent;Ljava/awt/Graphics;Ljava/lang/String;III)V",
                    "-> tsb/port/Textmalen.drawStringUnderlineCharAt",
                    "FlatLaf sucht diese Methode ueber MethodHandle, weil sie im JDK umgezogen "
                            + "ist und FlatLaf auf 8 bis 21 laufen muss. Unter uns liegt immer "
                            + "JDK 8 — die Frage stellt sich nicht. Und der Umweg geht hier "
                            + "nicht: MobiVM hat MethodType.methodType nicht (gemessen auf dem "
                            + "iPhone-Simulator, beim ersten Text), Android erst ab API 26."),
            new Regel("com/formdev/flatlaf/util/JavaCompatibility", "getClippedString",
                    "(Ljavax/swing/JComponent;Ljava/awt/FontMetrics;Ljava/lang/String;I)"
                            + "Ljava/lang/String;",
                    "-> tsb/port/Textmalen.getClippedString",
                    "Dieselbe Bauart wie drawStringUnderlineCharAt, derselbe Grund.")
    );

    static {
        SAETZE.put("port", PORT);
        SAETZE.put("flatlaf", FLATLAF);
    }

    private static List<Regel> REGELN;

    public static void main(String[] args) throws Exception {
        Path quelle = Path.of(args[0]);
        Path ziel = Path.of(args[1]);
        String satz = args.length > 2 ? args[2] : "port";
        REGELN = SAETZE.get(satz);
        if (REGELN == null) {
            System.out.println("  Unbekannter Regelsatz: " + satz + " — bekannt: " + SAETZE.keySet());
            System.exit(2);
        }
        System.out.println("  Regelsatz: " + satz);

        List<Regel> gegriffen = new ArrayList<>();

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

                String pfad = eintrag.getName();
                if (pfad.endsWith(".class")) {
                    String klasse = pfad.substring(0, pfad.length() - ".class".length());
                    boolean betroffen = REGELN.stream().anyMatch(r -> r.klasse().equals(klasse));
                    if (betroffen) {
                        ClassReader leser = new ClassReader(inhalt);
                        ClassWriter schreiber = new ClassWriter(0);
                        leser.accept(new ClassVisitor(Opcodes.ASM9, schreiber) {
                            @Override
                            public MethodVisitor visitMethod(int zugriff, String name, String besch,
                                                             String sig, String[] wirft) {
                                for (Regel r : REGELN) {
                                    if (r.passt(klasse, name, besch)) {
                                        gegriffen.add(r);
                                        MethodVisitor m = super.visitMethod(zugriff, name, besch, sig, wirft);
                                        if (r.istWeiterleitung()) {
                                            schreibeWeiterleitung(m, klasse, besch, r,
                                                    (zugriff & Opcodes.ACC_STATIC) != 0);
                                        } else {
                                            schreibeRumpf(m, besch, r.wert());
                                        }
                                        return null;   // den urspruenglichen Rumpf verwerfen
                                    }
                                }
                                return super.visitMethod(zugriff, name, besch, sig, wirft);
                            }
                        }, 0);
                        inhalt = schreiber.toByteArray();
                    }
                }

                aus.putNextEntry(new ZipEntry(pfad));
                aus.write(inhalt);
                aus.closeEntry();
            }
        }

        System.out.println("  Schnitte gesetzt: " + gegriffen.size() + " von " + REGELN.size());
        for (Regel r : REGELN) {
            System.out.println((gegriffen.contains(r) ? "      OK    " : "      LEER  ")
                    + r.klasse() + "." + r.methode());
        }
        System.out.println("  geschrieben:      " + ziel);
        if (gegriffen.size() != REGELN.size()) {
            System.out.println("  ACHTUNG: eine Regel hat nicht gegriffen — sie sieht nach"
                    + " Absicherung aus und ist keine.");
            System.exit(1);
        }
    }

    /**
     * Ein Rumpf, der nur weiterreicht: {@code return Ziel.methode(this);}
     *
     * <p>Nur fuer Methoden ohne Parameter — mehr wurde bisher nicht gebraucht, und eine
     * allgemeine Fassung waere Code fuer einen Fall, den es nicht gibt.
     */
    private static void schreibeWeiterleitung(MethodVisitor m, String klasse, String signatur,
                                              Regel regel, boolean statisch) {
        Type rueck = Type.getReturnType(signatur);
        Type[] parameter = Type.getArgumentTypes(signatur);

        // Bei einer statischen Methode ist der erste Parameter schon das, was das Ziel
        // braucht; bei einer Instanzmethode ist es "this", und die Signatur des Ziels bekommt
        // den Empfaengertyp vorangestellt.
        String zielSignatur = statisch ? signatur : "(L" + klasse + ";)" + rueck.getDescriptor();

        m.visitCode();
        int platz = 0;
        if (!statisch) {
            m.visitVarInsn(Opcodes.ALOAD, 0);
            platz = 1;
        } else {
            for (Type p : parameter) {
                m.visitVarInsn(p.getOpcode(Opcodes.ILOAD), platz);
                platz += p.getSize();
            }
        }
        m.visitMethodInsn(Opcodes.INVOKESTATIC, regel.zielBesitzer(), regel.zielName(),
                zielSignatur, false);
        m.visitInsn(rueck.getOpcode(Opcodes.IRETURN));
        m.visitMaxs(Math.max(1, platz), Math.max(1, platz));
        m.visitEnd();
    }

    /** Ein Rumpf, der nichts tut ausser zurueckzugeben. Stapeltiefe hoechstens eins. */
    private static void schreibeRumpf(MethodVisitor m, String signatur, String wert) {
        m.visitCode();
        Type rueck = Type.getReturnType(signatur);
        switch (rueck.getSort()) {
            case Type.VOID:
                m.visitInsn(Opcodes.RETURN);
                break;
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.CHAR:
            case Type.SHORT:
            case Type.INT:
                m.visitInsn("true".equals(wert) ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
                m.visitInsn(Opcodes.IRETURN);
                break;
            case Type.LONG:
                m.visitInsn(Opcodes.LCONST_0);
                m.visitInsn(Opcodes.LRETURN);
                break;
            case Type.FLOAT:
                m.visitInsn(Opcodes.FCONST_0);
                m.visitInsn(Opcodes.FRETURN);
                break;
            case Type.DOUBLE:
                m.visitInsn(Opcodes.DCONST_0);
                m.visitInsn(Opcodes.DRETURN);
                break;
            default:
                m.visitInsn(Opcodes.ACONST_NULL);
                m.visitInsn(Opcodes.ARETURN);
        }
        m.visitMaxs(2, Type.getArgumentTypes(signatur).length + 1);
        m.visitEnd();
    }
}
