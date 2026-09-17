package ch.ehi.ili2ofgdb.jdbc.sql;

public class SelectValueString extends SelectValue {
	private String colName=null;
	private String literalValue=null;
	public SelectValueString(String colName, String literalValue) {
		super();
		this.colName = colName;
		this.literalValue = literalValue;
	}
	@Override
	public String getColumnName() {
		return colName;
	}
	public String getLiteralValue() {
		return literalValue;
	}

}
