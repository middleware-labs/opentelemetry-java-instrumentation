/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent.codeextraction;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

public class CodeExtractionUtils {
  private static final Logger LOGGER = Logger.getLogger(CodeExtractionUtils.class.getName());

  // Number of lines to show before and after the error line
  private static final int CONTEXT_LINES = 5;

  /**
   * Extracts code context from a stack trace element Returns a map with line numbers as keys and
   * code lines as values
   */
  public static Map<String, Object> extractCodeContext(StackTraceElement stackElement) {
    if (stackElement == null) {
      return Collections.emptyMap();
    }

    Map<String, Object> codeContext = new HashMap<>();

    try {
      String className = stackElement.getClassName();
      String fileName = stackElement.getFileName();
      int lineNumber = stackElement.getLineNumber();

      if (fileName == null || lineNumber <= 0) {
        LOGGER.fine("No file name or invalid line number in stack trace element: " + stackElement);
        return codeContext;
      }

      // Try to find and read the source file
      List<String> sourceLines = readSourceFile(className, fileName);
      if (sourceLines.isEmpty()) {
        LOGGER.fine("Could not read source file: " + fileName + " for class: " + className);
        return codeContext;
      }

      // Extract the complete method containing the error
      Map<Integer, String> methodLines =
          extractCompleteMethod(sourceLines, lineNumber, stackElement.getMethodName());

      // Build the result
      codeContext.put("file_name", fileName);
      codeContext.put("class_name", className);
      codeContext.put("method_name", stackElement.getMethodName());
      codeContext.put("line_number", lineNumber);
      codeContext.put("method_lines", methodLines);
      codeContext.put("error_line", methodLines.get(lineNumber));
      codeContext.put("extraction_type", "complete_method");

      LOGGER.info(
          "Extracted complete method code for "
              + className
              + "."
              + stackElement.getMethodName()
              + " containing error at line "
              + lineNumber);

    } catch (Exception e) {
      LOGGER.warning("Error extracting code context: " + e.getMessage());
    }

    return codeContext;
  }

  /**
   * Extracts code context from a full stack trace Returns a list of code contexts for each relevant
   * stack frame
   */
  public static List<Map<String, Object>> extractCodeContextFromStackTrace(
      StackTraceElement[] stackTrace) {
    List<Map<String, Object>> contexts = new ArrayList<>();

    if (stackTrace == null || stackTrace.length == 0) {
      return contexts;
    }

    // Process only the first few stack frames to avoid performance issues
    int framesToProcess = Math.min(stackTrace.length, 10);

    for (int i = 0; i < framesToProcess; i++) {
      StackTraceElement element = stackTrace[i];

      // Skip system/framework classes, focus on application code
      if (isApplicationCode(element.getClassName())) {
        Map<String, Object> context = extractCodeContext(element);
        if (!context.isEmpty()) {
          contexts.add(context);
        }
      }
    }

    return contexts;
  }

  /** Extracts code context from a Throwable */
  public static List<Map<String, Object>> extractCodeContextFromThrowable(Throwable throwable) {
    if (throwable == null) {
      return Collections.emptyList();
    }

    return extractCodeContextFromStackTrace(throwable.getStackTrace());
  }

  /** Logs the code context to console for debugging */
  public static void logCodeContextToConsole(Map<String, Object> codeContext) {
    try {
      System.out.println("=== CODE CONTEXT CAPTURED ===");
      System.out.println("File: " + codeContext.get("file_name"));
      System.out.println("Class: " + codeContext.get("class_name"));
      System.out.println("Method: " + codeContext.get("method_name"));
      System.out.println("Error Line: " + codeContext.get("line_number"));
      System.out.println(
          "Extraction Type: " + codeContext.getOrDefault("extraction_type", "context_lines"));
      System.out.println();

      // Handle complete method extraction
      if (codeContext.containsKey("method_lines")) {
        @SuppressWarnings("unchecked")
        Map<Integer, String> methodLines = (Map<Integer, String>) codeContext.get("method_lines");

        System.out.println("Complete Method Code:");
        System.out.println("--------------------");

        for (Map.Entry<Integer, String> entry : methodLines.entrySet()) {
          int lineNumber = entry.getKey();
          String lineContent = entry.getValue();

          // Highlight the error line
          if (lineNumber == (Integer) codeContext.get("line_number")) {
            System.out.printf(">>> %4d: %s\n", lineNumber, lineContent);
          } else {
            System.out.printf("    %4d: %s\n", lineNumber, lineContent);
          }
        }
      }
      // Fallback to context lines
      else if (codeContext.containsKey("context_lines")) {
        @SuppressWarnings("unchecked")
        Map<Integer, String> contextLines = (Map<Integer, String>) codeContext.get("context_lines");

        System.out.println("Source Code Context:");
        System.out.println("-------------------");

        for (Map.Entry<Integer, String> entry : contextLines.entrySet()) {
          int lineNumber = entry.getKey();
          String lineContent = entry.getValue();

          // Highlight the error line
          if (lineNumber == (Integer) codeContext.get("line_number")) {
            System.out.printf(">>> %4d: %s\n", lineNumber, lineContent);
          } else {
            System.out.printf("    %4d: %s\n", lineNumber, lineContent);
          }
        }
      }

      System.out.println("============================");
      System.out.println();

    } catch (Exception e) {
      System.err.println("Error logging code context: " + e.getMessage());
    }
  }

  /** Reads source file content by trying multiple strategies */
  private static List<String> readSourceFile(String className, String fileName) {
    // Strategy 1: Try to find source file relative to class location
    List<String> lines = readFromClassPath(className, fileName);
    if (!lines.isEmpty()) {
      return lines;
    }

    // Strategy 2: Try to find in current project structure
    lines = readFromProjectStructure(fileName);
    if (!lines.isEmpty()) {
      return lines;
    }

    // Strategy 3: Try to read from JAR source attachments (if available)
    lines = readFromSourceJar(className, fileName);
    if (!lines.isEmpty()) {
      return lines;
    }

    return Collections.emptyList();
  }

  /** Try to read source file from classpath */
  private static List<String> readFromClassPath(String className, String fileName) {
    try {
      // Convert class name to package path
      String packagePath = className.substring(0, className.lastIndexOf('.')).replace('.', '/');
      String sourcePath = packagePath + "/" + fileName;

      // Try to find the source file in classpath
      ClassLoader classLoader = CodeExtractionUtils.class.getClassLoader();
      URL resource = classLoader.getResource(sourcePath);

      if (resource != null) {
        try (InputStream is = resource.openStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

          List<String> lines = new ArrayList<>();
          String line;
          while ((line = reader.readLine()) != null) {
            lines.add(line);
          }
          return lines;
        }
      }
    } catch (Exception e) {
      LOGGER.fine("Could not read from classpath: " + e.getMessage());
    }

    return Collections.emptyList();
  }

  /** Try to read source file from project directory structure */
  private static List<String> readFromProjectStructure(String fileName) {
    try {
      // Common Java project structures to search
      String[] searchPaths = {"src/main/java", "src/java", "src"};

      for (String basePath : searchPaths) {
        Path sourceFile = findFileInDirectory(Paths.get(basePath), fileName);
        if (sourceFile != null && Files.exists(sourceFile)) {
          return Files.readAllLines(sourceFile);
        }
      }
    } catch (Exception e) {
      LOGGER.fine("Could not read from project structure: " + e.getMessage());
    }

    return Collections.emptyList();
  }

  /** Try to read from source JAR (if available) */
  private static List<String> readFromSourceJar(String className, String fileName) {
    // This would require more complex JAR inspection
    // For now, return empty - can be enhanced later
    return Collections.emptyList();
  }

  /** Recursively search for a file in directory */
  private static Path findFileInDirectory(Path directory, String fileName) {
    try {
      return Files.walk(directory)
          .filter(path -> path.getFileName().toString().equals(fileName))
          .findFirst()
          .orElse(null);
    } catch (Exception e) {
      return null;
    }
  }

  /** Extract context lines around a specific line number */
  private static Map<Integer, String> extractContextLines(
      List<String> sourceLines, int errorLineNumber, int contextSize) {
    Map<Integer, String> contextLines = new LinkedHashMap<>();

    int startLine = Math.max(1, errorLineNumber - contextSize);
    int endLine = Math.min(sourceLines.size(), errorLineNumber + contextSize);

    for (int i = startLine; i <= endLine; i++) {
      if (i >= 1 && i <= sourceLines.size()) {
        contextLines.put(i, sourceLines.get(i - 1)); // Convert to 0-based index
      }
    }

    return contextLines;
  }

  /** Extract the complete method containing the specified line number */
  private static Map<Integer, String> extractCompleteMethod(
      List<String> sourceLines, int errorLineNumber, String methodName) {
    Map<Integer, String> methodLines = new LinkedHashMap<>();

    try {
      // Find the method declaration line
      int methodStartLine = findMethodDeclarationLine(sourceLines, errorLineNumber, methodName);
      if (methodStartLine == -1) {
        LOGGER.fine("Could not find method declaration for: " + methodName);
        return extractContextLines(
            sourceLines, errorLineNumber, CONTEXT_LINES); // Fallback to context
      }

      // Find the method end line by tracking braces
      int methodEndLine = findMethodEndLine(sourceLines, methodStartLine);
      if (methodEndLine == -1) {
        LOGGER.fine("Could not find method end for: " + methodName);
        return extractContextLines(
            sourceLines, errorLineNumber, CONTEXT_LINES); // Fallback to context
      }

      // Extract all lines from method start to end
      for (int i = methodStartLine; i <= methodEndLine; i++) {
        if (i >= 1 && i <= sourceLines.size()) {
          methodLines.put(i, sourceLines.get(i - 1)); // Convert to 0-based index
        }
      }

      LOGGER.info(
          "Extracted complete method "
              + methodName
              + " from lines "
              + methodStartLine
              + " to "
              + methodEndLine);

    } catch (Exception e) {
      LOGGER.warning("Error extracting complete method: " + e.getMessage());
      return extractContextLines(
          sourceLines, errorLineNumber, CONTEXT_LINES); // Fallback to context
    }

    return methodLines;
  }

  /** Find the line number where the method declaration starts */
  private static int findMethodDeclarationLine(
      List<String> sourceLines, int errorLineNumber, String methodName) {
    // Search backwards from error line to find method declaration
    for (int i = errorLineNumber - 1; i >= 0; i--) {
      String line = sourceLines.get(i).trim();

      // Look for method declaration patterns
      if (isMethodDeclaration(line, methodName)) {
        return i + 1; // Convert to 1-based line number
      }

      // Stop if we hit another method or class declaration
      if (isAnotherMethodOrClassDeclaration(line, methodName)) {
        break;
      }
    }

    return -1; // Not found
  }

  /** Find the line number where the method ends by tracking braces */
  private static int findMethodEndLine(List<String> sourceLines, int methodStartLine) {
    int openBraces = 0;
    boolean foundFirstBrace = false;

    for (int i = methodStartLine - 1; i < sourceLines.size(); i++) {
      String line = sourceLines.get(i);

      // Count braces in this line
      for (char c : line.toCharArray()) {
        if (c == '{') {
          openBraces++;
          foundFirstBrace = true;
        } else if (c == '}') {
          openBraces--;

          // If we've found the first brace and now braces are balanced, method is complete
          if (foundFirstBrace && openBraces == 0) {
            return i + 1; // Convert to 1-based line number
          }
        }
      }
    }

    return -1; // Not found
  }

  /** Check if a line contains a method declaration */
  private static boolean isMethodDeclaration(String line, String methodName) {
    // Remove comments
    int commentIndex = line.indexOf("//");
    if (commentIndex != -1) {
      line = line.substring(0, commentIndex).trim();
    }

    // Skip empty lines
    if (line.isEmpty()) {
      return false;
    }

    // Look for method patterns: [modifiers] [return_type] methodName(
    String methodPattern = "\\b" + methodName + "\\s*\\(";

    // Check if line contains the method name with opening parenthesis
    if (line.matches(".*" + methodPattern + ".*")) {
      // Additional checks to ensure it's a method declaration
      // Should have access modifiers or return type before method name
      return line.matches(
              ".*(public|private|protected|static|final|abstract|synchronized).*"
                  + methodPattern
                  + ".*")
          || line.matches(".*\\b\\w+\\s+" + methodPattern + ".*"); // return type + method
    }

    return false;
  }

  /** Check if a line contains another method or class declaration that should stop our search */
  private static boolean isAnotherMethodOrClassDeclaration(String line, String currentMethodName) {
    // Remove comments
    int commentIndex = line.indexOf("//");
    if (commentIndex != -1) {
      line = line.substring(0, commentIndex).trim();
    }

    // Skip empty lines
    if (line.isEmpty()) {
      return false;
    }

    // Check for class/interface/enum declarations
    if (line.matches(".*\\b(class|interface|enum)\\s+\\w+.*")) {
      return true;
    }

    // Check for other method declarations (but not the current one)
    if (line.matches(
        ".*(public|private|protected|static|final|abstract|synchronized).*\\w+\\s*\\(.*")) {
      return !line.contains(currentMethodName + "(");
    }

    return false;
  }

  /** Determines if a class name represents application code vs framework code */
  private static boolean isApplicationCode(String className) {
    if (className == null) {
      return false;
    }

    // Skip common framework/system packages
    String[] systemPackages = {
      "java.",
      "javax.",
      "sun.",
      "com.sun.",
      "org.springframework.",
      "org.apache.",
      "io.opentelemetry.",
      "org.slf4j.",
      "ch.qos.logback.",
      "org.junit."
    };

    for (String systemPackage : systemPackages) {
      if (className.startsWith(systemPackage)) {
        return false;
      }
    }

    // Consider it application code
    return true;
  }
}
