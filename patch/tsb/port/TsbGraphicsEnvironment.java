package tsb.port;

/**
 * Die Grafikumgebung, die es auf dem Telefon sonst nicht gibt.
 *
 * <p>Dasselbe Muster wie bei {@link TsbToolkit}, eine Ebene tiefer:
 * {@code GraphicsEnvironment.createGE()} liest {@code java.awt.graphicsenv} und laedt die
 * genannte Klasse. Auf einem Telefon setzt diese Eigenschaft niemand, also ist der Name
 * {@code null} und {@code Class.forName(null)} wirft.
 *
 * <p>Gefunden ueber {@code MetalLookAndFeel.initComponentDefaults} →
 * {@code SwingUtilities2.isLocalDisplay} — Swing fragt beim Fuellen der Vorgabetabelle, ob
 * die Anzeige oertlich ist, um zu entscheiden, ob es Kantenglaettung einschaltet.
 *
 * <p><b>Das JDK sucht seine Plattformklassen ueber Systemeigenschaften.</b> Das ist keine
 * einzelne Luecke, sondern eine Sorte; jede wird gleich behandelt und kostet wenig.
 *
 * <h2>Warum kein GraphicsDevice</h2>
 *
 * <p>Ein {@code GraphicsDevice} steht fuer einen Bildschirm, auf dem Fenster liegen. Hier gibt
 * es keine Fenster — die Zeichenflaeche <i>ist</i> der Bildschirm. {@code getScreenDevices}
 * liefert deshalb ein leeres Feld, und wer das voreingestellte Geraet verlangt, bekommt eine
 * {@code HeadlessException} mit Begruendung statt eines Scheingeraets.
 *
 * <p>{@code createGraphics(BufferedImage)} ist die Stelle, an der spaeter der Skia-Unterbau
 * haengt. Bis dahin wirft sie mit ihrem Namen.
 */
public class TsbGraphicsEnvironment extends java.awt.GraphicsEnvironment {

    @Override
    public java.awt.GraphicsDevice[] getScreenDevices() throws java.awt.HeadlessException {
        // Genau einer, und niemals null: Swing laeuft ueber dieses Feld, wenn es entscheidet,
        // auf welchem Schirm ein Aufklappmenue landet.
        return new java.awt.GraphicsDevice[] { Bildschirm.geraet() };
    }

    @Override
    public java.awt.GraphicsDevice getDefaultScreenDevice() throws java.awt.HeadlessException {
        // War einmal eine HeadlessException. Siehe tsb.port.Bildschirm, warum nicht mehr:
        // FlatLaf fragt beim Start danach und faellt sonst auf Metal zurueck, und ohne
        // Bildschirmgrenzen klappt kein Dropdown auf.
        return Bildschirm.geraet();
    }

    /**
     * tsbMobile: eine Flaeche, die nichts aufnimmt.
     *
     * <p>Swing malt Textfelder zwischendurch ausserhalb des Bildschirms. Ohne diese Methode
     * bricht das Malen ab — mit einer leeren laeuft es weiter, und der Baustein fehlt eben
     * im Bild. Zwischenschritt, kein Ziel: sobald es einen BufferedImage-Unterbau gibt,
     * gehoert hierher eine Flaeche, die wirklich etwas aufnimmt.
     */
    @Override
    public java.awt.Graphics2D createGraphics(java.awt.image.BufferedImage a0) {
        int b = a0 == null ? 1 : a0.getWidth();
        int h = a0 == null ? 1 : a0.getHeight();
        return new Portgrafik(new Leerwerk(), b, h);
    }

    @Override
    public java.awt.Font[] getAllFonts() {
        return new java.awt.Font[0];
    }

    @Override
    public java.lang.String[] getAvailableFontFamilyNames() {
        return new String[] {"Dialog", "SansSerif", "Serif", "Monospaced"};
    }

    @Override
    public java.lang.String[] getAvailableFontFamilyNames(java.util.Locale a0) {
        return new String[] {"Dialog", "SansSerif", "Serif", "Monospaced"};
    }
}
