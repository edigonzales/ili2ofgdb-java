package ch.ehi.ili2ofgdb;


import ch.ehi.ili2db.AbstractTestSetup;

import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UpdateOfgdbTest extends ch.ehi.ili2db.UpdateTest {
    private static final String FGDBFILENAME = "build/test-ofgdb/UpdateOfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }

    @Override
    protected void assertDatabaseContainsClassA2(Statement stmt, String oid, String attrA2, String classA1Oid) throws Exception {
        ResultSet rs = stmt.executeQuery("SELECT attra2, classa1 FROM " + setup.prefixName("classa2")
                + " WHERE t_ili_tid='" + oid + "'");
        assertTrue(String.format("Object with oid <%s> does not exist.", oid), rs.next());
        assertEquals(attrA2, rs.getObject(1));
        long classA1Sqlid = ((Number) rs.getObject(2)).longValue();
        assertFalse(rs.next());
        rs.close();

        rs = stmt.executeQuery("SELECT t_ili_tid FROM " + setup.prefixName("classa1")
                + " WHERE t_id=" + classA1Sqlid);
        assertTrue(String.format("Referenced object with t_id <%s> does not exist.", Long.valueOf(classA1Sqlid)), rs.next());
        assertEquals(classA1Oid, rs.getObject(1));
        assertFalse(rs.next());
        rs.close();
    }
}
