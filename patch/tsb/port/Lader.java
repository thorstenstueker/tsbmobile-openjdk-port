package tsb.port;

/**
 * Der Klassenlader, den der Port benutzt, wo das JDK den Systemlader nehmen wuerde.
 *
 * <h2>Warum</h2>
 *
 * <p>Auf Android ist {@code ClassLoader.getSystemClassLoader()} <b>nicht</b> der Lader der
 * App. Gemessen im Emulator (API 36):
 *
 * <pre>
 * PathClassLoader[DexPathList[[directory "."]]]   (ANDERER als unserer)
 * </pre>
 *
 * <p>Er entsteht aus {@code java.class.path}, und das ist in einem App-Prozess schlicht
 * {@code "."}. Er sieht die APK nicht und kann deshalb <i>keine</i> Klasse daraus laden.
 *
 * <p>Das JDK benutzt ihn an 17 Stellen, um Plattformklassen nach Namen zu holen — darunter
 * {@code sun.font.FontManagerFactory} und, viel wichtiger, {@code javax.swing.UIDefaults}:
 * <b>so findet Swing seine UI-Delegierten.</b> Ohne diese Umleitung kann ein Look-and-Feel,
 * das seine Klassen ueber Namen eintraegt — also jedes —, auf Android nicht laden.
 *
 * <h2>Wie</h2>
 *
 * <p>{@code Laderwechsel} schreibt jeden Aufruf von {@code ClassLoader.getSystemClassLoader()}
 * im Port auf {@link #system()} um. Das ist eine Regel statt siebzehn Flicken, und sie bleibt
 * richtig, wenn der naechste JDK-Stand achtzehn Stellen hat.
 *
 * <p>Die Vorgabe — der eigene Lader — ist auf allen Zielen die richtige: dort liegt der Port,
 * dort liegt die Anwendung. {@link #benutze} gibt es fuer den Fall, dass eine Einbettung
 * etwas anderes braucht.
 */
public final class Lader {

    private static volatile ClassLoader lader = Lader.class.getClassLoader();

    private Lader() {
    }

    /** Vom Einstiegspunkt der Plattform zu rufen, falls der eigene Lader nicht genuegt. */
    public static void benutze(ClassLoader neuer) {
        if (neuer != null) lader = neuer;
    }

    /** Was im Port an die Stelle von {@code ClassLoader.getSystemClassLoader()} tritt. */
    public static ClassLoader system() {
        return lader;
    }
}
