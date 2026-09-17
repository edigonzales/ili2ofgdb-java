package ch.ehi.ili2ofgdb.jdbc.sql;

public abstract class SelectValue {

	public abstract String getColumnName();

	@Override
	public String toString() {
		return "SelectValue [getColumnName()=" + getColumnName() + "]";
	}

}
