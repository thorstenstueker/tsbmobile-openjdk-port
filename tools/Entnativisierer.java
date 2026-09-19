import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Nimmt jeder Methode im Port das {@code native} und gibt ihr einen Rumpf.
 *
 * <h2>Warum überhaupt</h2>
 *
 * <p>Ein mitgeliefertes {@code java.desktop} bringt 438 native Methoden in 141 Klassen mit,
 * und dahinter steht {@code libawt} — eine Bibliothek, die es auf dem Telefon nicht gibt
 * und nicht geben soll. Jede dieser Methoden hält den Start auf, sobald ihre Klasse
 * initialisiert wird: zuerst {@code Toolkit.initIDs}, dann {@code Component.initIDs}, und so
 * weiter, eine Klasse nach der anderen.
 *
 * <h2>Zwei Sorten, und der Unterschied ist der ganze Punkt</h2>
 *
 * <p><b>{@code initIDs} und {@code registerNatives}</b> bekommen einen <i>leeren</i> Rumpf.
 * Sie registrieren JNI-Feldkennungen für {@code libawt}. Ohne {@code libawt} gibt es nichts
 * zu registrieren, und nichts zu tun ist hier die richtige Tat.
 *
 * <p><b>Alles andere</b> wirft einen {@link UnsatisfiedLinkError} <i>mit seinem Namen darin</i>.
 * Das ist bewusst kein stiller Standardwert: eine {@code Blit}-Methode, die still nichts tut,
 * ergäbe ein Fenster, das einfach leer bleibt — und niemand wüsste, warum. So sagt der erste
 * Lauf, welche native Methode tatsächlich gebraucht wird, in der Reihenfolge, in der sie
 * gebraucht wird. <b>Das macht dieses Werkzeug zu einem Messinstrument statt zu einer
 * Attrappe.</b>
 *
 * <p>Die Liste, die dabei herauskommt, ist genau die Arbeitsliste für den Skia-Unterbau.
 */
public final class Entnativisierer {

    public static void main(String[] args) throws IOException {
        Path quelle = Path.of(args[0]);
        Path ziel = Path.of(args[1]);

        List<String> geleert = new ArrayList<>();
        List<String> werfend = new ArrayList<>();

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
                    inhalt = umschreiben(inhalt, geleert, werfend);
                }
                aus.putNextEntry(new ZipEntry(eintrag.getName()));
                aus.write(inhalt);
                aus.closeEntry();
            }
        }

        System.out.println("geleert (initIDs/registerNatives): " + geleert.size());
        System.out.println("werfend (echte Arbeit):            " + werfend.size());
        Files.write(Path.of("nativ-werfend.txt"), werfend);
        Files.write(Path.of("nativ-geleert.txt"), geleert);
    }

    private static byte[] umschreiben(byte[] klasse, List<String> geleert, List<String> werfend) {
        ClassReader leser = new ClassReader(klasse);
        // COMPUTE_MAXS genügt: die erzeugten Rümpfe sind ein RETURN oder ein ATHROW, also
        // ohne Sprünge und ohne Rahmen, die berechnet werden müssten.
        ClassWriter schreiber = new ClassWriter(leser, ClassWriter.COMPUTE_MAXS);

        leser.accept(new ClassVisitor(Opcodes.ASM9, schreiber) {
            private String klassenName;

            @Override
            public void visit(int fassung, int zugriff, String name, String signatur,
                              String ober, String[] schnittstellen) {
                this.klassenName = name;
                super.visit(fassung, zugriff, name, signatur, ober, schnittstellen);
            }

            @Override
            public MethodVisitor visitMethod(int zugriff, String name, String beschreibung,
                                             String signatur, String[] ausnahmen) {
                if ((zugriff & Opcodes.ACC_NATIVE) == 0) {
                    return super.visitMethod(zugriff, name, beschreibung, signatur, ausnahmen);
                }

                String voll = klassenName.replace('/', '.') + "." + name + beschreibung;
                boolean harmlos = name.equals("initIDs") || name.equals("registerNatives");
                Type rueckgabe = Type.getReturnType(beschreibung);

                // Ein leerer Rumpf geht nur, wenn nichts zurückzugeben ist. Eine
                // initIDs mit Rückgabewert gibt es nicht, aber verlassen wird sich
                // darauf nicht.
                boolean leeren = harmlos && rueckgabe.getSort() == Type.VOID;
                (leeren ? geleert : werfend).add(voll);

                MethodVisitor m = super.visitMethod(
                        zugriff & ~Opcodes.ACC_NATIVE, name, beschreibung, signatur, ausnahmen);
                m.visitCode();
                if (leeren) {
                    m.visitInsn(Opcodes.RETURN);
                } else {
                    m.visitTypeInsn(Opcodes.NEW, "java/lang/UnsatisfiedLinkError");
                    m.visitInsn(Opcodes.DUP);
                    m.visitLdcInsn("tsbMobile: " + voll + " braucht einen Unterbau");
                    m.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/UnsatisfiedLinkError",
                            "<init>", "(Ljava/lang/String;)V", false);
                    m.visitInsn(Opcodes.ATHROW);
                }
                m.visitMaxs(0, 0);
                m.visitEnd();
                return null;
            }
        }, 0);

        return schreiber.toByteArray();
    }
}
