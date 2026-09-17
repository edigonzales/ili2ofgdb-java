package ch.ehi.ili2ofgdb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import ch.ehi.ili2db.base.Ili2db;
import ch.ehi.ili2db.gui.Config;

/**
 * Behaviour tests for the file geodatabase specific post processing of {@link OfgdbMapping}:
 * relationship classes and the domain switch, verified through the catalog of the written
 * geodatabase.
 */
public class OfgdbMappingPostScriptTest {

    private static final String TEST_DB = "build/test-ofgdb/OfgdbMappingPostScriptTest.gdb";
    private static final String RELATIONSHIP_MODEL =
            "ili2ofgdb/test/data/mapping/OfgdbRelationshipTest.ili";
    private static final String DATATYPES_MODEL = "test/data/Datatypes23/Datatypes23.ili";

    @Test
    public void createsRelationshipClassesFromModel() throws Exception {
        OfgdbTestSetup setup = new OfgdbTestSetup(TEST_DB);
        setup.resetDb();
        schemaImport(setup, RELATIONSHIP_MODEL);

        List<String> relationships = OfgdbTestCatalogue.relationships(TEST_DB);
        assertEquals(3, relationships.size());
        boolean oneToMany = false;
        boolean oneToOne = false;
        for (String relationship : relationships) {
            String definition = OfgdbTestCatalogue.relationshipDefinition(TEST_DB, relationship);
            assertNotNull(definition);
            if (definition.contains("esriRelCardinalityOneToMany")) {
                oneToMany = true;
            }
            if (definition.contains("esriRelCardinalityOneToOne")) {
                oneToOne = true;
            }
        }
        assertTrue("expected a 1:n relationship class", oneToMany);
        assertTrue("expected a 1:1 relationship class", oneToOne);
    }

    /**
     * An n:m association is mapped to an association table by ili2db; the relationship class is
     * bound to that existing table (same name, attributed) instead of creating a new mapping table.
     */
    @Test
    public void createsManyToManyRelationshipOverTheAssociationTable() throws Exception {
        OfgdbTestSetup setup = new OfgdbTestSetup(TEST_DB);
        setup.resetDb();
        schemaImport(setup, RELATIONSHIP_MODEL);

        String manyToManyName = null;
        String definition = null;
        for (String relationship : OfgdbTestCatalogue.relationships(TEST_DB)) {
            String itemDefinition = OfgdbTestCatalogue.relationshipDefinition(TEST_DB, relationship);
            if (itemDefinition != null && itemDefinition.contains("esriRelCardinalityManyToMany")) {
                manyToManyName = relationship;
                definition = itemDefinition;
            }
        }
        assertNotNull("expected an n:m relationship class", manyToManyName);
        assertTrue("expected an attributed relationship: " + definition,
                definition.contains("IsAttributed") && definition.contains(">true<"));
        // the relationship is bound to the existing association table of ili2db
        assertNotNull("expected the mapping/association table " + manyToManyName,
                OfgdbTestCatalogue.definition(TEST_DB, manyToManyName));
    }

    @Test
    public void relationshipAndDomainCreationIsIdempotent() throws Exception {
        OfgdbTestSetup setup = new OfgdbTestSetup(TEST_DB);
        setup.resetDb();
        schemaImport(setup, RELATIONSHIP_MODEL);
        List<String> firstRelationships = OfgdbTestCatalogue.relationships(TEST_DB);
        assertFalse(firstRelationships.isEmpty());

        schemaImport(setup, RELATIONSHIP_MODEL);
        List<String> secondRelationships = OfgdbTestCatalogue.relationships(TEST_DB);
        assertEquals(firstRelationships.size(), secondRelationships.size());
    }

    @Test
    public void booleanDomainsAreCreatedByDefaultAndCanBeDisabled() throws Exception {
        OfgdbTestSetup setup = new OfgdbTestSetup(TEST_DB);
        setup.resetDb();
        schemaImport(setup, DATATYPES_MODEL);
        assertTrue(OfgdbTestCatalogue.domains(TEST_DB).contains("INTERLIS_BOOLEAN"));

        setup.resetDb();
        Config config = setup.initConfig(DATATYPES_MODEL, null);
        Ili2db.setNoSmartMapping(config);
        config.setFunction(Config.FC_SCHEMAIMPORT);
        config.setTidHandling(Config.TID_HANDLING_PROPERTY);
        config.setBasketHandling(Config.BASKET_HANDLING_READWRITE);
        config.setValue(OfgdbMapping.FGDB_CREATE_DOMAINS, Config.FALSE);
        Ili2db.run(config, null);
        assertFalse(OfgdbTestCatalogue.domains(TEST_DB).contains("INTERLIS_BOOLEAN"));
    }

    private static void schemaImport(OfgdbTestSetup setup, String modelPath) throws Exception {
        Config config = setup.initConfig(modelPath, null);
        Ili2db.setNoSmartMapping(config);
        config.setFunction(Config.FC_SCHEMAIMPORT);
        config.setTidHandling(Config.TID_HANDLING_PROPERTY);
        config.setBasketHandling(Config.BASKET_HANDLING_READWRITE);
        Ili2db.run(config, null);
    }
}
