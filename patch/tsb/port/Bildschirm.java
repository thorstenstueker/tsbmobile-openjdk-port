package tsb.port;

import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Rectangle;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.image.ColorModel;

/**
 * Ein Bildschirm, den es wirklich gibt.
 *
 * <h2>Warum ueberhaupt einer</h2>
 *
 * <p>S1 hatte entschieden, dass es kein {@code GraphicsDevice} braucht: gemalt wird auf eine
 * Flaeche, die die Plattform stellt, und die Geraetebeschreibung des JDK ist eine Schicht, die
 * niemand betritt. Der Stummel warf deshalb eine {@code HeadlessException}.
 *
 * <p>Das hielt genau so lange, wie {@code GraphicsEnvironment.isHeadless()} wahr war. Sobald
 * es falsch ist — und das muss es sein, sonst wickelt das JDK unser Toolkit in einen
 * {@code HeadlessToolkit}, der bei jeder Frage wirft — fragen zwei sehr gewoehnliche Wege
 * danach:
 *
 * <pre>
 * FlatLaf   LinuxFontPolicy.isSystemScaling  → getDefaultScreenDevice()
 * Swing     JPopupMenu.show                  → Bildschirmgrenzen fuer das Aufklappmenue
 * </pre>
 *
 * <p>Der erste liess FlatLaf beim Start scheitern — die Anwendung fiel auf Metal zurueck, und
 * plötzlich fehlten alle Raender. Der zweite verhinderte jedes Dropdown. <b>Zwei Symptome, die
 * nichts miteinander zu tun zu haben schienen, und eine Ursache.</b>
 *
 * <h2>Was er meldet</h2>
 *
 * <p>Die Groesse der Zeichenflaeche in <b>Entwurfseinheiten</b>, nicht in Geraetepixeln. Das
 * ist kein Versehen: alles, was Swing mit diesen Zahlen tut, rechnet in derselben Ebene wie
 * die Bausteine — ein Aufklappmenue unterbringen, ein Fenster zentrieren. Der Massstab kommt
 * erst beim Malen dazu.
 *
 * <p>Gesetzt wird sie von der Plattform, wie bei {@link Schrift}. Bis dahin gilt ein Telefon.
 */
public final class Bildschirm {

    private static volatile int breite = 390;
    private static volatile int hoehe = 844;

    private Bildschirm() {
    }

    /** Vom Einstiegspunkt der Plattform zu rufen, in Entwurfseinheiten. */
    public static void groesse(int neueBreite, int neueHoehe) {
        if (neueBreite > 0) breite = neueBreite;
        if (neueHoehe > 0) hoehe = neueHoehe;
    }

    public static Dimension groesse() {
        return new Dimension(breite, hoehe);
    }

    public static GraphicsDevice geraet() {
        return GERAET;
    }

    private static final GraphicsDevice GERAET = new GraphicsDevice() {

        @Override
        public int getType() {
            return TYPE_RASTER_SCREEN;
        }

        @Override
        public String getIDstring() {
            return "tsbMobile";
        }

        @Override
        public GraphicsConfiguration[] getConfigurations() {
            return new GraphicsConfiguration[] { FLAECHE };
        }

        @Override
        public GraphicsConfiguration getDefaultConfiguration() {
            return FLAECHE;
        }
    };

    private static final GraphicsConfiguration FLAECHE = new GraphicsConfiguration() {

        @Override
        public GraphicsDevice getDevice() {
            return GERAET;
        }

        @Override
        public ColorModel getColorModel() {
            return ColorModel.getRGBdefault();
        }

        @Override
        public ColorModel getColorModel(int durchsichtigkeit) {
            return durchsichtigkeit == Transparency.OPAQUE
                    ? ColorModel.getRGBdefault() : ColorModel.getRGBdefault();
        }

        /**
         * Die Einheitsmatrix, und das ist hier die Wahrheit.
         *
         * <p>Auf dem Desktop steht hier die Bildschirmskalierung. Bei uns skaliert die Ansicht
         * beim Malen, nicht das Koordinatensystem — Swing rechnet durchgehend in
         * Entwurfseinheiten. Etwas anderes zu melden hiesse, denselben Massstab zweimal
         * anzuwenden.
         */
        @Override
        public AffineTransform getDefaultTransform() {
            return new AffineTransform();
        }

        @Override
        public AffineTransform getNormalizingTransform() {
            return new AffineTransform();
        }

        @Override
        public Rectangle getBounds() {
            return new Rectangle(0, 0, breite, hoehe);
        }
    };
}
