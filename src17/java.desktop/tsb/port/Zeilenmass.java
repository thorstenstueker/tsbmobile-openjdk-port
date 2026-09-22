/*
 * Copyright (C) 2026 tsb Thorsten Stueker Buero for Technology development
 * Licensed under the GNU General Public License, version 2, with the Classpath Exception.
 */
package tsb.port;

import java.awt.FontMetrics;
import java.awt.font.LineMetrics;

/**
 * Die Zeilenmasse einer Schrift aus der Metrik des Ports — was {@code Font.getLineMetrics}
 * liefert, ohne den Weg ueber {@code sun.font}. Unterstreichung und Durchstreichung sind
 * Schaetzungen aus der Schriftgroesse, wie sie das JDK selbst fuer Schriften ohne diese
 * Angaben trifft.
 */
final class Zeilenmass extends LineMetrics {

    private final FontMetrics metrik;
    private final int zeichen;

    Zeilenmass(FontMetrics metrik, int zeichen) {
        this.metrik = metrik;
        this.zeichen = zeichen;
    }

    @Override public int getNumChars() { return zeichen; }
    @Override public float getAscent() { return metrik.getAscent(); }
    @Override public float getDescent() { return metrik.getDescent(); }
    @Override public float getLeading() { return metrik.getLeading(); }
    @Override public float getHeight() { return metrik.getHeight(); }
    @Override public int getBaselineIndex() { return java.awt.Font.ROMAN_BASELINE; }
    @Override public float[] getBaselineOffsets() { return new float[] {0f, -getAscent() * 0.5f, -getAscent()}; }
    @Override public float getStrikethroughOffset() { return -getAscent() / 3f; }
    @Override public float getStrikethroughThickness() { return Math.max(1f, metrik.getFont().getSize2D() / 14f); }
    @Override public float getUnderlineOffset() { return Math.max(1f, getDescent() / 2f); }
    @Override public float getUnderlineThickness() { return Math.max(1f, metrik.getFont().getSize2D() / 14f); }
}
