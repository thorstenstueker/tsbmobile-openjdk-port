package tsb.port;

import java.security.AccessControlContext;
import java.security.PrivilegedAction;

/**
 * Führt die Aktion aus. Mehr ist auf einem Telefon nicht zu tun.
 *
 * <p>Vier Klassen des Ports — {@code EventQueue}, {@code RepaintManager},
 * {@code TransferHandler}, {@code DocumentHandler} — holen sich über
 * {@code sun.misc.SharedSecrets.getJavaSecurityAccess()} einen Zugang, um eine Aktion unter
 * dem <i>Schnitt</i> zweier Sicherheitskontexte laufen zu lassen: dem des Aufrufers und dem,
 * unter dem ein Ereignis einst erzeugt wurde. Das schützt in einem Applet-Browser davor,
 * dass Code aus einer Quelle Rechte einer anderen erbt.
 *
 * <p>MobiVMs {@code sun.misc} stammt aus Androids libcore und hat diese Methode nicht — der
 * Grund, warum der Start dreimal hintereinander an derselben Klasse scheiterte.
 *
 * <p>Ihn nachzubauen wäre sinnlos: eine App aus einem einzigen signierten Bündel hat nur
 * einen Kontext, und es gibt keinen Sicherheitsverwalter, der einen Schnitt auswerten
 * würde. Der Schnitt zweier gleicher Mengen ist die Menge selbst.
 */
public final class Sicherheitszugang implements sun.misc.JavaSecurityAccess {

    public static final sun.misc.JavaSecurityAccess INSTANZ = new Sicherheitszugang();

    private Sicherheitszugang() {
    }

    @Override
    public <T> T doIntersectionPrivilege(PrivilegedAction<T> aktion,
                                         AccessControlContext stapel,
                                         AccessControlContext kontext) {
        return aktion.run();
    }

    @Override
    public <T> T doIntersectionPrivilege(PrivilegedAction<T> aktion,
                                         AccessControlContext kontext) {
        return aktion.run();
    }
}
