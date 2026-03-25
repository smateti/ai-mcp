package com.nystax.nimba.analyzer.parser;

import com.nystax.nimba.analyzer.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.*;

@Component
public class JobXmlParser {

    private static final Logger log = LoggerFactory.getLogger(JobXmlParser.class);

    @Value("${nimba.analyzer.framework-reader-prefix:fw}")
    private String frameworkReaderPrefix;

    public JobDefinition parse(Path xmlFile) {
        try {
            String jobId = deriveJobId(xmlFile);
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = factory.newDocumentBuilder().parse(xmlFile.toFile());
            Element jobElement = doc.getDocumentElement();

            JobDefinition job = new JobDefinition();
            job.setJobId(jobId);
            job.setXmlFilePath(xmlFile.toString());
            job.setResumable(getBooleanAttr(jobElement, "resumable", false));
            job.setArchiveFiles(getBooleanAttr(jobElement, "archiveFiles", false));
            job.setJobListenerId(extractJobListenerId(jobElement));
            job.setSteps(extractSteps(jobElement));

            log.info("Parsed job {} with {} steps", jobId, job.getSteps().size());
            return job;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse job XML: " + xmlFile, e);
        }
    }

    private String deriveJobId(Path xmlFile) {
        String fileName = xmlFile.getFileName().toString();
        return fileName.endsWith(".xml") ? fileName.substring(0, fileName.length() - 4) : fileName;
    }

    private String extractJobListenerId(Element jobElement) {
        NodeList listeners = jobElement.getElementsByTagName("jobListener");
        if (listeners.getLength() > 0) {
            return ((Element) listeners.item(0)).getAttribute("id");
        }
        return null;
    }

    private List<StepDefinition> extractSteps(Element jobElement) {
        List<StepDefinition> steps = new ArrayList<>();
        NodeList stepNodes = jobElement.getElementsByTagName("step");

        for (int i = 0; i < stepNodes.getLength(); i++) {
            Element stepEl = (Element) stepNodes.item(i);
            StepDefinition step = new StepDefinition();
            step.setStepId(stepEl.getAttribute("id"));
            step.setParallelism(getAttrOrDefault(stepEl, "parallelism", "1"));
            step.setFailOnError(getBooleanAttr(stepEl, "failOnError", true));

            // Parse reader
            ReaderDefinition reader = extractReader(stepEl);
            step.setReader(reader);
            step.setStepType(reader != null ? StepType.MANAGED : StepType.CUSTOM);

            // Parse processor
            step.setProcessor(extractProcessor(stepEl));

            steps.add(step);
        }
        return steps;
    }

    private ReaderDefinition extractReader(Element stepElement) {
        NodeList readers = stepElement.getElementsByTagName("reader");
        if (readers.getLength() == 0) return null;

        Element readerEl = (Element) readers.item(0);
        String readerId = readerEl.getAttribute("id");

        ReaderDefinition reader = new ReaderDefinition();
        reader.setReaderId(readerId);
        reader.setReaderType(readerId.startsWith(frameworkReaderPrefix)
                ? ReaderType.FRAMEWORK : ReaderType.CUSTOM);
        reader.setParameters(extractParameters(readerEl));
        return reader;
    }

    private ProcessorDefinition extractProcessor(Element stepElement) {
        NodeList processors = stepElement.getElementsByTagName("processor");
        if (processors.getLength() == 0) return null;

        Element procEl = (Element) processors.item(0);
        ProcessorDefinition proc = new ProcessorDefinition();
        proc.setProcessorId(procEl.getAttribute("id"));
        String source = procEl.getAttribute("source");
        if (!source.isEmpty()) {
            proc.setSource(source);
        }
        proc.setParameters(extractParameters(procEl));
        return proc;
    }

    private Map<String, String> extractParameters(Element parentElement) {
        Map<String, String> params = new LinkedHashMap<>();
        NodeList paramNodes = parentElement.getElementsByTagName("parameter");
        for (int i = 0; i < paramNodes.getLength(); i++) {
            Element paramEl = (Element) paramNodes.item(i);
            String key = paramEl.getAttribute("key");
            String value = paramEl.getTextContent().trim();
            if (!key.isEmpty()) {
                params.put(key, value);
            }
        }
        return params;
    }

    private boolean getBooleanAttr(Element el, String attr, boolean defaultVal) {
        String val = el.getAttribute(attr);
        return val.isEmpty() ? defaultVal : Boolean.parseBoolean(val);
    }

    private String getAttrOrDefault(Element el, String attr, String defaultVal) {
        String val = el.getAttribute(attr);
        return val.isEmpty() ? defaultVal : val;
    }
}
