/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent.codeextraction;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class SpanCodeEnhancer {
  private static final Logger LOGGER = Logger.getLogger(SpanCodeEnhancer.class.getName());

  // Attribute keys for code context
  private static final AttributeKey<String> CODE_FUNCTION = AttributeKey.stringKey("code.function");
  private static final AttributeKey<String> CODE_FILEPATH = AttributeKey.stringKey("code.filepath");
  private static final AttributeKey<Long> CODE_LINENO = AttributeKey.longKey("code.lineno");
  private static final AttributeKey<String> CODE_NAMESPACE =
      AttributeKey.stringKey("code.namespace");

  // Enhanced exception stack details attributes (matching Python implementation)
  private static final AttributeKey<String> EXCEPTION_STACK_DETAILS =
      AttributeKey.stringKey("exception.stack_details");

  /** Enhances span data with code context from exceptions */
  public static boolean enhanceSpanWithCodeContext(
      SpanData spanData, AttributesBuilder attributesBuilder) {
    boolean enhanced = false;

    try {
      // Check if span has exception events
      List<EventData> events = spanData.getEvents();
      for (EventData event : events) {
        if (isExceptionEvent(event)) {
          enhanced |= processExceptionEvent(event, attributesBuilder);
        }
      }

      // Also check span status for errors
      if (spanData.getStatus().getStatusCode() == io.opentelemetry.api.trace.StatusCode.ERROR) {
        enhanced |= processSpanError(spanData, attributesBuilder);
      }

    } catch (Exception e) {
      LOGGER.warning("Error enhancing span with code context: " + e.getMessage());
    }

    return enhanced;
  }

  /** Checks if an event represents an exception */
  private static boolean isExceptionEvent(EventData event) {
    String eventName = event.getName().toLowerCase();
    return eventName.contains("exception")
        || eventName.contains("error")
        || event.getAttributes().get(AttributeKey.stringKey("exception.type")) != null;
  }

  /** Processes exception events to extract code context */
  private static boolean processExceptionEvent(
      EventData event, AttributesBuilder attributesBuilder) {
    try {
      Attributes eventAttributes = event.getAttributes();

      // Look for exception stacktrace in event attributes
      String stacktrace = eventAttributes.get(AttributeKey.stringKey("exception.stacktrace"));
      if (stacktrace != null && !stacktrace.isEmpty()) {
        return processStacktraceString(stacktrace, attributesBuilder);
      }

      // Look for exception message and try to extract class/method info
      String exceptionType = eventAttributes.get(AttributeKey.stringKey("exception.type"));

      if (exceptionType != null) {
        LOGGER.info("Processing exception event: " + exceptionType);
        // Could enhance this to try to find the source of the exception
        return false; // For now, only process full stacktraces
      }

    } catch (Exception e) {
      LOGGER.warning("Error processing exception event: " + e.getMessage());
    }

    return false;
  }

  /** Processes span-level errors */
  private static boolean processSpanError(SpanData spanData, AttributesBuilder attributesBuilder) {
    try {
      // Get the current thread's stack trace as a fallback
      // This is less ideal but can provide some context
      StackTraceElement[] currentStack = Thread.currentThread().getStackTrace();

      // Filter to find application code in the stack
      for (StackTraceElement element : currentStack) {
        if (isApplicationCode(element.getClassName())
            && !element.getClassName().contains("SpanCodeEnhancer")) {

          List<Map<String, Object>> stackDetails =
              createStackDetails(new StackTraceElement[] {element});
          if (!stackDetails.isEmpty()) {
            addStackDetailsToAttributes(stackDetails, attributesBuilder);
            return true;
          }
        }
      }

    } catch (Exception e) {
      LOGGER.warning("Error processing span error: " + e.getMessage());
    }

    return false;
  }

  /** Processes a stacktrace string to extract code context */
  private static boolean processStacktraceString(
      String stacktrace, AttributesBuilder attributesBuilder) {
    try {
      // Parse the stacktrace string to extract stack trace elements
      List<StackTraceElement> elements = parseStacktraceString(stacktrace);

      if (elements.isEmpty()) {
        return false;
      }

      // Create stack details for all application code frames
      List<Map<String, Object>> stackDetails =
          createStackDetails(elements.toArray(new StackTraceElement[0]));

      if (!stackDetails.isEmpty()) {
        addStackDetailsToAttributes(stackDetails, attributesBuilder);
        return true;
      }

    } catch (Exception e) {
      LOGGER.warning("Error processing stacktrace string: " + e.getMessage());
    }

    return false;
  }

  /** Creates stack details array matching Python implementation format */
  private static List<Map<String, Object>> createStackDetails(StackTraceElement[] stackTrace) {
    List<Map<String, Object>> stackDetailsList = new ArrayList<>();

    for (StackTraceElement element : stackTrace) {
      if (isApplicationCode(element.getClassName())) {
        Map<String, Object> codeContext = CodeExtractionUtils.extractCodeContext(element);
        if (!codeContext.isEmpty()) {
          Map<String, Object> stackDetail = createStackDetailEntry(codeContext, element);
          stackDetailsList.add(stackDetail);

          // Log the extracted details for debugging
          logStackDetailToConsole(stackDetail);
        }
      }
    }

    return stackDetailsList;
  }

  /** Creates a single stack detail entry matching the Python format */
  private static Map<String, Object> createStackDetailEntry(
      Map<String, Object> codeContext, StackTraceElement element) {
    Map<String, Object> stackDetail = new java.util.HashMap<>();

    // Required fields matching Python implementation
    stackDetail.put("exception.function_name", element.getMethodName());
    stackDetail.put("exception.file", element.getFileName());
    stackDetail.put("exception.line", element.getLineNumber());
    stackDetail.put("exception.language", "java");
    stackDetail.put("exception.is_file_external", !isApplicationCode(element.getClassName()));

    // Add method boundaries and code if available
    if (codeContext.containsKey("method_lines")) {
      @SuppressWarnings("unchecked")
      Map<Integer, String> methodLines = (Map<Integer, String>) codeContext.get("method_lines");

      if (!methodLines.isEmpty()) {
        // Find start and end lines
        int startLine =
            methodLines.keySet().stream()
                .mapToInt(Integer::intValue)
                .min()
                .orElse(element.getLineNumber());
        int endLine =
            methodLines.keySet().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(element.getLineNumber());

        stackDetail.put("exception.start_line", startLine);
        stackDetail.put("exception.end_line", endLine);

        // Format the complete method body
        String functionBody = formatMethodBody(methodLines);
        stackDetail.put("exception.function_body", functionBody);
      }
    }

    return stackDetail;
  }

  /** Formats method lines into a single string for function body */
  private static String formatMethodBody(Map<Integer, String> methodLines) {
    StringBuilder sb = new StringBuilder();

    for (Map.Entry<Integer, String> entry : methodLines.entrySet()) {
      String line = entry.getValue();
      sb.append(line).append("\n");
    }

    // Remove trailing newline
    if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
      sb.setLength(sb.length() - 1);
    }

    return sb.toString();
  }

  /** Adds stack details to span attributes as JSON-like string */
  private static void addStackDetailsToAttributes(
      List<Map<String, Object>> stackDetails, AttributesBuilder attributesBuilder) {
    try {
      // Convert stack details to JSON-like string representation
      StringBuilder jsonBuilder = new StringBuilder();
      jsonBuilder.append("[");

      for (int i = 0; i < stackDetails.size(); i++) {
        if (i > 0) {
          jsonBuilder.append(",");
        }

        Map<String, Object> detail = stackDetails.get(i);
        jsonBuilder.append("{");

        boolean first = true;
        for (Map.Entry<String, Object> entry : detail.entrySet()) {
          if (!first) {
            jsonBuilder.append(",");
          }
          first = false;

          jsonBuilder.append("\"").append(entry.getKey()).append("\":");

          Object value = entry.getValue();
          if (value instanceof String) {
            jsonBuilder.append("\"").append(escapeJsonString((String) value)).append("\"");
          } else {
            jsonBuilder.append(value);
          }
        }

        jsonBuilder.append("}");
      }

      jsonBuilder.append("]");

      // Add to span attributes
      attributesBuilder.put(EXCEPTION_STACK_DETAILS, jsonBuilder.toString());

    } catch (Exception e) {
      LOGGER.warning("Error adding stack details to attributes: " + e.getMessage());
    }
  }

  /** Escapes special characters in JSON strings */
  private static String escapeJsonString(String str) {
    if (str == null) {
      return "";
    }

    return str.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  /** Logs stack detail to console for debugging */
  private static void logStackDetailToConsole(Map<String, Object> stackDetail) {
    try {
      System.out.println("=== STACK DETAIL CAPTURED ===");
      System.out.println("Function: " + stackDetail.get("exception.function_name"));
      System.out.println("File: " + stackDetail.get("exception.file"));
      System.out.println("Line: " + stackDetail.get("exception.line"));
      System.out.println("Start Line: " + stackDetail.get("exception.start_line"));
      System.out.println("End Line: " + stackDetail.get("exception.end_line"));
      System.out.println("Language: " + stackDetail.get("exception.language"));
      System.out.println("Is External: " + stackDetail.get("exception.is_file_external"));

      if (stackDetail.containsKey("exception.function_body")) {
        System.out.println("\nFunction Body:");
        System.out.println("-------------");
        System.out.println(stackDetail.get("exception.function_body"));
      }

      System.out.println("=============================");
      System.out.println();

    } catch (Exception e) {
      System.err.println("Error logging stack detail: " + e.getMessage());
    }
  }

  /** Parses a stacktrace string into StackTraceElement objects */
  private static List<StackTraceElement> parseStacktraceString(String stacktrace) {
    List<StackTraceElement> elements = new java.util.ArrayList<>();

    try {
      String[] lines = stacktrace.split("\n");

      for (String line : lines) {
        line = line.trim();
        if (line.startsWith("at ")) {
          StackTraceElement element = parseStackTraceLine(line);
          if (element != null) {
            elements.add(element);
          }
        }
      }

    } catch (Exception e) {
      LOGGER.fine("Error parsing stacktrace string: " + e.getMessage());
    }

    return elements;
  }

  /** Parses a single stack trace line into a StackTraceElement */
  private static StackTraceElement parseStackTraceLine(String line) {
    try {
      // Format: "at com.example.Class.method(File.java:123)"
      line = line.substring(3); // Remove "at "

      int parenIndex = line.lastIndexOf('(');
      if (parenIndex == -1) {
        return null;
      }

      String classAndMethod = line.substring(0, parenIndex);
      String fileAndLine = line.substring(parenIndex + 1, line.length() - 1);

      int lastDotIndex = classAndMethod.lastIndexOf('.');
      if (lastDotIndex == -1) {
        return null;
      }

      String className = classAndMethod.substring(0, lastDotIndex);
      String methodName = classAndMethod.substring(lastDotIndex + 1);

      String fileName = null;
      int lineNumber = -1;

      int colonIndex = fileAndLine.lastIndexOf(':');
      if (colonIndex != -1) {
        fileName = fileAndLine.substring(0, colonIndex);
        try {
          lineNumber = Integer.parseInt(fileAndLine.substring(colonIndex + 1));
        } catch (NumberFormatException e) {
          lineNumber = -1;
        }
      } else {
        fileName = fileAndLine;
      }

      return new StackTraceElement(className, methodName, fileName, lineNumber);

    } catch (Exception e) {
      LOGGER.fine("Error parsing stack trace line: " + line + " - " + e.getMessage());
      return null;
    }
  }

  /** Determines if a class name represents application code */
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

    return true;
  }
}
