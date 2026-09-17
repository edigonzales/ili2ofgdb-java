package ch.ehi.ili2ofgdb.jdbc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import ch.ehi.ili2db.base.DbNames;
import ch.ehi.ili2ofgdb.jdbc.OfgdbSql.ColumnSpec;
import ch.ehi.ili2ofgdb.jdbc.OfgdbSql.CreateTableSpec;
import ch.ehi.ili2ofgdb.jdbc.OfgdbSql.DeleteSpec;
import ch.ehi.ili2ofgdb.jdbc.OfgdbSql.InsertSpec;
import ch.ehi.ili2ofgdb.jdbc.OfgdbSql.UpdateSpec;
import ch.so.agi.filegdb.FileGeodatabase;
import ch.so.agi.filegdb.catalog.CodedValue;
import ch.so.agi.filegdb.catalog.CodedValueDomain;
import ch.so.agi.filegdb.catalog.CrsDefinition;
import ch.so.agi.filegdb.catalog.Dataset;
import ch.so.agi.filegdb.catalog.GdbCatalog;
import ch.so.agi.filegdb.catalog.RangeDomain;
import ch.so.agi.filegdb.catalog.RelationshipCardinality;
import ch.so.agi.filegdb.geometry.FileGdbGeometry;
import ch.so.agi.filegdb.geometry.GeometryFieldDefinition;
import ch.so.agi.filegdb.geometry.GeometryKind;
import ch.so.agi.filegdb.table.FileGdbField;
import ch.so.agi.filegdb.table.FileGdbFieldType;
import ch.so.agi.filegdb.table.FileGdbRow;
import ch.so.agi.filegdb.table.FileGdbTable;
import ch.so.agi.filegdb.write.FeatureClassDefinition;
import ch.so.agi.filegdb.write.GdbFeatureWriter;
import ch.so.agi.filegdb.write.GdbTableWriter;
import ch.so.agi.filegdb.write.RelationshipDefinition;
import ch.so.agi.filegdb.write.TableDefinition;

/**
 * Storage engine of the JDBC driver, backed by the pure Java filegdb4j library.
 *
 * <p>All openfgdb4j specifics of the former implementation are replaced by this class: table and
 * column resolution, row access, DDL/DML execution as well as domain and relationship handling.
 */
public final class OfgdbFileGdb implements AutoCloseable {

    /** One writable session per geodatabase file, shared by all JDBC connections. */
    private static final Map<String, OfgdbFileGdb> SESSIONS =
            new HashMap<String, OfgdbFileGdb>();

    /**
     * Returns the shared session for the given geodatabase, creating it on first use. Every caller
     * must call {@link #close()} once; the underlying database is closed with the last handle.
     */
    public static OfgdbFileGdb acquire(Path gdbDirectory) throws SQLException {
        Path normalized = gdbDirectory.toAbsolutePath().normalize();
        synchronized (SESSIONS) {
            OfgdbFileGdb session = SESSIONS.get(normalized.toString());
            if (session == null) {
                session = new OfgdbFileGdb(normalized);
                SESSIONS.put(normalized.toString(), session);
            }
            session.sessionRefCount++;
            return session;
        }
    }

    /**
     * Fine grained XY precision (micro metres). The ArcGIS default of 0.1 mm is too coarse for the
     * unrounded ili2db imports.
     */
    private static final ch.so.agi.filegdb.geometry.CoordinatePrecision OFGDB_PRECISION =
            new ch.so.agi.filegdb.geometry.CoordinatePrecision(
                    -2147483647, -2147483647, 1000000, 0.00001,
                    -100000, 10000, 0.001,
                    -100000, 10000, 0.001);

    private final Path directory;
    private final Map<String, String> knownTables = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, TableWriter> writers = new TreeMap<String, TableWriter>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, Long> nextIds = new TreeMap<String, Long>(String.CASE_INSENSITIVE_ORDER);
    /** Fine grained geometry kinds of tables created by this session (MULTILINE, MULTISURFACE, ...). */
    private final Map<String, String> geometryKinds =
            new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
    /** Tables whose feature class was created with a native spatial index. */
    private final java.util.Set<String> spatialIndexedTables =
            new java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
    private GdbCatalog catalog;
    private FileGeodatabase database;
    private int sessionRefCount = 0;
    private boolean closed = false;

    private OfgdbFileGdb(Path gdbDirectory) throws SQLException {
        try {
            this.directory = gdbDirectory.toAbsolutePath().normalize();
            Path parent = this.directory.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            if (Files.exists(this.directory)) {
                this.database = FileGeodatabase.openWritable(this.directory);
            } else {
                this.database = FileGeodatabase.create(this.directory);
            }
            refreshTables();
        } catch (IOException e) {
            throw new SQLException("failed to open file geodatabase " + gdbDirectory, e);
        }
    }

    public Path getDirectory() {
        return directory;
    }

    // ------------------------------------------------------------------
    // Table and column resolution
    // ------------------------------------------------------------------

    public synchronized List<String> listTableNames() throws SQLException {
        refreshTables();
        return new ArrayList<String>(knownTables.values());
    }

    public synchronized String resolveTableName(String requested) throws SQLException {
        if (requested == null) {
            return null;
        }
        String probe = requested.trim();
        if (probe.isEmpty()) {
            return probe;
        }
        String resolved = findTableName(probe);
        if (resolved != null) {
            return resolved;
        }
        refreshTables();
        resolved = findTableName(probe);
        if (resolved != null) {
            return resolved;
        }
        String suffix = lastIdentifierPart(probe);
        if (!suffix.equals(probe)) {
            resolved = findTableName(suffix);
            if (resolved != null) {
                return resolved;
            }
        }
        return probe;
    }

    private String findTableName(String probe) {
        String direct = knownTables.get(probe);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, String> entry : knownTables.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(probe)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String lastIdentifierPart(String identifier) {
        String text = identifier;
        if (text.startsWith("\"") && text.endsWith("\"") && text.length() > 1) {
            text = text.substring(1, text.length() - 1);
        }
        int dot = text.lastIndexOf('.');
        return dot >= 0 ? text.substring(dot + 1) : text;
    }

    private void refreshTables() throws SQLException {
        try {
            knownTables.clear();
            GdbCatalog nameCatalog = GdbCatalog.open(directory);
            for (String name : nameCatalog.tableNames()) {
                knownTables.put(name, name);
            }
            catalog = null;
        } catch (IOException e) {
            throw new SQLException("failed to read file geodatabase catalog", e);
        }
    }

    /** Opens the full catalog (definitions, CRS, domains) lazily. */
    private GdbCatalog catalog() throws SQLException {
        if (catalog == null) {
            try {
                catalog = GdbCatalog.open(directory);
            } catch (IOException e) {
                throw new SQLException("failed to read file geodatabase catalog", e);
            }
        }
        return catalog;
    }

    public synchronized ColumnInfo[] columns(String tableName) throws SQLException {
        String name = resolveTableName(tableName);
        Dataset dataset = resolveDataset(name);
        if (dataset == null) {
            throw new SQLException("table not found: " + tableName);
        }
        try {
            FileGdbTable table = new FileGdbTable(dataset, java.util.Collections.<String, ch.so.agi.filegdb.table.FieldMetadata>emptyMap(), catalog);
            List<FileGdbField> fields = table.fields();
            List<ColumnInfo> columns = new ArrayList<ColumnInfo>();
            for (FileGdbField field : fields) {
                if (field.type() == FileGdbFieldType.OBJECTID) {
                    continue;
                }
                ColumnInfo column = new ColumnInfo();
                column.name = field.name();
                column.nullable = field.nullable();
                column.maxWidth = field.maxWidth();
                column.field = field;
                if (field.type() == FileGdbFieldType.GEOMETRY) {
                    column.geometry = true;
                    GeometryFieldDefinition definition = table.geomField() != null
                            ? table.geomField().geometry()
                            : GeometryFieldDefinition.of(field.name(), GeometryKind.NONE);
                    column.geometryDefinition = definition;
                }
                columns.add(column);
            }
            return columns.toArray(new ColumnInfo[columns.size()]);
        } catch (IOException e) {
            throw new SQLException("failed to read table " + name, e);
        }
    }

    private Dataset datasetOf(String tableName) {
        if (catalog == null) {
            try {
                catalog = GdbCatalog.open(directory);
            } catch (IOException e) {
                return null;
            }
        }
        java.util.Optional<Dataset> dataset = catalog.dataset(tableName);
        return dataset.isPresent() ? dataset.get() : null;
    }

    /** Resolves a dataset, refreshing the catalog once when it is not known yet. */
    private Dataset resolveDataset(String tableName) throws SQLException {
        Dataset dataset = datasetOf(tableName);
        if (dataset != null) {
            return dataset;
        }
        refreshTables();
        return datasetOf(tableName);
    }

    // ------------------------------------------------------------------
    // Row access
    // ------------------------------------------------------------------

    /** Materializes all rows of a table; geometry columns are returned as WKB. */
    public synchronized TableData readRows(String tableName) throws SQLException {
        String name = resolveTableName(tableName);
        Dataset dataset = resolveDataset(name);
        if (dataset == null) {
            throw new SQLException("table not found: " + tableName);
        }
        try {
            FileGdbTable table = new FileGdbTable(dataset, java.util.Collections.<String, ch.so.agi.filegdb.table.FieldMetadata>emptyMap(), catalog);
            ColumnInfo[] columns = columns(name);
            GeometryFieldDefinition geometryDefinition = null;
            for (ColumnInfo column : columns) {
                if (column.geometry) {
                    geometryDefinition = column.geometryDefinition;
                    break;
                }
            }
            List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
            List<Long> objectIds = new ArrayList<Long>();
            String fineKind = geometryKinds.get(name);
            for (FileGdbRow row : table) {
                Map<String, Object> values = new LinkedHashMap<String, Object>();
                for (ColumnInfo column : columns) {
                    Object value = row.get(column.name);
                    values.put(column.name,
                            toDriverValue(column, value, geometryDefinition, fineKind));
                }
                rows.add(values);
                objectIds.add(Long.valueOf(row.objectId()));
            }
            TableData data = new TableData();
            data.tableName = name;
            data.columns = new ArrayList<String>();
            for (ColumnInfo column : columns) {
                data.columns.add(column.name);
            }
            data.rows = rows;
            data.objectIds = objectIds;
            return data;
        } catch (IOException e) {
            throw new SQLException("failed to read table " + name, e);
        }
    }

    private Object toDriverValue(ColumnInfo column, Object value,
            GeometryFieldDefinition definition, String fineKind) throws SQLException {
        if (value == null) {
            return null;
        }
        if (column.geometry) {
            byte[] wkb = OfgdbGeometryBridge.toWkb(
                    (FileGdbGeometry) value, definition, isMultiKind(fineKind));
            return wkb;
        }
        switch (column.field.type()) {
            case BINARY:
                return value;
            case INT16:
            case INT32:
            case INT64:
                return Long.valueOf(((Number) value).longValue());
            case FLOAT32:
            case FLOAT64:
                return Double.valueOf(((Number) value).doubleValue());
            case DATETIME:
                return value.toString();
            case DATE:
            case TIME:
                return value.toString();
            case GUID:
            case GLOBALID:
                return value.toString();
            default:
                return value.toString();
        }
    }

    // ------------------------------------------------------------------
    // DDL / DML
    // ------------------------------------------------------------------

    public synchronized int executeUpdate(String sql) throws SQLException {
        String statement = sql != null ? sql.trim() : "";
        while (statement.endsWith(";")) {
            statement = statement.substring(0, statement.length() - 1).trim();
        }
        if (OfgdbSql.startsWithKeyword(statement, "CREATE TABLE")) {
            createTable(OfgdbSql.parseCreateTable(statement));
            return 0;
        }
        if (OfgdbSql.startsWithKeyword(statement, "INSERT")) {
            insert(OfgdbSql.parseInsert(statement));
            return 0;
        }
        if (OfgdbSql.startsWithKeyword(statement, "UPDATE")) {
            update(OfgdbSql.parseUpdate(statement));
            return 0;
        }
        if (OfgdbSql.startsWithKeyword(statement, "DELETE")) {
            delete(OfgdbSql.parseDelete(statement));
            return 0;
        }
        throw new SQLException("unsupported SQL statement: " + statement);
    }

    private void createTable(CreateTableSpec spec) throws SQLException {
        String tableName = resolveTableName(spec.tableName);
        if (datasetOf(tableName) != null || findTableName(spec.tableName) != null) {
            throw new SQLException("table already exists: " + tableName);
        }
        List<FileGdbField> fields = new ArrayList<FileGdbField>();
        FileGdbField geometryField = null;
        GeometryFieldDefinition geometryDefinition = null;
        boolean spatialIndex = false;
        for (ColumnSpec column : spec.columns) {
            if ("OFGDB_GEOMETRY".equals(column.type)) {
                GeometryKind kind = toGeometryKind(column.geometryKind);
                GeometryFieldDefinition definition =
                        GeometryFieldDefinition.of(column.name, kind)
                                .withNullable(!column.notNull)
                                .withPrecision(OFGDB_PRECISION);
                if (column.dimension == 3) {
                    definition = definition.withZ();
                }
                geometryDefinition = definition;
                geometryField = FileGdbField.binary(column.name);
                geometryKinds.put(tableName, column.geometryKind);
                spatialIndex = column.spatialIndex;
                continue;
            }
            FileGdbField field = toField(column);
            fields.add(field);
        }
        try {
            if (geometryDefinition != null) {
                FeatureClassDefinition.Builder builder =
                        FeatureClassDefinition.builder(tableName).spatialIndex(spatialIndex);
                for (FileGdbField field : fields) {
                    builder.field(field);
                }
                CrsDefinition crs;
                {
                    int srsId = srsIdOf(spec);
                    String wkt = ch.ehi.ili2ofgdb.OfgdbCrs.wktFor(srsId);
                    crs = new CrsDefinition(srsId, srsId, wkt);
                    geometryDefinition = geometryDefinition.withWkt(wkt);
                }
                builder.geometry(geometryDefinition).crs(crs);
                GdbFeatureWriter writer = database.createFeatureClass(builder.build());
                writers.put(tableName, new TableWriter(writer, null));
                if (spatialIndex) {
                    spatialIndexedTables.add(tableName);
                }
            } else {
                TableDefinition.Builder builder = TableDefinition.builder(tableName);
                for (FileGdbField field : fields) {
                    builder.field(field);
                }
                GdbTableWriter writer = database.createTable(builder.build());
                writers.put(tableName, new TableWriter(null, writer));
            }
            catalog = null;
            knownTables.put(tableName, tableName);
        } catch (IOException | RuntimeException e) {
            throw new SQLException("failed to create table " + tableName, e);
        }
    }

    private int srsIdOf(CreateTableSpec spec) {
        for (ColumnSpec column : spec.columns) {
            if ("OFGDB_GEOMETRY".equals(column.type)) {
                return column.srsId;
            }
        }
        return 0;
    }

    private static FileGdbField toField(ColumnSpec column) {
        String type = column.type == null ? "" : column.type.toUpperCase(Locale.ROOT);
        FileGdbField field;
        if ("SMALLINT".equals(type) || "INT16".equals(type)) {
            field = FileGdbField.smallInteger(column.name);
        } else if ("INTEGER".equals(type) || "INT".equals(type) || "INT32".equals(type)) {
            field = FileGdbField.integer(column.name);
        } else if ("BIGINT".equals(type) || "INT64".equals(type)) {
            field = FileGdbField.bigInteger(column.name);
        } else if ("DOUBLE".equals(type) || "REAL".equals(type) || "FLOAT".equals(type)
                || "DECIMAL".equals(type) || "NUMERIC".equals(type)) {
            field = FileGdbField.real(column.name);
        } else if ("BLOB".equals(type) || "BINARY".equals(type) || "VARBINARY".equals(type)) {
            field = FileGdbField.binary(column.name);
        } else if ("XML".equals(type)) {
            field = FileGdbField.xml(column.name);
        } else {
            int width = column.varcharLength > 0 ? column.varcharLength : 4096;
            field = FileGdbField.string(column.name, width);
        }
        if (!column.notNull) {
            field = field.asNullable();
        }
        if (column.domain != null) {
            field = field.withDomain(column.domain);
        }
        return field;
    }

    private static GeometryKind toGeometryKind(String kind) {
        if (kind == null) {
            return GeometryKind.NONE;
        }
        String upper = kind.toUpperCase(Locale.ROOT);
        if ("POINT".equals(upper)) {
            return GeometryKind.POINT;
        }
        if ("MULTIPOINT".equals(upper)) {
            return GeometryKind.MULTIPOINT;
        }
        if ("LINE".equals(upper) || "CIRCULARSTRING".equals(upper) || "COMPOUNDCURVE".equals(upper)
                || "MULTILINE".equals(upper) || "MULTICURVE".equals(upper)) {
            return GeometryKind.LINE;
        }
        if ("POLYGON".equals(upper) || "CURVEPOLYGON".equals(upper)
                || "MULTIPOLYGON".equals(upper) || "MULTISURFACE".equals(upper)) {
            return GeometryKind.POLYGON;
        }
        return GeometryKind.NONE;
    }

    private void insert(InsertSpec spec) throws SQLException {
        String tableName = resolveTableName(spec.tableName);
        ColumnInfo[] columns = columns(tableName);
        Map<String, Integer> indexByName = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < columns.length; i++) {
            indexByName.put(columns[i].name.toLowerCase(Locale.ROOT), Integer.valueOf(i));
            indexByName.put(columns[i].name, Integer.valueOf(i));
        }
        Object[] allValues = new Object[columns.length];
        boolean[] provided = new boolean[columns.length];
        if (spec.columns.isEmpty()) {
            for (int i = 0; i < spec.values.size() && i < columns.length; i++) {
                allValues[i] = spec.values.get(i);
                provided[i] = true;
            }
        } else {
            for (int i = 0; i < spec.columns.size(); i++) {
                Integer index = indexByName.get(spec.columns.get(i));
                if (index == null) {
                    index = indexByName.get(spec.columns.get(i).toLowerCase(Locale.ROOT));
                }
                if (index == null) {
                    throw new SQLException("unknown column " + spec.columns.get(i) + " in table " + tableName);
                }
                allValues[index.intValue()] = spec.values.get(i);
                provided[index.intValue()] = true;
            }
        }
        List<Object> attributes = new ArrayList<Object>();
        FileGdbGeometry geometry = null;
        for (int i = 0; i < columns.length; i++) {
            ColumnInfo column = columns[i];
            Object value = provided[i] ? toFieldValue(column, allValues[i]) : null;
            if (!provided[i] && isIdColumn(column)) {
                value = Long.valueOf(nextId(tableName, columns));
            }
            if (column.geometry) {
                geometry = (FileGdbGeometry) value;
            } else {
                attributes.add(value);
            }
        }
        try {
            TableWriter writer = writer(tableName, spec, columns);
            if (writer.featureWriter != null) {
                writer.featureWriter.write(attributes.toArray(), geometry);
            } else {
                writer.tableWriter.write(attributes.toArray());
            }
        } catch (IOException | RuntimeException e) {
            throw new SQLException("failed to insert into " + tableName, e);
        }
    }

    private Object toFieldValue(ColumnInfo column, Object literal) throws SQLException {
        if (literal == null) {
            return null;
        }
        if (column.geometry) {
            if (literal instanceof byte[]) {
                return OfgdbGeometryBridge.fromWkb((byte[]) literal, column.geometryDefinition);
            }
            if (literal instanceof FileGdbGeometry) {
                return literal;
            }
            throw new SQLException("unsupported geometry literal for column " + column.name);
        }
        FileGdbFieldType type = column.field.type();
        switch (type) {
            case BINARY:
                if (literal instanceof byte[]) {
                    return literal;
                }
                return String.valueOf(literal).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            case INT16:
            case INT32:
            case INT64:
                return Long.valueOf(((Number) literal).longValue());
            case FLOAT32:
            case FLOAT64:
                return Double.valueOf(((Number) literal).doubleValue());
            case DATETIME:
                return parseDateTime(String.valueOf(literal));
            case DATE:
                return parseDate(String.valueOf(literal));
            case TIME:
                return parseTime(String.valueOf(literal));
            case GUID:
            case GLOBALID:
                return String.valueOf(literal);
            default:
                return String.valueOf(literal);
        }
    }

    private static Object parseDateTime(String text) throws SQLException {
        String normalized = normalizeDateTimeText(text);
        try {
            if (normalized.length() <= 10) {
                return java.time.LocalDate.parse(normalized).atStartOfDay();
            }
            String value = normalized.replace(' ', 'T');
            if (value.endsWith("Z")) {
                value = value.substring(0, value.length() - 1);
            }
            return java.time.LocalDateTime.parse(value);
        } catch (RuntimeException e) {
            throw new SQLException("invalid date/time literal " + text, e);
        }
    }

    private static Object parseDate(String text) throws SQLException {
        String normalized = normalizeDateTimeText(text);
        try {
            return java.time.LocalDate.parse(normalized.substring(0, Math.min(10, normalized.length())));
        } catch (RuntimeException e) {
            throw new SQLException("invalid date literal " + text, e);
        }
    }

    private static Object parseTime(String text) throws SQLException {
        String normalized = normalizeDateTimeText(text);
        try {
            int blank = normalized.indexOf(' ');
            String value = blank >= 0 ? normalized.substring(blank + 1) : normalized;
            int dot = value.indexOf('.');
            if (dot > 0) {
                value = value.substring(0, dot);
            }
            return java.time.LocalTime.parse(value);
        } catch (RuntimeException e) {
            throw new SQLException("invalid time literal " + text, e);
        }
    }

    private static String normalizeDateTimeText(String text) {
        String value = text.trim();
        if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1);
        }
        return value.replace('/', '-');
    }

    private void update(UpdateSpec spec) throws SQLException {
        String tableName = resolveTableName(spec.tableName);
        ColumnInfo[] columns = columns(tableName);
        TableData data = readRows(tableName);
        Map<String, Integer> updateIndexes = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < spec.columns.size(); i++) {
            int index = indexOfColumn(columns, spec.columns.get(i));
            updateIndexes.put(spec.columns.get(i), Integer.valueOf(index));
        }
        try {
            TableWriter writer = ensureWriter(tableName, columns);
            for (int rowIndex = 0; rowIndex < data.rows.size(); rowIndex++) {
                Map<String, Object> row = data.rows.get(rowIndex);
                if (spec.where != null && !spec.where.matches(normalizedRow(row, columns))) {
                    continue;
                }
                Object[] newValues = new Object[columns.length];
                for (int i = 0; i < columns.length; i++) {
                    Object current = row.get(columns[i].name);
                    newValues[i] = toFieldValue(columns[i], current);
                }
                for (int i = 0; i < spec.columns.size(); i++) {
                    int index = updateIndexes.get(spec.columns.get(i)).intValue();
                    newValues[index] = toFieldValue(columns[index], spec.values.get(i));
                }
                applyUpdate(writer, data.objectIds.get(rowIndex).longValue(), columns, newValues);
            }
        } catch (IOException | RuntimeException e) {
            throw new SQLException("failed to update " + tableName, e);
        }
    }

    private void applyUpdate(TableWriter writer, long objectId, ColumnInfo[] columns, Object[] values)
            throws IOException {
        List<Object> attributes = new ArrayList<Object>();
        FileGdbGeometry geometry = null;
        for (int i = 0; i < columns.length; i++) {
            if (columns[i].geometry) {
                geometry = (FileGdbGeometry) values[i];
            } else {
                attributes.add(values[i]);
            }
        }
        if (writer.featureWriter != null) {
            writer.featureWriter.updateRow(objectId, attributes.toArray(), geometry);
        } else {
            writer.tableWriter.updateRow(objectId, attributes.toArray());
        }
    }

    private void delete(DeleteSpec spec) throws SQLException {
        String tableName = resolveTableName(spec.tableName);
        ColumnInfo[] columns = columns(tableName);
        TableData data = readRows(tableName);
        try {
            TableWriter writer = ensureWriter(tableName, columns);
            for (int rowIndex = 0; rowIndex < data.rows.size(); rowIndex++) {
                Map<String, Object> row = data.rows.get(rowIndex);
                if (spec.where != null && !spec.where.matches(normalizedRow(row, columns))) {
                    continue;
                }
                long objectId = data.objectIds.get(rowIndex).longValue();
                if (writer.featureWriter != null) {
                    writer.featureWriter.deleteRow(objectId);
                } else {
                    writer.tableWriter.deleteRow(objectId);
                }
            }
        } catch (IOException | RuntimeException e) {
            throw new SQLException("failed to delete from " + tableName, e);
        }
    }

    private static Map<String, Object> normalizedRow(Map<String, Object> row, ColumnInfo[] columns) {
        Map<String, Object> normalized = new LinkedHashMap<String, Object>();
        for (ColumnInfo column : columns) {
            normalized.put(column.name, row.get(column.name));
        }
        return normalized;
    }

    private static int indexOfColumn(ColumnInfo[] columns, String name) throws SQLException {
        for (int i = 0; i < columns.length; i++) {
            if (columns[i].name.equals(name) || columns[i].name.equalsIgnoreCase(name)) {
                return i;
            }
        }
        throw new SQLException("unknown column " + name);
    }

    // ------------------------------------------------------------------
    // Writers
    // ------------------------------------------------------------------

    private static final class TableWriter {
        private final GdbFeatureWriter featureWriter;
        private final GdbTableWriter tableWriter;

        private TableWriter(GdbFeatureWriter featureWriter, GdbTableWriter tableWriter) {
            this.featureWriter = featureWriter;
            this.tableWriter = tableWriter;
        }

        private void close() throws IOException {
            if (featureWriter != null) {
                featureWriter.close();
            }
            if (tableWriter != null) {
                tableWriter.close();
            }
        }
    }

    private TableWriter writer(String tableName, InsertSpec spec, ColumnInfo[] columns) throws SQLException {
        TableWriter existing = writers.get(tableName);
        if (existing != null) {
            return existing;
        }
        try {
            boolean featureClass = hasGeometry(columns);
            if (featureClass) {
                GdbFeatureWriter writer = database.appendFeatures(tableName, hasSpatialIndex(tableName));
                TableWriter result = new TableWriter(writer, null);
                writers.put(tableName, result);
                return result;
            }
            GdbTableWriter writer = database.appendRows(tableName);
            TableWriter result = new TableWriter(null, writer);
            writers.put(tableName, result);
            return result;
        } catch (IOException e) {
            throw new SQLException(
                    "failed to open " + tableName + " for writing: " + e.getMessage(), e);
        }
    }

    private TableWriter ensureWriter(String tableName, ColumnInfo[] columns) throws SQLException {
        return writer(tableName, null, columns);
    }

    /** True if the feature class carries a native spatial index (.spx). */
    private boolean hasSpatialIndex(String tableName) {
        if (spatialIndexedTables.contains(tableName)) {
            return true;
        }
        try {
            Dataset dataset = datasetOf(resolveTableName(tableName));
            if (dataset == null || !dataset.isFeatureClass()) {
                return false;
            }
            return Files.exists(ch.so.agi.filegdb.index.SpatialIndex.path(dataset.tableFile()));
        } catch (SQLException ex) {
            return false;
        }
    }

    private static boolean isMultiKind(String fineKind) {
        if (fineKind == null) {
            return false;
        }
        String kind = fineKind.toUpperCase(Locale.ROOT);
        return "MULTIPOINT".equals(kind) || "MULTILINE".equals(kind) || "MULTICURVE".equals(kind)
                || "MULTIPOLYGON".equals(kind) || "MULTISURFACE".equals(kind);
    }

    private static boolean hasGeometry(ColumnInfo[] columns) {
        for (ColumnInfo column : columns) {
            if (column.geometry) {
                return true;
            }
        }
        return false;
    }

    private static boolean isIdColumn(ColumnInfo column) {
        if (column == null || column.geometry || column.nullable) {
            return false;
        }
        String name = column.name;
        return name != null && name.equalsIgnoreCase(DbNames.T_ID_COL);
    }

    /** Auto assigns the next T_Id for inserts that do not provide one (GDAL FID semantics). */
    private long nextId(String tableName, ColumnInfo[] columns) throws SQLException {
        Long next = nextIds.get(tableName);
        if (next == null) {
            long max = 0;
            TableData data = readRows(tableName);
            for (Map<String, Object> row : data.rows) {
                Object value = row.get(DbNames.T_ID_COL);
                if (value instanceof Number) {
                    max = Math.max(max, ((Number) value).longValue());
                }
            }
            next = Long.valueOf(max + 1);
        }
        nextIds.put(tableName, Long.valueOf(next.longValue() + 1));
        return next.longValue();
    }

    public synchronized void flushWriters() throws SQLException {
        List<IOException> errors = new ArrayList<IOException>();
        for (TableWriter writer : writers.values()) {
            try {
                writer.close();
            } catch (IOException e) {
                errors.add(e);
            }
        }
        writers.clear();
        if (!errors.isEmpty()) {
            throw new SQLException("failed to flush file geodatabase writers", errors.get(0));
        }
    }

    // ------------------------------------------------------------------
    // Domains
    // ------------------------------------------------------------------

    public synchronized List<String> listDomains() throws SQLException {
        try {
            List<String> names = new ArrayList<String>();
            for (ch.so.agi.filegdb.catalog.Domain domain : database.domains()) {
                names.add(domain.name());
            }
            return names;
        } catch (RuntimeException e) {
            throw new SQLException("failed to list domains", e);
        }
    }

    public synchronized void createCodedDomain(String name, String fieldType,
            Map<String, String> codedValues) throws SQLException {
        try {
            if (database.domain(name).isPresent()) {
                return;
            }
            List<CodedValue> values = new ArrayList<CodedValue>();
            for (Map.Entry<String, String> entry : codedValues.entrySet()) {
                values.add(new CodedValue(entry.getValue(), entry.getKey()));
            }
            database.createDomain(new CodedValueDomain(name, toFieldType(fieldType), "", values));
            catalog = null;
        } catch (IOException | RuntimeException e) {
            throw new SQLException("failed to create coded domain " + name, e);
        }
    }

    public synchronized void createRangeDomain(String name, String fieldType, String minValue,
            boolean minInclusive, String maxValue, boolean maxInclusive) throws SQLException {
        try {
            if (database.domain(name).isPresent()) {
                return;
            }
            String min = minValue == null ? null : (minInclusive ? minValue : "(" + minValue);
            String max = maxValue == null ? null : (maxInclusive ? maxValue : maxValue + ")");
            database.createDomain(new RangeDomain(name, toFieldType(fieldType), "", min, max));
            catalog = null;
        } catch (IOException | RuntimeException e) {
            throw new SQLException("failed to create range domain " + name, e);
        }
    }

    private static FileGdbFieldType toFieldType(String fieldType) {
        if (fieldType == null) {
            return FileGdbFieldType.STRING;
        }
        String upper = fieldType.toUpperCase(Locale.ROOT);
        if ("SMALLINT".equals(upper)) {
            return FileGdbFieldType.INT16;
        }
        if ("INTEGER".equals(upper)) {
            return FileGdbFieldType.INT32;
        }
        if ("BIGINT".equals(upper)) {
            return FileGdbFieldType.INT64;
        }
        if ("DOUBLE".equals(upper)) {
            return FileGdbFieldType.FLOAT64;
        }
        return FileGdbFieldType.STRING;
    }

    // ------------------------------------------------------------------
    // Relationships
    // ------------------------------------------------------------------

    public synchronized List<String> listRelationships() throws SQLException {
        try {
            List<String> names = new ArrayList<String>();
            for (ch.so.agi.filegdb.catalog.RelationshipClass relationship : database.relationships()) {
                names.add(relationship.name());
            }
            return names;
        } catch (RuntimeException e) {
            throw new SQLException("failed to list relationships", e);
        }
    }

    public synchronized void createRelationshipClass(String name, String originTable,
            String destinationTable, String originPrimaryKey, String originForeignKey,
            String destinationPrimaryKey, String destinationForeignKey, String forwardLabel,
            String backwardLabel, String cardinality, boolean composite, String mappingTable,
            boolean attributed) throws SQLException {
        try {
            if (listRelationships().contains(name)) {
                return;
            }
            RelationshipDefinition.Builder builder = RelationshipDefinition.builder(name)
                    .originClass(originTable)
                    .destinationClass(destinationTable)
                    .cardinality(toCardinality(cardinality))
                    .originPrimaryKey(originPrimaryKey)
                    .originForeignKey(originForeignKey)
                    .destinationPrimaryKey(destinationPrimaryKey)
                    .destinationForeignKey(destinationForeignKey)
                    .labels(forwardLabel == null ? "" : forwardLabel,
                            backwardLabel == null ? "" : backwardLabel)
                    .composite(composite);
            if (mappingTable != null) {
                builder.mappingTable(mappingTable);
            }
            builder.attributed(attributed);
            database.createRelationship(builder.build());
            catalog = null;
        } catch (IOException | RuntimeException e) {
            throw new SQLException("failed to create relationship " + name, e);
        }
    }

    private static RelationshipCardinality toCardinality(String cardinality) {
        if ("1:1".equals(cardinality)) {
            return RelationshipCardinality.ONE_TO_ONE;
        }
        if ("m:n".equals(cardinality)) {
            return RelationshipCardinality.MANY_TO_MANY;
        }
        return RelationshipCardinality.ONE_TO_MANY;
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Override
    public synchronized void close() throws SQLException {
        synchronized (SESSIONS) {
            if (closed) {
                return;
            }
            sessionRefCount--;
            if (sessionRefCount > 0) {
                return;
            }
            SESSIONS.remove(directory.toString());
            closed = true;
        }
        SQLException failure = null;
        try {
            flushWriters();
        } catch (SQLException e) {
            failure = e;
        }
        try {
            database.close();
        } catch (IOException e) {
            if (failure == null) {
                failure = new SQLException("failed to close file geodatabase", e);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /** Reopens the underlying database, for example after a transaction rollback. */
    public synchronized void reopenDatabase() throws SQLException {
        try {
            try {
                flushWriters();
            } catch (SQLException ignore) {
                // the transaction snapshot will replace the files anyway
            }
            database.close();
        } catch (IOException e) {
            throw new SQLException("failed to close file geodatabase for reopen", e);
        }
        try {
            if (Files.exists(directory)) {
                database = FileGeodatabase.openWritable(directory);
            } else {
                database = FileGeodatabase.create(directory);
            }
        } catch (IOException e) {
            throw new SQLException("failed to reopen file geodatabase " + directory, e);
        }
        catalog = null;
        knownTables.clear();
        writers.clear();
        geometryKinds.clear();
        spatialIndexedTables.clear();
        nextIds.clear();
        refreshTables();
    }

    // ------------------------------------------------------------------
    // Value classes
    // ------------------------------------------------------------------

    /** Column metadata of a table. */
    public static final class ColumnInfo {
        public String name;
        public boolean nullable;
        public int maxWidth;
        public boolean geometry;
        public GeometryFieldDefinition geometryDefinition;
        FileGdbField field;

        public String sqlType() {
            if (geometry) {
                return "GEOMETRY";
            }
            switch (field.type()) {
                case INT16:
                    return "SMALLINT";
                case INT32:
                    return "INTEGER";
                case INT64:
                    return "BIGINT";
                case FLOAT32:
                case FLOAT64:
                    return "DOUBLE";
                case BINARY:
                    return "BLOB";
                case DATETIME:
                    return "TIMESTAMP";
                case DATE:
                    return "DATE";
                case TIME:
                    return "TIME";
                default:
                    return "VARCHAR";
            }
        }
    }

    /** Materialized table content. */
    public static final class TableData {
        public String tableName;
        public List<String> columns;
        public List<Map<String, Object>> rows;
        public List<Long> objectIds;
    }
}
