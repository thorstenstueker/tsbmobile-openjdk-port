/*
 * tsbMobile: ersetzt sun.awt.PlatformGraphicsInfo des JDK 17 (macOS-Fassung).
 *
 * Seit JDK 9 fragen Toolkit.getDefaultToolkit() und GraphicsEnvironment nicht mehr die
 * Eigenschaften awt.toolkit und java.awt.graphicsenv, sondern diese Klasse. Die Fassung des
 * JDK laedt libawt und erzeugt LWCToolkit; hier entstehen die Naehte des Ports.
 *
 * Lizenz: GPLv2 mit Classpath-Ausnahme, wie das Original.
 */
package sun.awt;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;

public class PlatformGraphicsInfo {

    public static GraphicsEnvironment createGE() {
        return new tsb.port.TsbGraphicsEnvironment();
    }

    public static Toolkit createToolkit() {
        return new tsb.port.TsbToolkit();
    }

    /** Nie headless: der Bildschirm ist die Ansicht der App. */
    public static boolean getDefaultHeadlessProperty() {
        return false;
    }

    public static String getDefaultHeadlessMessage() {
        return "\ntsbMobile: the port has a screen; this should not be reached.";
    }
}
