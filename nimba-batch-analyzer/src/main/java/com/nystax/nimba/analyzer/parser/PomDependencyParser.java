package com.nystax.nimba.analyzer.parser;

import com.nystax.nimba.analyzer.model.WasDependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class PomDependencyParser {

    private static final Logger log = LoggerFactory.getLogger(PomDependencyParser.class);
    private static final String WAS_GROUP_ID = "gov.nystax.was.functions";

    public List<WasDependency> extractWasDependencies(Path pomXmlPath) {
        List<WasDependency> result = new ArrayList<>();
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = factory.newDocumentBuilder().parse(pomXmlPath.toFile());

            NodeList dependencies = doc.getElementsByTagName("dependency");
            for (int i = 0; i < dependencies.getLength(); i++) {
                Element dep = (Element) dependencies.item(i);
                String groupId = getChildText(dep, "groupId");
                if (WAS_GROUP_ID.equals(groupId)) {
                    result.add(new WasDependency(
                            groupId,
                            getChildText(dep, "artifactId"),
                            getChildText(dep, "version")
                    ));
                }
            }
            log.info("Found {} WAS function dependencies", result.size());
        } catch (Exception e) {
            log.warn("Failed to parse pom.xml: {}", e.getMessage());
        }
        return result;
    }

    private String getChildText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }
        return "";
    }
}
