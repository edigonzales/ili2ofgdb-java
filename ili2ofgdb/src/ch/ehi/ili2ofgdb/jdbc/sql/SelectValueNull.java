package ch.ehi.ili2ofgdb.jdbc.sql;

public class SelectValueNull extends SelectValue {
	private String colName=null;
	public SelectValueNull(String colName) {
		super();
		this.colName = colName;
	}
	@Override
	public String getColumnName() {
		return colName;
	}

}
