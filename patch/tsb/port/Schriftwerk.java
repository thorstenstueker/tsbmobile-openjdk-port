package tsb.port;

import java.awt.Font;

/**
 * Misst Text. Die eine Schnittstelle, die jede Plattform selbst erfüllt.
 *
 * <h2>Warum hier der Schnitt liegt</h2>
 *
 * <p>Swing fragt über {@code JComponent.getFontMetrics} nach der Breite eines Textes, und
 * das JDK antwortet über {@code sun.font.FontDesignMetrics} → {@code SunFontManager} →
 * {@code StrikeCache} → FreeType. Diese Kette hält auf beiden Telefonen an, aus jeweils
 * eigenem Grund: auf iOS fehlt die native Methode
 * {@code StrikeCache.getGlyphCacheDescription}, auf Android verwehrt die Laufzeit
 * {@code Unsafe.getUnsafe()} allem, was nicht vom Bootclasspath kommt.
 *
 * <p><b>Beide Male ist die Reparatur falsch.</b> Eine Schriftmaschine nachzubauen, die
 * daneben schon im Betriebssystem liegt, wäre verschwendete Arbeit — Android zeichnet mit
 * Skia, iOS mit CoreText, und beide messen Text besser als ein nachgebauter Glyphen-Cache.
 *
 * <p>Deshalb wird nicht repariert, sondern <b>abgeschnitten</b>: {@code SwingUtilities2}
 * fragt künftig hier, und hier antwortet die Plattform.
 *
 * <h2>Was eine Umsetzung liefern muss</h2>
 *
 * <p>Fünf Zahlen, mehr braucht Swing nicht, um eine Maske auszulegen. Die Breite eines
 * Textes bestimmt die bevorzugte Größe eines {@code JLabel}; Ober- und Unterlänge
 * bestimmen, wo die Grundlinie liegt.
 *
 * <p>Alle Werte in Punkten, nicht in Gerätepixeln — Swing rechnet in derselben Einheit, in
 * der der Designer zeichnet.
 */
public interface Schriftwerk {

    /** Die Breite dieses Textes in dieser Schrift. */
    float breite(Font schrift, String text);

    /** Von der Grundlinie nach oben. */
    float oberlaenge(Font schrift);

    /** Von der Grundlinie nach unten. */
    float unterlaenge(Font schrift);

    /** Der Abstand zwischen zwei Zeilen, über Ober- und Unterlänge hinaus. */
    float zeilenabstand(Font schrift);

    /** Die Breite eines einzelnen Zeichens. Swing fragt das für Zeichenketten zeichenweise. */
    float zeichenbreite(Font schrift, char zeichen);
}
