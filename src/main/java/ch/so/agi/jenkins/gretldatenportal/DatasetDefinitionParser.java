package ch.so.agi.jenkins.gretldatenportal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public final class DatasetDefinitionParser {
    public ParseResult parse(Path datasetDefinitionFile) {
        List<ValidationMessage> messages = new ArrayList<>();
        Document document;

        try {
            document = documentBuilder().parse(datasetDefinitionFile.toFile());
        } catch (IOException | ParserConfigurationException | SAXException ex) {
            messages.add(new ValidationMessage(
                    ValidationMessage.Severity.ERROR,
                    "Could not read or parse dataset metadata XML/XTF: " + ex.getMessage(),
                    datasetDefinitionFile));
            return new ParseResult(null, messages);
        }

        Element datasetElement = datasetElement(document, datasetDefinitionFile, messages);
        if (datasetElement == null) {
            return new ParseResult(null, messages);
        }

        String id = requiredString(datasetElement, "identifier", datasetDefinitionFile, messages);
        String title = requiredString(datasetElement, "title", datasetDefinitionFile, messages);
        String description = requiredString(datasetElement, "description", datasetDefinitionFile, messages);
        boolean series = "DatasetSeries".equals(localName(datasetElement));

        if (!messages.isEmpty()) {
            return new ParseResult(null, messages);
        }

        return new ParseResult(new DatasetDefinition(id, title, description, series), messages);
    }

    private static String requiredString(
            Element parent,
            String key,
            Path path,
            List<ValidationMessage> messages) {
        String value = directChildText(parent, key);
        if (value == null || value.isBlank()) {
            messages.add(new ValidationMessage(
                    ValidationMessage.Severity.ERROR,
                    "Dataset metadata XML/XTF is missing required non-empty element '" + key + "'.",
                    path));
            return null;
        }
        return value.trim();
    }

    private static Element datasetElement(Document document, Path path, List<ValidationMessage> messages) {
        Element transfer = document.getDocumentElement();
        Element datasection = directChildElement(transfer, "datasection");
        if (datasection == null) {
            messages.add(new ValidationMessage(
                    ValidationMessage.Severity.ERROR,
                    "Dataset metadata XML/XTF is missing ili:datasection.",
                    path));
            return null;
        }

        Element metadata = directChildElement(datasection, "Metadata");
        if (metadata == null) {
            messages.add(new ValidationMessage(
                    ValidationMessage.Severity.ERROR,
                    "Dataset metadata XML/XTF is missing Metadata.",
                    path));
            return null;
        }

        List<Element> datasetElements = directChildElements(metadata, List.of("Dataset", "DatasetSeries"));
        if (datasetElements.size() != 1) {
            messages.add(new ValidationMessage(
                    ValidationMessage.Severity.ERROR,
                    "Dataset metadata XML/XTF must contain exactly one direct Metadata child of type Dataset or DatasetSeries.",
                    path));
            return null;
        }
        return datasetElements.get(0);
    }

    private static DocumentBuilder documentBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder();
    }

    private static Element directChildElement(Element parent, String localName) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && localName.equals(localName(child))) {
                return (Element) child;
            }
        }
        return null;
    }

    private static List<Element> directChildElements(Element parent, List<String> localNames) {
        List<Element> matches = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && localNames.contains(localName(child))) {
                matches.add((Element) child);
            }
        }
        return matches;
    }

    private static String directChildText(Element parent, String localName) {
        Element child = directChildElement(parent, localName);
        if (child == null) {
            return null;
        }
        return child.getTextContent();
    }

    private static String localName(Node node) {
        return node.getLocalName() != null ? node.getLocalName() : node.getNodeName();
    }

    public static final class ParseResult {
        private final DatasetDefinition definition;
        private final List<ValidationMessage> messages;

        private ParseResult(DatasetDefinition definition, List<ValidationMessage> messages) {
            this.definition = definition;
            this.messages = List.copyOf(messages);
        }

        public DatasetDefinition getDefinition() {
            return definition;
        }

        public List<ValidationMessage> getMessages() {
            return messages;
        }

        public boolean isValid() {
            return definition != null && messages.stream().noneMatch(ValidationMessage::isError);
        }
    }
}
