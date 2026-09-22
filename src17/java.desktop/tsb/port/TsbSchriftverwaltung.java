package tsb.port;

import java.awt.Font;
import java.io.File;

import sun.font.CreatedFontTracker;
import sun.font.Font2D;
import sun.font.Font2DHandle;
import sun.font.FontManager;

/**
 * Die dritte Plattformklasse, die das JDK ueber eine Systemeigenschaft sucht.
 *
 * <h2>Warum es sie ueberhaupt gibt</h2>
 *
 * <p>{@code sun.font.FontManagerFactory} liest {@code sun.font.fontmanager} und laedt, was
 * dort steht — als Vorgabe {@code sun.awt.X11FontManager}. Auf einem Telefon gibt es kein
 * X11, also endet das in
 * {@code InternalError: ClassNotFoundException: sun.awt.X11FontManager}.
 *
 * <p>Dasselbe Muster wie {@link TsbToolkit} ({@code awt.toolkit}) und
 * {@link TsbGraphicsEnvironment} ({@code java.awt.graphicsenv}). Beim dritten Mal lohnt es,
 * die Familie zu zaehlen statt weiter einzeln zu flicken — die Liste steht im README von S2.
 *
 * <h2>Warum sie fast nichts tut</h2>
 *
 * <p>Ein echter {@code FontManager} verwaltet Schriftdateien, Glyphen-Caches und
 * Ersatzschriften. Genau das liegt unterhalb der Grenze, die S1 gezogen hat: **gemessen und
 * gezeichnet wird ueber {@link Schriftwerk}**, nicht ueber {@code sun.font}. Was hier
 * gebraucht wird, ist nur, dass die Fabrik etwas zurueckgibt statt zu werfen.
 *
 * <p>Deshalb antwortet jede Methode so knapp wie moeglich: {@code false}, {@code null}, oder
 * gar nichts. Wer hier landet und ein Ergebnis braucht, hat einen Weg genommen, den der Port
 * nicht bedient — und das soll dann auffallen, nicht stillschweigend falsch weiterlaufen.
 * Genau dafuer fuehrt {@link #gefragt} Buch.
 */
public final class TsbSchriftverwaltung implements FontManager {

    /**
     * Welche Methoden tatsaechlich gerufen wurden.
     *
     * <p>Dieselbe Haltung wie {@link Portgrafik#luecken()}: Buch fuehren statt werfen. Wenn
     * sich herausstellt, dass eine davon wirklich gebraucht wird, steht sie hier — und man
     * weiss, wofuer sich Arbeit lohnt, ohne es zu vermuten.
     */
    private static final java.util.Set<String> GEFRAGT =
            java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());

    public static java.util.Set<String> gefragt() {
        return GEFRAGT;
    }

    private static void notiere(String was) {
        GEFRAGT.add(was);
    }

    @Override
    public boolean registerFont(Font schrift) {
        notiere("registerFont");
        return false;
    }

    @Override
    public void deRegisterBadFont(Font2D schrift) {
        notiere("deRegisterBadFont");
    }

    /**
     * Die einzige Methode mit Inhalt — und sie muss ihn haben.
     *
     * <p>{@code java.awt.Font.getFont2D()} liest das Ergebnis ohne Pruefung
     * ({@code fm.findFont2D(...).handle}). {@code null} ist also kein zurueckhaltendes
     * "kenne ich nicht", sondern eine {@code NullPointerException} in
     * {@code Font.getFamily()} — einer ganz gewoehnlichen oeffentlichen Methode.
     *
     * <p>Zwischengespeichert, weil Swing beim Auslegen oft fragt und zwei gleiche Schriften
     * dasselbe {@code Font2D} bekommen sollen: {@code Font.getFont2D} vergleicht Griffe.
     */
    @Override
    public Font2D findFont2D(String name, int stil, int rueckfall) {
        notiere("findFont2D");
        String schluessel = name + "/" + stil;
        return BEKANNT.computeIfAbsent(schluessel,
                s -> new sun.font.TsbFont2D(name, name, stil));
    }

    private static final java.util.Map<String, Font2D> BEKANNT =
            new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public Font2D[] createFont2D(File datei, int art, boolean alle, boolean istKopie,
                                 CreatedFontTracker beobachter) {
        // JDK 17: liefert alle Schriften einer Datei; der Port erzeugt keine.
        notiere("createFont2D");
        return new Font2D[0];
    }

    @Override
    public Font2DHandle getNewComposite(String name, int stil, Font2DHandle alt) {
        notiere("getNewComposite");
        return alt;
    }

    @Override
    public void preferLocaleFonts() {
        notiere("preferLocaleFonts");
    }

    @Override
    public void preferProportionalFonts() {
        notiere("preferProportionalFonts");
    }
}
