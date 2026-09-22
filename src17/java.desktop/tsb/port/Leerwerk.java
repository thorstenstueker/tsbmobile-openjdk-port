package tsb.port;

import java.awt.Font;

/**
 * Ein {@link Zeichenwerk}, das nichts tut.
 *
 * <p>Fuer {@code GraphicsEnvironment.createGraphics(BufferedImage)}: Swing malt Textfelder
 * und andere Bausteine zwischendurch in eine Flaeche ausserhalb des Bildschirms und kopiert
 * sie danach. Ohne eine solche Flaeche bricht das Malen ab — mit einer, die nichts tut,
 * laeuft es weiter, und was dort gemalt worden waere, fehlt eben im Bild.
 *
 * <p>Das ist bewusst ein Zwischenschritt und keine Loesung: sobald ein
 * {@code BufferedImage}-Unterbau steht, gehoert hierher eine Flaeche, die wirklich etwas
 * aufnimmt. Bis dahin ist ein fehlender Baustein besser als eine abgebrochene Maske —
 * dieselbe Ueberlegung wie bei den Luecken in {@link Portgrafik}.
 */
public final class Leerwerk implements Zeichenwerk {

    @Override public int sichern() { return 0; }
    @Override public void wiederherstellen(int marke) { }
    @Override public void verschieben(float dx, float dy) { }
    @Override public void skalieren(float sx, float sy) { }
    @Override public void schneiden(int x, int y, int breite, int hoehe) { }
    @Override public void fuelleRechteck(int x, int y, int b, int h, int argb) { }
    @Override public void stricheRechteck(int x, int y, int b, int h, int argb, float d) { }
    @Override public void fuelleRundRechteck(int x, int y, int b, int h, int bb, int bh, int argb) { }
    @Override public void stricheRundRechteck(int x, int y, int b, int h, int bb, int bh, int argb, float d) { }
    @Override public void fuelleOval(int x, int y, int b, int h, int argb) { }
    @Override public void stricheOval(int x, int y, int b, int h, int argb, float d) { }
    @Override public void pfad(java.util.List<float[]> teilzuege, boolean geradeUngerade,
                               boolean fuellen, int argb, float d) { }
    @Override public void linie(int x1, int y1, int x2, int y2, int argb, float d) { }
    @Override public void text(String t, float x, float g, Font s, int argb) { }
    @Override public void bild(int[] argb, int qb, int qh, int x, int y, int b, int h) { }
}
