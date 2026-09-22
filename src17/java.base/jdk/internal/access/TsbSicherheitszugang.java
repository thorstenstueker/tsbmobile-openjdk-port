/*
 * tsbMobile: der Sicherheitszugang des Ports — Nachfolger von tsb.port.Sicherheitszugang
 * aus dem JDK-8-Port, hier im Paket der Schnittstelle, damit SharedSecrets ihn ohne Blick
 * nach java.desktop liefern kann.
 *
 * Es gibt auf dem Telefon keinen Sicherheitsverwalter und keinen fremden Code, vor dem
 * ein Schnitt aus zwei AccessControlContexten schuetzen wuerde: eine App ist ein
 * signiertes Buendel aus einer Quelle. Also wird die Aktion einfach ausgefuehrt.
 *
 * Lizenz: GPLv2 mit Classpath-Ausnahme.
 */
package jdk.internal.access;

import java.security.AccessControlContext;
import java.security.PermissionCollection;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.IdentityHashMap;
import java.util.Map;

@SuppressWarnings("removal")
public final class TsbSicherheitszugang implements JavaSecurityAccess {

    public static final JavaSecurityAccess INSTANZ = new TsbSicherheitszugang();

    private TsbSicherheitszugang() {
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

    @Override
    public ProtectionDomain[] getProtectDomains(AccessControlContext kontext) {
        return new ProtectionDomain[0];
    }

    @Override
    public ProtectionDomainCache getProtectionDomainCache() {
        return new ProtectionDomainCache() {
            private final Map<ProtectionDomain, PermissionCollection> map = new IdentityHashMap<>();

            @Override
            public synchronized void put(ProtectionDomain pd, PermissionCollection pc) {
                map.put(pd, pc);
            }

            @Override
            public synchronized PermissionCollection get(ProtectionDomain pd) {
                return map.get(pd);
            }
        };
    }
}
