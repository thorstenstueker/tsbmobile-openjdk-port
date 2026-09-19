package tsb.port;

import java.awt.Font;

/**
 * Was eine Plattform zeichnen können muss. Die zweite Schnittstelle nach {@link Schriftwerk}.
 *
 * <h2>Warum so wenige Methoden</h2>
 *
 * <p>{@code java.awt.Graphics2D} hat 75 abstrakte Methoden. Die hier sind zwölf, und das ist
 * kein Sparen, sondern die Arbeitsteilung: **alles, was reine Rechnung ist, bleibt in
 * {@link Portgrafik}** — Zustand, Umrechnung, Formen auf Rechtecke zurückführen. Nur was
 * wirklich Farbe auf eine Fläche bringt, steht hier.
 *
 * <p>Damit ist die Plattformhälfte klein genug, um sie zweimal zu schreiben: einmal auf
 * {@code android.graphics.Canvas}, einmal auf Skia. Und beide Male ist es dieselbe Liste.
 *
 * <h2>Koordinaten</h2>
 *
 * <p>In der <b>örtlichen</b> Ebene, nicht in Gerätepixeln. Verschiebung, Maßstab und Schnitt
 * werden über {@link #sichern}, {@link #verschieben}, {@link #skalieren} und
 * {@link #schneiden} gesetzt; die Plattform rechnet sie mit, weil beide — Canvas wie Skia —
 * das ohnehin in Hardware tun.
 *
 * <p>Das ist der Grund, warum {@code Portgrafik} keine Matrizen multipliziert: Skia kann das
 * besser, und doppelt gerechnet wäre doppelt falsch.
 *
 * <h2>Farben</h2>
 *
 * <p>Als {@code int} in ARGB, so wie {@code java.awt.Color.getRGB()} und
 * {@code android.graphics.Color} es beide verstehen. Keine Farbobjekte über die Grenze.
 */
public interface Zeichenwerk {

    /**
     * Zustand merken — Verschiebung, Maßstab, Schnitt.
     *
     * @return eine Marke, die {@link #wiederherstellen(int)} genau diesen Zustand
     *         zurückholen lässt
     */
    int sichern();

    /**
     * Zurück auf die Marke — <b>nicht</b> „einen Schritt zurück".
     *
     * <p>Der Unterschied ist nicht theoretisch. Swing entsorgt ein {@code Graphics}
     * gelegentlich zweimal, und auf einem echten {@code Graphics} ist das folgenlos. Auf
     * einem Stapel nicht:
     *
     * <pre>
     * IllegalStateException: Underflow in restore - more restores than saves
     * </pre>
     *
     * <p>Das Malen brach danach mitten im Bild ab — sichtbar als halb gezeichnete, „irgendwie
     * graue" Maske, und teuer obendrein, weil jede Ausnahme ihren Stapel aufnimmt.
     *
     * <p>Mit einer Marke ist die Reihenfolge egal und ein zweites Zurückholen wirkungslos.
     * Beide Plattformen bieten das an: {@code Canvas.restoreToCount}, Skia
     * {@code restoreToCount}. Es kostet nichts und nimmt eine ganze Fehlerfamilie weg.
     */
    void wiederherstellen(int marke);

    void verschieben(float dx, float dy);

    void skalieren(float sx, float sy);

    /** Schneidet auf dieses Rechteck, zusätzlich zum bestehenden Schnitt. */
    void schneiden(int x, int y, int breite, int hoehe);

    void fuelleRechteck(int x, int y, int breite, int hoehe, int argb);

    void stricheRechteck(int x, int y, int breite, int hoehe, int argb, float dicke);

    void fuelleRundRechteck(int x, int y, int breite, int hoehe,
                            int bogenBreite, int bogenHoehe, int argb);

    void stricheRundRechteck(int x, int y, int breite, int hoehe,
                             int bogenBreite, int bogenHoehe, int argb, float dicke);

    void fuelleOval(int x, int y, int breite, int hoehe, int argb);

    void stricheOval(int x, int y, int breite, int hoehe, int argb, float dicke);

    void linie(int x1, int y1, int x2, int y2, int argb, float dicke);

    /**
     * Ein Streckenzug — und damit jede Form, die keine der obigen ist.
     *
     * <p>Die dreizehnte Methode, und sie kam nicht aus einem Entwurf, sondern aus einer
     * Messung: FlatLaf malt seine Knöpfe über {@code Path2D.Float}, und die Lückenliste
     * sagte {@code fill(Float)}. Eine Form einzeln nachzuziehen hätte die nächste offen
     * gelassen — {@code Arc2D}, Polygone, was FlatLaf als nächstes benutzt.
     *
     * <p>Deshalb hier die allgemeine Antwort. <b>Kurven kommen nicht an:</b>
     * {@link Portgrafik} zerlegt jeden Pfad vorher in Strecken
     * ({@code getPathIterator(null, 0.5)}), weil ein Streckenzug auf jeder Plattform
     * dasselbe bedeutet und eine Bézier-Kurve drei verschiedene Schreibweisen hat.
     *
     * @param teilzuege      je Eintrag ein Streckenzug als {@code x0,y0,x1,y1,…}; geschlossene
     *                       wiederholen ihren ersten Punkt am Ende
     * @param geradeUngerade Füllregel: {@code true} = even-odd, {@code false} = nonzero.
     *                       Nur beim Füllen von Belang, und nur bei Formen, die sich selbst
     *                       überschneiden — dort entscheidet sie über Loch oder Fläche
     * @param fuellen        Fläche statt Kontur
     */
    void pfad(java.util.List<float[]> teilzuege, boolean geradeUngerade, boolean fuellen,
              int argb, float dicke);

    /**
     * Text auf der Grundlinie.
     *
     * <p>{@code y} ist die Grundlinie, nicht die Oberkante — wie bei
     * {@code Graphics.drawString} und wie bei {@code Canvas.drawText}. Beide meinen dasselbe,
     * also wird hier nichts umgerechnet.
     */
    void text(String text, float x, float grundlinie, Font schrift, int argb);

    /**
     * Ein Bild aus rohen Pixeln.
     *
     * <h3>Warum Pixel und nicht ein Bildobjekt</h3>
     *
     * <p>Die Naht kennt weder {@code BufferedImage} noch {@code Bitmap} noch {@code CGImage} —
     * sie kennt <b>ARGB-Werte in Zeilenfolge</b>. Das ist die kleinste Form, die beide Seiten
     * ohne Umweg verstehen: Android baut daraus eine {@code Bitmap}, iOS einen
     * {@code CGBitmapContext}.
     *
     * <p>Der Zuschnitt ist Absicht und nicht auf {@code Picture} gemuenzt. <b>Alles, was
     * Pixel erzeugt statt sie zu laden</b> — ein gerenderter Plan, ein Diagramm, ein Barcode —
     * geht denselben Weg. Ein Bildobjekt entgegenzunehmen haette jede Plattform auf ihre
     * eigene Klasse festgelegt und genau das verhindert.
     *
     * @param argb       Breite × Hoehe Werte, Zeile fuer Zeile, 0xAARRGGBB
     * @param quellBreite Breite des Feldes in Pixeln
     * @param quellHoehe  Hoehe des Feldes in Pixeln
     * @param x          Ziel, linke Kante
     * @param y          Ziel, obere Kante
     * @param breite     Zielbreite — gestreckt, wenn sie von der Quelle abweicht
     * @param hoehe      Zielhoehe
     */
    void bild(int[] argb, int quellBreite, int quellHoehe,
              int x, int y, int breite, int hoehe);
}
