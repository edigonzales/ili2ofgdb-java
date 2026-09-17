package ch.ehi.ili2ofgdb;

import ch.ehi.ili2db.converter.ConverterException;
import ch.interlis.iox_j.wkb.WKBConstants;

final class OfgdbStrokeZSanitizer {
    private OfgdbStrokeZSanitizer() {
    }

    static byte[] sanitizeNaNZToZero(byte[] wkb) throws ConverterException {
        if (wkb == null || wkb.length == 0) {
            return wkb;
        }
        Cursor cursor = new Cursor();
        sanitizeGeometry(wkb, cursor);
        if (cursor.offset != wkb.length) {
            throw new ConverterException("invalid WKB: trailing bytes after geometry at byte " + cursor.offset);
        }
        return wkb;
    }

    private static void sanitizeGeometry(byte[] wkb, Cursor cursor) throws ConverterException {
        int geometryOffset = cursor.offset;
        int byteOrder = readUnsignedByte(wkb, cursor, "byte order");
        if (byteOrder != WKBConstants.wkbNDR && byteOrder != WKBConstants.wkbXDR) {
            throw new ConverterException("invalid WKB byte order " + byteOrder + " at byte " + geometryOffset);
        }
        boolean littleEndian = byteOrder == WKBConstants.wkbNDR;
        int typeWord = readInt(wkb, cursor, littleEndian, "geometry type");
        GeometryTypeInfo typeInfo = GeometryTypeInfo.fromTypeWord(typeWord);
        if (typeInfo.hasSrid) {
            skipBytes(wkb, cursor, 4, "SRID");
        }

        switch (typeInfo.geometryType) {
            case WKBConstants.wkbPoint:
                sanitizePoint(wkb, cursor, littleEndian, typeInfo);
                return;
            case WKBConstants.wkbLineString:
            case WKBConstants.wkbCircularString:
                sanitizeCoordinateSequence(wkb, cursor, littleEndian, typeInfo);
                return;
            case WKBConstants.wkbPolygon:
            case WKBConstants.wkbTriangle:
                sanitizePolygonLike(wkb, cursor, littleEndian, typeInfo);
                return;
            case WKBConstants.wkbCompoundCurve:
            case WKBConstants.wkbCurvePolygon:
            case WKBConstants.wkbMultiPoint:
            case WKBConstants.wkbMultiLineString:
            case WKBConstants.wkbMultiPolygon:
            case WKBConstants.wkbGeometryCollection:
            case WKBConstants.wkbMultiCurve:
            case WKBConstants.wkbMultiSurface:
            case WKBConstants.wkbPolyhedralSurface:
            case WKBConstants.wkbTIN:
                sanitizeNestedGeometries(wkb, cursor, littleEndian);
                return;
            default:
                throw new ConverterException("unsupported WKB geometry type " + typeInfo.geometryType + " at byte " + geometryOffset);
        }
    }

    private static void sanitizePolygonLike(byte[] wkb, Cursor cursor, boolean littleEndian, GeometryTypeInfo typeInfo) throws ConverterException {
        int ringCount = readCount(wkb, cursor, littleEndian, "ring count");
        for (int ringIndex = 0; ringIndex < ringCount; ringIndex++) {
            int pointCount = readCount(wkb, cursor, littleEndian, "ring point count");
            for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
                sanitizeCoordinate(wkb, cursor, littleEndian, typeInfo.hasZ, typeInfo.hasM);
            }
        }
    }

    private static void sanitizeNestedGeometries(byte[] wkb, Cursor cursor, boolean littleEndian) throws ConverterException {
        int geometryCount = readCount(wkb, cursor, littleEndian, "nested geometry count");
        for (int geometryIndex = 0; geometryIndex < geometryCount; geometryIndex++) {
            sanitizeGeometry(wkb, cursor);
        }
    }

    private static void sanitizePoint(byte[] wkb, Cursor cursor, boolean littleEndian, GeometryTypeInfo typeInfo) throws ConverterException {
        sanitizeCoordinate(wkb, cursor, littleEndian, typeInfo.hasZ, typeInfo.hasM);
    }

    private static void sanitizeCoordinateSequence(byte[] wkb, Cursor cursor, boolean littleEndian, GeometryTypeInfo typeInfo) throws ConverterException {
        int pointCount = readCount(wkb, cursor, littleEndian, "point count");
        for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
            sanitizeCoordinate(wkb, cursor, littleEndian, typeInfo.hasZ, typeInfo.hasM);
        }
    }

    private static void sanitizeCoordinate(byte[] wkb, Cursor cursor, boolean littleEndian, boolean hasZ, boolean hasM) throws ConverterException {
        skipBytes(wkb, cursor, 16, "XY ordinates");
        if (hasZ) {
            int zOffset = cursor.offset;
            double z = readDouble(wkb, cursor, littleEndian, "Z ordinate");
            if (Double.isNaN(z)) {
                writeDouble(wkb, zOffset, littleEndian, 0.0d);
            }
        }
        if (hasM) {
            skipBytes(wkb, cursor, 8, "M ordinate");
        }
    }

    private static int readUnsignedByte(byte[] wkb, Cursor cursor, String what) throws ConverterException {
        ensureRemaining(wkb, cursor, 1, what);
        return wkb[cursor.offset++] & 0xFF;
    }

    private static int readCount(byte[] wkb, Cursor cursor, boolean littleEndian, String what) throws ConverterException {
        int value = readInt(wkb, cursor, littleEndian, what);
        if (value < 0) {
            throw new ConverterException("invalid WKB " + what + " " + value + " at byte " + (cursor.offset - 4));
        }
        return value;
    }

    private static int readInt(byte[] wkb, Cursor cursor, boolean littleEndian, String what) throws ConverterException {
        ensureRemaining(wkb, cursor, 4, what);
        int offset = cursor.offset;
        cursor.offset += 4;
        if (littleEndian) {
            return (wkb[offset] & 0xFF)
                    | ((wkb[offset + 1] & 0xFF) << 8)
                    | ((wkb[offset + 2] & 0xFF) << 16)
                    | ((wkb[offset + 3] & 0xFF) << 24);
        }
        return ((wkb[offset] & 0xFF) << 24)
                | ((wkb[offset + 1] & 0xFF) << 16)
                | ((wkb[offset + 2] & 0xFF) << 8)
                | (wkb[offset + 3] & 0xFF);
    }

    private static double readDouble(byte[] wkb, Cursor cursor, boolean littleEndian, String what) throws ConverterException {
        ensureRemaining(wkb, cursor, 8, what);
        int offset = cursor.offset;
        cursor.offset += 8;
        long bits;
        if (littleEndian) {
            bits = (wkb[offset] & 0xFFL)
                    | ((wkb[offset + 1] & 0xFFL) << 8)
                    | ((wkb[offset + 2] & 0xFFL) << 16)
                    | ((wkb[offset + 3] & 0xFFL) << 24)
                    | ((wkb[offset + 4] & 0xFFL) << 32)
                    | ((wkb[offset + 5] & 0xFFL) << 40)
                    | ((wkb[offset + 6] & 0xFFL) << 48)
                    | ((wkb[offset + 7] & 0xFFL) << 56);
        } else {
            bits = ((wkb[offset] & 0xFFL) << 56)
                    | ((wkb[offset + 1] & 0xFFL) << 48)
                    | ((wkb[offset + 2] & 0xFFL) << 40)
                    | ((wkb[offset + 3] & 0xFFL) << 32)
                    | ((wkb[offset + 4] & 0xFFL) << 24)
                    | ((wkb[offset + 5] & 0xFFL) << 16)
                    | ((wkb[offset + 6] & 0xFFL) << 8)
                    | (wkb[offset + 7] & 0xFFL);
        }
        return Double.longBitsToDouble(bits);
    }

    private static void writeDouble(byte[] wkb, int offset, boolean littleEndian, double value) {
        long bits = Double.doubleToRawLongBits(value);
        if (littleEndian) {
            wkb[offset] = (byte) (bits & 0xFFL);
            wkb[offset + 1] = (byte) ((bits >> 8) & 0xFFL);
            wkb[offset + 2] = (byte) ((bits >> 16) & 0xFFL);
            wkb[offset + 3] = (byte) ((bits >> 24) & 0xFFL);
            wkb[offset + 4] = (byte) ((bits >> 32) & 0xFFL);
            wkb[offset + 5] = (byte) ((bits >> 40) & 0xFFL);
            wkb[offset + 6] = (byte) ((bits >> 48) & 0xFFL);
            wkb[offset + 7] = (byte) ((bits >> 56) & 0xFFL);
            return;
        }
        wkb[offset] = (byte) ((bits >> 56) & 0xFFL);
        wkb[offset + 1] = (byte) ((bits >> 48) & 0xFFL);
        wkb[offset + 2] = (byte) ((bits >> 40) & 0xFFL);
        wkb[offset + 3] = (byte) ((bits >> 32) & 0xFFL);
        wkb[offset + 4] = (byte) ((bits >> 24) & 0xFFL);
        wkb[offset + 5] = (byte) ((bits >> 16) & 0xFFL);
        wkb[offset + 6] = (byte) ((bits >> 8) & 0xFFL);
        wkb[offset + 7] = (byte) (bits & 0xFFL);
    }

    private static void skipBytes(byte[] wkb, Cursor cursor, int byteCount, String what) throws ConverterException {
        ensureRemaining(wkb, cursor, byteCount, what);
        cursor.offset += byteCount;
    }

    private static void ensureRemaining(byte[] wkb, Cursor cursor, int byteCount, String what) throws ConverterException {
        if (cursor.offset + byteCount > wkb.length) {
            throw new ConverterException("invalid WKB: unexpected end while reading " + what + " at byte " + cursor.offset);
        }
    }

    private static final class Cursor {
        int offset;
    }

    private static final class GeometryTypeInfo {
        final int geometryType;
        final boolean hasZ;
        final boolean hasM;
        final boolean hasSrid;

        private GeometryTypeInfo(int geometryType, boolean hasZ, boolean hasM, boolean hasSrid) {
            this.geometryType = geometryType;
            this.hasZ = hasZ;
            this.hasM = hasM;
            this.hasSrid = hasSrid;
        }

        static GeometryTypeInfo fromTypeWord(int typeWord) {
            int simpleType = typeWord & 0xFFFF;
            int baseType = simpleType % 1000;
            int isoDimEncoding = simpleType - baseType;

            boolean hasEwkbZ = (typeWord & WKBConstants.ewkbIncludesZ) == WKBConstants.ewkbIncludesZ;
            boolean hasEwkbM = (typeWord & WKBConstants.ewkbIncludesM) == WKBConstants.ewkbIncludesM;
            boolean hasIsoZ = isoDimEncoding == WKBConstants.wkbIncludesZ || isoDimEncoding == WKBConstants.wkbIncludesZM;
            boolean hasIsoM = isoDimEncoding == WKBConstants.wkbIncludesM || isoDimEncoding == WKBConstants.wkbIncludesZM;
            boolean hasSrid = (typeWord & WKBConstants.ewkbIncludesSRID) == WKBConstants.ewkbIncludesSRID;

            return new GeometryTypeInfo(baseType, hasEwkbZ || hasIsoZ, hasEwkbM || hasIsoM, hasSrid);
        }
    }
}
