package com.nystax.nimba.analyzer.scanner;

import java.nio.file.Path;
import java.util.List;

public class ProjectFiles {

    private Path projectRoot;
    private Path pomXml;
    private List<Path> jobXmlFiles;
    private List<Path> javaSourceFiles;

    public ProjectFiles(Path projectRoot, Path pomXml, List<Path> jobXmlFiles, List<Path> javaSourceFiles) {
        this.projectRoot = projectRoot;
        this.pomXml = pomXml;
        this.jobXmlFiles = jobXmlFiles;
        this.javaSourceFiles = javaSourceFiles;
    }

    public Path getProjectRoot() { return projectRoot; }
    public Path getPomXml() { return pomXml; }
    public List<Path> getJobXmlFiles() { return jobXmlFiles; }
    public List<Path> getJavaSourceFiles() { return javaSourceFiles; }
}
