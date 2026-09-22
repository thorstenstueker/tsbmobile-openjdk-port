package sun.font;

/**
 * Das kleinste {@code Font2D}, mit dem {@code java.awt.Font} arbeiten kann.
 *
 * <h2>Warum es sein muss, obwohl S1 {@code sun.font} abgeschnitten hat</h2>
 *
 * <p>{@code java.awt.Font.getFont2D()} ist der Engpass der ganzen Klasse: {@code getFamily},
 * {@code getFontName}, {@code canDisplay}, {@code getNumGlyphs} — alles laeuft dort hindurch.
 * Gibt die Schriftverwaltung {@code null} zurueck, wirft schon {@code font.getFamily()} eine
 * {@code NullPointerException}. Das ist nicht der Weg einer Sonderlocke, sondern die
 * oeffentliche API einer Kernklasse.
 *
 * <p>Der Schnitt von S1 bleibt trotzdem richtig: <b>gemessen und gezeichnet wird ueber
 * {@link tsb.port.Schriftwerk}.</b> Was hier fehlt, sind nur die Auskuenfte <i>ueber</i> eine
 * Schrift — Name, Familie, Stil. Die kennt der Port ohnehin, sie muessen nur an der Stelle
 * stehen, wo das JDK sie sucht.
 *
 * <h2>Die zwei abstrakten Methoden</h2>
 *
 * <p>{@code Font2D} hat 45 Methoden, aber nur zwei abstrakte: {@link #getMapper()} und
 * {@link #createStrike}. Beide gehoeren zur Glyphenarbeit — Zeichen auf Glyphenindizes
 * abbilden, Rasterbilder zwischenspeichern. Genau das macht auf dem Telefon Skia, und genau
 * dorthin fuehrt keiner unserer Wege.
 *
 * <p>Sie werfen deshalb, statt still etwas Falsches zu liefern. <b>Wer hier landet, ist auf
 * einem Pfad, den der Port nicht bedient</b>, und das soll auffallen — nach derselben Regel,
 * nach der {@code Entnativisierer} den nativen Methoden einen eigenen Fehlertext gibt.
 *
 * <h2>Warum die Klasse in {@code sun.font} liegt</h2>
 *
 * <p>Nicht aus Bequemlichkeit: beide abstrakten Methoden sind <b>paketprivat</b>. Eine
 * Unterklasse ausserhalb von {@code sun.font} kann sie nicht ueberschreiben und bleibt
 * abstrakt. Das ist der einzige Grund — die uebrigen Port-Naehte liegen in {@code tsb.port},
 * wo sie hingehoeren.
 */
public final class TsbFont2D extends Font2D {

    public TsbFont2D(String familie, String voll, int stil) {
        this.familyName = familie;
        this.fullName = voll;
        this.style = stil;
        this.fontRank = Font2D.DEFAULT_RANK;
        // Font.getFont2D() liest genau dieses Feld — ohne Griff ist das Ergebnis unbrauchbar.
        this.handle = new Font2DHandle(this);
    }

    /** Wie oft die Glyphenwege doch begangen werden — eine Ausnahme je Aufruf ist teuer. */
    public static final java.util.concurrent.atomic.AtomicLong GEWORFEN =
            new java.util.concurrent.atomic.AtomicLong();

    @Override
    CharToGlyphMapper getMapper() {
        GEWORFEN.incrementAndGet();
        throw new UnsupportedOperationException(
                "tsbMobile: Glyphenabbildung fuer " + fullName + " — der Port misst und malt"
                        + " ueber tsb.port.Schriftwerk, nicht ueber sun.font");
    }

    @Override
    FontStrike createStrike(FontStrikeDesc beschreibung) {
        GEWORFEN.incrementAndGet();
        throw new UnsupportedOperationException(
                "tsbMobile: Glyphen-Zwischenspeicher fuer " + fullName + " — der Port misst"
                        + " und malt ueber tsb.port.Schriftwerk, nicht ueber sun.font");
    }

    /** Genuegt fuer {@code Font.canDisplay}: die Plattformschriften koennen Latin-1. */
    @Override
    public boolean canDisplay(char zeichen) {
        return true;
    }

    @Override
    public boolean canDisplay(int zeichen) {
        return true;
    }

    /** {@code Font.canDisplayUpTo} und {@code StyleContext} fragen danach. */
    @Override
    boolean supportsEncoding(String kodierung) {
        return true;
    }

    @Override
    public boolean canDoStyle(int stil) {
        return true;
    }
}
