package com.microsoft.testcollector;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

public class JavaTestCollector {
    private String outputFilePath = "ci/app/test_index";
    private Path directoryPath;

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Please specify start path.");
            return;
        }

        try {
            JavaTestCollector testCollector = new JavaTestCollector(Paths.get(args[0]));
            if (args.length > 1) {
                testCollector.outputFilePath = args[1];
            }
            testCollector.collectTestData();
            System.out.println("Done@main");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private JavaTestCollector(Path directoryPath) {
        this.directoryPath = directoryPath;
        System.out.println("Start path: " + directoryPath.toString());
    }

    private StringBuilder parseMockFile(String moduleName, Path testReportsDir, StringBuilder taskList) throws IOException {
        DirectoryStream<Path> stream = Files.newDirectoryStream(testReportsDir, "*.xml");
        String taskTemplate = new BufferedReader(new InputStreamReader(JavaTestCollector.class.getResourceAsStream("/taskTemplate.json")))
                .lines().collect(Collectors.joining("\n"));
        for (Path path : stream) {
            System.out.println(path);
            try {
                File inputFile = new File(path.toString());
                DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                Document doc = dBuilder.parse(inputFile);
                doc.getDocumentElement().normalize();
                NodeList testCaseList = doc.getElementsByTagName("testcase");
                for (int i = 0; i < testCaseList.getLength(); i++) {
                    Node testCaseNode = testCaseList.item(i);
                    if (testCaseNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element testCaseElement = (Element) testCaseNode;
                        String testCaseName = testCaseElement.getAttribute("name");
                        String testSuiteName = testCaseElement.getAttribute("classname");
                        NodeList skipped = testCaseElement.getElementsByTagName("skipped");
//                        System.out.println(String.format("==> %s#%s (%s sec) %s", testSuiteName, testCaseName, testTime, (skipped.getLength() > 0 ? "SKIPPED" : "")));
                        if (skipped.getLength() == 0) {
                            taskList = taskList.append(createTestEntry(moduleName, testSuiteName, testCaseName, taskTemplate));
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return taskList;
    }

    private static String createTestEntry(String moduleName, String testSuiteName, String testCaseName, String taskTemplate) {
        return taskTemplate.replace("$module$", moduleName)
                .replace("$testSuite$", testSuiteName)
                .replace("$testCase$", testCaseName);

    }

    private void collectTestData() {
        StringBuilder taskList = new StringBuilder("[");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directoryPath)) {
            for (Path path : stream) {
                Path testReportsDir = Paths.get(path.toString(), "target", "surefire-reports");
                if (Files.isDirectory(path) && Files.exists(testReportsDir) && Files.isDirectory(testReportsDir)) {
                    System.out.println(testReportsDir.toString());
                    taskList = parseMockFile(path.getFileName().toString(), testReportsDir, taskList);
                }
            }
            String taskListString = taskList.substring(0, taskList.length() -1) + "]";
            try {
                Path outputPath = Paths.get(outputFilePath);
                Files.write(outputPath, taskListString.getBytes());
                System.out.println("Saved list of tests to: " + outputPath.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Done@collectTestData");
    }


}
