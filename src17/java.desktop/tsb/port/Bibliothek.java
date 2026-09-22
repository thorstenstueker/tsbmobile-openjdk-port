/*
 * Copyright (C) 2026 tsb Thorsten Stueker Buero for Technology development
 * Licensed under the GNU General Public License, version 2, with the Classpath Exception.
 */
package tsb.port;

/**
 * Die native Bibliothek, die nicht geladen wird.
 *
 * <p>Neunundzwanzig Stellen in {@code java.desktop} rufen {@code System.loadLibrary} —
 * {@code awt}, {@code fontmanager}, {@code lcms}, {@code javajpeg}, {@code mlib_image}.
 * Keine dieser Bibliotheken gibt es auf dem Telefon, und keine wird gebraucht: die
 * Rasterung, die Schrift und die Bilder kommen aus der Plattform. Im JDK-8-Port wurden
 * dafuer neun Klassen von Hand gepatcht; {@code Modulfrei} leitet jetzt jeden Aufruf
 * hierher, und hier passiert nichts. Was eine native Methode danach doch braucht, meldet
 * der {@code Entnativisierer} mit Namen.
 */
public final class Bibliothek {

    private Bibliothek() {
    }

    /** Ersatz fuer {@code System.loadLibrary(String)}. */
    public static void loadLibrary(String name) {
    }

    /** Ersatz fuer {@code System.load(String)}. */
    public static void load(String pfad) {
    }
}
