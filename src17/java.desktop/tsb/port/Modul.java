/*
 * Copyright (C) 2026 tsb Thorsten Stueker Buero for Technology development
 * Licensed under the GNU General Public License, version 2, with the Classpath Exception.
 */
package tsb.port;

import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Das Modul, das es nicht gibt.
 *
 * <p>Seit JDK 9 fragt Swing an zehn Stellen nach {@code Class.getModule()} — in
 * {@code UIDefaults.getUI} auf dem Weg zu jedem einzelnen Widget, in ImageIO, in
 * {@code PlainView}. Weder Android noch der RoboVM-Fork kennen {@code java.lang.Module};
 * beide laden alles aus einem Klassenpfad, in dem jede Klasse jede andere sieht.
 *
 * <p>Deshalb schreibt {@code Modulfrei} jeden dieser Aufrufe auf diese Klasse um und der
 * {@code Umbenenner} jede Nennung des Typs {@code java.lang.Module} auf {@code tsb.port.Modul}.
 * Es gibt genau ein Modul, das unbenannte, und es exportiert alles.
 */
public final class Modul {

    private static final Modul UNBENANNT = new Modul();

    private Modul() {
    }

    /** Ersatz fuer {@code Class.getModule()}. */
    public static Modul of(Class<?> klasse) {
        return UNBENANNT;
    }

    /** Ersatz fuer {@code ClassLoader.getUnnamedModule()}. */
    public static Modul unnamed(ClassLoader lader) {
        return UNBENANNT;
    }

    /** Ersatz fuer {@code Class.forName(Module, String)}: null, wenn es die Klasse nicht gibt. */
    public static Class<?> forName(Modul modul, String name) {
        try {
            return Class.forName(name, false, Lader.system());
        } catch (ClassNotFoundException | LinkageError nicht) {
            return null;
        }
    }

    /** Ersatz fuer {@code ResourceBundle.getBundle(String, Module)}. */
    public static ResourceBundle bundle(String name, Modul modul) {
        return ResourceBundle.getBundle(name, Locale.getDefault(), Lader.system());
    }

    /** Ersatz fuer {@code ResourceBundle.getBundle(String, Locale, Module)}. */
    public static ResourceBundle bundle(String name, Locale locale, Modul modul) {
        return ResourceBundle.getBundle(name, locale, Lader.system());
    }

    /**
     * Ersatz fuer {@code MethodHandles.lookup()}: null. Der einzige Gebrauch im Port ist
     * {@code lookup().ensureInitialized(c)} in AWTAccessor und SwingAccessor, und den
     * schreibt {@code Modulfrei} auf {@link #ensureInitialized} um.
     */
    public static MethodHandles.Lookup lookup() {
        return null;
    }

    /** Ersatz fuer {@code Lookup.ensureInitialized(Class)}: {@code Class.forName} mit init. */
    public static Class<?> ensureInitialized(MethodHandles.Lookup lookup, Class<?> klasse) {
        try {
            Class.forName(klasse.getName(), true, klasse.getClassLoader());
        } catch (ClassNotFoundException unmoeglich) {
            throw new InternalError("tsbMobile: " + klasse.getName() + " ist da, aber nicht da",
                    unmoeglich);
        }
        return klasse;
    }

    // --- was java.lang.Module im Port gefragt wird -----------------------------------

    public boolean isNamed() {
        return false;
    }

    public String getName() {
        return null;
    }

    public boolean isExported(String paket) {
        return true;
    }

    public boolean isExported(String paket, Modul an) {
        return true;
    }

    public boolean isOpen(String paket) {
        return true;
    }

    public boolean isOpen(String paket, Modul an) {
        return true;
    }

    public ClassLoader getClassLoader() {
        return Lader.system();
    }

    public InputStream getResourceAsStream(String name) {
        ClassLoader lader = Lader.system();
        return lader == null ? ClassLoader.getSystemResourceAsStream(name)
                : lader.getResourceAsStream(name);
    }

    @Override
    public String toString() {
        return "unnamed module (tsbMobile)";
    }
}
