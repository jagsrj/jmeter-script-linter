package io.github.jagsrj.jmxlint.model;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Parses a .jmx file into a {@link TestPlanModel} without needing a JMeter installation.
 *
 * <p>The XML parser is hardened against XXE / entity-expansion attacks, and the DOM is discarded once the
 * lightweight model is built, so linting many files does not accumulate memory.</p>
 */
public final class JmxParser {

    private static final String HASH_TREE = "hashTree";

    public TestPlanModel parse(Path file) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            return parse(in, file.toString());
        }
    }

    public TestPlanModel parse(InputStream in, String sourceName) throws IOException {
        Document doc;
        try {
            doc = newSecureBuilder().parse(in);
        } catch (SAXException e) {
            throw new IOException("Not a valid JMX/XML file: " + e.getMessage(), e);
        } catch (ParserConfigurationException e) {
            throw new IOException("XML parser configuration error: " + e.getMessage(), e);
        }
        Element root = doc.getDocumentElement();
        if (root == null || !"jmeterTestPlan".equals(root.getTagName())) {
            throw new IOException("Not a JMeter test plan (root element must be <jmeterTestPlan>)");
        }
        TestPlanModel model = new TestPlanModel(sourceName);
        Element topTree = firstChildElement(root, HASH_TREE);
        if (topTree != null) {
            parseHashTree(topTree, null, model);
        }
        return model;
    }

    static DocumentBuilder newSecureBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(false);
        f.setValidating(false);
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        trySetFeature(f, "http://apache.org/xml/features/disallow-doctype-decl", true);
        trySetFeature(f, "http://xml.org/sax/features/external-general-entities", false);
        trySetFeature(f, "http://xml.org/sax/features/external-parameter-entities", false);
        trySetFeature(f, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        try {
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (IllegalArgumentException ignored) {
            // attribute not supported by this parser implementation; features above still apply
        }
        DocumentBuilder b = f.newDocumentBuilder();
        b.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
            @Override
            public void fatalError(org.xml.sax.SAXParseException e) throws SAXException {
                throw e; // surface as exception instead of printing "[Fatal Error]" to stderr
            }
        });
        return b;
    }

    private static void trySetFeature(DocumentBuilderFactory f, String feature, boolean value) {
        try {
            f.setFeature(feature, value);
        } catch (ParserConfigurationException ignored) {
            // not supported by this implementation
        }
    }

    /** A hashTree holds pairs: [testElement, hashTree-of-its-children], [testElement, hashTree] ... */
    private void parseHashTree(Element hashTree, JmxElement parent, TestPlanModel model) {
        JmxElement last = null;
        for (Node n = hashTree.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element el = (Element) n;
            if (HASH_TREE.equals(el.getTagName())) {
                if (last != null) {
                    parseHashTree(el, last, model);
                }
            } else {
                last = buildElement(el, parent);
                model.add(last, parent);
            }
        }
    }

    private JmxElement buildElement(Element el, JmxElement parent) {
        String testClass = el.getAttribute("testclass");
        if (testClass.isEmpty()) {
            testClass = el.getTagName();
        }
        String enabledAttr = el.getAttribute("enabled");
        boolean enabled = enabledAttr.isEmpty() || Boolean.parseBoolean(enabledAttr);
        JmxElement je = new JmxElement(testClass, el.getAttribute("guiclass"), el.getAttribute("testname"),
                enabled, parent);
        collectProps(el, je);
        return je;
    }

    private void collectProps(Element node, JmxElement target) {
        for (Node n = node.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element c = (Element) n;
            String tag = c.getTagName();
            if (isSimpleProp(tag)) {
                target.putProp(c.getAttribute("name"), c.getTextContent());
            } else if ("elementProp".equals(tag)) {
                collectProps(c, target); // flatten: JMeter property names are already namespaced
            } else if ("collectionProp".equals(tag)) {
                collectCollection(c, target);
            } else if ("objProp".equals(tag)) {
                collectObjProp(c, target);
            }
        }
    }

    private void collectCollection(Element collection, JmxElement target) {
        for (Node n = collection.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element c = (Element) n;
            Map<String, String> item = new LinkedHashMap<>();
            if ("elementProp".equals(c.getTagName())) {
                item.put("_name", c.getAttribute("name"));
                fillItem(c, item);
            } else if (isSimpleProp(c.getTagName())) {
                item.put("_name", c.getAttribute("name"));
                item.put("value", c.getTextContent());
            } else if ("collectionProp".equals(c.getTagName())) {
                collectCollection(c, target);
                continue;
            }
            target.addItem(item);
        }
    }

    private void fillItem(Element elementProp, Map<String, String> item) {
        for (Node n = elementProp.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element c = (Element) n;
            if (isSimpleProp(c.getTagName())) {
                item.putIfAbsent(c.getAttribute("name"), c.getTextContent());
            } else if ("elementProp".equals(c.getTagName())) {
                fillItem(c, item);
            }
        }
    }

    /** objProp, e.g. a listener's saveConfig: stored as "saveConfig.responseData" = "true". */
    private void collectObjProp(Element objProp, JmxElement target) {
        Element nameEl = firstChildElement(objProp, "name");
        Element valueEl = firstChildElement(objProp, "value");
        if (nameEl == null || valueEl == null) {
            return;
        }
        String prefix = nameEl.getTextContent().trim();
        for (Node n = valueEl.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                Element c = (Element) n;
                target.putProp(prefix + "." + c.getTagName(), c.getTextContent().trim());
            }
        }
    }

    private static boolean isSimpleProp(String tag) {
        return "stringProp".equals(tag) || "boolProp".equals(tag) || "intProp".equals(tag)
                || "longProp".equals(tag) || "floatProp".equals(tag) || "doubleProp".equals(tag);
    }

    private static Element firstChildElement(Element parent, String tag) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE && tag.equals(((Element) n).getTagName())) {
                return (Element) n;
            }
        }
        return null;
    }
}
