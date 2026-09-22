#!/bin/bash
# Baut den java.desktop-Port aus JDK 17 — desktop17-<version>.jar — in einem Lauf.
#
#   tools/port17.sh               baut work/out/desktop17-0.1.0.jar
#   PORT_VERSION=0.2.0 tools/port17.sh
#
# Der JDK-8-Port hatte kein Skript ueber alles; jeder Schritt wurde von Hand gerufen und die
# Reihenfolge stand in einem README. Hier steht sie ausfuehrbar:
#
#   0. jmod → Klassen      java.desktop und java.datatransfer aus dem JDK 17, ohne die
#                          macOS-Schicht (sun.lwawt, com.apple, Metal, OpenGL, CFont…),
#                          dazu die Handvoll java.base-Klassen, die java.desktop braucht
#                          und die weder Android noch der RoboVM-Fork haben
#   1. Patches             src17/ gegen das JDK 17 uebersetzen (--patch-module) und ueber
#                          die Klassen legen: SwingUtilities2, MethodUtil, PlatformGraphicsInfo,
#                          SharedSecrets, CleanerFactory und die Naehte tsb.port.*
#   2. Modulfrei           Class.getModule, Thread(…, inheritThreadLocals), loadLibrary, …
#   3. Entnativisierer     jede native Methode bekommt einen Rumpf
#   4. Umbenenner          java.beans → tsb.port.beans, java.lang.Module → tsb.port.Modul, …
#   5. Laderwechsel        ClassLoader.getSystemClassLoader() → tsb.port.Lader.system()
#   6. Kurzschluss         die Schnitte des Ports (Regelsatz "port")
#   7. PlatformLogger      die umbenannte JDK-8-Fassung aus desktop8-0.1.0.jar, weil die
#                          des JDK 17 an jdk.internal.logger haengt
#
# Voraussetzungen: JDK 17 (Liberica 17.0.12 mit jmods), ASM 9.7.1 im ~/.m2, das alte
# desktop8-0.1.0.jar unter work/desktop8-0.1.0.jar (Schritt 7).
set -euo pipefail

cd "$(dirname "$0")/.."
J17=$(/usr/libexec/java_home -v 17)
M2=$HOME/.m2/repository/org/ow2/asm
ASM=$M2/asm/9.7.1/asm-9.7.1.jar:$M2/asm-commons/9.7.1/asm-commons-9.7.1.jar:$M2/asm-tree/9.7.1/asm-tree-9.7.1.jar
VERSION=${PORT_VERSION:-0.1.0}
W=$PWD/work
OUT=$W/out
rm -rf "$OUT" && mkdir -p "$OUT"

echo "== 0. Klassen aus den jmods"
rm -rf $W/desktop $W/base && mkdir -p $W/desktop $W/base
(cd $W/desktop && "$J17/bin/jmod" extract "$J17/jmods/java.desktop.jmod" && "$J17/bin/jmod" extract "$J17/jmods/java.datatransfer.jmod")
(cd $W/base && "$J17/bin/jmod" extract "$J17/jmods/java.base.jmod")
rm -rf $W/roh && mkdir -p $W/roh
# Die plattformfreien Klassen von java.desktop. Was macOS ist, bleibt draussen — samt der
# drei Gluestuecke, die nur von dort aus erreicht werden.
(cd $W/desktop/classes && find . -name '*.class' \
    -not -name module-info.class \
    -not -path './sun/lwawt/*' -not -path './com/apple/*' -not -path './apple/*' \
    -not -path './sun/java2d/metal/*' -not -path './sun/java2d/opengl/*' \
    -not -name 'CFont*' -not -name 'CStrike*' -not -name 'CCharToGlyphMapper*' -not -name 'CCompositeGlyphMapper*' \
    -not -name 'CGraphics*' -not -name 'MacOS*' -not -name 'OSXSurfaceData*' -not -name 'CRenderer*' \
    -not -name 'MacosxSurfaceManagerFactory*' -not -name 'PlatformPrinterJobProxy*' \
    -not -name 'CPrinter*' -not -name 'CImage*' -not -name 'CInputMethod*' \
    | cpio -pdm ../../roh 2>/dev/null)
# Aus java.base: was java.desktop nennt und die Telefone nicht haben.
(cd $W/base/classes && find . \( \
    -path './sun/security/action/GetPropertyAction*' -o -path './sun/security/action/GetBooleanAction*' \
    -o -path './sun/security/action/GetIntegerAction*' \
    -o -path './sun/reflect/misc/ReflectUtil*' \
    -o -path './sun/reflect/generics/reflectiveObjects/GenericArrayTypeImpl*' \
    -o -path './sun/reflect/generics/reflectiveObjects/ParameterizedTypeImpl*' \
    -o -path './jdk/internal/access/JavaSecurityAccess*' -o -path './jdk/internal/access/JavaAWTAccess*' \
    -o -path './jdk/internal/access/JavaAWTFontAccess*' -o -path './jdk/internal/access/JavaBeansAccess*' \
    -o -path './sun/security/util/SecurityConstants*' \
    \) -name '*.class' | cpio -pdm ../../roh 2>/dev/null)
echo "   $(find $W/roh -name '*.class' | wc -l | tr -d ' ') Klassen"

echo "== 1. Patches und Naehte uebersetzen"
# Zwei Laeufe, weil javac nur ein Modul je Lauf aus Quellen flicken kann. java.base zuerst;
# java.desktop braucht davon nichts, was nicht ohnehin exportiert ist.
rm -rf $W/patchout && mkdir -p $W/patchout
"$J17/bin/javac" -nowarn -d $W/patchout \
    --patch-module java.base=src17/java.base $(find src17/java.base -name '*.java')
"$J17/bin/javac" -nowarn -d $W/patchout \
    --patch-module java.desktop=src17/java.desktop $(find src17/java.desktop -name '*.java')
# Der Umbenenner erwartet Klassen in ihren Ursprungspaketen — die Naehte sind schon dort.
(cd $W/patchout && find . -name '*.class' | cpio -pdmu ../roh 2>/dev/null)
echo "   $(find $W/patchout -name '*.class' | wc -l | tr -d ' ') Klassen darueber gelegt"
(cd $W/roh && rm -f ../desktop17-0.jar && zip -qr ../desktop17-0.jar .)

echo "== Werkzeuge"
rm -rf $W/tools && mkdir -p $W/tools
"$J17/bin/javac" -nowarn -cp "$ASM" -d $W/tools tools/*.java
T="$W/tools:$ASM"

echo "== 2. Modulfrei"
(cd $W && "$J17/bin/java" -cp "$T" Modulfrei desktop17-0.jar desktop17-1.jar)
echo "== 3. Entnativisierer"
(cd $W && "$J17/bin/java" -cp "$T" Entnativisierer desktop17-1.jar desktop17-2.jar)
echo "== 4. Umbenenner"
(cd $W && "$J17/bin/java" -cp "$T" Umbenenner desktop17-2.jar desktop17-3.jar)
echo "== 5. Laderwechsel"
(cd $W && "$J17/bin/java" -cp "$T" Laderwechsel desktop17-3.jar desktop17-4.jar)
echo "== 6. Kurzschluss"
(cd $W && "$J17/bin/java" -cp "$T" Kurzschluss desktop17-4.jar desktop17-5.jar port)

echo "== 7. PlatformLogger aus dem JDK-8-Port"
rm -rf $W/logging && mkdir -p $W/logging
(cd $W/logging && unzip -qo ../desktop8-0.1.0.jar 'tsb/port/logging/*')
cp $W/desktop17-5.jar "$OUT/desktop17-$VERSION.jar"
(cd $W/logging && zip -qr "../out/desktop17-$VERSION.jar" tsb)

echo "== fertig"
echo "   $(unzip -l "$OUT/desktop17-$VERSION.jar" | grep -c '\.class$') Klassen in $OUT/desktop17-$VERSION.jar"
