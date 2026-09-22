/*
 * tsbMobile: ersetzt jdk.internal.access.SharedSecrets des JDK 17 fuer den Port.
 *
 * Das Original vermittelt Zugriffe zwischen den Modulen des JDK ueber ein Dutzend
 * Schnittstellen und haengt an jdk.internal.misc.Unsafe, an java.lang.Module und an
 * Klassen, die weder Android noch RoboVM haben. Der Port braucht vier davon: die AWT-
 * und Beans-Zugaenge, die java.desktop selbst setzt, und den Sicherheitszugang, den
 * EventQueue, RepaintManager, TransferHandler und DocumentHandler abfragen.
 *
 * Im JDK-8-Port wurden dafuer vier Klassen gepatcht und ein Block in AppContext
 * gestrichen. Ein Ersatz an dieser einen Stelle macht beides ueberfluessig.
 *
 * Lizenz: GPLv2 mit Classpath-Ausnahme, wie das Original.
 */
package jdk.internal.access;

public final class SharedSecrets {

    private static JavaAWTAccess javaAWTAccess;
    private static JavaAWTFontAccess javaAWTFontAccess;
    private static JavaBeansAccess javaBeansAccess;

    private SharedSecrets() {
    }

    public static JavaSecurityAccess getJavaSecurityAccess() {
        return TsbSicherheitszugang.INSTANZ;
    }

    public static void setJavaAWTAccess(JavaAWTAccess jaa) {
        javaAWTAccess = jaa;
    }

    public static JavaAWTAccess getJavaAWTAccess() {
        return javaAWTAccess;
    }

    public static void setJavaAWTFontAccess(JavaAWTFontAccess jafa) {
        javaAWTFontAccess = jafa;
    }

    public static JavaAWTFontAccess getJavaAWTFontAccess() {
        if (javaAWTFontAccess == null) {
            // Wie im Original: die Klasse, die den Zugang setzt, initialisieren.
            try {
                Class.forName("java.awt.font.TextAttribute", true,
                        SharedSecrets.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new InternalError(e);
            }
        }
        return javaAWTFontAccess;
    }

    public static void setJavaBeansAccess(JavaBeansAccess access) {
        javaBeansAccess = access;
    }

    public static JavaBeansAccess getJavaBeansAccess() {
        return javaBeansAccess;
    }
}
