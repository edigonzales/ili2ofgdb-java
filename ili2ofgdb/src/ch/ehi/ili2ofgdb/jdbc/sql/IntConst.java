package ch.ehi.ili2ofgdb.jdbc.sql;

public class IntConst extends Value {
	private int value;
	public IntConst(int v){
		this.value=v;
	}
	public int getValue() {
		return value;
	}
}
