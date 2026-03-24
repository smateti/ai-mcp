package com.nystax.nimba.analyzer.scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Component
public class ProjectScanner {

    private static final Logger log = LoggerFactory.getLogger(ProjectScanner.class);

    public ProjectFiles scan(Path projectRoot) throws IOException {
        if (!Files.isDirectory(projectRoot)) {
            throw new IllegalArgumentException("Project root does not exist: " + projectRoot);
        }

        Path pomXml = projectRoot.resolve("pom.xml");
        if (!Files.exists(pomXml)) {
            throw new IllegalArgumentException("No pom.xml found at: " + projectRoot);
        }

        List<Path> jobXmlFiles = findJobXmlFiles(projectRoot);
        List<Path> javaSourceFiles = findJavaSourceFiles(projectRoot);

        log.info("Scanned project: {} job XMLs, {} Java files", jobXmlFiles.size(), javaSourceFiles.size());
        return new ProjectFiles(projectRoot, pomXml, jobXmlFiles, javaSourceFiles);
    }

    private List<Path> findJobXmlFiles(Path projectRoot) throws IOException {
        Path resourcesDir = projectRoot.resolve("src/main/resources");
        if (!Files.isDirectory(resourcesDir)) {
            return List.of();
        }

        List<Path> jobXmls = new ArrayList<>();
        try (Stream<Path> files = Files.list(resourcesDir)) {
            files.filter(p -> p.toString().endsWith(".xml"))
                    .filter(this::isJobXml)
                    .forEach(jobXmls::add);
        }
        return jobXmls;
    }

    private boolean isJobXml(Path xmlPath) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var doc = factory.newDocumentBuilder().parse(xmlPath.toFile());
            return "job".equals(doc.getDocumentElement().getTagName());
        } catch (Exception e) {
            return false;
        }
    }

    private List<Path> findJavaSourceFiles(Path projectRoot) throws IOException {
        Path javaDir = projectRoot.resolve("src/main/java");
        if (!Files.isDirectory(javaDir)) {
            return List.of();
        }

        List<Path> javaFiles = new ArrayList<>();
        try (Stream<Path> files = Files.walk(javaDir, 20)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .forEach(javaFiles::add);
        }
        return javaFiles;
    }
}
