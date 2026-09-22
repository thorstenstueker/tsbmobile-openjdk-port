/*
 * tsbMobile: ersetzt jdk.internal.ref.CleanerFactory des JDK 17.
 *
 * Das Original haengt seinen Faden an jdk.internal.misc.InnocuousThread. Hier genuegt ein
 * java.lang.ref.Cleaner — den hat Android ab 13 und der RoboVM-Fork seit 22.09.2026.
 * Benutzer im Port: TitledBorder, FileSystemView und der Marlin-Rasterer.
 *
 * Lizenz: GPLv2 mit Classpath-Ausnahme.
 */
package jdk.internal.ref;

import java.lang.ref.Cleaner;

public final class CleanerFactory {

    private static final Cleaner CLEANER = Cleaner.create();

    private CleanerFactory() {
    }

    public static Cleaner cleaner() {
        return CLEANER;
    }
}
