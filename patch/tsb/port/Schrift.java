package tsb.port;

import java.awt.Font;
import java.awt.FontMetrics;

/**
 * Wo die Plattform ihr {@link Schriftwerk} hinterlegt — und woher Swing seine Metrik bekommt.
 *
 * <p>Dieselbe Bauart wie {@code Backend.use(Toolkit)} in {@code tsb.mobile}: ein einziges
 * Stück globaler Zustand, gesetzt vom Einstiegspunkt der Plattform, gelesen von allen.
 *
 * <h2>Die Notfassung</h2>
 *
 * <p>Ist nichts gesetzt, rechnet {@link Schaetzung} mit festen Verhältnissen weiter, statt zu
 * werfen. Das ist bewusst: eine Maske, die mit ungenauen Breiten erscheint, lässt sich
 * ansehen und beurteilen; eine, die gar nicht erscheint, nicht. Die Zahlen stammen aus den
 * üblichen Verhältnissen einer serifenlosen Schrift — brauchbar zum Auslegen, falsch für
 * jede Feinheit.
 *
 * <p>Sobald eine Plattform ihr Werk einträgt, ist die Schätzung aus dem Spiel.
 */
public final class Schrift {

    private static volatile Schriftwerk werk = new Schaetzung();

    private Schrift() {
    }

    /** Vom Einstiegspunkt der Plattform zu rufen, vor der ersten Maske. */
    public static void benutze(Schriftwerk neues) {
        if (neues != null) werk = neues;
    }

    /** Ob schon eine echte Plattform gemeldet ist — für Diagnosen. */
    public static boolean istEcht() {
        return !(werk instanceof Schaetzung);
    }

    public static Schriftwerk werk() {
        return werk;
    }

    /**
     * Die Metrik, die {@code SwingUtilities2.getFontMetrics} künftig liefert.
     *
     * <p>Nicht zwischengespeichert: Swing fragt beim Auslegen oft, aber das Messen ist auf
     * beiden Plattformen ein Aufruf ins Betriebssystem und kein Glyphen-Cache. Wenn sich das
     * als teuer erweist, gehört der Speicher hierher — gemessen, nicht vermutet.
     */
    public static FontMetrics metrik(Font schrift) {
        return new Portmetrik(schrift);
    }

    /** Ein {@link FontMetrics}, das seine Antworten vom {@link Schriftwerk} holt. */
    private static final class Portmetrik extends FontMetrics {

        private static final long serialVersionUID = 1L;

        Portmetrik(Font schrift) {
            super(schrift);
        }

        @Override
        public int getAscent() {
            return Math.round(werk.oberlaenge(font));
        }

        @Override
        public int getDescent() {
            return Math.round(werk.unterlaenge(font));
        }

        @Override
        public int getLeading() {
            return Math.round(werk.zeilenabstand(font));
        }

        @Override
        public int charWidth(char zeichen) {
            return Math.round(werk.zeichenbreite(font, zeichen));
        }

        @Override
        public int charWidth(int kennzahl) {
            return charWidth((char) kennzahl);
        }

        /**
         * Die ganze Zeichenkette auf einmal, nicht Zeichen für Zeichen.
         *
         * <p>{@code FontMetrics.stringWidth} summiert sonst Einzelbreiten — und verliert
         * damit Unterschneidung und Ligaturen. Beide Plattformen messen eine Kette besser
         * als die Summe ihrer Teile, also wird sie als Kette gereicht.
         */
        @Override
        public int stringWidth(String text) {
            return text == null ? 0 : Math.round(werk.breite(font, text));
        }

        @Override
        public int charsWidth(char[] zeichen, int von, int anzahl) {
            return stringWidth(new String(zeichen, von, anzahl));
        }
    }

    /**
     * Feste Verhältnisse, solange keine Plattform gemeldet ist.
     *
     * <p>Erkennbar falsch und trotzdem nützlich: die Maske erscheint, die Anordnung stimmt
     * grob, und man sieht sofort, dass die Schrift noch nicht angeschlossen ist.
     */
    static final class Schaetzung implements Schriftwerk {

        @Override
        public float breite(Font schrift, String text) {
            return text == null ? 0f : text.length() * zeichenbreite(schrift, 'n');
        }

        @Override
        public float oberlaenge(Font schrift) {
            return schrift.getSize2D() * 0.80f;
        }

        @Override
        public float unterlaenge(Font schrift) {
            return schrift.getSize2D() * 0.20f;
        }

        @Override
        public float zeilenabstand(Font schrift) {
            return schrift.getSize2D() * 0.15f;
        }

        @Override
        public float zeichenbreite(Font schrift, char zeichen) {
            return schrift.getSize2D() * 0.55f;
        }
    }
}
