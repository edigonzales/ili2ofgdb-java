package ch.ehi.ili2ofgdb;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import ch.ehi.basics.logging.EhiLogger;
import ch.ehi.ili2db.base.AbstractJdbcMapping;
import ch.ehi.ili2db.base.DbNames;
import ch.ehi.ili2db.base.Ili2cUtility;
import ch.ehi.ili2db.fromxtf.EnumValueMap;
import ch.ehi.ili2db.gui.Config;
import ch.ehi.ili2db.mapping.NameMapping;
import ch.ehi.ili2ofgdb.jdbc.OfgdbConnection;
import ch.ehi.ili2ofgdb.jdbc.OfgdbFileGdb;
import ch.ehi.sqlgen.generator_impl.ofgdb.GeneratorOfgdb;
import ch.ehi.sqlgen.repository.DbColBoolean;
import ch.ehi.sqlgen.repository.DbColDecimal;
import ch.ehi.sqlgen.repository.DbColGeometry;
import ch.ehi.sqlgen.repository.DbColId;
import ch.ehi.sqlgen.repository.DbColNumber;
import ch.ehi.sqlgen.repository.DbColVarchar;
import ch.ehi.sqlgen.repository.DbColumn;
import ch.ehi.sqlgen.repository.DbTable;
import ch.ehi.sqlgen.repository.DbTableName;
import ch.interlis.ili2c.metamodel.AbstractEnumerationType;
import ch.interlis.ili2c.metamodel.AssociationDef;
import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.Cardinality;
import ch.interlis.ili2c.metamodel.Domain;
import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.EnumTreeValueType;
import ch.interlis.ili2c.metamodel.Enumeration;
import ch.interlis.ili2c.metamodel.NumericType;
import ch.interlis.ili2c.metamodel.PrecisionDecimal;
import ch.interlis.ili2c.metamodel.RoleDef;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.metamodel.Type;
import ch.interlis.ili2c.metamodel.TypeAlias;
import ch.interlis.iom_j.itf.ModelUtilities;

/**
 * ili2db custom mapping strategy for the file geodatabase.
 *
 * <p>Domains and relationship classes are the only file geodatabase concepts that the ili2db core
 * does not produce through the JDBC/SQL interface, so this strategy takes care of them:
 *
 * <ul>
 *   <li>Domain definitions are collected while the schema is mapped and created through the JDBC
 *       connection when the mapping phase ends - before the DDL generator creates the tables. The
 *       column carries the domain name as custom value, so the DDL generator can emit a
 *       {@code DOMAIN} clause. The definitions are also handed to the DDL generator (transient
 *       config value), so that an offline DDL script can create them without model knowledge.
 *   <li>Relationship classes are reconstructed after the schema import from the persisted mapping
 *       tables ({@code T_ILI2DB_ATTRNAME} and {@code T_ILI2DB_TRAFO}) and the compiled INTERLIS
 *       model. This avoids any change to the ili2db core.
 * </ul>
 */
public class OfgdbMapping extends AbstractJdbcMapping {

    /** Config key for domain creation ({@code --fgdbCreateDomains}). */
    public static final String FGDB_CREATE_DOMAINS = "ch.ehi.ili2ofgdb.fgdbCreateDomains";
    /** Config key for inactive enum values ({@code --fgdbIncludeInactiveEnumValues}). */
    public static final String FGDB_INCLUDE_INACTIVE_ENUM_VALUES =
            "ch.ehi.ili2ofgdb.fgdbIncludeInactiveEnumValues";
    /** Config key for relationship classes ({@code --fgdbCreateRelationshipClasses}). */
    public static final String FGDB_CREATE_RELATIONSHIP_CLASSES =
            "ch.ehi.ili2ofgdb.fgdbCreateRelationshipClasses";
    /** Custom value key that carries the domain name from the mapping to the DDL generator. */
    public static final String DOMAIN_CUSTOM_KEY = "ch.ehi.ili2ofgdb.domain";
    /**
     * Transient config key that carries the {@code CREATE DOMAIN} statements from the mapping to the
     * DDL generator, so that an offline DDL script is self-contained.
     */
    public static final String DOMAIN_DDL_CUSTOM_KEY = "ch.ehi.ili2ofgdb.domainDdl";
    /** Custom value key that marks a geometry column for a native spatial index. */
    public static final String GEOM_INDEX_CUSTOM_KEY = "ch.ehi.ili2ofgdb.geomIndex";

    private static final String TRAFO_INHERITANCE_TAG = "ch.ehi.ili2db.inheritance";
    private static final String TRAFO_EMBEDDED = "embedded";

    final Map<String, DomainDefinition> domains = new LinkedHashMap<String, DomainDefinition>();
    private boolean createDomains = true;
    private boolean createRangeDomains = false;
    private boolean includeInactiveEnumValues = false;
    private boolean createRelationships = true;
    private boolean createGeomIndex = false;
    private NameMapping enumNameMapping = null;
    private TransferDescription transferDescription = null;
    private String defaultXyResolution = null;
    private String defaultXyTolerance = null;
    private Connection connection = null;

    @Override
    public void fromIliInit(Config config) {
        domains.clear();
        defaultXyResolution = config.getValue(GeneratorOfgdb.XY_RESOLUTION);
        defaultXyTolerance = config.getValue(GeneratorOfgdb.XY_TOLERANCE);
        createDomains = isTrue(config, FGDB_CREATE_DOMAINS, true);
        createRangeDomains = config.isCreateCreateNumChecks();
        includeInactiveEnumValues = isTrue(config, FGDB_INCLUDE_INACTIVE_ENUM_VALUES, false);
        createRelationships = isTrue(config, FGDB_CREATE_RELATIONSHIP_CLASSES, true);
        createGeomIndex = Config.TRUE.equalsIgnoreCase(config.getValue(Config.CREATE_GEOM_INDEX));
        transferDescription = (TransferDescription) config.getTransientObject(Config.TRANSIENT_MODEL);
        enumNameMapping = null;
        if (transferDescription != null) {
            enumNameMapping = new NameMapping(transferDescription, config);
        }
    }

    private static boolean isTrue(Config config, String key, boolean defaultValue) {
        String value = config.getValue(key);
        if (value == null) {
            return defaultValue;
        }
        return Config.TRUE.equalsIgnoreCase(value);
    }

    @Override
    public void fromIliEnd(Config config) {
        if (!createDomains || domains.isEmpty()) {
            return;
        }
        // A script-only run has no database connection and an offline DDL script is executed on an
        // empty database without any model knowledge. Hand the domain definitions to the DDL
        // generator, which emits CREATE DOMAIN statements before the tables.
        List<String> domainDdl = new ArrayList<String>();
        for (DomainDefinition domain : domains.values()) {
            domainDdl.add(toCreateDomainSql(domain));
        }
        config.setTransientObject(DOMAIN_DDL_CUSTOM_KEY, domainDdl);
        if (connection == null) {
            return;
        }
        try {
            createDomains(resolveBackend(connection));
        } catch (SQLException ex) {
            throw new IllegalStateException("ili2ofgdb: failed to create domains", ex);
        }
    }

    /** Formats a domain as {@code CREATE DOMAIN} statement of the offline DDL script. */
    static String toCreateDomainSql(DomainDefinition domain) {
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE DOMAIN ").append(domain.domainName).append(" AS ").append(domain.fieldType);
        if (domain.kind == DomainKind.RANGE) {
            sql.append(" RANGE ").append(domain.rangeMinInclusive ? "[" : "(");
            if (domain.rangeMinValue != null) {
                sql.append(domain.rangeMinValue);
            }
            sql.append(" .. ");
            if (domain.rangeMaxValue != null) {
                sql.append(domain.rangeMaxValue);
            }
            sql.append(domain.rangeMaxInclusive ? "]" : ")");
            return sql.toString();
        }
        sql.append(" VALUES (");
        String separator = "";
        for (Map.Entry<String, String> entry : domain.codedValues.entrySet()) {
            sql.append(separator)
                    .append(toSqlString(entry.getKey()))
                    .append("=")
                    .append(toSqlString(entry.getValue()));
            separator = ",";
        }
        return sql.append(")").toString();
    }

    private static String toSqlString(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    @Override
    public void postConnect(Connection conn, Config config) {
        connection = conn;
        enforceSingleGeometryPerTable(config);
    }

    @Override
    public void fixupAttribute(DbTable sqlTableDef, DbColumn sqlColDef, AttributeDef iliAttrDef) {
        if (sqlColDef instanceof DbColGeometry) {
            sqlColDef.setCustomValue(GeneratorOfgdb.XY_RESOLUTION, defaultXyResolution);
            sqlColDef.setCustomValue(GeneratorOfgdb.XY_TOLERANCE, defaultXyTolerance);
            if (createGeomIndex) {
                sqlColDef.setCustomValue(GEOM_INDEX_CUSTOM_KEY, Config.TRUE);
            }
        }
        if (!createDomains || sqlTableDef == null || sqlColDef == null || iliAttrDef == null) {
            return;
        }
        // ARRAY_TRAFO_COALESCE values are serialized as JSON text; a domain on the text column
        // would not be compatible.
        if (sqlColDef.getArraySize() != DbColumn.NOT_AN_ARRAY) {
            return;
        }

        Type originalType = iliAttrDef.getDomain();
        Domain rootAliasDomain = resolveRootAliasDomain(originalType);
        Type effectiveType = iliAttrDef.getDomainResolvingAll();
        if (isBooleanType(originalType, effectiveType, rootAliasDomain)) {
            // Scalar-only scope: ARRAY_TRAFO_COALESCE booleans are stored as JSON/VARCHAR.
            if (!(sqlColDef instanceof DbColBoolean)) {
                return;
            }
            String booleanDomainName = resolveBooleanDomainName(rootAliasDomain);
            registerCodedDomain(sqlTableDef, sqlColDef, booleanDomainName, resolveFieldType(sqlColDef),
                    buildBooleanValues());
            return;
        }
        if (effectiveType instanceof AbstractEnumerationType) {
            Element enumOwner = resolveEnumOwner(iliAttrDef, originalType, rootAliasDomain);
            registerCodedDomain(sqlTableDef, sqlColDef, resolveDomainName(iliAttrDef),
                    resolveFieldType(sqlColDef),
                    buildEnumValues(enumOwner, (AbstractEnumerationType) effectiveType));
            return;
        }
        if (createRangeDomains && isNumericRangeColumn(sqlColDef)) {
            RangeBounds rangeBounds = resolveNumericRange(effectiveType);
            if (rangeBounds != null) {
                registerRangeDomain(sqlTableDef, sqlColDef, resolveDomainName(iliAttrDef),
                        resolveFieldType(sqlColDef), rangeBounds);
            }
        }
    }

    @Override
    public void postPostScript(Connection conn, Config config) {
        if (!createRelationships) {
            return;
        }
        try {
            createRelationships(resolveBackend(conn));
        } catch (SQLException ex) {
            throw new IllegalStateException("ili2ofgdb: failed to create relationship classes", ex);
        }
    }

    private OfgdbConnection resolveOfgdbConnection(Connection conn) throws SQLException {
        if (conn == null) {
            return null;
        }
        if (conn instanceof OfgdbConnection) {
            return (OfgdbConnection) conn;
        }
        if (conn.isWrapperFor(OfgdbConnection.class)) {
            return conn.unwrap(OfgdbConnection.class);
        }
        return null;
    }

    private OfgdbFileGdb resolveBackend(Connection conn) throws SQLException {
        OfgdbConnection ofgdbConn = resolveOfgdbConnection(conn);
        if (ofgdbConn == null) {
            throw new SQLException("connection is not a file geodatabase connection");
        }
        return ofgdbConn.getBackend();
    }

    @Override
    public void preConnect(String url, String dbusr, String dbpwd, Config config) {
        enforceSingleGeometryPerTable(config);
    }

    private void enforceSingleGeometryPerTable(Config config) {
        if (config == null) {
            return;
        }
        if (!config.isOneGeomPerTable()) {
            EhiLogger.logAdaption(
                    "ili2ofgdb: forcing oneGeomPerTable=true because OpenFileGDB supports only one geometry column per table");
        }
        config.setOneGeomPerTable(true);
    }

    // ------------------------------------------------------------------
    // Domains
    // ------------------------------------------------------------------

    private void createDomains(OfgdbFileGdb backend) throws SQLException {
        Set<String> existingDomains = new HashSet<String>();
        for (String name : backend.listDomains()) {
            existingDomains.add(normalizeName(name));
        }
        for (DomainDefinition domain : domains.values()) {
            if (existingDomains.contains(normalizeName(domain.domainName))) {
                continue;
            }
            if (domain.kind == DomainKind.RANGE) {
                backend.createRangeDomain(domain.domainName, domain.fieldType,
                        domain.rangeMinValue, domain.rangeMinInclusive,
                        domain.rangeMaxValue, domain.rangeMaxInclusive);
            } else {
                backend.createCodedDomain(domain.domainName, domain.fieldType, domain.codedValues);
            }
            existingDomains.add(normalizeName(domain.domainName));
        }
    }

    private void registerCodedDomain(DbTable sqlTableDef, DbColumn sqlColDef, String domainName,
            String fieldType, Map<String, String> codedValues) {
        String sanitizedDomainName = sanitizeName(domainName);
        // Coded domains of an integer column may only carry integer codes; enum values that do not fit
        // (for example a text code on an enum foreign key column) are skipped, like the former native
        // backend tolerated them.
        if (!codesFitFieldType(fieldType, codedValues)) {
            ch.ehi.basics.logging.EhiLogger.logAdaption("ili2ofgdb: skip domain " + sanitizedDomainName
                    + "; coded values do not fit SQL type " + fieldType);
            return;
        }
        DomainDefinition domainDefinition = domains.get(sanitizedDomainName);
        if (domainDefinition == null) {
            domainDefinition = new DomainDefinition();
            domainDefinition.kind = DomainKind.CODED;
            domainDefinition.domainName = sanitizedDomainName;
            domainDefinition.fieldType = fieldType;
            domainDefinition.codedValues.putAll(codedValues);
            domains.put(sanitizedDomainName, domainDefinition);
        } else {
            assertCompatibleCodedDomain(domainName, domainDefinition, fieldType, codedValues);
        }
        registerDomainReference(sqlTableDef, sqlColDef, sanitizedDomainName);
    }

    private void registerRangeDomain(DbTable sqlTableDef, DbColumn sqlColDef, String domainName,
            String fieldType, RangeBounds rangeBounds) {
        String sanitizedDomainName = sanitizeName(domainName);
        DomainDefinition domainDefinition = domains.get(sanitizedDomainName);
        if (domainDefinition == null) {
            domainDefinition = new DomainDefinition();
            domainDefinition.kind = DomainKind.RANGE;
            domainDefinition.domainName = sanitizedDomainName;
            domainDefinition.fieldType = fieldType;
            domainDefinition.rangeMinValue = rangeBounds.minValue;
            domainDefinition.rangeMinInclusive = rangeBounds.minInclusive;
            domainDefinition.rangeMaxValue = rangeBounds.maxValue;
            domainDefinition.rangeMaxInclusive = rangeBounds.maxInclusive;
            domains.put(sanitizedDomainName, domainDefinition);
        } else {
            assertCompatibleRangeDomain(domainName, domainDefinition, fieldType, rangeBounds);
        }
        registerDomainReference(sqlTableDef, sqlColDef, sanitizedDomainName);
    }

    private void registerDomainReference(DbTable sqlTableDef, DbColumn sqlColDef, String sanitizedDomainName) {
        sqlColDef.setCustomValue(DOMAIN_CUSTOM_KEY, sanitizedDomainName);
    }

    private void assertCompatibleCodedDomain(String rawDomainName, DomainDefinition existing,
            String fieldType, Map<String, String> codedValues) {
        if (existing.kind != DomainKind.CODED) {
            throw new IllegalStateException("ili2ofgdb: domain name collision between range and coded domain: " + rawDomainName);
        }
        if (!existing.fieldType.equals(fieldType)) {
            throw new IllegalStateException("ili2ofgdb: domain field type mismatch for " + rawDomainName);
        }
        if (!existing.codedValues.equals(codedValues)) {
            throw new IllegalStateException("ili2ofgdb: coded domain definition mismatch for " + rawDomainName);
        }
    }

    private void assertCompatibleRangeDomain(String rawDomainName, DomainDefinition existing,
            String fieldType, RangeBounds rangeBounds) {
        if (existing.kind != DomainKind.RANGE) {
            throw new IllegalStateException("ili2ofgdb: domain name collision between coded and range domain: " + rawDomainName);
        }
        if (!existing.fieldType.equals(fieldType)
                || !equal(existing.rangeMinValue, rangeBounds.minValue)
                || existing.rangeMinInclusive != rangeBounds.minInclusive
                || !equal(existing.rangeMaxValue, rangeBounds.maxValue)
                || existing.rangeMaxInclusive != rangeBounds.maxInclusive) {
            throw new IllegalStateException("ili2ofgdb: range domain definition mismatch for " + rawDomainName);
        }
    }

    private static boolean equal(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    /**
     * Coded domains of an integer column may only carry integer codes; enum values that do not fit
     * (for example a text code on an enum foreign key column) are skipped, like the former native
     * backend tolerated them.
     */
    private static boolean codesFitFieldType(String fieldType, Map<String, String> codedValues) {
        if (fieldType == null) {
            return true;
        }
        String type = fieldType.toUpperCase(Locale.ROOT);
        for (String code : codedValues.keySet()) {
            try {
                if ("SMALLINT".equals(type)) {
                    Short.parseShort(code);
                } else if ("INTEGER".equals(type)) {
                    Integer.parseInt(code);
                } else if ("BIGINT".equals(type)) {
                    Long.parseLong(code);
                } else if ("DOUBLE".equals(type)) {
                    Double.parseDouble(code);
                }
            } catch (RuntimeException ex) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Relationships
    // ------------------------------------------------------------------

    private void createRelationships(OfgdbFileGdb backend) throws SQLException {
        if (transferDescription == null) {
            return;
        }
        List<RoleLinkDefinition> roleLinks = collectRoleLinks(backend);
        if (roleLinks.isEmpty()) {
            return;
        }
        Collections.sort(roleLinks, new Comparator<RoleLinkDefinition>() {
            @Override
            public int compare(RoleLinkDefinition lhs, RoleLinkDefinition rhs) {
                int scopedCompare = lhs.associationScopedName.compareTo(rhs.associationScopedName);
                if (scopedCompare != 0) {
                    return scopedCompare;
                }
                int roleCompare = lhs.roleName.compareTo(rhs.roleName);
                if (roleCompare != 0) {
                    return roleCompare;
                }
                int sourceCompare = lhs.sourceTable.compareTo(rhs.sourceTable);
                if (sourceCompare != 0) {
                    return sourceCompare;
                }
                return lhs.sourceFkColumn.compareTo(rhs.sourceFkColumn);
            }
        });
        Set<String> existingRelationships = new HashSet<String>();
        for (String name : backend.listRelationships()) {
            existingRelationships.add(normalizeName(name));
        }
        Set<String> relationshipNames = new HashSet<String>();
        Map<String, RoleLinkDefinition> manyToManyPairs =
                new LinkedHashMap<String, RoleLinkDefinition>();
        for (RoleLinkDefinition roleLink : roleLinks) {
            if ("m:n".equalsIgnoreCase(roleLink.cardinality)) {
                String key = roleLink.associationScopedName + "|" + normalizeName(roleLink.sourceTable);
                RoleLinkDefinition first = manyToManyPairs.get(key);
                if (first == null) {
                    manyToManyPairs.put(key, roleLink);
                } else if (first.peer == null) {
                    first.peer = roleLink;
                }
                continue;
            }
            createSimpleRelationship(backend, roleLink, existingRelationships, relationshipNames);
        }
        for (RoleLinkDefinition pair : manyToManyPairs.values()) {
            createManyToManyRelationship(backend, pair, existingRelationships);
        }
    }

    private void createSimpleRelationship(OfgdbFileGdb backend, RoleLinkDefinition roleLink,
            Set<String> existingRelationships, Set<String> relationshipNames) throws SQLException {
        String originTable = backend.resolveTableName(roleLink.targetTable);
        String destinationTable = backend.resolveTableName(roleLink.sourceTable);
        if (!hasTable(backend, originTable) || !hasTable(backend, destinationTable)) {
            EhiLogger.logAdaption("ili2ofgdb: skip relationship; table missing: "
                    + roleLink.targetTable + " -> " + roleLink.sourceTable);
            return;
        }
        if (!hasColumn(backend, originTable, roleLink.targetPkColumn)
                || !hasColumn(backend, destinationTable, roleLink.sourceFkColumn)) {
            EhiLogger.logAdaption("ili2ofgdb: skip relationship; key column missing: "
                    + originTable + "." + roleLink.targetPkColumn + " / "
                    + destinationTable + "." + roleLink.sourceFkColumn);
            return;
        }
        String relationshipName = uniqueRelationshipName(roleLink, relationshipNames);
        if (existingRelationships.contains(normalizeName(relationshipName))) {
            return;
        }
        backend.createRelationshipClass(relationshipName, originTable, destinationTable,
                roleLink.targetPkColumn, roleLink.sourceFkColumn, "", "",
                roleLink.forwardLabel, roleLink.backwardLabel, roleLink.cardinality, false, null,
                false);
        existingRelationships.add(normalizeName(relationshipName));
    }

    /**
     * Creates the relationship class of an n:m association over the association table that ili2db
     * already created (name of relationship class and mapping table are identical, as expected by
     * the file geodatabase).
     */
    private void createManyToManyRelationship(OfgdbFileGdb backend, RoleLinkDefinition originRole,
            Set<String> existingRelationships) throws SQLException {
        if (originRole.peer == null) {
            EhiLogger.logAdaption("ili2ofgdb: skip many-to-many relationship "
                    + originRole.associationScopedName + "; only one role of the association table "
                    + originRole.sourceTable + " was found");
            return;
        }
        String mappingTable = backend.resolveTableName(originRole.sourceTable);
        String originTable = backend.resolveTableName(originRole.targetTable);
        String destinationTable = backend.resolveTableName(originRole.peer.targetTable);
        if (!hasTable(backend, mappingTable) || !hasTable(backend, originTable)
                || !hasTable(backend, destinationTable)) {
            EhiLogger.logAdaption("ili2ofgdb: skip many-to-many relationship; table missing: "
                    + originTable + " / " + destinationTable + " / " + mappingTable);
            return;
        }
        if (!hasColumn(backend, mappingTable, originRole.sourceFkColumn)
                || !hasColumn(backend, mappingTable, originRole.peer.sourceFkColumn)
                || !hasColumn(backend, originTable, originRole.targetPkColumn)
                || !hasColumn(backend, destinationTable, originRole.peer.targetPkColumn)) {
            EhiLogger.logAdaption("ili2ofgdb: skip many-to-many relationship; key column missing: "
                    + mappingTable + "." + originRole.sourceFkColumn + " / "
                    + mappingTable + "." + originRole.peer.sourceFkColumn);
            return;
        }
        if (existingRelationships.contains(normalizeName(mappingTable))) {
            return;
        }
        backend.createRelationshipClass(mappingTable, originTable, destinationTable,
                originRole.targetPkColumn, originRole.sourceFkColumn,
                originRole.peer.targetPkColumn, originRole.peer.sourceFkColumn,
                originRole.peer.forwardLabel, originRole.peer.backwardLabel, "m:n", false,
                mappingTable, originRole.attributed);
        existingRelationships.add(normalizeName(mappingTable));
    }

    private boolean hasTable(OfgdbFileGdb backend, String tableName) throws SQLException {
        for (String name : backend.listTableNames()) {
            if (name.equalsIgnoreCase(tableName)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasColumn(OfgdbFileGdb backend, String tableName, String columnName) throws SQLException {
        for (OfgdbFileGdb.ColumnInfo column : backend.columns(tableName)) {
            if (column.name.equalsIgnoreCase(columnName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reconstructs the role links from the persisted ili2db mapping tables and the compiled model.
     */
    private List<RoleLinkDefinition> collectRoleLinks(OfgdbFileGdb backend) throws SQLException {
        Map<String, String> inheritanceTrafo = readInheritanceTrafo();
        List<RoleLinkDefinition> result = new ArrayList<RoleLinkDefinition>();
        if (!hasTable(backend, DbNames.ATTRNAME_TAB)) {
            return result;
        }
        Connection conn = connection;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery("SELECT " + DbNames.ATTRNAME_TAB_ILINAME_COL + ","
                    + DbNames.ATTRNAME_TAB_SQLNAME_COL + "," + DbNames.ATTRNAME_TAB_COLOWNER_COL + ","
                    + DbNames.ATTRNAME_TAB_TARGET_COL + " FROM " + DbNames.ATTRNAME_TAB);
            while (rs.next()) {
                String iliName = rs.getString(1);
                String sqlName = rs.getString(2);
                String ownerTable = rs.getString(3);
                String targetTable = rs.getString(4);
                if (iliName == null || sqlName == null || ownerTable == null || targetTable == null) {
                    continue;
                }
                Element element = transferDescription.getElement(iliName);
                if (!(element instanceof RoleDef)) {
                    continue;
                }
                RoleDef role = (RoleDef) element;
                RoleLinkDefinition roleLink = new RoleLinkDefinition();
                roleLink.associationScopedName = parentScopedName(iliName);
                roleLink.roleName = role.getName();
                roleLink.sourceTable = ownerTable;
                roleLink.sourceFkColumn = sqlName;
                roleLink.targetTable = targetTable;
                roleLink.targetPkColumn = DbNames.T_ID_COL;
                roleLink.forwardLabel = role.getOppEnd() != null ? role.getOppEnd().getName() : role.getName();
                roleLink.backwardLabel = role.getName();
                roleLink.embedded = TRAFO_EMBEDDED.equalsIgnoreCase(
                        inheritanceTrafo.get(normalizeName(roleLink.associationScopedName)));
                roleLink.attributed = isAttributed(roleLink.associationScopedName);
                roleLink.cardinality = calcCardinality(role);
                result.add(roleLink);
            }
        } finally {
            if (rs != null) {
                rs.close();
            }
            if (stmt != null) {
                stmt.close();
            }
        }
        return result;
    }

    private Map<String, String> readInheritanceTrafo() throws SQLException {
        Map<String, String> result = new LinkedHashMap<String, String>();
        if (connection == null) {
            return result;
        }
        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = connection.createStatement();
            rs = stmt.executeQuery("SELECT " + DbNames.TRAFO_TAB_ILINAME_COL + ","
                    + DbNames.TRAFO_TAB_TAG_COL + "," + DbNames.TRAFO_TAB_SETTING_COL + " FROM "
                    + DbNames.TRAFO_TAB);
            while (rs.next()) {
                if (TRAFO_INHERITANCE_TAG.equals(rs.getString(2))) {
                    result.put(normalizeName(rs.getString(1)), rs.getString(3));
                }
            }
        } catch (SQLException ex) {
            // mapping tables may not exist (for example when only pre/post scripts are run)
            return result;
        } finally {
            if (rs != null) {
                rs.close();
            }
            if (stmt != null) {
                stmt.close();
            }
        }
        return result;
    }

    private static String parentScopedName(String scopedName) {
        int dot = scopedName.lastIndexOf('.');
        return dot > 0 ? scopedName.substring(0, dot) : scopedName;
    }

    private boolean isAttributed(String associationScopedName) {
        if (transferDescription == null) {
            return false;
        }
        Element element = transferDescription.getElement(associationScopedName);
        if (element instanceof AssociationDef) {
            return ((AssociationDef) element).getAttributes().hasNext();
        }
        return false;
    }

    private String calcCardinality(RoleDef role) {
        long max = role.getCardinality().getMaximum();
        long oppMax = role.getOppEnd() != null ? role.getOppEnd().getCardinality().getMaximum() : 1;
        boolean many = max > 1 || max == Cardinality.UNBOUND;
        boolean oppMany = oppMax > 1 || oppMax == Cardinality.UNBOUND;
        if (!many && !oppMany) {
            return "1:1";
        }
        if (many && oppMany) {
            return "m:n";
        }
        return "1:n";
    }

    private String uniqueRelationshipName(RoleLinkDefinition roleLink, Set<String> usedNames) {
        String base = sanitizeName("_" + roleLink.associationScopedName + "_" + roleLink.roleName);
        String name = base;
        int idx = 2;
        while (usedNames.contains(name)) {
            name = base + "_" + idx;
            idx++;
        }
        usedNames.add(name);
        return name;
    }

    // ------------------------------------------------------------------
    // Model helpers
    // ------------------------------------------------------------------

    private String resolveFieldType(DbColumn sqlColDef) {
        if (sqlColDef instanceof DbColBoolean) {
            return "SMALLINT";
        }
        if (sqlColDef instanceof DbColDecimal) {
            return "DOUBLE";
        }
        if (sqlColDef instanceof DbColId || sqlColDef instanceof DbColNumber) {
            if (sqlColDef instanceof DbColNumber && ((DbColNumber) sqlColDef).getSize() > 10) {
                return "BIGINT";
            }
            return "INTEGER";
        }
        return "STRING";
    }

    private Domain resolveRootAliasDomain(Type originalType) {
        if (!(originalType instanceof TypeAlias)) {
            return null;
        }
        Domain alias = ((TypeAlias) originalType).getAliasing();
        return Ili2cUtility.getRootBaseDomain(alias);
    }

    private String resolveDomainName(AttributeDef iliAttrDef) {
        Type originalType = iliAttrDef.getDomain();
        if (originalType instanceof TypeAlias) {
            Domain alias = ((TypeAlias) originalType).getAliasing();
            alias = Ili2cUtility.getRootBaseDomain(alias);
            return alias.getScopedName(null);
        }
        return iliAttrDef.getContainer().getScopedName(null) + "." + iliAttrDef.getName();
    }

    private Element resolveEnumOwner(AttributeDef iliAttrDef, Type originalType, Domain rootAliasDomain) {
        if (!(originalType instanceof TypeAlias) || rootAliasDomain == null) {
            return iliAttrDef;
        }
        if (rootAliasDomain.getType() instanceof AbstractEnumerationType) {
            return rootAliasDomain;
        }
        return iliAttrDef;
    }

    private boolean isBooleanType(Type originalType, Type effectiveType, Domain rootAliasDomain) {
        if (isBooleanByTypeSignature(effectiveType) || isBooleanByTypeSignature(originalType)) {
            return true;
        }
        if (transferDescription != null && originalType != null
                && Ili2cUtility.isBoolean(transferDescription, originalType)) {
            return true;
        }
        if (rootAliasDomain != null && transferDescription != null
                && rootAliasDomain.getType() != null
                && Ili2cUtility.isBoolean(transferDescription, rootAliasDomain.getType())) {
            return true;
        }
        return rootAliasDomain != null && "INTERLIS.BOOLEAN".equalsIgnoreCase(rootAliasDomain.getScopedName(null));
    }

    private boolean isBooleanByTypeSignature(Type type) {
        return type != null && "BooleanType".equals(type.getClass().getSimpleName());
    }

    private String resolveBooleanDomainName(Domain rootAliasDomain) {
        if (rootAliasDomain != null && !"INTERLIS.BOOLEAN".equalsIgnoreCase(rootAliasDomain.getScopedName(null))) {
            return rootAliasDomain.getScopedName(null);
        }
        return "INTERLIS_BOOLEAN";
    }

    private Map<String, String> buildBooleanValues() {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("0", "false");
        values.put("1", "true");
        return values;
    }

    private boolean isNumericRangeColumn(DbColumn sqlColDef) {
        return sqlColDef instanceof DbColDecimal || sqlColDef instanceof DbColNumber;
    }

    private RangeBounds resolveNumericRange(Type effectiveType) {
        if (!(effectiveType instanceof NumericType) || effectiveType.isAbstract()) {
            return null;
        }
        PrecisionDecimal min = ((NumericType) effectiveType).getMinimum();
        PrecisionDecimal max = ((NumericType) effectiveType).getMaximum();
        if (min == null || max == null) {
            return null;
        }
        RangeBounds rangeBounds = new RangeBounds();
        rangeBounds.minValue = min.toString();
        rangeBounds.maxValue = max.toString();
        return rangeBounds;
    }

    private Map<String, String> buildEnumValues(Element enumOwner, AbstractEnumerationType enumType) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        if (enumNameMapping == null || enumOwner == null) {
            return values;
        }
        EnumValueMap enumValueMap = EnumValueMap.createEnumValueMap(enumOwner, enumNameMapping);
        List<String> codes = new ArrayList<String>(enumValueMap.getXtfCodes());
        Collections.sort(codes, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                int seqCompare = Integer.compare(enumValueMap.mapXtfValueToSeq(o1), enumValueMap.mapXtfValueToSeq(o2));
                if (seqCompare != 0) {
                    return seqCompare;
                }
                return o1.compareTo(o2);
            }
        });

        Map<String, Boolean> inactiveByCode = buildInactiveIndex(enumType);
        for (String code : codes) {
            if (!includeInactiveEnumValues && Boolean.TRUE.equals(inactiveByCode.get(code))) {
                continue;
            }
            String label = enumValueMap.mapXtfValueToDisplayName(code);
            if (label == null) {
                label = code.replace('_', ' ');
            }
            values.put(code, label);
        }
        return values;
    }

    private Map<String, Boolean> buildInactiveIndex(AbstractEnumerationType enumType) {
        Map<String, Boolean> inactiveByCode = new LinkedHashMap<String, Boolean>();
        List<Map.Entry<String, Enumeration.Element>> enumElements = new ArrayList<Map.Entry<String, Enumeration.Element>>();
        if (enumType instanceof EnumTreeValueType) {
            ModelUtilities.buildEnumElementListAll(enumElements, "", enumType.getConsolidatedEnumeration());
        } else {
            ModelUtilities.buildEnumElementList(enumElements, "", enumType.getConsolidatedEnumeration());
        }
        for (Map.Entry<String, Enumeration.Element> enumElement : enumElements) {
            inactiveByCode.put(enumElement.getKey(), isInactive(enumElement.getValue()));
        }
        return inactiveByCode;
    }

    private boolean isInactive(Enumeration.Element element) {
        String inactive = element.getMetaValues().getValue("inactive");
        if (inactive == null) {
            inactive = element.getMetaValues().getValue("ili2db.inactive");
        }
        if (inactive == null) {
            inactive = element.getMetaValues().getValue("INTERLIS.inactive");
        }
        return "true".equalsIgnoreCase(inactive) || "1".equals(inactive);
    }

    private String sanitizeName(String rawName) {
        return rawName.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private String normalizeName(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private enum DomainKind {
        CODED,
        RANGE
    }

    static class DomainDefinition {
        DomainKind kind = DomainKind.CODED;
        String domainName;
        String fieldType;
        final Map<String, String> codedValues = new LinkedHashMap<String, String>();
        String rangeMinValue;
        boolean rangeMinInclusive = true;
        String rangeMaxValue;
        boolean rangeMaxInclusive = true;
    }

    private static class RangeBounds {
        private String minValue;
        private boolean minInclusive = true;
        private String maxValue;
        private boolean maxInclusive = true;
    }

    private static class RoleLinkDefinition {
        private String associationScopedName;
        private String roleName;
        private String sourceTable;
        private String sourceFkColumn;
        private String targetTable;
        private String targetPkColumn;
        private String forwardLabel;
        private String backwardLabel;
        private boolean embedded;
        private boolean attributed;
        private String cardinality;
        /** the second role of an n:m association (same association table) */
        private RoleLinkDefinition peer;
    }
}
