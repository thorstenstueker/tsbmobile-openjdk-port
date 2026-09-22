package tsb.port;

import java.awt.FontMetrics;
import java.awt.Graphics;

import javax.swing.JComponent;

/**
 * Was FlatLaf ueber {@code MethodHandle} sucht — hier unmittelbar gerufen.
 *
 * <h2>Warum FlatLaf das umstaendlich macht</h2>
 *
 * <p>{@code com.formdev.flatlaf.util.JavaCompatibility} ruft zwei Methoden, die im JDK
 * umgezogen sind: in Java 8 liegen sie in {@code sun.swing.SwingUtilities2}, spaeter
 * woanders. FlatLaf muss auf jedem JDK von 8 bis 21 laufen und sucht sie deshalb zur Laufzeit
 * ueber {@code MethodHandles.lookup()}.
 *
 * <p><b>Wir wissen, welches JDK darunter liegt.</b> Es ist immer JDK 8, weil der Port JDK 8
 * ist — die Frage, fuer die FlatLaf den Umweg baut, stellt sich hier nicht.
 *
 * <h2>Und warum der Umweg hier nicht geht</h2>
 *
 * <p>MobiVMs Klassenbibliothek hat {@code java.lang.invoke.MethodType.methodType} nicht:
 *
 * <pre>
 * NoSuchMethodError: java.lang.invoke.MethodType.methodType(Class, Class[])
 *     bei com.formdev.flatlaf.util.JavaCompatibility.drawStringUnderlineCharAt
 * </pre>
 *
 * <p>Gemessen auf dem iPhone-Simulator, beim ersten Text, den FlatLaf malen wollte. Auf
 * Android traf uns dieselbe Familie von der anderen Seite: dort <i>gibt</i> es
 * {@code MethodHandle}, aber erst ab API 26, und darunter ersetzt d8 die Befehle durch
 * werfende.
 *
 * <p>Ein direkter Aufruf loest beides auf einmal — und erlaubt es, die Untergrenze auf
 * Android wieder auf 24 zu senken.
 */
public final class Textmalen {

    private Textmalen() {
    }

    public static void drawStringUnderlineCharAt(JComponent c, Graphics g, String text,
                                                 int unterstrichen, int x, int y) {
        sun.swing.SwingUtilities2.drawStringUnderlineCharAt(c, g, text, unterstrichen, x, y);
    }

    public static String getClippedString(JComponent c, FontMetrics metrik, String text,
                                          int verfuegbar) {
        return sun.swing.SwingUtilities2.clipStringIfNecessary(c, metrik, text, verfuegbar);
    }
}
