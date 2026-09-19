package tsb.port;

import java.awt.Component;
import java.awt.Point;

/**
 * Wo ein Baustein auf dem Bildschirm sitzt — ohne Fenster.
 *
 * <h2>Warum es das geben muss</h2>
 *
 * <p>{@code Component.getLocationOnScreen()} sucht sich den naechsten <b>nativen</b> Behaelter
 * und fragt dessen Peer. Auf dem Desktop ist das das Fenster. Hier gibt es keines:
 * {@code getNativeContainer()} liefert {@code null}, und die naechste Zeile liest darauf ein
 * Feld —
 *
 * <pre>
 * NullPointerException: Attempt to read from field 'java.awt.Component.peer'
 *     bei java.awt.Component.getLocationOnScreen_NoTreeLock(Component.java:2051)
 * </pre>
 *
 * <p>Gefunden am Dropdown: {@code JPopupMenu.show} braucht Bildschirmkoordinaten, um das
 * Aufklappmenue zu setzen. Der Pfeil reagierte, die Liste erschien nie.
 *
 * <h2>Und warum die Antwort so kurz ist</h2>
 *
 * <p>Ein Fenster ist eine Zwischenstufe: Baustein → Fenster → Bildschirm. Ohne Fenster faellt
 * die Zwischenstufe weg, und was bleibt, ist die Summe der Positionen bis zur Wurzel. <b>Der
 * Baum ist der Bildschirm.</b>
 *
 * <p>Das stimmt hier sogar genauer als auf dem Desktop: die Maske belegt die ganze Ansicht,
 * und der Massstab wird erst beim Malen angewandt. Wer diese Punkte vergleicht — und mehr tut
 * ein Aufklappmenue nicht —, vergleicht Entwurfseinheiten mit Entwurfseinheiten.
 */
public final class Ortung {

    private Ortung() {
    }

    /**
     * Die Wurzel eines Baums ohne Fenster.
     *
     * <p>{@code SwingUtilities.getRoot} laeuft nach oben und sucht ein {@code Window} oder ein
     * {@code Applet}. Findet es keines, gibt es <b>null</b> zurueck — und dieses null ist der
     * Grund, warum kein Dropdown aufklappte:
     *
     * <pre>
     * LightWeightPopup.fitsOnScreen()  →  getRoot(owner) == null  →  "passt nicht"
     *                                  →  PopupFactory weicht auf ein Fenster aus
     *                                  →  Toolkit.createFrame  →  gibt es hier nicht
     * </pre>
     *
     * <p>Die Antwort ist nicht, ein Fenster zu bauen, sondern die Frage richtig zu
     * beantworten: <b>ohne Fenster ist der oberste Baustein die Wurzel.</b> Er ist es, der die
     * ganze Flaeche belegt, und an ihm gemessen passt ein Aufklappmenue oder passt nicht.
     *
     * <p>Ein echtes {@code Window} gewinnt weiter, falls es je eines gibt — die Schleife
     * sucht zuerst danach.
     */
    public static Component wurzel(Component baustein) {
        Component oberster = baustein;
        for (Component p = baustein; p != null; p = p.getParent()) {
            if (p instanceof java.awt.Window) return p;
            oberster = p;
        }
        return oberster;
    }

    /**
     * Vom Bildschirmpunkt zum Baustein darunter — ohne den Umweg ueber ein Fenster.
     *
     * <p>{@code SwingUtilities.convertScreenLocationToParent} laeuft nach oben, sucht ein
     * {@code Window} und wirft, wenn es keines gibt:
     *
     * <pre>
     * Error: convertScreenLocationToParent: no window ancestor
     * </pre>
     *
     * <p>Benutzt wird es, waehrend ein Aufklappmenue offen ist: jede Mausbewegung fragt, ueber
     * welchem Eintrag der Finger steht. Ohne diese Naht klappt das Menue auf und nimmt keine
     * Auswahl entgegen.
     *
     * <p>Die Umrechnung ist dieselbe wie in {@link #aufDemSchirm} — nur andersherum.
     */
    public static Point imElter(java.awt.Container elter, int x, int y) {
        Point ort = aufDemSchirm(elter);
        return new Point(x - ort.x, y - ort.y);
    }

    public static Point aufDemSchirm(Component baustein) {
        int x = 0;
        int y = 0;
        for (Component k = baustein; k != null; k = k.getParent()) {
            x += k.getX();
            y += k.getY();
        }
        return new Point(x, y);
    }
}
