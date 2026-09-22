import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Nimmt dem JDK-17-Port die Aufrufe, die es auf dem Telefon nicht gibt.
 *
 * <h2>Wozu</h2>
 *
 * <p>Der JDK-8-Port kam mit vier Werkzeugen und neunzehn Handpatches aus. Neun der Patches
 * entfernten nur ein {@code System.loadLibrary}, vier weitere leiteten nur
 * {@code SharedSecrets} um. Bei JDK 17 kommt das Modulsystem dazu: {@code Class.getModule()}
 * an zehn Stellen, darunter {@code UIDefaults.getUI} auf dem Weg zu jedem Widget; ein
 * {@code Thread}-Konstruktor mit fuenf Argumenten an 32 Stellen; {@code MethodHandles.lookup()}
 * in den Accessoren. Weder Android noch der RoboVM-Fork haben davon alles.
 *
 * <p>Neunzehn Patches auf 17 nachzuziehen und dann fuer jede Modulstelle einen weiteren zu
 * schreiben, waere die Arbeit, die dieses Werkzeug spart — und die beim naechsten JDK-Stand
 * wieder anfiele. Eine Regel je Aufruf, ueber alle Klassen, bleibt richtig, wenn die
 * naechste Fassung eine Stelle mehr hat.
 *
 * <h2>Wie</h2>
 *
 * <p>Jede Regel tauscht eine Aufrufanweisung gegen eine andere mit derselben Stapelform:
 * ein {@code invokevirtual} auf ein {@code invokestatic}, das den Empfaenger als erstes
 * Argument bekommt; ein {@code invokestatic} auf ein anderes. Deshalb {@code ClassWriter(0)}:
 * kein Rahmen muss neu gerechnet werden. Die eine Ausnahme ist der {@code Thread}-Konstruktor:
 * dort wird das fuenfte Argument ({@code inheritThreadLocals}, ein {@code boolean}) vom
 * Stapel genommen und der Vier-Argumente-Konstruktor gerufen — {@code POP} vor dem Aufruf,
 * die Rahmen an Sprungzielen bleiben gleich.
 *
 * <p>Die Zieltypen sind schon die des Ports ({@code tsb/port/Modul}); der {@code Umbenenner}
 * zieht danach die restlichen Nennungen von {@code java/lang/Module} nach.
 *
 * <p>Aufruf: {@code java -cp .:asm Modulfrei ein.jar aus.jar}
 */
public final class Modulfrei {

    /** Aufruf → Ersatz. Der Ersatz ist immer statisch. */
    private record Regel(int befehl, String besitzer, String name, String beschreibung,
                         String neuBesitzer, String neuName, String neuBeschreibung, String grund) {
        String schluessel() {
            return besitzer + "." + name + beschreibung;
        }
    }

    private static final String MODUL = "Ltsb/port/Modul;";

    private static final List<Regel> REGELN = List.of(
            new Regel(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getModule", "()Ljava/lang/Module;",
                    "tsb/port/Modul", "of", "(Ljava/lang/Class;)" + MODUL,
                    "Es gibt kein Modulsystem auf dem Telefon; alles ist das unbenannte Modul."),
            new Regel(Opcodes.INVOKESTATIC, "java/lang/Class", "forName",
                    "(Ljava/lang/Module;Ljava/lang/String;)Ljava/lang/Class;",
                    "tsb/port/Modul", "forName", "(" + MODUL + "Ljava/lang/String;)Ljava/lang/Class;",
                    "UIDefaults und UIManager laden UI-Klassen ueber das Modul; hier ueber tsb.port.Lader."),
            new Regel(Opcodes.INVOKESTATIC, "java/util/ResourceBundle", "getBundle",
                    "(Ljava/lang/String;Ljava/lang/Module;)Ljava/util/ResourceBundle;",
                    "tsb/port/Modul", "bundle", "(Ljava/lang/String;" + MODUL + ")Ljava/util/ResourceBundle;",
                    "java.awt.Window holt seine Texte ueber das Modul."),
            new Regel(Opcodes.INVOKESTATIC, "java/util/ResourceBundle", "getBundle",
                    "(Ljava/lang/String;Ljava/util/Locale;Ljava/lang/Module;)Ljava/util/ResourceBundle;",
                    "tsb/port/Modul", "bundle",
                    "(Ljava/lang/String;Ljava/util/Locale;" + MODUL + ")Ljava/util/ResourceBundle;",
                    "UIDefaults und ImageIO holen ihre Texte ueber das Modul."),
            new Regel(Opcodes.INVOKEVIRTUAL, "java/lang/ClassLoader", "getUnnamedModule", "()Ljava/lang/Module;",
                    "tsb/port/Modul", "unnamed", "(Ljava/lang/ClassLoader;)" + MODUL,
                    "java.awt.Window fragt den Lader nach seinem Modul; es ist dasselbe eine."),
            new Regel(Opcodes.INVOKESTATIC, "java/lang/ClassLoader", "getPlatformClassLoader",
                    "()Ljava/lang/ClassLoader;",
                    "tsb/port/Lader", "system", "()Ljava/lang/ClassLoader;",
                    "Den Plattformlader gibt es seit 9; auf dem Telefon ist es der eine Lader der App."),
            new Regel(Opcodes.INVOKESTATIC, "java/lang/invoke/MethodHandles", "lookup",
                    "()Ljava/lang/invoke/MethodHandles$Lookup;",
                    "tsb/port/Modul", "lookup", "()Ljava/lang/invoke/MethodHandles$Lookup;",
                    "AWTAccessor und SwingAccessor holen sich ein Lookup nur fuer ensureInitialized."),
            new Regel(Opcodes.INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "ensureInitialized",
                    "(Ljava/lang/Class;)Ljava/lang/Class;",
                    "tsb/port/Modul", "ensureInitialized",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/Class;)Ljava/lang/Class;",
                    "Seit 15; Class.forName mit initialize=true tut dasselbe, auch im JDK-8-Port."),
            new Regel(Opcodes.INVOKESTATIC, "java/lang/System", "loadLibrary", "(Ljava/lang/String;)V",
                    "tsb/port/Bibliothek", "loadLibrary", "(Ljava/lang/String;)V",
                    "libawt, libfontmanager, liblcms, libjavajpeg, libmlib_image: keine davon gibt es"
                            + " auf dem Telefon, keine wird gebraucht."),
            new Regel(Opcodes.INVOKESTATIC, "java/lang/System", "load", "(Ljava/lang/String;)V",
                    "tsb/port/Bibliothek", "load", "(Ljava/lang/String;)V",
                    "Dasselbe mit Pfad.")
    );

    /** Der Konstruktor mit inheritThreadLocals (seit 9): das fuenfte Argument faellt weg. */
    private static final String THREAD_5 = "(Ljava/lang/ThreadGroup;Ljava/lang/Runnable;Ljava/lang/String;JZ)V";
    private static final String THREAD_4 = "(Ljava/lang/ThreadGroup;Ljava/lang/Runnable;Ljava/lang/String;J)V";

    public static void main(String[] args) throws Exception {
        Path quelle = Path.of(args[0]);
        Path ziel = Path.of(args[1]);

        Map<String, Regel> nachSchluessel = new LinkedHashMap<>();
        for (Regel r : REGELN) nachSchluessel.put(r.schluessel(), r);
        Map<String, Integer> gezaehlt = new TreeMap<>();
        Map<String, Integer> klassen = new TreeMap<>();

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

                if (eintrag.getName().endsWith(".class")) {
                    String klasse = eintrag.getName();
                    ClassReader leser = new ClassReader(inhalt);
                    ClassWriter schreiber = new ClassWriter(0);
                    leser.accept(new ClassVisitor(Opcodes.ASM9, schreiber) {
                        @Override
                        public MethodVisitor visitMethod(int zugriff, String mName, String besch,
                                                         String signatur, String[] wirft) {
                            MethodVisitor m = super.visitMethod(zugriff, mName, besch, signatur, wirft);
                            return new MethodVisitor(Opcodes.ASM9, m) {
                                @Override
                                public void visitMethodInsn(int befehl, String besitzer, String iName,
                                                            String iBesch, boolean schnittstelle) {
                                    if (befehl == Opcodes.INVOKESPECIAL && "java/lang/Thread".equals(besitzer)
                                            && "<init>".equals(iName) && THREAD_5.equals(iBesch)) {
                                        zaehle(gezaehlt, klassen, "java/lang/Thread.<init>" + THREAD_5, klasse);
                                        super.visitInsn(Opcodes.POP);
                                        super.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Thread",
                                                "<init>", THREAD_4, false);
                                        return;
                                    }
                                    Regel r = nachSchluessel.get(besitzer + "." + iName + iBesch);
                                    if (r != null && r.befehl() == befehl) {
                                        zaehle(gezaehlt, klassen, r.schluessel(), klasse);
                                        super.visitMethodInsn(Opcodes.INVOKESTATIC, r.neuBesitzer(),
                                                r.neuName(), r.neuBeschreibung(), false);
                                        return;
                                    }
                                    super.visitMethodInsn(befehl, besitzer, iName, iBesch, schnittstelle);
                                }
                            };
                        }
                    }, 0);
                    inhalt = schreiber.toByteArray();
                }

                aus.putNextEntry(new ZipEntry(eintrag.getName()));
                aus.write(inhalt);
                aus.closeEntry();
            }
        }

        System.out.println("  Aufrufe umgeschrieben:");
        for (Regel r : REGELN) {
            int n = gezaehlt.getOrDefault(r.schluessel(), 0);
            System.out.println("      " + (n == 0 ? "LEER  " : "OK    ") + String.format("%4d  ", n)
                    + r.besitzer() + "." + r.name());
        }
        int t = gezaehlt.getOrDefault("java/lang/Thread.<init>" + THREAD_5, 0);
        System.out.println("      " + (t == 0 ? "LEER  " : "OK    ") + String.format("%4d  ", t)
                + "java/lang/Thread.<init>(…JZ)V → (…J)V");
        System.out.println("  in Klassen:               " + klassen.size());
        System.out.println("  geschrieben:              " + ziel);
    }

    private static void zaehle(Map<String, Integer> gezaehlt, Map<String, Integer> klassen,
                               String schluessel, String klasse) {
        gezaehlt.merge(schluessel, 1, Integer::sum);
        klassen.merge(klasse, 1, Integer::sum);
    }
}
