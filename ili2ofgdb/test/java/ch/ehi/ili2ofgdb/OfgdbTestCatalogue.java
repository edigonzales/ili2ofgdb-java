package ch.ehi.ili2ofgdb;

import java.io.StringReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import ch.so.agi.filegdb.FileGeodatabase;
import ch.so.agi.filegdb.catalog.Dataset;
import ch.so.agi.filegdb.catalog.Domain;
import ch.so.agi.filegdb.catalog.RelationshipClass;

/**
 * Test helper that reads the file geodatabase catalog and definition XML through filegdb4j instead
 * of the former native openfgdb4j API.
 */
public final class OfgdbTestCatalogue {

    private OfgdbTestCatalogue() {
    }

    public static String definition(String gdbPath, String tableName) throws Exception {
        try (FileGeodatabase database = FileGeodatabase.open(Paths.get(gdbPath))) {
            Optional<Dataset> dataset = database.dataset(tableName);
            return dataset.isPresent() ? dataset.get().definition() : null;
        }
    }

    /** Returns the definition XML of any catalog item (table, feature class or domain). */
    public static String itemDefinition(String gdbPath, String itemName) throws Exception {
        try (FileGeodatabase database = FileGeodatabase.open(Paths.get(gdbPath))) {
            for (ch.so.agi.filegdb.catalog.GdbItem item : database.items()) {
                if (item.name() != null && item.name().equalsIgnoreCase(itemName)) {
                    return item.definition();
                }
            }
            return null;
        }
    }

    /**
     * Returns the definition XML of a relationship class. Needed because an n:m relationship class
     * shares its name with its mapping table.
     */
    public static String relationshipDefinition(String gdbPath, String relationshipName)
            throws Exception {
        try (FileGeodatabase database = FileGeodatabase.open(Paths.get(gdbPath))) {
            for (ch.so.agi.filegdb.catalog.GdbItem item : database.items()) {
                String definition = item.definition();
                if (definition != null && definition.contains("DERelationshipClassInfo")
                        && item.name() != null && item.name().equalsIgnoreCase(relationshipName)) {
                    return definition;
                }
            }
            return null;
        }
    }

    public static List<String> domains(String gdbPath) throws Exception {
        try (FileGeodatabase database = FileGeodatabase.open(Paths.get(gdbPath))) {
            List<String> names = new ArrayList<String>();
            for (Domain domain : database.domains()) {
                names.add(domain.name());
            }
            return names;
        }
    }

    public static Domain domain(String gdbPath, String domainName) throws Exception {
        try (FileGeodatabase database = FileGeodatabase.open(Paths.get(gdbPath))) {
            Optional<Domain> domain = database.domain(domainName);
            return domain.isPresent() ? domain.get() : null;
        }
    }

    public static List<String> relationships(String gdbPath) throws Exception {
        try (FileGeodatabase database = FileGeodatabase.open(Paths.get(gdbPath))) {
            List<String> names = new ArrayList<String>();
            for (RelationshipClass relationship : database.relationships()) {
                names.add(relationship.name());
            }
            return names;
        }
    }

    /** Returns the text of the first element with the given tag, or null. */
    public static String findFirstTagText(String xml, String tagName) throws Exception {
        if (xml == null || xml.trim().isEmpty()) {
            return null;
        }
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));
        NodeList nodes = document.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element && node.getNodeName().equalsIgnoreCase(tagName)) {
                return node.getTextContent();
            }
        }
        return null;
    }

    /** Returns the definition block of a field (GPFieldInfoEx) as text, or null. */
    public static String fieldBlock(String definitionXml, String fieldName) throws Exception {
        if (definitionXml == null || definitionXml.trim().isEmpty()) {
            return null;
        }
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new InputSource(new StringReader(definitionXml)));
        NodeList nodes = document.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (!(node instanceof Element) || !node.getNodeName().equalsIgnoreCase("GPFieldInfoEx")) {
                continue;
            }
            Element field = (Element) node;
            NodeList children = field.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (child instanceof Element
                        && child.getNodeName().equalsIgnoreCase("Name")
                        && fieldName.equalsIgnoreCase(child.getTextContent().trim())) {
                    return fieldText(field);
                }
            }
        }
        return null;
    }

    private static String fieldText(Element element) throws Exception {
        javax.xml.transform.Transformer transformer =
                javax.xml.transform.TransformerFactory.newInstance().newTransformer();
        java.io.StringWriter output = new java.io.StringWriter();
        transformer.transform(new javax.xml.transform.dom.DOMSource(element),
                new javax.xml.transform.stream.StreamResult(output));
        return output.toString();
    }
}
