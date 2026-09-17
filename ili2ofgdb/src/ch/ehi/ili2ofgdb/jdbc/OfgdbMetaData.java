package ch.ehi.ili2ofgdb.jdbc;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.RowIdLifetime;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class OfgdbMetaData implements DatabaseMetaData {
	private OfgdbConnection conn=null;
	public OfgdbMetaData(OfgdbConnection conn1){
		conn=conn1;
	}
	@Override
	public boolean isWrapperFor(Class<?> arg0) throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public <T> T unwrap(Class<T> arg0) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean allProceduresAreCallable() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean allTablesAreSelectable() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean autoCommitFailureClosesAllResultSets() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean dataDefinitionCausesTransactionCommit() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean dataDefinitionIgnoredInTransactions() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean deletesAreDetected(int type) throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean doesMaxRowSizeIncludeBlobs() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public ResultSet getAttributes(String catalog, String schemaPattern,
			String typeNamePattern, String attributeNamePattern)
			throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ResultSet getBestRowIdentifier(String catalog, String schema,
			String table, int scope, boolean nullable) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getCatalogSeparator() throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getCatalogTerm() throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ResultSet getCatalogs() throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ResultSet getClientInfoProperties() throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ResultSet getColumnPrivileges(String catalog, String schema,
			String table, String columnNamePattern) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ResultSet getColumns(String catalog, String schemaPattern,
			String tableNamePattern, String columnNamePattern)
			throws SQLException {
		List<Map<String,Object>> rows=new ArrayList<Map<String,Object>>();
		List<String> columns=new ArrayList<String>();
		columns.add("TABLE_CAT");
		columns.add("TABLE_SCHEM");
		columns.add("TABLE_NAME");
		columns.add("COLUMN_NAME");
		columns.add("DATA_TYPE");
		columns.add("TYPE_NAME");
		columns.add("COLUMN_SIZE");
		columns.add("ORDINAL_POSITION");
		columns.add("NULLABLE");
		columns.add("IS_NULLABLE");
		for(String tableName:conn.getKnownTableNames()){
			if(!matchesPattern(tableName, tableNamePattern)){
				continue;
			}
			Map<String,ColumnDefinition> columnDefinitions=readColumnDefinitions(tableName);
			long tableHandle=0L;
			try{
				tableHandle=conn.getApi().openTable(conn.getDbHandle(), tableName);
				List<String> fieldNames=conn.getApi().getFieldNames(tableHandle);
				for(int i=0;i<fieldNames.size();i++){
					String fieldName=fieldNames.get(i);
					if(!matchesPattern(fieldName, columnNamePattern)){
						continue;
					}
					Map<String,Object> row=new HashMap<String,Object>();
					row.put("TABLE_CAT", catalog);
					row.put("TABLE_SCHEM", schemaPattern);
					row.put("TABLE_NAME", tableName);
					row.put("COLUMN_NAME", fieldName);
					ColumnDefinition columnDefinition=columnDefinitions.get(fieldName.toLowerCase(Locale.ROOT));
					if(columnDefinition==null){
						columnDefinition=ColumnDefinition.fallback(fieldName);
					}
					row.put("DATA_TYPE", Integer.valueOf(columnDefinition.dataType));
					row.put("TYPE_NAME", columnDefinition.typeName);
					row.put("COLUMN_SIZE", columnDefinition.columnSize);
					row.put("ORDINAL_POSITION", Integer.valueOf(i+1));
					if(columnDefinition.nullable==null){
						row.put("NULLABLE", Integer.valueOf(columnNullableUnknown));
						row.put("IS_NULLABLE", "");
					}else{
						boolean nullable=columnDefinition.nullable.booleanValue();
						row.put("NULLABLE", Integer.valueOf(nullable ? columnNullable : columnNoNulls));
						row.put("IS_NULLABLE", nullable ? "YES" : "NO");
					}
					rows.add(row);
				}
			}catch(ch.ehi.openfgdb4j.OpenFgdbException e){
				throw new SQLException("failed to read columns metadata",e);
			}finally{
				if(tableHandle!=0L){
					try {
						conn.getApi().closeTable(conn.getDbHandle(), tableHandle);
					} catch (ch.ehi.openfgdb4j.OpenFgdbException ignore) {
					}
				}
			}
		}
		return new OfgdbResultSet(rows,columns);
	}

	private Map<String,ColumnDefinition> readColumnDefinitions(String tableName) throws SQLException {
		Map<String,ColumnDefinition> ret=new HashMap<String,ColumnDefinition>();
		long itemsTable=0L;
		long cursor=0L;
		try{
			itemsTable=conn.getApi().openTable(conn.getDbHandle(), "GDB_Items");
			List<String> fieldNames=conn.getApi().getFieldNames(itemsTable);
			String nameColumn=findColumnIgnoreCase(fieldNames, "Name");
			String definitionColumn=findColumnIgnoreCase(fieldNames, "Definition");
			if(nameColumn==null || definitionColumn==null){
				return ret;
			}
			cursor=conn.getApi().search(itemsTable, nameColumn+","+definitionColumn, "");
			while(true){
				long rowHandle=conn.getApi().fetchRow(cursor);
				if(rowHandle==0L){
					return ret;
				}
				try{
					String rowName=conn.getApi().rowGetString(rowHandle, nameColumn);
					if(rowName==null || !rowName.equalsIgnoreCase(tableName)){
						continue;
					}
					String definitionXml=conn.getApi().rowGetString(rowHandle, definitionColumn);
					return parseColumnDefinitions(definitionXml);
				}finally{
					conn.getApi().closeRow(rowHandle);
				}
			}
		}catch(ch.ehi.openfgdb4j.OpenFgdbException e){
			return ret;
		}finally{
			if(cursor!=0L){
				try {
					conn.getApi().closeCursor(cursor);
				} catch (ch.ehi.openfgdb4j.OpenFgdbException ignore) {
				}
			}
			if(itemsTable!=0L){
				try {
					conn.getApi().closeTable(conn.getDbHandle(), itemsTable);
				} catch (ch.ehi.openfgdb4j.OpenFgdbException ignore) {
				}
			}
		}
	}

	private Map<String,ColumnDefinition> parseColumnDefinitions(String definitionXml) {
		Map<String,ColumnDefinition> ret=new HashMap<String,ColumnDefinition>();
		if(definitionXml==null || definitionXml.trim().length()==0){
			return ret;
		}
		try{
			Document document = DocumentBuilderFactory.newInstance()
					.newDocumentBuilder()
					.parse(new InputSource(new StringReader(definitionXml)));
			NodeList allNodes=document.getElementsByTagName("*");
			for(int i=0;i<allNodes.getLength();i++){
				Node node=allNodes.item(i);
				if(!(node instanceof Element) || !nodeNameMatches(node, "GPFieldInfoEx")){
					continue;
				}
				Element fieldNode=(Element)node;
				String fieldName=childTagText(fieldNode, "Name");
				if(fieldName==null || fieldName.trim().length()==0){
					continue;
				}
				ColumnDefinition definition=ColumnDefinition.fromEsriField(
						childTagText(fieldNode, "FieldType"),
						parseInteger(childTagText(fieldNode, "Length")),
						parseBoolean(childTagText(fieldNode, "IsNullable")));
				ret.put(fieldName.toLowerCase(Locale.ROOT), definition);
			}
		}catch(Exception ex){
			return ret;
		}
		return ret;
	}

	private static Integer parseInteger(String value) {
		if(value==null || value.trim().length()==0){
			return null;
		}
		try{
			return Integer.valueOf(Integer.parseInt(value.trim()));
		}catch(NumberFormatException ex){
			return null;
		}
	}

	private static Boolean parseBoolean(String value) {
		if(value==null || value.trim().length()==0){
			return null;
		}
		String normalized=value.trim();
		if("true".equalsIgnoreCase(normalized) || "1".equals(normalized)){
			return Boolean.TRUE;
		}
		if("false".equalsIgnoreCase(normalized) || "0".equals(normalized)){
			return Boolean.FALSE;
		}
		return null;
	}

	private static String childTagText(Element parent, String tagName) {
		NodeList descendants = parent.getElementsByTagName("*");
		for (int i = 0; i < descendants.getLength(); i++) {
			Node child = descendants.item(i);
			if (child instanceof Element && nodeNameMatches(child, tagName)) {
				String text = child.getTextContent();
				return text != null ? text.trim() : null;
			}
		}
		NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (child instanceof Element && nodeNameMatches(child, tagName)) {
				String text = child.getTextContent();
				return text != null ? text.trim() : null;
			}
		}
		return null;
	}

	private static boolean nodeNameMatches(Node node, String localTagName) {
		if (node == null || localTagName == null) {
			return false;
		}
		String name = node.getNodeName();
		return name != null && name.endsWith(localTagName);
	}

	private static String findColumnIgnoreCase(List<String> columns, String expected) {
		for (String candidate : columns) {
			if (candidate.equalsIgnoreCase(expected)) {
				return candidate;
			}
		}
		return null;
	}

	private static class ColumnDefinition {
		private final int dataType;
		private final String typeName;
		private final Integer columnSize;
		private final Boolean nullable;

		private ColumnDefinition(int dataType, String typeName, Integer columnSize, Boolean nullable) {
			this.dataType=dataType;
			this.typeName=typeName;
			this.columnSize=columnSize;
			this.nullable=nullable;
		}

		private static ColumnDefinition fallback(String fieldName) {
			if("OBJECTID".equalsIgnoreCase(fieldName) || "OID".equalsIgnoreCase(fieldName)
					|| "T_ID".equalsIgnoreCase(fieldName) || "T_Id".equalsIgnoreCase(fieldName)){
				return new ColumnDefinition(Types.INTEGER, "INTEGER", Integer.valueOf(10), Boolean.FALSE);
			}
			return new ColumnDefinition(Types.VARCHAR, "VARCHAR", Integer.valueOf(4096), Boolean.TRUE);
		}

		private static ColumnDefinition fromEsriField(String fieldType, Integer length, Boolean nullable) {
			String normalized=fieldType!=null ? fieldType.trim().toLowerCase(Locale.ROOT) : "";
			if("esrifieldtypeoid".equals(normalized) || "esrifieldtypeinteger".equals(normalized)){
				return new ColumnDefinition(Types.INTEGER, "INTEGER", Integer.valueOf(10), nullable);
			}
			if("esrifieldtypesmallinteger".equals(normalized)){
				return new ColumnDefinition(Types.SMALLINT, "SMALLINT", Integer.valueOf(5), nullable);
			}
			if("esrifieldtypebiginteger".equals(normalized)){
				return new ColumnDefinition(Types.BIGINT, "BIGINT", Integer.valueOf(19), nullable);
			}
			if("esrifieldtypedouble".equals(normalized)){
				return new ColumnDefinition(Types.DOUBLE, "DOUBLE", Integer.valueOf(15), nullable);
			}
			if("esrifieldtypesingle".equals(normalized)){
				return new ColumnDefinition(Types.REAL, "REAL", Integer.valueOf(7), nullable);
			}
			if("esrifieldtypedate".equals(normalized) || "esrifieldtypetimestampoffset".equals(normalized)){
				return new ColumnDefinition(Types.TIMESTAMP, "TIMESTAMP", Integer.valueOf(26), nullable);
			}
			if("esrifieldtypeblob".equals(normalized)){
				return new ColumnDefinition(Types.BLOB, "BLOB", Integer.valueOf(Integer.MAX_VALUE), nullable);
			}
			if("esrifieldtypegeometry".equals(normalized)){
				return new ColumnDefinition(Types.VARBINARY, "GEOMETRY", Integer.valueOf(Integer.MAX_VALUE), nullable);
			}
			Integer stringLength=length!=null && length.intValue()>0 ? length : Integer.valueOf(4096);
			return new ColumnDefinition(Types.VARCHAR, "VARCHAR", stringLength, nullable);
		}
	}

	@Override
	public Connection getConnection() throws SQLException {
		return conn;
	}

	@Override
	public ResultSet getCrossReference(String parentCatalog,
			String parentSchema, String parentTable, String foreignCatalog,
			String foreignSchema, String foreignTable) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getDatabaseMajorVersion() throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getDatabaseMinorVersion() throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String getDatabaseProductName() throws SQLException {
		return "ESRI FileGDB API";
	}

	@Override
	public String getDatabaseProductVersion() throws SQLException {
		return "1.5";
	}

	@Override
	public int getDefaultTransactionIsolation() throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getDriverMajorVersion() {
		return 0;
	}

	@Override
	public int getDriverMinorVersion() {
		return 1;
	}

	@Override
	public String getDriverName() throws SQLException {
		return "openfgdb4j";
	}

	@Override
	public String getDriverVersion() throws SQLException {
		return String.valueOf(getDriverMajorVersion()) + "." + String.valueOf(getDriverMinorVersion());
	}

	@Override
	public ResultSet getExportedKeys(String catalog, String schema, String table)
			throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getExtraNameCharacters() throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ResultSet getFunctionColumns(String catalog, String schemaPattern,
			String functionNamePattern, String columnNamePattern)
			throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ResultSet getFunctions(String catalog, String schemaPattern,
			String functionNamePattern) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getIdentifierQuoteString() throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ResultSet getImportedKeys(String catalog, String schema, String table)
			throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ResultSet getIndexInfo(String catalog, String schema, String table,
			boolean unique, boolean approximate) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getJDBCMajorVersion() throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getJDBCMinorVersion() throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMaxBinaryLiteralLength() throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMaxCatalogNameLength() throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMaxCharLiteralLength() throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMaxColumnNameLength() throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMaxColumnsInGroupBy() throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMaxColumnsInIndex() throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMaxColumnsInOrderBy() throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMaxColumnsInSelect() throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMaxColumnsInTable() throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMaxConnections() throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMaxCursorNameLength() throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMaxIndexLength() throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMaxProcedureNameLength() throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMaxRowSize() throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMaxSchemaNameLength() throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMaxStatementLength() throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMaxStatements() throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMaxTableNameLength() throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMaxTablesInSelect() throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMaxUserNameLength() throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String getNumericFunctions() throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ResultSet getPrimaryKeys(String catalog, String schema, String table)
			throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ResultSet getProcedureColumns(String catalog, String schemaPattern,
			String procedureNamePattern, String columnNamePattern)
			throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getProcedureTerm() throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ResultSet getProcedures(String catalog, String schemaPattern,
			String procedureNamePattern) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getResultSetHoldability() throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public RowIdLifetime getRowIdLifetime() throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getSQLKeywords() throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getSQLStateType() throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String getSchemaTerm() throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ResultSet getSchemas() throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ResultSet getSchemas(String catalog, String schemaPattern)
			throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getSearchStringEscape() throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getStringFunctions() throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ResultSet getSuperTables(String catalog, String schemaPattern,
			String tableNamePattern) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ResultSet getSuperTypes(String catalog, String schemaPattern,
			String typeNamePattern) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getSystemFunctions() throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ResultSet getTablePrivileges(String catalog, String schemaPattern,
			String tableNamePattern) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ResultSet getTableTypes() throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ResultSet getTables(String catalog, String schemaPattern,
			String tableNamePattern, String[] types) throws SQLException {
		List<Map<String,Object>> rows=new ArrayList<Map<String,Object>>();
		for(String tableName:conn.getKnownTableNames()){
			if(!matchesPattern(tableName, tableNamePattern)){
				continue;
			}
			Map<String,Object> row=new HashMap<String,Object>();
			row.put("TABLE_CAT", catalog);
			row.put("TABLE_SCHEM", schemaPattern);
			row.put("TABLE_NAME", tableName);
			row.put("TABLE_TYPE", "TABLE");
			rows.add(row);
		}
		List<String> columns=new ArrayList<String>();
		columns.add("TABLE_CAT");
		columns.add("TABLE_SCHEM");
		columns.add("TABLE_NAME");
		columns.add("TABLE_TYPE");
		return new OfgdbResultSet(rows,columns);
	}

	private static boolean matchesPattern(String value,String jdbcPattern){
		if(jdbcPattern==null || "%".equals(jdbcPattern)){
			return true;
		}
		String normalizedPattern=jdbcPattern.replace("%", ".*").replace("_", ".");
		return value!=null && value.matches("(?i)"+normalizedPattern);
	}

	@Override
	public String getTimeDateFunctions() throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ResultSet getTypeInfo() throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ResultSet getUDTs(String catalog, String schemaPattern,
			String typeNamePattern, int[] types) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getURL() throws SQLException {
		return conn.getUrl();
	}

	@Override
	public String getUserName() throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ResultSet getVersionColumns(String catalog, String schema,
			String table) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean insertsAreDetected(int type) throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCatalogAtStart() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isReadOnly() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean locatorsUpdateCopy() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean nullPlusNonNullIsNull() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean nullsAreSortedAtEnd() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean nullsAreSortedAtStart() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean nullsAreSortedHigh() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean nullsAreSortedLow() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean othersDeletesAreVisible(int type) throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean othersInsertsAreVisible(int type) throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean othersUpdatesAreVisible(int type) throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean ownDeletesAreVisible(int type) throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean ownInsertsAreVisible(int type) throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean ownUpdatesAreVisible(int type) throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean storesLowerCaseIdentifiers() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean storesLowerCaseQuotedIdentifiers() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean storesMixedCaseIdentifiers() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean storesMixedCaseQuotedIdentifiers() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean storesUpperCaseIdentifiers() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean storesUpperCaseQuotedIdentifiers() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsANSI92EntryLevelSQL() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsANSI92FullSQL() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsANSI92IntermediateSQL() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsAlterTableWithAddColumn() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsAlterTableWithDropColumn() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsBatchUpdates() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsCatalogsInDataManipulation() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsCatalogsInIndexDefinitions() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsCatalogsInPrivilegeDefinitions() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsCatalogsInProcedureCalls() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsCatalogsInTableDefinitions() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsColumnAliasing() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsConvert() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsConvert(int fromType, int toType)
			throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsCoreSQLGrammar() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsCorrelatedSubqueries() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsDataDefinitionAndDataManipulationTransactions()
			throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsDataManipulationTransactionsOnly()
			throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsDifferentTableCorrelationNames() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsExpressionsInOrderBy() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsExtendedSQLGrammar() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsFullOuterJoins() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsGetGeneratedKeys() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsGroupBy() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsGroupByBeyondSelect() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsGroupByUnrelated() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsIntegrityEnhancementFacility() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsLikeEscapeClause() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsLimitedOuterJoins() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsMinimumSQLGrammar() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsMixedCaseIdentifiers() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsMixedCaseQuotedIdentifiers() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsMultipleOpenResults() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsMultipleResultSets() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsMultipleTransactions() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsNamedParameters() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsNonNullableColumns() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsOpenCursorsAcrossCommit() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsOpenCursorsAcrossRollback() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsOpenStatementsAcrossCommit() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsOpenStatementsAcrossRollback() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsOrderByUnrelated() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsOuterJoins() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsPositionedDelete() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsPositionedUpdate() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsResultSetConcurrency(int type, int concurrency)
			throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsResultSetHoldability(int holdability)
			throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsResultSetType(int type) throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsSavepoints() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsSchemasInDataManipulation() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsSchemasInIndexDefinitions() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsSchemasInPrivilegeDefinitions() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsSchemasInProcedureCalls() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsSchemasInTableDefinitions() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsSelectForUpdate() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsStatementPooling() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsStoredFunctionsUsingCallSyntax() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsStoredProcedures() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsSubqueriesInComparisons() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsSubqueriesInExists() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsSubqueriesInIns() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsSubqueriesInQuantifieds() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsTableCorrelationNames() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsTransactionIsolationLevel(int level)
			throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsTransactions() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsUnion() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsUnionAll() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean updatesAreDetected(int type) throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean usesLocalFilePerTable() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean usesLocalFiles() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}
	public boolean generatedKeyAlwaysReturned() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}
	public ResultSet getPseudoColumns(String arg0, String arg1, String arg2,
			String arg3) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

}
