package ch.ehi.ili2ofgdb;

import ch.ehi.ili2db.AbstractTestSetup;
import ch.interlis.iox_j.wkb.Wkb2iox;
import org.junit.Test;

import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class MultiCoord24OfgdbTest extends ch.ehi.ili2db.MultiCoord24Test {
    private static final String FGDBFILENAME = "build/test-ofgdb/MultiCoord24OfgdbTest.gdb";

    @Override
    protected AbstractTestSetup createTestSetup() {
        return new OfgdbTestSetup(FGDBFILENAME);
    }

    @Override
    protected void assertMultiChoord24_classa1_geomattr1(Statement stmt) throws Exception {
        ResultSet rs = stmt.executeQuery("SELECT geomattr1 FROM " + setup.prefixName("classa1"));
        Wkb2iox wkb2iox = new Wkb2iox();

        while (rs.next()) {
            byte[] value = rs.getBytes(1);
            if (value == null) {
                fail("expected binary value in geometry column but got null");
            }
            assertEquals(
                    "MULTICOORD {coord [COORD {C1 2530001.0, C2 1150002.0}, COORD {C1 2740003.0, C2 1260004.0}]}",
                    wkb2iox.read(value).toString());
        }
    }

    @Test
    @Override
    public void importXtf() throws Exception {
        super.importXtf();
    }

    @Test
    @Override
    public void exportXtf() throws Exception {
        super.exportXtf();
    }
}
