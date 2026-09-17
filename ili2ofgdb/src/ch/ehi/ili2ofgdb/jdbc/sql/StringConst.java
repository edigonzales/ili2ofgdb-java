package ch.ehi.ili2ofgdb.jdbc.sql;

public class StringConst extends Value {
	private String value;
	public StringConst(String v){
		this.value=v;
	}
	public String getValue() {
		return value;
	}
}
