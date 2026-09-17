package ch.ehi.ili2ofgdb.jdbc.sql;

public class ComplexSelectStmt extends AbstractSelectStmt {

	private AbstractSelectStmt subselect=null;
	public ComplexSelectStmt(AbstractSelectStmt subselect) {
		this.subselect=subselect;
	}

	public AbstractSelectStmt getSubSelect() {
		return subselect;
	}

}
