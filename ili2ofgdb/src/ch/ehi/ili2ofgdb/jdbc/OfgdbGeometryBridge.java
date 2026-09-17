package ch.ehi.ili2ofgdb.jdbc;

import java.util.ArrayList;
import java.util.List;

import ch.ehi.ili2ofgdb.OfgdbWkb2iox;
import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox_j.wkb.Iox2wkb;
import ch.so.agi.filegdb.geometry.FileGdbGeometry;
import ch.so.agi.filegdb.geometry.FileGdbMultiPoint;
import ch.so.agi.filegdb.geometry.FileGdbPart;
import ch.so.agi.filegdb.geometry.FileGdbPoint;
import ch.so.agi.filegdb.geometry.FileGdbPolygon;
import ch.so.agi.filegdb.geometry.FileGdbPolyline;
import ch.so.agi.filegdb.geometry.GeometryFieldDefinition;

/**
 * Converts between the WKB representation used by the JDBC layer and the native filegdb4j geometry
 * model.
 *
 * <p>The conversion goes through the INTERLIS IOM model: WKB is decoded by the established IOX WKB
 * reader, {@link OfgdbIomGeometry} maps between IOM and filegdb4j, and WKB is written by the IOX WKB
 * writer.
 */
public final class OfgdbGeometryBridge {

    private OfgdbGeometryBridge() {
    }

    /** Converts a WKB value to the native geometry model. */
    public static FileGdbGeometry fromWkb(byte[] wkb, GeometryFieldDefinition definition) {
        if (wkb == null || wkb.length < 5) {
            return null;
        }
        try {
            byte[] sanitized = ch.ehi.ili2ofgdb.OfgdbStrokeZSanitizer.sanitizeNaNZToZero(wkb);
            IomObject iom = new OfgdbWkb2iox().read(sanitized);
            if (iom == null) {
                return null;
            }
            return sanitize(OfgdbIomGeometry.toFileGdbGeometry(iom));
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to convert WKB to FileGDB geometry", ex);
        }
    }

    /** Converts a native geometry value to WKB. */
    public static byte[] toWkb(FileGdbGeometry geometry, GeometryFieldDefinition definition) {
        return toWkb(geometry, definition, false);
    }

    /**
     * Converts a native geometry value to WKB.
     *
     * @param forceMulti wraps single polylines/surfaces into their multi container, matching the
     *     declared geometry type of the column (MULTILINE, MULTISURFACE, ...)
     */
    public static byte[] toWkb(FileGdbGeometry geometry, GeometryFieldDefinition definition,
            boolean forceMulti) {
        if (geometry == null) {
            return null;
        }
        try {
            IomObject iom = OfgdbIomGeometry.toIom(geometry);
            if (forceMulti) {
                iom = OfgdbIomGeometry.asMulti(iom);
            }
            Iox2wkb encoder = new Iox2wkb(definition != null && definition.hasZ() ? 3 : 2);
            String tag = iom.getobjecttag();
            if (Iom_jObject.COORD.equals(tag)) {
                return encoder.coord2wkb(iom);
            }
            if (Iom_jObject.MULTICOORD.equals(tag)) {
                return encoder.multicoord2wkb(iom);
            }
            if (Iom_jObject.POLYLINE.equals(tag)) {
                return encoder.polyline2wkb(iom, false, true, 0.0);
            }
            if (Iom_jObject.MULTIPOLYLINE.equals(tag)) {
                return encoder.multiline2wkb(iom, true, 0.0);
            }
            if (Iom_jObject.SURFACE.equals(tag)) {
                return encoder.surface2wkb(iom, true, 0.0, false);
            }
            if (Iom_jObject.MULTISURFACE.equals(tag)) {
                return encoder.multisurface2wkb(iom, true, 0.0, false);
            }
            throw new IllegalArgumentException("unsupported geometry object " + tag);
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to convert FileGDB geometry to WKB", ex);
        }
    }

    /** Replaces invalid (NaN/infinite) ordinates; filegdb4j rejects them on write. */
    private static FileGdbGeometry sanitize(FileGdbGeometry geometry) {
        if (geometry instanceof FileGdbPoint) {
            return sanitizePoint((FileGdbPoint) geometry);
        }
        if (geometry instanceof FileGdbMultiPoint) {
            List<FileGdbPoint> points = new ArrayList<FileGdbPoint>();
            for (FileGdbPoint point : ((FileGdbMultiPoint) geometry).points()) {
                points.add(sanitizePoint(point));
            }
            return new FileGdbMultiPoint(points);
        }
        if (geometry instanceof FileGdbPolyline) {
            return new FileGdbPolyline(sanitizeParts(((FileGdbPolyline) geometry).parts()));
        }
        if (geometry instanceof FileGdbPolygon) {
            return new FileGdbPolygon(sanitizeParts(((FileGdbPolygon) geometry).parts()));
        }
        return geometry;
    }

    private static List<FileGdbPart> sanitizeParts(List<FileGdbPart> parts) {
        List<FileGdbPart> result = new ArrayList<FileGdbPart>(parts.size());
        for (FileGdbPart part : parts) {
            List<FileGdbPoint> points = new ArrayList<FileGdbPoint>(part.points().size());
            for (FileGdbPoint point : part.points()) {
                points.add(sanitizePoint(point));
            }
            result.add(new FileGdbPart(points, part.segments()));
        }
        return result;
    }

    private static FileGdbPoint sanitizePoint(FileGdbPoint point) {
        double x = point.x();
        double y = point.y();
        Double z = point.z();
        Double m = point.m();
        boolean changed = false;
        if (!isValid(x)) {
            x = 0;
            changed = true;
        }
        if (!isValid(y)) {
            y = 0;
            changed = true;
        }
        if (z != null && !isValid(z.doubleValue())) {
            z = Double.valueOf(0);
            changed = true;
        }
        if (m != null && !isValid(m.doubleValue())) {
            m = Double.valueOf(0);
            changed = true;
        }
        return changed ? new FileGdbPoint(x, y, z, m) : point;
    }

    private static boolean isValid(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
