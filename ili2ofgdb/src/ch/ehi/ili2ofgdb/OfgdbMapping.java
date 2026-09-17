package ch.ehi.ili2ofgdb;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ch.ehi.basics.logging.EhiLogger;
import ch.ehi.ili2db.base.AbstractJdbcMapping;
import ch.ehi.ili2db.base.Ili2cUtility;
import ch.ehi.ili2db.fromxtf.EnumValueMap;
import ch.ehi.ili2db.gui.Config;
import ch.ehi.ili2db.mapping.NameMapping;
import ch.ehi.ili2ofgdb.jdbc.OfgdbConnection;
import ch.ehi.openfgdb4j.OpenFgdb;
import ch.ehi.openfgdb4j.OpenFgdbException;
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
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.metamodel.RoleDef;
import ch.interlis.ili2c.metamodel.Type;
import ch.interlis.ili2c.metamodel.TypeAlias;
import ch.interlis.iom_j.itf.ModelUtilities;

public class OfgdbMapping extends AbstractJdbcMapping {
    private final Map<String, DomainDefinition> domains = new LinkedHashMap<String, DomainDefinition>();
    private final Map<String, DomainAssignment> assignments = new LinkedHashMap<String, DomainAssignment>();
    private final Map<String, RoleLinkDefinition> roleLinks = new LinkedHashMap<String, RoleLinkDefinition>();
    private boolean createDomains = true;
    private boolean createRangeDomains = false;
    private boolean includeInactiveEnumValues = false;
    private boolean createRelationships = true;
    private NameMapping enumNameMapping = null;
    private TransferDescription transferDescription = null;
    private String defaultXyResolution = null;
    private String defaultXyTolerance = null;

    @Override
    public void fromIliInit(Config config) {
        domains.clear();
        assignments.clear();
        roleLinks.clear();
        defaultXyResolution = config.getValue(GeneratorOfgdb.XY_RESOLUTION);
        defaultXyTolerance = config.getValue(GeneratorOfgdb.XY_TOLERANCE);
        createDomains = config.isFgdbCreateDomains();
        createRangeDomains = config.isCreateCreateNumChecks();
        includeInactiveEnumValues = config.isFgdbIncludeInactiveEnumValues();
        createRelationships = config.isFgdbCreateRelationshipClasses();
        transferDescription = (TransferDescription) config.getTransientObject(Config.TRANSIENT_MODEL);
        enumNameMapping = null;
        if (transferDescription != null) {
            enumNameMapping = new NameMapping(transferDescription, config);
        }
    }

    @Override
    public void fromIliEnd(Config config) {
    }

    @Override
    public void fixupAttribute(DbTable sqlTableDef, DbColumn sqlColDef, AttributeDef iliAttrDef) {
        if (sqlColDef instanceof DbColGeometry) {
            sqlColDef.setCustomValue(GeneratorOfgdb.XY_RESOLUTION, defaultXyResolution);
            sqlColDef.setCustomValue(GeneratorOfgdb.XY_TOLERANCE, defaultXyTolerance);
        }
        if (!createDomains || sqlTableDef == null || sqlColDef == null || iliAttrDef == null) {
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
            registerCodedDomainAssignment(sqlTableDef, sqlColDef, booleanDomainName, resolveFieldType(sqlColDef), buildBooleanValues());
            return;
        }
        if (effectiveType instanceof AbstractEnumerationType) {
            Element enumOwner = resolveEnumOwner(iliAttrDef, originalType, rootAliasDomain);
            registerCodedDomainAssignment(
                    sqlTableDef,
                    sqlColDef,
                    resolveDomainName(iliAttrDef),
                    resolveFieldType(sqlColDef),
                    buildEnumValues(enumOwner, (AbstractEnumerationType) effectiveType));
            return;
        }
        if (createRangeDomains && isNumericRangeColumn(sqlColDef)) {
            RangeBounds rangeBounds = resolveNumericRange(effectiveType);
            if (rangeBounds != null) {
                registerRangeDomainAssignment(
                        sqlTableDef,
                        sqlColDef,
                        resolveDomainName(iliAttrDef),
                        resolveFieldType(sqlColDef),
                        rangeBounds.minValue,
                        rangeBounds.minInclusive,
                        rangeBounds.maxValue,
                        rangeBounds.maxInclusive);
            }
        }
        return;
    }

    @Override
    public void fixupRoleLink(DbTable dbTable, DbColumn dbColId, AssociationDef roleOwner, RoleDef role,
            DbTableName targetTable, String targetPk, boolean embedded) {
        if (!createRelationships || dbTable == null || dbColId == null || roleOwner == null || role == null || targetTable == null
                || targetPk == null) {
            return;
        }
        RoleLinkDefinition roleLinkDefinition = new RoleLinkDefinition();
        roleLinkDefinition.associationScopedName = roleOwner.getScopedName(null);
        roleLinkDefinition.roleName = role.getName();
        roleLinkDefinition.sourceTable = dbTable.getName().getName();
        roleLinkDefinition.sourceFkColumn = dbColId.getName();
        roleLinkDefinition.targetTable = targetTable.getName();
        roleLinkDefinition.targetPkColumn = targetPk;
        roleLinkDefinition.forwardLabel = role.getOppEnd() != null ? role.getOppEnd().getName() : role.getName();
        roleLinkDefinition.backwardLabel = role.getName();
        roleLinkDefinition.embedded = embedded;
        roleLinkDefinition.attributed = roleOwner.getAttributes().hasNext();
        roleLinkDefinition.cardinality = calcCardinality(role);

        String linkKey = roleLinkDefinition.associationScopedName + "|" + roleLinkDefinition.roleName + "|" + roleLinkDefinition.sourceTable
                + "|" + roleLinkDefinition.sourceFkColumn;
        roleLinks.put(linkKey, roleLinkDefinition);
    }

    @Override
    public void postPostScript(Connection conn, Config config) {
        if ((!createDomains || domains.isEmpty()) && (!createRelationships || roleLinks.isEmpty())) {
            return;
        }
        OpenFgdb api = null;
        long dbHandle = 0L;
        boolean closeHandle = false;
        String handleSource = "jdbc-connection";
        try {
            OfgdbConnection ofgdbConn = resolveOfgdbConnection(conn);
            if (ofgdbConn != null) {
                api = ofgdbConn.getOpenFgdbApi();
                dbHandle = ofgdbConn.getOpenFgdbHandle();
            } else {
                String dbFile = config.getDbfile();
                if (dbFile == null) {
                    EhiLogger.logAdaption("ili2ofgdb: missing --dbfile and no JDBC handle; skip domain/relationship post processing");
                    return;
                }
                File dbPath = new File(dbFile);
                api = new OpenFgdb();
                handleSource = "dbfile";
                closeHandle = true;
                if (dbPath.exists()) {
                    dbHandle = api.open(dbPath.getAbsolutePath());
                } else {
                    dbHandle = api.create(dbPath.getAbsolutePath());
                }
            }
            if (createDomains) {
                createDomains(api, dbHandle);
            }
            if (createRelationships) {
                createRelationships(api, dbHandle);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("ili2ofgdb post processing failed while resolving " + handleSource + " handle", ex);
        } catch (OpenFgdbException ex) {
            throw new IllegalStateException("ili2ofgdb post processing failed using " + handleSource + " handle", ex);
        } finally {
            if (closeHandle && api != null && dbHandle != 0L) {
                try {
                    api.close(dbHandle);
                } catch (OpenFgdbException ex) {
                    EhiLogger.logAdaption("ili2ofgdb: failed to close openfgdb handle: " + ex.getMessage());
                }
            }
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

    @Override
    public void preConnect(String url, String dbusr, String dbpwd, Config config) {
        enforceSingleGeometryPerTable(config);
    }

    @Override
    public void postConnect(Connection conn, Config config) {
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

    private void createDomains(OpenFgdb api, long dbHandle) throws OpenFgdbException {
        Set<String> existingDomains = new HashSet<String>(api.listDomains(dbHandle));
        Set<String> existingAssignments = readExistingDomainAssignments(api, dbHandle);
        Map<String, String> availableTables = readTableNameIndex(api, dbHandle);
        Map<String, Set<String>> tableColumns = new LinkedHashMap<String, Set<String>>();
        for (DomainDefinition domain : domains.values()) {
            if (existingDomains.contains(domain.domainName)) {
                continue;
            }
            if (domain.kind == DomainKind.RANGE) {
                try {
                    api.createRangeDomain(
                            dbHandle,
                            domain.domainName,
                            domain.fieldType,
                            domain.rangeMinValue,
                            domain.rangeMinInclusive,
                            domain.rangeMaxValue,
                            domain.rangeMaxInclusive);
                } catch (OpenFgdbException ex) {
                    if (isKnownBigIntRangeDomainLimitation(domain.fieldType, ex)) {
                        EhiLogger.logAdaption(
                                "ili2ofgdb: skip BIGINT range domain " + domain.domainName
                                        + "; current openfgdb4j/GDAL backend does not support creating this FileGeoDatabase domain on Windows");
                        continue;
                    }
                    throw ex;
                }
            } else {
                api.createCodedDomain(dbHandle, domain.domainName, domain.fieldType);
            }
            existingDomains.add(domain.domainName);
            if (domain.kind == DomainKind.CODED) {
                for (Map.Entry<String, String> codedValue : domain.codedValues.entrySet()) {
                    api.addCodedValue(dbHandle, domain.domainName, codedValue.getKey(), codedValue.getValue());
                }
            }
        }
        for (DomainAssignment assignment : assignments.values()) {
            if (!existingDomains.contains(assignment.domainName)) {
                continue;
            }
            String targetTable = resolveName(availableTables, assignment.tableName);
            if (targetTable == null) {
                EhiLogger.logAdaption("ili2ofgdb: skip domain assignment; table not found: " + assignment.tableName);
                continue;
            }
            Set<String> columns = tableColumns.get(normalizeName(targetTable));
            if (columns == null) {
                columns = readColumnNameIndex(api, dbHandle, targetTable);
                tableColumns.put(normalizeName(targetTable), columns);
            }
            if (!columns.contains(normalizeName(assignment.columnName))) {
                EhiLogger.logAdaption("ili2ofgdb: skip domain assignment; column not found: "
                        + targetTable + "." + assignment.columnName);
                continue;
            }
            String assignmentKey = normalizeAssignmentKey(assignment.domainName, targetTable, assignment.columnName);
            if (existingAssignments.contains(assignmentKey)) {
                continue;
            }
            try {
                api.assignDomainToField(dbHandle, targetTable, assignment.columnName, assignment.domainName);
                existingAssignments.add(assignmentKey);
            } catch (OpenFgdbException ex) {
                if (ex.getErrorCode() == OpenFgdb.OFGDB_ERR_NOT_FOUND) {
                    EhiLogger.logAdaption("ili2ofgdb: skip domain assignment; target no longer available: "
                            + targetTable + "." + assignment.columnName);
                    continue;
                }
                throw ex;
            }
        }
    }

    static boolean isKnownBigIntRangeDomainLimitation(String fieldType, OpenFgdbException ex) {
        if (!"BIGINT".equalsIgnoreCase(fieldType) || ex == null) {
            return false;
        }
        String message = ex.getMessage();
        return message != null && message.contains("Unsupported field type for FileGeoDatabase domain")
                && message.contains("requestedType=BIGINT");
    }

    private void createRelationships(OpenFgdb api, long dbHandle) throws OpenFgdbException {
        Set<String> existingRelationships = new HashSet<String>(api.listRelationships(dbHandle));
        Map<String, String> availableTables = readTableNameIndex(api, dbHandle);
        Map<String, Set<String>> tableColumns = new LinkedHashMap<String, Set<String>>();
        List<RoleLinkDefinition> orderedRoleLinks = new ArrayList<RoleLinkDefinition>(roleLinks.values());
        Collections.sort(orderedRoleLinks, new Comparator<RoleLinkDefinition>() {
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
        Set<String> relationshipNames = new HashSet<String>();
        for (RoleLinkDefinition roleLink : orderedRoleLinks) {
            String originTable = resolveName(availableTables, roleLink.targetTable);
            String destinationTable = resolveName(availableTables, roleLink.sourceTable);
            if (originTable == null || destinationTable == null) {
                EhiLogger.logAdaption("ili2ofgdb: skip relationship; table missing: "
                        + roleLink.targetTable + " -> " + roleLink.sourceTable);
                continue;
            }
            Set<String> originColumns = tableColumns.get(normalizeName(originTable));
            if (originColumns == null) {
                originColumns = readColumnNameIndex(api, dbHandle, originTable);
                tableColumns.put(normalizeName(originTable), originColumns);
            }
            Set<String> destinationColumns = tableColumns.get(normalizeName(destinationTable));
            if (destinationColumns == null) {
                destinationColumns = readColumnNameIndex(api, dbHandle, destinationTable);
                tableColumns.put(normalizeName(destinationTable), destinationColumns);
            }
            if (!originColumns.contains(normalizeName(roleLink.targetPkColumn))
                    || !destinationColumns.contains(normalizeName(roleLink.sourceFkColumn))) {
                EhiLogger.logAdaption("ili2ofgdb: skip relationship; key column missing: "
                        + originTable + "." + roleLink.targetPkColumn + " / "
                        + destinationTable + "." + roleLink.sourceFkColumn);
                continue;
            }
            String relationshipName = uniqueRelationshipName(roleLink, relationshipNames, destinationTable);
            if (existingRelationships.contains(relationshipName)) {
                continue;
            }
            api.createRelationshipClass(
                    dbHandle,
                    relationshipName,
                    originTable,
                    destinationTable,
                    roleLink.targetPkColumn,
                    roleLink.sourceFkColumn,
                    roleLink.forwardLabel,
                    roleLink.backwardLabel,
                    roleLink.cardinality,
                    false,
                    roleLink.attributed);
            existingRelationships.add(relationshipName);
        }
    }

    private Set<String> readExistingDomainAssignments(OpenFgdb api, long dbHandle) {
        Set<String> existingAssignments = new HashSet<String>();
        long tableHandle = 0L;
        long cursorHandle = 0L;
        try {
            tableHandle = api.openTable(dbHandle, "GDB_Items");
            cursorHandle = api.search(tableHandle, "Name,Definition", "");
            Map<String, String> tableDefinitionByName = new LinkedHashMap<String, String>();
            while (true) {
                long rowHandle = api.fetchRow(cursorHandle);
                if (rowHandle == 0L) {
                    break;
                }
                try {
                    String itemName = api.rowGetString(rowHandle, "Name");
                    if (itemName == null) {
                        continue;
                    }
                    String definition = api.rowGetString(rowHandle, "Definition");
                    tableDefinitionByName.put(normalizeName(itemName), definition);
                } finally {
                    api.closeRow(rowHandle);
                }
            }
            for (DomainAssignment assignment : assignments.values()) {
                String definition = tableDefinitionByName.get(normalizeName(assignment.tableName));
                if (!hasDomainAssignment(definition, assignment.columnName, assignment.domainName)) {
                    continue;
                }
                existingAssignments.add(normalizeAssignmentKey(assignment.domainName, assignment.tableName, assignment.columnName));
            }
        } catch (OpenFgdbException ex) {
            EhiLogger.logAdaption("ili2ofgdb: unable to read existing domain assignments: " + ex.getMessage());
        } finally {
            if (cursorHandle != 0L) {
                try {
                    api.closeCursor(cursorHandle);
                } catch (OpenFgdbException ex) {
                    EhiLogger.logAdaption("ili2ofgdb: failed to close assignment cursor: " + ex.getMessage());
                }
            }
            if (tableHandle != 0L) {
                try {
                    api.closeTable(dbHandle, tableHandle);
                } catch (OpenFgdbException ex) {
                    EhiLogger.logAdaption("ili2ofgdb: failed to close assignment table: " + ex.getMessage());
                }
            }
        }
        return existingAssignments;
    }

    private boolean hasDomainAssignment(String tableDefinition, String columnName, String domainName) {
        if (tableDefinition == null || columnName == null || domainName == null) {
            return false;
        }
        String quotedColumn = Pattern.quote(columnName);
        Pattern fieldPattern = Pattern.compile(
                "(?is)<GPFieldInfoEx\\b[^>]*>.*?<Name>\\s*" + quotedColumn + "\\s*</Name>.*?</GPFieldInfoEx>");
        Matcher fieldMatcher = fieldPattern.matcher(tableDefinition);
        while (fieldMatcher.find()) {
            String fieldBlock = fieldMatcher.group();
            Pattern domainPattern = Pattern.compile("(?is)<DomainName>\\s*" + Pattern.quote(domainName) + "\\s*</DomainName>");
            if (domainPattern.matcher(fieldBlock).find()) {
                return true;
            }
        }
        return false;
    }

    private String normalizeAssignmentKey(String domainName, String tableName, String columnName) {
        return normalizeName(domainName) + "|" + normalizeName(tableName) + "|" + normalizeName(columnName);
    }

    private Map<String, String> readTableNameIndex(OpenFgdb api, long dbHandle) throws OpenFgdbException {
        Map<String, String> byNormalizedName = new LinkedHashMap<String, String>();
        for (String tableName : api.listTableNames(dbHandle)) {
            byNormalizedName.put(normalizeName(tableName), tableName);
        }
        return byNormalizedName;
    }

    private Set<String> readColumnNameIndex(OpenFgdb api, long dbHandle, String tableName) throws OpenFgdbException {
        Set<String> columns = new HashSet<String>();
        long tableHandle = 0L;
        try {
            tableHandle = api.openTable(dbHandle, tableName);
            for (String column : api.getFieldNames(tableHandle)) {
                columns.add(normalizeName(column));
            }
            return columns;
        } finally {
            if (tableHandle != 0L) {
                try {
                    api.closeTable(dbHandle, tableHandle);
                } catch (OpenFgdbException ex) {
                    EhiLogger.logAdaption("ili2ofgdb: failed to close table handle for " + tableName + ": " + ex.getMessage());
                }
            }
        }
    }

    private String resolveName(Map<String, String> availableByNormalizedName, String requestedName) {
        if (requestedName == null) {
            return null;
        }
        return availableByNormalizedName.get(normalizeName(requestedName));
    }

    private String normalizeName(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

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
        if (sqlColDef instanceof DbColVarchar) {
            return "STRING";
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
        if (transferDescription != null && originalType != null && Ili2cUtility.isBoolean(transferDescription, originalType)) {
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

    private void registerCodedDomainAssignment(DbTable sqlTableDef, DbColumn sqlColDef, String domainName, String fieldType,
            Map<String, String> codedValues) {
        String sanitizedDomainName = sanitizeName(domainName);

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

    private void registerRangeDomainAssignment(
            DbTable sqlTableDef,
            DbColumn sqlColDef,
            String domainName,
            String fieldType,
            String minValue,
            boolean minInclusive,
            String maxValue,
            boolean maxInclusive) {
        String sanitizedDomainName = sanitizeName(domainName);

        DomainDefinition domainDefinition = domains.get(sanitizedDomainName);
        if (domainDefinition == null) {
            domainDefinition = new DomainDefinition();
            domainDefinition.kind = DomainKind.RANGE;
            domainDefinition.domainName = sanitizedDomainName;
            domainDefinition.fieldType = fieldType;
            domainDefinition.rangeMinValue = minValue;
            domainDefinition.rangeMinInclusive = minInclusive;
            domainDefinition.rangeMaxValue = maxValue;
            domainDefinition.rangeMaxInclusive = maxInclusive;
            domains.put(sanitizedDomainName, domainDefinition);
        } else {
            assertCompatibleRangeDomain(domainName, domainDefinition, fieldType, minValue, minInclusive, maxValue, maxInclusive);
        }
        registerDomainReference(sqlTableDef, sqlColDef, sanitizedDomainName);
    }

    private void registerDomainReference(DbTable sqlTableDef, DbColumn sqlColDef, String sanitizedDomainName) {
        DomainAssignment assignment = new DomainAssignment();
        assignment.tableName = sqlTableDef.getName().getName();
        assignment.columnName = sqlColDef.getName();
        assignment.domainName = sanitizedDomainName;
        assignments.put(assignment.tableName + "." + assignment.columnName, assignment);
    }

    private void assertCompatibleCodedDomain(
            String rawDomainName,
            DomainDefinition existing,
            String fieldType,
            Map<String, String> codedValues) {
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

    private void assertCompatibleRangeDomain(
            String rawDomainName,
            DomainDefinition existing,
            String fieldType,
            String minValue,
            boolean minInclusive,
            String maxValue,
            boolean maxInclusive) {
        if (existing.kind != DomainKind.RANGE) {
            throw new IllegalStateException("ili2ofgdb: domain name collision between coded and range domain: " + rawDomainName);
        }
        if (!existing.fieldType.equals(fieldType)
                || !existing.rangeMinValue.equals(minValue)
                || existing.rangeMinInclusive != minInclusive
                || !existing.rangeMaxValue.equals(maxValue)
                || existing.rangeMaxInclusive != maxInclusive) {
            throw new IllegalStateException("ili2ofgdb: range domain definition mismatch for " + rawDomainName);
        }
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

    private String sanitizeName(String rawName) {
        return rawName.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private String uniqueRelationshipName(RoleLinkDefinition roleLink, Set<String> usedNames, String destinationTableName) {
        String base = sanitizeName("_" + roleLink.associationScopedName + "_" + roleLink.roleName);
        if (roleLink.attributed && "m:n".equalsIgnoreCase(roleLink.cardinality)
                && destinationTableName != null && destinationTableName.trim().length() > 0) {
            // GDAL/OpenFileGDB expects the many-to-many mapping table name
            // to match the relationship class name.
            base = sanitizeName(destinationTableName);
        }
        String name = base;
        int idx = 2;
        while (usedNames.contains(name)) {
            name = base + "_" + idx;
            idx++;
        }
        usedNames.add(name);
        return name;
    }

    private enum DomainKind {
        CODED,
        RANGE
    }

    private static class DomainDefinition {
        private DomainKind kind = DomainKind.CODED;
        private String domainName;
        private String fieldType;
        private final Map<String, String> codedValues = new LinkedHashMap<String, String>();
        private String rangeMinValue;
        private boolean rangeMinInclusive = true;
        private String rangeMaxValue;
        private boolean rangeMaxInclusive = true;
    }

    private static class RangeBounds {
        private String minValue;
        private boolean minInclusive = true;
        private String maxValue;
        private boolean maxInclusive = true;
    }

    private static class DomainAssignment {
        private String tableName;
        private String columnName;
        private String domainName;
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
    }
}
