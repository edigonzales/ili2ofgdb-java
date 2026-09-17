package ch.ehi.ili2ofgdb.jdbc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.so.agi.filegdb.geometry.CircularArcSegment;
import ch.so.agi.filegdb.geometry.FileGdbGeometry;
import ch.so.agi.filegdb.geometry.FileGdbMultiPoint;
import ch.so.agi.filegdb.geometry.FileGdbPart;
import ch.so.agi.filegdb.geometry.FileGdbPoint;
import ch.so.agi.filegdb.geometry.FileGdbPolygon;
import ch.so.agi.filegdb.geometry.FileGdbPolyline;
import ch.so.agi.filegdb.geometry.FileGdbSegment;

/**
 * Converts between the INTERLIS IOM geometry model and the filegdb4j geometry model.
 *
 * <p>Both models are curve aware: IOM carries arcs as {@code ARC} segment objects whose
 * {@code A1}/{@code A2} attributes hold a point on the arc, filegdb4j carries them as
 * {@link CircularArcSegment}s.
 */
final class OfgdbIomGeometry {

    private static final String ARC = Iom_jObject.ARC;

    private OfgdbIomGeometry() {
    }

    // ------------------------------------------------------------------
    // IOM -> filegdb4j
    // ------------------------------------------------------------------

    static FileGdbGeometry toFileGdbGeometry(IomObject object) {
        String tag = object.getobjecttag();
        if (Iom_jObject.COORD.equals(tag)) {
            return point(object);
        }
        if (Iom_jObject.MULTICOORD.equals(tag)) {
            List<FileGdbPoint> points = new ArrayList<FileGdbPoint>();
            int count = object.getattrvaluecount(Iom_jObject.MULTICOORD_COORD);
            for (int i = 0; i < count; i++) {
                points.add(point(object.getattrobj(Iom_jObject.MULTICOORD_COORD, i)));
            }
            return new FileGdbMultiPoint(points);
        }
        if (Iom_jObject.POLYLINE.equals(tag)) {
            List<FileGdbPart> parts = new ArrayList<FileGdbPart>();
            parts.add(part(object));
            return new FileGdbPolyline(parts);
        }
        if (Iom_jObject.MULTIPOLYLINE.equals(tag)) {
            List<FileGdbPart> parts = new ArrayList<FileGdbPart>();
            int count = object.getattrvaluecount(Iom_jObject.MULTIPOLYLINE_POLYLINE);
            for (int i = 0; i < count; i++) {
                parts.add(part(object.getattrobj(Iom_jObject.MULTIPOLYLINE_POLYLINE, i)));
            }
            return new FileGdbPolyline(parts);
        }
        if (Iom_jObject.SURFACE.equals(tag)) {
            List<FileGdbPart> parts = new ArrayList<FileGdbPart>();
            collectRings(object, parts);
            return new FileGdbPolygon(parts);
        }
        if (Iom_jObject.MULTISURFACE.equals(tag)) {
            List<FileGdbPart> parts = new ArrayList<FileGdbPart>();
            int count = object.getattrvaluecount(Iom_jObject.MULTISURFACE_SURFACE);
            for (int i = 0; i < count; i++) {
                collectRings(object.getattrobj(Iom_jObject.MULTISURFACE_SURFACE, i), parts);
            }
            return new FileGdbPolygon(parts);
        }
        throw new IllegalArgumentException("unsupported geometry object " + tag);
    }

    private static void collectRings(IomObject surface, List<FileGdbPart> parts) {
        int count = surface.getattrvaluecount(Iom_jObject.SURFACE_BOUNDARY);
        for (int i = 0; i < count; i++) {
            IomObject boundary = surface.getattrobj(Iom_jObject.SURFACE_BOUNDARY, i);
            int polylineCount = boundary.getattrvaluecount(Iom_jObject.BOUNDARY_POLYLINE);
            for (int j = 0; j < polylineCount; j++) {
                FileGdbPart ring = part(boundary.getattrobj(Iom_jObject.BOUNDARY_POLYLINE, j));
                parts.add(closeRing(ring));
            }
        }
    }

    private static FileGdbPart closeRing(FileGdbPart ring) {
        List<FileGdbPoint> points = ring.points();
        if (points.isEmpty()) {
            return ring;
        }
        FileGdbPoint first = points.get(0);
        FileGdbPoint last = points.get(points.size() - 1);
        if (first.x() == last.x() && first.y() == last.y()) {
            return ring;
        }
        List<FileGdbPoint> closed = new ArrayList<FileGdbPoint>(points);
        closed.add(first);
        return new FileGdbPart(closed, ring.segments());
    }

    private static FileGdbPoint point(IomObject coord) {
        double x = parseDouble(coord.getattrvalue(Iom_jObject.COORD_C1));
        double y = parseDouble(coord.getattrvalue(Iom_jObject.COORD_C2));
        String zValue = coord.getattrvalue(Iom_jObject.COORD_C3);
        Double z = zValue != null && zValue.length() > 0 ? Double.valueOf(parseDouble(zValue)) : null;
        return new FileGdbPoint(x, y, z, null);
    }

    private static FileGdbPart part(IomObject polyline) {
        List<FileGdbPoint> points = new ArrayList<FileGdbPoint>();
        List<FileGdbSegment> segments = new ArrayList<FileGdbSegment>();
        IomObject sequence = polyline.getattrobj(Iom_jObject.POLYLINE_SEQUENCE, 0);
        if (sequence == null) {
            return new FileGdbPart(points, segments);
        }
        int count = sequence.getattrvaluecount(Iom_jObject.SEGMENTS_SEGMENT);
        for (int i = 0; i < count; i++) {
            IomObject segment = sequence.getattrobj(Iom_jObject.SEGMENTS_SEGMENT, i);
            String tag = segment.getobjecttag();
            if (Iom_jObject.COORD.equals(tag) || ARC.equals(tag)) {
                points.add(point(segment));
                if (ARC.equals(tag)) {
                    if (points.size() < 2) {
                        throw new IllegalArgumentException("arc without start point");
                    }
                    int startIndex = points.size() - 2;
                    FileGdbPoint start = points.get(startIndex);
                    FileGdbPoint end = points.get(startIndex + 1);
                    double interiorX = parseDouble(segment.getattrvalue(Iom_jObject.ARC_A1));
                    double interiorY = parseDouble(segment.getattrvalue(Iom_jObject.ARC_A2));
                    segments.add(arc(start, interiorX, interiorY, end, startIndex));
                }
            } else {
                throw new IllegalArgumentException("unsupported segment object " + tag);
            }
        }
        return new FileGdbPart(points, segments);
    }

    private static CircularArcSegment arc(
            FileGdbPoint start, double interiorX, double interiorY, FileGdbPoint end, int startIndex) {
        boolean counterClockwise = isCounterClockwise(start, interiorX, interiorY, end);
        return new CircularArcSegment(startIndex, interiorX, interiorY, false, counterClockwise);
    }

    private static boolean isCounterClockwise(
            FileGdbPoint start, double interiorX, double interiorY, FileGdbPoint end) {
        double[] center = circleCenter(start.x(), start.y(), interiorX, interiorY, end.x(), end.y());
        if (center == null) {
            return true;
        }
        double cross = (start.x() - center[0]) * (end.y() - center[1])
                - (start.y() - center[1]) * (end.x() - center[0]);
        return cross > 0;
    }

    /** Circle through three points; null if they are collinear. */
    private static double[] circleCenter(
            double x1, double y1, double x2, double y2, double x3, double y3) {
        double d = 2 * (x1 * (y2 - y3) + x2 * (y3 - y1) + x3 * (y1 - y2));
        if (Math.abs(d) < 1e-12) {
            return null;
        }
        double ux = ((x1 * x1 + y1 * y1) * (y2 - y3)
                + (x2 * x2 + y2 * y2) * (y3 - y1)
                + (x3 * x3 + y3 * y3) * (y1 - y2)) / d;
        double uy = ((x1 * x1 + y1 * y1) * (x3 - x2)
                + (x2 * x2 + y2 * y2) * (x1 - x3)
                + (x3 * x3 + y3 * y3) * (x2 - x1)) / d;
        return new double[] {ux, uy};
    }

    // ------------------------------------------------------------------
    // filegdb4j -> IOM
    // ------------------------------------------------------------------

    static IomObject toIom(FileGdbGeometry geometry) {
        if (geometry instanceof FileGdbPoint) {
            return coord((FileGdbPoint) geometry);
        }
        if (geometry instanceof FileGdbMultiPoint) {
            IomObject result = new Iom_jObject(Iom_jObject.MULTICOORD, null);
            for (FileGdbPoint point : ((FileGdbMultiPoint) geometry).points()) {
                result.addattrobj(Iom_jObject.MULTICOORD_COORD, coord(point));
            }
            return result;
        }
        if (geometry instanceof FileGdbPolyline) {
            List<FileGdbPart> parts = ((FileGdbPolyline) geometry).parts();
            if (parts.size() == 1) {
                return polyline(parts.get(0));
            }
            IomObject result = new Iom_jObject(Iom_jObject.MULTIPOLYLINE, null);
            for (FileGdbPart part : parts) {
                result.addattrobj(Iom_jObject.MULTIPOLYLINE_POLYLINE, polyline(part));
            }
            return result;
        }
        if (geometry instanceof FileGdbPolygon) {
            IomObject multiSurface = new Iom_jObject(Iom_jObject.MULTISURFACE, null);
            FileGdbPart exterior = null;
            IomObject surface = null;
            for (FileGdbPart part : ((FileGdbPolygon) geometry).parts()) {
                if (surface == null || exterior == null || !containsRing(exterior, part)) {
                    surface = multiSurface.addattrobj(Iom_jObject.MULTISURFACE_SURFACE, Iom_jObject.SURFACE);
                    exterior = part;
                }
                IomObject boundary = surface.addattrobj(Iom_jObject.SURFACE_BOUNDARY, Iom_jObject.BOUNDARY);
                boundary.addattrobj(Iom_jObject.BOUNDARY_POLYLINE, polyline(part));
            }
            return multiSurface;
        }
        throw new IllegalArgumentException("unsupported geometry " + geometry.getClass().getName());
    }

    /** Point in polygon test used to group rings into surfaces. */
    private static boolean containsRing(FileGdbPart exterior, FileGdbPart ring) {
        List<FileGdbPoint> points = exterior.points();
        if (points.size() < 4 || ring.points().isEmpty()) {
            return false;
        }
        double x = ring.points().get(0).x();
        double y = ring.points().get(0).y();
        boolean inside = false;
        for (int i = 0, j = points.size() - 1; i < points.size(); j = i++) {
            double xi = points.get(i).x();
            double yi = points.get(i).y();
            double xj = points.get(j).x();
            double yj = points.get(j).y();
            if (((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)) {
                inside = !inside;
            }
        }
        return inside;
    }

    /** Wraps a single polyline/surface into its multi container. */
    static IomObject asMulti(IomObject object) {
        if (object == null || Iom_jObject.MULTIPOLYLINE.equals(object.getobjecttag())
                || Iom_jObject.MULTISURFACE.equals(object.getobjecttag())) {
            return object;
        }
        if (Iom_jObject.POLYLINE.equals(object.getobjecttag())) {
            IomObject multi = new Iom_jObject(Iom_jObject.MULTIPOLYLINE, null);
            multi.addattrobj(Iom_jObject.MULTIPOLYLINE_POLYLINE, object);
            return multi;
        }
        if (Iom_jObject.SURFACE.equals(object.getobjecttag())) {
            IomObject multi = new Iom_jObject(Iom_jObject.MULTISURFACE, null);
            multi.addattrobj(Iom_jObject.MULTISURFACE_SURFACE, object);
            return multi;
        }
        return object;
    }

    private static IomObject polyline(FileGdbPart part) {
        IomObject polyline = new Iom_jObject(Iom_jObject.POLYLINE, null);
        IomObject sequence = polyline.addattrobj(Iom_jObject.POLYLINE_SEQUENCE, Iom_jObject.SEGMENTS);
        List<FileGdbPoint> points = part.points();
        Map<Integer, CircularArcSegment> arcs = new HashMap<Integer, CircularArcSegment>();
        for (FileGdbSegment segment : part.segments()) {
            if (segment instanceof CircularArcSegment) {
                arcs.put(Integer.valueOf(((CircularArcSegment) segment).startPointIndex()),
                        (CircularArcSegment) segment);
            }
        }
        for (int i = 0; i < points.size(); i++) {
            FileGdbPoint point = points.get(i);
            if (i == 0) {
                sequence.addattrobj(Iom_jObject.SEGMENTS_SEGMENT, coord(point));
                continue;
            }
            CircularArcSegment arc = arcs.get(Integer.valueOf(i - 1));
            if (arc == null) {
                sequence.addattrobj(Iom_jObject.SEGMENTS_SEGMENT, coord(point));
                continue;
            }
            IomObject arcCoord = coord(point);
            arcCoord.setobjecttag(ARC);
            double[] interior = arcInterior(points.get(i - 1), arc, point);
            arcCoord.setattrvalue(Iom_jObject.ARC_A1, format(interior[0]));
            arcCoord.setattrvalue(Iom_jObject.ARC_A2, format(interior[1]));
            sequence.addattrobj(Iom_jObject.SEGMENTS_SEGMENT, arcCoord);
        }
        return polyline;
    }

    private static double[] arcInterior(
            FileGdbPoint start, CircularArcSegment arc, FileGdbPoint end) {
        if (!arc.byCenter()) {
            return new double[] {arc.interiorX(), arc.interiorY()};
        }
        double centerX = arc.interiorX();
        double centerY = arc.interiorY();
        double startAngle = Math.atan2(start.y() - centerY, start.x() - centerX);
        double endAngle = Math.atan2(end.y() - centerY, end.x() - centerX);
        double direction = arc.counterClockwise() ? 1.0 : -1.0;
        double sweep = endAngle - startAngle;
        while (direction > 0 && sweep <= 0) {
            sweep += 2 * Math.PI;
        }
        while (direction < 0 && sweep >= 0) {
            sweep -= 2 * Math.PI;
        }
        double middleAngle = startAngle + sweep / 2;
        double radius = Math.sqrt(
                (start.x() - centerX) * (start.x() - centerX)
                        + (start.y() - centerY) * (start.y() - centerY));
        return new double[] {
            centerX + radius * Math.cos(middleAngle),
            centerY + radius * Math.sin(middleAngle)
        };
    }

    private static IomObject coord(FileGdbPoint point) {
        IomObject coord = new Iom_jObject(Iom_jObject.COORD, null);
        coord.setattrvalue(Iom_jObject.COORD_C1, format(point.x()));
        coord.setattrvalue(Iom_jObject.COORD_C2, format(point.y()));
        if (point.z() != null) {
            coord.setattrvalue(Iom_jObject.COORD_C3, format(point.z().doubleValue()));
        }
        return coord;
    }

    private static String format(double value) {
        return Double.toString(value);
    }

    private static double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Double.NaN;
        }
        return Double.parseDouble(value.trim());
    }
}
