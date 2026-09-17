package ch.ehi.ili2ofgdb.jdbc.sql;

public class ColRef extends Value {
	private String colName=null;
	public ColRef(String colName) {
		this.colName=colName;
	}

	public String getName() {
		return colName;
	}

}
