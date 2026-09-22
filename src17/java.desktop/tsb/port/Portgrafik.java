package tsb.port;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.text.AttributedCharacterIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Ein {@link Graphics2D}, das auf zwölf Plattformmethoden zurückführt.
 *
 * <h2>Die Arbeitsteilung</h2>
 *
 * <p>{@code Graphics2D} hat 75 abstrakte Methoden. Die allermeisten sind <b>Zustand und
 * Rechnung</b> — Farbe merken, Schrift merken, Formen auf Rechtecke zurückführen,
 * Verschiebungen verketten. Das steht hier, in reinem Java, einmal für beide Plattformen.
 *
 * <p>Was übrig bleibt, ist {@link Zeichenwerk}: zwölf Methoden, die Farbe auf eine Fläche
 * bringen. Die schreibt jede Plattform selbst — Android mit {@code android.graphics.Canvas},
 * iOS später mit Skia.
 *
 * <h2>Warum nichts wirft</h2>
 *
 * <p>Was noch keinen Unterbau hat, wird <b>vermerkt statt geworfen</b>. Der Unterschied ist
 * entscheidend: eine geworfene Ausnahme mitten im Malen bricht die Maske ab, und man sieht
 * nichts — auch nicht das, was schon funktioniert. Ein Vermerk lässt die Maske fertig malen
 * und liefert hinterher die Liste.
 *
 * <p>{@link #luecken()} ist damit die Arbeitsliste für den nächsten Schritt, in genau der
 * Reihenfolge, in der FlatLaf sie braucht. Dieselbe Haltung wie beim
 * {@code Entnativisierer} — nur dass dort geworfen wird, weil ein fehlender Glyphen-Cache
 * nichts Halbes zustande bringt, und hier nicht, weil eine halb gemalte Maske sehr wohl
 * etwas zeigt.
 *
 * <h2>Warum keine Matrizen</h2>
 *
 * <p>Verschiebung, Maßstab und Schnitt gehen unverändert an die Plattform. Beide — Canvas
 * wie Skia — rechnen das in Hardware. Hier nochmal zu multiplizieren hieße, es doppelt zu
 * tun und doppelt falsch machen zu können.
 */
public final class Portgrafik extends Graphics2D {

    private final Zeichenwerk werk;
    private final Set<String> luecken;

    private Color farbe = Color.BLACK;
    private Color hintergrund = Color.WHITE;
    private Font schrift = new Font("Dialog", Font.PLAIN, 12);
    private Stroke strich = new BasicStroke(1f);
    private Composite mischung = AlphaComposite.SrcOver;
    private AffineTransform verwandlung = new AffineTransform();
    private Rectangle schnitt;

    /**
     * Verschiebung und Maßstab seit dem Grundzustand dieses {@code Graphics} — als <b>eine</b>
     * Matrix, nicht als Liste.
     *
     * <p>Die erste Fassung merkte sich jeden Schritt einzeln und wiederholte sie der Reihe
     * nach. Das war messbar falsch: {@code BasicTableUI} verschiebt je Zelle hin und wieder
     * zurück, die Liste wuchs mit jeder Zelle, und {@link #setClip} spielte sie jedesmal ganz
     * ab. <b>Die Malzeit stieg von 0,34 ms auf 49,5 ms je Bild</b> — quadratisch statt linear.
     *
     * <p>Eine Matrix aus Verschiebung und Maßstab lässt sich verlustfrei zusammenfassen und in
     * zwei Aufrufen wiederherstellen: {@code verschieben(m02, m12)}, dann
     * {@code skalieren(m00, m11)}. Beide Plattformen verketten in derselben Reihenfolge wie
     * {@link AffineTransform}, deshalb kommt dasselbe heraus.
     *
     * <p>Nicht {@link #verwandlung}: die wird bei {@link #create()} vom Elter übernommen und
     * misst damit ab der Wurzel. Gebraucht wird der Abstand zum eigenen Grundzustand.
     */
    private AffineTransform seitDerBasis = new AffineTransform();

    /** Die Marke des eigenen Grundzustands. Siehe {@link Zeichenwerk#wiederherstellen(int)}. */
    private int basis;

    public Portgrafik(Zeichenwerk werk, int breite, int hoehe) {
        this(werk, new ConcurrentSkipListSet<>(), new Rectangle(0, 0, breite, hoehe));
        // Ein Grundzustand muss auch fuer die Wurzel gesichert sein — setClip braucht ihn.
        basis = werk.sichern();
    }

    private Portgrafik(Zeichenwerk werk, Set<String> luecken, Rectangle schnitt) {
        this.werk = werk;
        this.luecken = luecken;
        this.schnitt = schnitt;
    }

    /** Was FlatLaf gerufen hat und hier noch keinen Unterbau hat. Sortiert, ohne Dubletten. */
    public Set<String> luecken() {
        return Collections.unmodifiableSet(new TreeSet<>(luecken));
    }

    private void luecke(String was) {
        luecken.add(was);
    }

    private int argb() {
        return farbe.getRGB();
    }

    private float dicke() {
        return strich instanceof BasicStroke ? ((BasicStroke) strich).getLineWidth() : 1f;
    }

    // --- Zustand ---------------------------------------------------------------------------

    @Override
    public Graphics create() {
        Portgrafik kind = new Portgrafik(werk, luecken,
                schnitt == null ? null : new Rectangle(schnitt));
        kind.basis = werk.sichern();
        kind.farbe = farbe;
        kind.hintergrund = hintergrund;
        kind.schrift = schrift;
        kind.strich = strich;
        kind.mischung = mischung;
        kind.verwandlung = new AffineTransform(verwandlung);
        // Der Grundzustand des Kindes ist das sichern() oben — ab hier zaehlt es neu.
        kind.seitDerBasis = new AffineTransform();
        return kind;
    }

    /**
     * Gibt den gesicherten Zustand der Plattform zurück.
     *
     * <p>Swing ruft {@code create()} und {@code dispose()} streng geschachtelt — jede
     * Komponente malt in einem eigenen Ableger. Deshalb passt das Paar auf ein
     * {@code save}/{@code restore} der Plattform, ohne dass hier Buch geführt werden muss.
     */
    @Override
    public void dispose() {
        // Genau einmal. Swing entsorgt ein Graphics gelegentlich zweimal, und ein zweites
        // Zurueckbuchen wuerde eine fremde Marke treffen.
        if (entsorgt) return;
        entsorgt = true;
        werk.wiederherstellen(basis);
    }

    private boolean entsorgt;

    /**
     * <b>Nichts.</b> Und das ist die Behebung eines Fehlers, der lange wie ein Zufall aussah.
     *
     * <p>{@link java.awt.Graphics#finalize()} ruft {@link #dispose()}. Das ist auf dem Desktop
     * harmlos — dort gibt jedes Graphics native Mittel frei, und wann das geschieht, ist egal.
     *
     * <p><b>Bei uns ist es das Gegenteil von egal.</b> {@code dispose()} bucht den Zeichenstand
     * der Leinwand auf eine Marke zurueck, und diese Marke gilt nur innerhalb des Bildes, in
     * dem sie genommen wurde. Der Finalisierer laeuft auf einem eigenen Faden und zu einem
     * Zeitpunkt, den die Speicherbereinigung bestimmt — also mitten in einem ganz anderen Bild.
     *
     * <p>Gemessen an einem rollenden Formular mit einem Bild darin: <b>11 194 Meldungen</b>
     * „Zeichenstand verdreht", alle vom Faden {@code FinalizerDaemon}. Sichtbar wurde es als
     * ein Bild, das beim Rollen zwischen seiner Stelle und der linken oberen Ecke sprang —
     * weil die Leinwand ihre Verschiebung verloren hatte und der naechste Malbefehl auf dem
     * Ursprung landete.
     *
     * <p>Ein Graphics, das niemand ausdruecklich entsorgt hat, hat nichts zurueckzubuchen: sein
     * Bild ist laengst vorbei. Deshalb hier ein leerer Rumpf statt des geerbten Aufrufs.
     */
    @Override
    @SuppressWarnings("deprecation")
    public void finalize() {
        // absichtlich leer — siehe oben
    }

    @Override
    public Color getColor() {
        return farbe;
    }

    @Override
    public void setColor(Color neu) {
        if (neu != null) farbe = neu;
    }

    @Override
    public void setPaintMode() {
        // Der Normalfall — hier ist nichts umzustellen.
    }

    @Override
    public void setXORMode(Color c1) {
        luecke("setXORMode");
    }

    @Override
    public Font getFont() {
        return schrift;
    }

    @Override
    public void setFont(Font neu) {
        if (neu != null) schrift = neu;
    }

    @Override
    public FontMetrics getFontMetrics(Font f) {
        return Schrift.metrik(f == null ? schrift : f);
    }

    // --- Schnitt ---------------------------------------------------------------------------

    @Override
    public Rectangle getClipBounds() {
        return schnitt == null ? null : new Rectangle(schnitt);
    }

    @Override
    public void clipRect(int x, int y, int b, int h) {
        Rectangle neu = new Rectangle(x, y, b, h);
        schnitt = schnitt == null ? neu : schnitt.intersection(neu);
        werk.schneiden(x, y, b, h);
    }

    /**
     * <b>Ersetzt</b> den Schnitt — und genau darin liegt der Unterschied zu
     * {@link #clipRect}, an dem das Dropdown seine Beschriftung verloren hat.
     *
     * <p>Swing benutzt {@code setClip}, um nach einem Kind wieder auf die volle Fläche
     * zurückzugehen. Skia kann Schnitte aber nur <i>verengen</i> — Android hat
     * {@code Region.Op.REPLACE} mit API 26 entfernt, und Metal kennt es ebenso wenig. Der
     * Aufruf lief deshalb ins Leere:
     *
     * <pre>
     * schneiden 368,0 22x76  -> Rect(368,0 - 390,76)   der Aufklapppfeil
     * schneiden 0,0 390x76   -> Rect(368,0 - 390,76)   sollte zuruecksetzen, tat nichts
     * schneiden 0,0 367x74   -> Rect(0,0 - 0,0)        leer
     * </pre>
     *
     * <p>Der Text wurde danach gemalt und vollständig weggeschnitten — sichtbar nur daran,
     * dass er bei der Plattform ankam. Deshalb steht in {@code Zeichenwerk} bewusst kein
     * „Schnitt ersetzen": <b>keine der beiden Plattformen kann es</b>, und eine Methode, die
     * überall nachgebaut werden müsste, gehört nicht in eine Plattformschnittstelle.
     *
     * <p>Stattdessen wird auf den Grundzustand dieses {@code Graphics} zurückgegangen und neu
     * aufgebaut. Der Grundzustand ist der {@code sichern()}-Punkt aus {@link #create()}; was
     * seither an Verschiebung und Maßstab kam, steht zusammengefasst in
     * {@link #seitDerBasis} und wird in zwei Aufrufen wiederhergestellt.
     */
    @Override
    public void setClip(int x, int y, int b, int h) {
        schnitt = new Rectangle(x, y, b, h);
        werk.wiederherstellen(basis);
        basis = werk.sichern();
        if (seitDerBasis.getTranslateX() != 0 || seitDerBasis.getTranslateY() != 0) {
            werk.verschieben((float) seitDerBasis.getTranslateX(),
                    (float) seitDerBasis.getTranslateY());
        }
        if (seitDerBasis.getScaleX() != 1 || seitDerBasis.getScaleY() != 1) {
            werk.skalieren((float) seitDerBasis.getScaleX(), (float) seitDerBasis.getScaleY());
        }
        werk.schneiden(x, y, b, h);
    }

    @Override
    public Shape getClip() {
        return getClipBounds();
    }

    @Override
    public void setClip(Shape form) {
        if (form == null) return;
        Rectangle r = form.getBounds();
        setClip(r.x, r.y, r.width, r.height);
    }

    @Override
    public void clip(Shape form) {
        if (form == null) return;
        Rectangle r = form.getBounds();
        clipRect(r.x, r.y, r.width, r.height);
    }

    // --- Verwandlung -----------------------------------------------------------------------

    @Override
    public void translate(int dx, int dy) {
        translate((double) dx, (double) dy);
    }

    @Override
    public void translate(double dx, double dy) {
        verwandlung.translate(dx, dy);
        seitDerBasis.translate(dx, dy);
        werk.verschieben((float) dx, (float) dy);
        if (schnitt != null) schnitt.translate(-(int) dx, -(int) dy);
    }

    @Override
    public void scale(double sx, double sy) {
        verwandlung.scale(sx, sy);
        seitDerBasis.scale(sx, sy);
        werk.skalieren((float) sx, (float) sy);
    }

    @Override
    public void rotate(double w) {
        luecke("rotate");
    }

    @Override
    public void rotate(double w, double x, double y) {
        luecke("rotate(Punkt)");
    }

    @Override
    public void shear(double sx, double sy) {
        luecke("shear");
    }

    @Override
    public void transform(AffineTransform t) {
        luecke("transform");
    }

    @Override
    public void setTransform(AffineTransform t) {
        luecke("setTransform");
    }

    @Override
    public AffineTransform getTransform() {
        return new AffineTransform(verwandlung);
    }

    // --- Zeichnen --------------------------------------------------------------------------

    @Override
    public void drawLine(int x1, int y1, int x2, int y2) {
        werk.linie(x1, y1, x2, y2, argb(), dicke());
    }

    @Override
    public void fillRect(int x, int y, int b, int h) {
        werk.fuelleRechteck(x, y, b, h, argb());
    }

    @Override
    public void drawRect(int x, int y, int b, int h) {
        werk.stricheRechteck(x, y, b, h, argb(), dicke());
    }

    @Override
    public void clearRect(int x, int y, int b, int h) {
        werk.fuelleRechteck(x, y, b, h, hintergrund.getRGB());
    }

    @Override
    public void drawRoundRect(int x, int y, int b, int h, int bb, int bh) {
        werk.stricheRundRechteck(x, y, b, h, bb, bh, argb(), dicke());
    }

    @Override
    public void fillRoundRect(int x, int y, int b, int h, int bb, int bh) {
        werk.fuelleRundRechteck(x, y, b, h, bb, bh, argb());
    }

    @Override
    public void drawOval(int x, int y, int b, int h) {
        werk.stricheOval(x, y, b, h, argb(), dicke());
    }

    @Override
    public void fillOval(int x, int y, int b, int h) {
        werk.fuelleOval(x, y, b, h, argb());
    }

    // Bögen und gefüllte Vielecke sind keine eigenen Fälle mehr, seit die Plattform Pfade
    // kann: Arc2D und Polygon sind Shapes, und draw/fill zerlegen sie.

    @Override
    public void drawArc(int x, int y, int b, int h, int von, int bogen) {
        draw(new java.awt.geom.Arc2D.Float(x, y, b, h, von, bogen, java.awt.geom.Arc2D.OPEN));
    }

    @Override
    public void fillArc(int x, int y, int b, int h, int von, int bogen) {
        fill(new java.awt.geom.Arc2D.Float(x, y, b, h, von, bogen, java.awt.geom.Arc2D.PIE));
    }

    /**
     * Linienzüge als Folge von Strecken.
     *
     * <p>Reine Rechnung, deshalb hier und nicht in der Plattform. Ein Linienzug ist n-1
     * Strecken, und jede Plattform kann Strecken.
     */
    @Override
    public void drawPolyline(int[] xs, int[] ys, int n) {
        for (int i = 0; i + 1 < n; i++) drawLine(xs[i], ys[i], xs[i + 1], ys[i + 1]);
    }

    @Override
    public void drawPolygon(int[] xs, int[] ys, int n) {
        drawPolyline(xs, ys, n);
        if (n > 1) drawLine(xs[n - 1], ys[n - 1], xs[0], ys[0]);
    }

    @Override
    public void fillPolygon(int[] xs, int[] ys, int n) {
        fill(new java.awt.Polygon(xs, ys, n));
    }

    @Override
    public void drawString(String text, int x, int y) {
        drawString(text, (float) x, (float) y);
    }

    @Override
    public void drawString(String text, float x, float y) {
        if (text != null && !text.isEmpty()) werk.text(text, x, y, schrift, argb());
    }

    @Override
    public void drawString(AttributedCharacterIterator it, int x, int y) {
        luecke("drawString(AttributedCharacterIterator)");
    }

    @Override
    public void drawString(AttributedCharacterIterator it, float x, float y) {
        luecke("drawString(AttributedCharacterIterator,float)");
    }

    @Override
    public void drawGlyphVector(GlyphVector v, float x, float y) {
        luecke("drawGlyphVector");
    }

    /**
     * Formen auf das zurückgeführt, was die Plattform kann.
     *
     * <p>FlatLaf zeichnet fast alles als {@code Rectangle2D} oder {@code RoundRectangle2D} —
     * Knöpfe, Felder, Ränder. Was darüber hinausgeht, wird vermerkt, und die Liste sagt
     * dann, ob sich ein Pfad-Unterbau lohnt.
     */
    @Override
    public void draw(Shape form) {
        if (form instanceof Rectangle2D) {
            Rectangle r = form.getBounds();
            drawRect(r.x, r.y, r.width, r.height);
        } else if (form instanceof RoundRectangle2D) {
            RoundRectangle2D rr = (RoundRectangle2D) form;
            drawRoundRect((int) rr.getX(), (int) rr.getY(), (int) rr.getWidth(),
                    (int) rr.getHeight(), (int) rr.getArcWidth(), (int) rr.getArcHeight());
        } else if (form instanceof Ellipse2D) {
            Rectangle r = form.getBounds();
            drawOval(r.x, r.y, r.width, r.height);
        } else {
            alsPfad(form, false);
        }
    }

    @Override
    public void fill(Shape form) {
        if (form instanceof Rectangle2D) {
            Rectangle r = form.getBounds();
            fillRect(r.x, r.y, r.width, r.height);
        } else if (form instanceof RoundRectangle2D) {
            RoundRectangle2D rr = (RoundRectangle2D) form;
            fillRoundRect((int) rr.getX(), (int) rr.getY(), (int) rr.getWidth(),
                    (int) rr.getHeight(), (int) rr.getArcWidth(), (int) rr.getArcHeight());
        } else if (form instanceof Ellipse2D) {
            Rectangle r = form.getBounds();
            fillOval(r.x, r.y, r.width, r.height);
        } else {
            alsPfad(form, true);
        }
    }

    /**
     * Jede andere Form: in Strecken zerlegen und der Plattform als Pfad reichen.
     *
     * <p>{@code getPathIterator(null, 0.5)} liefert nur {@code SEG_MOVETO},
     * {@code SEG_LINETO} und {@code SEG_CLOSE} — Kurven sind da bereits in Strecken
     * aufgelöst, mit einer halben Einheit Genauigkeit. Auf einem Schirm mit Maßstab 2,77
     * ist das ein Sechstel Pixel; feiner zu unterteilen kostet Punkte, die niemand sieht.
     *
     * <p>Die Zerlegung gehört hierher und nicht in die Plattform: sie ist reine Rechnung,
     * und sie zweimal zu schreiben hieße, sie zweimal falsch machen zu können.
     */
    private void alsPfad(Shape form, boolean fuellen) {
        java.awt.geom.PathIterator lauf = form.getPathIterator(null, 0.5);
        List<float[]> teilzuege = new ArrayList<>();
        float[] koord = new float[6];
        float[] puffer = new float[32];
        int anzahl = 0;
        float startX = 0f;
        float startY = 0f;

        while (!lauf.isDone()) {
            switch (lauf.currentSegment(koord)) {
                case java.awt.geom.PathIterator.SEG_MOVETO:
                    if (anzahl >= 4) teilzuege.add(Arrays.copyOf(puffer, anzahl));
                    anzahl = 0;
                    startX = koord[0];
                    startY = koord[1];
                    puffer = anhaengen(puffer, anzahl, koord[0], koord[1]);
                    anzahl += 2;
                    break;
                case java.awt.geom.PathIterator.SEG_LINETO:
                    puffer = anhaengen(puffer, anzahl, koord[0], koord[1]);
                    anzahl += 2;
                    break;
                case java.awt.geom.PathIterator.SEG_CLOSE:
                    // Geschlossen heisst: zurueck zum Anfang. Die Plattform muss dafuer
                    // keine eigene Vorstellung haben, wenn der Punkt einfach dasteht.
                    puffer = anhaengen(puffer, anzahl, startX, startY);
                    anzahl += 2;
                    if (anzahl >= 4) teilzuege.add(Arrays.copyOf(puffer, anzahl));
                    anzahl = 0;
                    break;
                default:
                    // Kann nach dem Flachklopfen nicht vorkommen — und wenn doch, soll es
                    // in der Lueckenliste stehen statt still zu verschwinden.
                    luecke("Pfadsegment(" + form.getClass().getName() + ")");
                    break;
            }
            lauf.next();
        }
        if (anzahl >= 4) teilzuege.add(Arrays.copyOf(puffer, anzahl));
        if (teilzuege.isEmpty()) return;

        werk.pfad(teilzuege,
                lauf.getWindingRule() == java.awt.geom.PathIterator.WIND_EVEN_ODD,
                fuellen, argb(), dicke());
    }

    private static float[] anhaengen(float[] puffer, int anzahl, float x, float y) {
        if (anzahl + 2 > puffer.length) puffer = Arrays.copyOf(puffer, puffer.length * 2);
        puffer[anzahl] = x;
        puffer[anzahl + 1] = y;
        return puffer;
    }

    // --- Bilder ----------------------------------------------------------------------------

    /**
     * Die Pixel eines Bildes, in ARGB und Zeilenfolge.
     *
     * <p>{@code BufferedImage.getRGB} liefert genau das und rechnet jedes Farbmodell selbst um
     * — Graustufen, Palette, vormultipliziert. Ein {@code Image}, das keines ist (etwa ein
     * {@code VolatileImage}), wird einmal in ein {@code BufferedImage} gemalt; das ist der
     * einzige Weg, ohne die Plattformrueckseite des JDK an die Punkte zu kommen.
     *
     * @return {@code null}, wenn das Bild noch keine Groesse hat — dann ist es nicht geladen
     */
    private static int[] punkte(Image bild, int breite, int hoehe) {
        if (bild == null || breite <= 0 || hoehe <= 0) return null;
        java.awt.image.BufferedImage puffer;
        if (bild instanceof java.awt.image.BufferedImage) {
            puffer = (java.awt.image.BufferedImage) bild;
        } else {
            puffer = new java.awt.image.BufferedImage(
                    breite, hoehe, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics g = puffer.getGraphics();
            try {
                g.drawImage(bild, 0, 0, breite, hoehe, null);
            } finally {
                g.dispose();
            }
        }
        return puffer.getRGB(0, 0, breite, hoehe, null, 0, breite);
    }

    /** Der gemeinsame Weg aller Ueberladungen: Punkte holen, verschoben weiterreichen. */
    private boolean malen(Image bild, int x, int y, int zielBreite, int zielHoehe) {
        if (bild == null) return true;
        int qb = bild.getWidth(null);
        int qh = bild.getHeight(null);
        if (qb <= 0 || qh <= 0) return false;

        // Eine Zielgroesse von null heisst null, nicht "nicht angegeben".
        //
        // Hier stand zuerst "zielBreite > 0 ? zielBreite : qb" — gedacht als Bequemlichkeit
        // fuer die Ueberladung ohne Groesse. Das macht aus einem Baustein, der (noch) keine
        // Groesse hat, ein Bild in voller Aufloesung: aus 160 Punkten werden 512, der Inhalt
        // waechst, und das Rollen wird wirr. Die Ueberladungen ohne Groesse reichen jetzt
        // ausdruecklich qb/qh herein.
        if (zielBreite <= 0 || zielHoehe <= 0) return true;

        int[] punkte = punkte(bild, qb, qh);
        if (punkte == null) return false;

        // Koordinaten gehen unveraendert durch: die Verschiebung steht im Zeichenwerk, so
        // wie bei fuelleRechteck auch.
        werk.bild(punkte, qb, qh, x, y, zielBreite, zielHoehe);
        return true;
    }

    @Override
    public boolean drawImage(Image b, int x, int y, ImageObserver o) {
        if (b == null) return true;
        return malen(b, x, y, b.getWidth(null), b.getHeight(null));
    }

    @Override
    public boolean drawImage(Image b, int x, int y, int br, int h, ImageObserver o) {
        return malen(b, x, y, br, h);
    }

    @Override
    public boolean drawImage(Image b, int x, int y, Color hg, ImageObserver o) {
        if (b == null) return true;
        if (hg != null) fillRect(x, y, b.getWidth(null), b.getHeight(null));
        return malen(b, x, y, b.getWidth(null), b.getHeight(null));
    }

    @Override
    public boolean drawImage(Image b, int x, int y, int br, int h, Color hg, ImageObserver o) {
        if (hg != null) fillRect(x, y, br, h);
        return malen(b, x, y, br, h);
    }

    @Override
    public boolean drawImage(Image b, int dx1, int dy1, int dx2, int dy2,
                             int sx1, int sy1, int sx2, int sy2, ImageObserver o) {
        luecke("drawImage(Ausschnitt)");
        return true;
    }

    @Override
    public boolean drawImage(Image b, int dx1, int dy1, int dx2, int dy2,
                             int sx1, int sy1, int sx2, int sy2, Color hg, ImageObserver o) {
        luecke("drawImage(Ausschnitt,Hintergrund)");
        return true;
    }

    @Override
    public boolean drawImage(Image b, AffineTransform t, ImageObserver o) {
        luecke("drawImage(verwandelt)");
        return true;
    }

    @Override
    public void drawImage(BufferedImage b, BufferedImageOp op, int x, int y) {
        luecke("drawImage(BufferedImageOp)");
    }

    @Override
    public void drawRenderedImage(RenderedImage b, AffineTransform t) {
        luecke("drawRenderedImage");
    }

    @Override
    public void drawRenderableImage(RenderableImage b, AffineTransform t) {
        luecke("drawRenderableImage");
    }

    @Override
    public void copyArea(int x, int y, int b, int h, int dx, int dy) {
        luecke("copyArea");
    }

    // --- Malwerkzeug -----------------------------------------------------------------------

    /**
     * Nur einfarbig. Verläufe und Texturen werden vermerkt.
     *
     * <p>FlatLaf ist ein flaches Thema — es benutzt fast nur Volltonfarben. Kommt doch ein
     * {@code GradientPaint}, steht er in der Lückenliste, und dann weiß man, dass er
     * gebraucht wird. Bis dahin wird die Grundfarbe genommen, damit etwas erscheint.
     */
    @Override
    public void setPaint(Paint p) {
        if (p instanceof Color) {
            farbe = (Color) p;
        } else if (p != null) {
            luecke("setPaint(" + p.getClass().getSimpleName() + ")");
        }
    }

    @Override
    public Paint getPaint() {
        return farbe;
    }

    @Override
    public void setStroke(Stroke s) {
        if (s != null) strich = s;
    }

    @Override
    public Stroke getStroke() {
        return strich;
    }

    @Override
    public void setComposite(Composite c) {
        if (c != null) mischung = c;
    }

    @Override
    public Composite getComposite() {
        return mischung;
    }

    @Override
    public void setBackground(Color c) {
        if (c != null) hintergrund = c;
    }

    @Override
    public Color getBackground() {
        return hintergrund;
    }

    // --- Hinweise und Sonstiges ------------------------------------------------------------

    /**
     * Zeichenhinweise werden geschluckt.
     *
     * <p>Kantenglättung, Interpolation, Farbwiedergabe — das entscheidet auf dem Telefon die
     * Plattform, und beide glätten von Haus aus. Ein Hinweis, den niemand auswertet, ist
     * keine Lücke, sondern eine Frage, die sich nicht stellt.
     */
    @Override
    public void setRenderingHint(RenderingHints.Key k, Object wert) {
    }

    @Override
    public Object getRenderingHint(RenderingHints.Key k) {
        return null;
    }

    @Override
    public void setRenderingHints(Map<?, ?> hinweise) {
    }

    @Override
    public void addRenderingHints(Map<?, ?> hinweise) {
    }

    @Override
    public RenderingHints getRenderingHints() {
        return new RenderingHints(null);
    }

    @Override
    public FontRenderContext getFontRenderContext() {
        return new FontRenderContext(null, true, true);
    }

    @Override
    public GraphicsConfiguration getDeviceConfiguration() {
        luecke("getDeviceConfiguration");
        return null;
    }

    @Override
    public boolean hit(Rectangle r, Shape form, boolean strichen) {
        return form != null && form.intersects(r);
    }
}
