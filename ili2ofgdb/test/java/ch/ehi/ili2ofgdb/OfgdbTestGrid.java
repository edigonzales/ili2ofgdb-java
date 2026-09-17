package ch.ehi.ili2ofgdb;

/**
 * XY grid of the flavor tests.
 *
 * <p>{@link OfgdbTestSetup#setXYParams(ch.interlis.ili2db.gui.Config)} configures a 1 mm XY
 * resolution, which is the storage grid of the file geodatabase. Tests that run through this setup
 * have to compare against the grid rounded X/Y values; Z and M keep the finer default precision.
 */
final class OfgdbTestGrid {

    static final double RESOLUTION = 0.001;

    private OfgdbTestGrid() {
    }

    static double x(double value) {
        return round(value);
    }

    static double y(double value) {
        return round(value);
    }

    static double round(double value) {
        return Math.round(value / RESOLUTION) * RESOLUTION;
    }
}
