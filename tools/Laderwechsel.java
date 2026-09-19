import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Leitet {@code ClassLoader.getSystemClassLoader()} im Port auf {@link tsb.port.Lader} um.
 *
 * <h2>Die Messung dahinter</h2>
 *
 * <p>Auf Android entsteht der Systemlader aus {@code java.class.path}, und das ist im
 * App-Prozess {@code "."}. Gemessen im Emulator, API 36:
 *
 * <pre>
 * PathClassLoader[DexPathList[[directory "."]]]   (ANDERER als unserer)
 * </pre>
 *
 * <p>Er kann also keine Klasse aus der APK laden. Das JDK benutzt ihn trotzdem, um
 * Plattformklassen nach Namen zu holen — in 17 Klassen des Ports, darunter
 * {@code javax.swing.UIDefaults}, ueber die Swing <b>seine UI-Delegierten findet</b>.
 *
 * <h2>Warum umschreiben und nicht flicken</h2>
 *
 * <p>Siebzehn Quellen zu patchen waere siebzehn Gelegenheiten, eine zu vergessen — und genau
 * das ist in diesem Vorhaben schon zweimal passiert ({@code SharedSecrets},
 * {@code java.beans}). Eine Regel ueber alle Klassen ist nicht nur kuerzer, sie bleibt
 * richtig, wenn der naechste JDK-Stand achtzehn Stellen hat.
 *
 * <p>Der Eingriff ist minimal: ein {@code INVOKESTATIC} auf ein anderes {@code INVOKESTATIC}
 * mit derselben Signatur ({@code ()Ljava/lang/ClassLoader;}). Keine Stapeltiefe aendert sich,
 * kein Rahmen muss neu gerechnet werden — deshalb {@code ClassWriter(0)}.
 *
 * <p>Aufruf: {@code java -cp .:asm:asm-commons Laderwechsel ein.jar aus.jar}
 */
public final class Laderwechsel {

    private static final String ALT_BESITZER = "java/lang/ClassLoader";
    private static final String ALT_NAME = "getSystemClassLoader";
    private static final String SIGNATUR = "()Ljava/lang/ClassLoader;";
    private static final String NEU_BESITZER = "tsb/port/Lader";
    private static final String NEU_NAME = "system";

    public static void main(String[] args) throws Exception {
        Path quelle = Path.of(args[0]);
        Path ziel = Path.of(args[1]);

        Set<String> betroffen = new LinkedHashSet<>();
        int[] stellen = {0};

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
                    ClassReader leser = new ClassReader(inhalt);
                    ClassWriter schreiber = new ClassWriter(0);
                    String name = eintrag.getName();
                    leser.accept(new ClassVisitor(Opcodes.ASM9, schreiber) {
                        @Override
                        public MethodVisitor visitMethod(int zugriff, String mName, String besch,
                                                         String signatur, String[] wirft) {
                            MethodVisitor m = super.visitMethod(zugriff, mName, besch, signatur, wirft);
                            return new MethodVisitor(Opcodes.ASM9, m) {
                                @Override
                                public void visitMethodInsn(int befehl, String besitzer, String iName,
                                                            String iBesch, boolean schnittstelle) {
                                    if (befehl == Opcodes.INVOKESTATIC
                                            && ALT_BESITZER.equals(besitzer)
                                            && ALT_NAME.equals(iName)
                                            && SIGNATUR.equals(iBesch)) {
                                        stellen[0]++;
                                        betroffen.add(name);
                                        super.visitMethodInsn(Opcodes.INVOKESTATIC, NEU_BESITZER,
                                                NEU_NAME, SIGNATUR, false);
                                        return;
                                    }
                                    super.visitMethodInsn(befehl, besitzer, iName, iBesch, schnittstelle);
                                }
                            };
                        }
                    }, 0);
                    inhalt = schreiber.toByteArray();
                }

                ZipEntry neu = new ZipEntry(eintrag.getName());
                aus.putNextEntry(neu);
                aus.write(inhalt);
                aus.closeEntry();
            }
        }

        System.out.println("  Aufrufstellen umgeleitet: " + stellen[0]);
        System.out.println("  in Klassen:               " + betroffen.size());
        for (String k : betroffen) System.out.println("      " + k);
        System.out.println("  geschrieben:              " + ziel);
    }
}
