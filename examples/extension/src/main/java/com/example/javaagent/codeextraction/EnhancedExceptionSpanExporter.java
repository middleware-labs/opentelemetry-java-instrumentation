/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent.codeextraction;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

/**
 * SpanExporter that enriches exception events with detailed stack information matching the Python
 * implementation format with exception.stack_details
 */
public class EnhancedExceptionSpanExporter implements SpanExporter {
  private static final Logger LOGGER =
      Logger.getLogger(EnhancedExceptionSpanExporter.class.getName());

  private final SpanExporter delegate;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private static int processedSpans = 0;
  private static int enhancedSpans = 0;

  public EnhancedExceptionSpanExporter(SpanExporter delegate) {
    this.delegate = delegate;
    System.out.println("🚀 EnhancedExceptionSpanExporter initialized - Python-style format");
  }

  @Override
  public CompletableResultCode export(Collection<SpanData> spans) {
    System.out.println("📦 EXPORTING " + spans.size() + " SPANS");

    List<SpanData> enrichedSpans = new ArrayList<>();

    for (SpanData span : spans) {
      processedSpans++;

      if (hasExceptions(span)) {
        System.out.println("🔥 FOUND EXCEPTION SPAN: " + span.getName());

        SpanData enrichedSpan = enrichSpanWithStackDetails(span);
        if (enrichedSpan != span) {
          enhancedSpans++;
          System.out.println("✅ SUCCESSFULLY ENRICHED SPAN #" + enhancedSpans);

          // Debug: Show what's actually being exported
          List<EventData> events = enrichedSpan.getEvents();
          for (EventData event : events) {
            if (event.getName().equals("exception")) {
              String stackDetails =
                  event.getAttributes().get(AttributeKey.stringKey("exception.stack_details"));
              if (stackDetails != null && !stackDetails.equals("[]")) {
                System.out.println(
                    "🎉 CONFIRMED: exception.stack_details present in exported span");
                System.out.println(
                    "📏 Stack details length: " + stackDetails.length() + " characters");

                // Show a preview of the function body
                if (stackDetails.contains("exception.function_body")) {
                  System.out.println("📝 Contains function body: ✅");

                  // Extract and show first few characters of function body
                  int bodyStart = stackDetails.indexOf("\"exception.function_body\":\"") + 27;
                  if (bodyStart > 26 && bodyStart < stackDetails.length()) {
                    int bodyEnd = Math.min(bodyStart + 100, stackDetails.indexOf("\"", bodyStart));
                    if (bodyEnd > bodyStart) {
                      String bodyPreview = stackDetails.substring(bodyStart, bodyEnd);
                      System.out.println("📝 Function body preview: " + bodyPreview + "...");
                    }
                  }
                }
              } else {
                System.out.println("❌ WARNING: exception.stack_details is empty or missing");
              }
              break;
            }
          }

          enrichedSpans.add(enrichedSpan);
        } else {
          System.out.println("❌ Failed to enrich span");
          enrichedSpans.add(span);
        }
      } else {
        enrichedSpans.add(span);
      }
    }

    System.out.println(
        "📊 EXPORT SUMMARY: " + processedSpans + " processed, " + enhancedSpans + " enhanced");

    return delegate.export(enrichedSpans);
  }

  /** Enrich span by modifying exception events to include stack_details */
  private SpanData enrichSpanWithStackDetails(SpanData originalSpan) {
    try {
      System.out.println("🔍 ENRICHING SPAN WITH STACK DETAILS: " + originalSpan.getName());

      List<EventData> originalEvents = originalSpan.getEvents();
      List<EventData> enrichedEvents = new ArrayList<>();

      boolean eventEnhanced = false;

      for (EventData event : originalEvents) {
        if (isExceptionEvent(event)) {
          System.out.println("🚨 Processing exception event: " + event.getName());

          EventData enrichedEvent = createEnrichedExceptionEvent(event);
          if (enrichedEvent != null) {
            enrichedEvents.add(enrichedEvent);
            eventEnhanced = true;
            System.out.println("✅ Exception event enhanced with stack details");
          } else {
            enrichedEvents.add(event); // Keep original if enhancement failed
          }
        } else {
          enrichedEvents.add(event); // Keep non-exception events unchanged
        }
      }

      if (eventEnhanced) {
        return new EnrichedSpanData(originalSpan, enrichedEvents);
      } else {
        return originalSpan;
      }

    } catch (Exception e) {
      System.out.println("❌ Error enriching span: " + e.getMessage());
      e.printStackTrace();
      return originalSpan;
    }
  }

  /** Create enriched exception event with stack_details attribute */
  private EventData createEnrichedExceptionEvent(EventData originalEvent) {
    try {
      Attributes originalAttributes = originalEvent.getAttributes();

      // Extract original exception data
      String exceptionType = originalAttributes.get(AttributeKey.stringKey("exception.type"));
      String exceptionMessage = originalAttributes.get(AttributeKey.stringKey("exception.message"));
      String exceptionStacktrace =
          originalAttributes.get(AttributeKey.stringKey("exception.stacktrace"));

      if (exceptionStacktrace == null || exceptionStacktrace.isEmpty()) {
        System.out.println("❌ No stacktrace found in exception event");
        return null;
      }

      // Extract stack details
      List<Map<String, Object>> stackDetails = extractStackDetails(exceptionStacktrace);

      if (stackDetails.isEmpty()) {
        System.out.println("❌ No application code found in stacktrace");
        return null;
      }

      System.out.println("📝 Extracted " + stackDetails.size() + " stack detail entries");

      // Convert stack details to JSON string
      String stackDetailsJson = convertStackDetailsToJson(stackDetails);

      // Debug: Show the final JSON being exported
      System.out.println("🎯 FINAL JSON BEING EXPORTED:");
      System.out.println("exception.stack_details = " + stackDetailsJson);

      // Create new attributes with original data + stack_details
      AttributesBuilder newAttributes =
          Attributes.builder()
              .put("exception.type", exceptionType)
              .put("exception.message", exceptionMessage)
              .put("exception.stacktrace", exceptionStacktrace)
              .put("exception.stack_details", stackDetailsJson)
              .put("exception.escaped", true); // Match Python format

      // Add any other original attributes
      originalAttributes.forEach(
          (key, value) -> {
            if (!key.getKey().startsWith("exception.")) {
              if (value instanceof String) {
                newAttributes.put(key.getKey(), (String) value);
              } else if (value instanceof Long) {
                newAttributes.put(key.getKey(), (Long) value);
              } else if (value instanceof Double) {
                newAttributes.put(key.getKey(), (Double) value);
              } else if (value instanceof Boolean) {
                newAttributes.put(key.getKey(), (Boolean) value);
              }
            }
          });

      return new EnrichedEventData(
          originalEvent.getName(), originalEvent.getEpochNanos(), newAttributes.build());

    } catch (Exception e) {
      System.out.println("❌ Error creating enriched exception event: " + e.getMessage());
      return null;
    }
  }

  // /** Extract stack details for each application code frame */
  // private List<Map<String, Object>> extractStackDetails(String stacktrace) {
  //   List<Map<String, Object>> stackDetails = new ArrayList<>();

  //   try {
  //     // Parse stacktrace to get application code frames
  //     List<StackTraceElement> appCodeElements = parseStacktraceForAppCode(stacktrace);

  //     System.out.println("📊 Found " + appCodeElements.size() + " application code frames");

  //     for (StackTraceElement element : appCodeElements) {
  //       // Only process application code to avoid noise from system classes
  //       if (isApplicationCode(element.getClassName())) {
  //         Map<String, Object> stackDetail = createStackDetailEntry(element);
  //         if (!stackDetail.isEmpty()) {
  //           stackDetails.add(stackDetail);

  //           System.out.println(
  //               "📋 Stack detail: "
  //                   + element.getMethodName()
  //                   + " ("
  //                   + element.getFileName()
  //                   + ":"
  //                   + element.getLineNumber()
  //                   + ")");
  //         }
  //       } else {
  //         System.out.println(
  //             "⏭️  Skipping system class: "
  //                 + element.getClassName()
  //                 + "."
  //                 + element.getMethodName());
  //       }
  //     }

  //   } catch (Exception e) {
  //     System.out.println("❌ Error extracting stack details: " + e.getMessage());
  //   }

  //   return stackDetails;
  // }
  /** Extract stack details for each application code frame */
  private List<Map<String, Object>> extractStackDetails(String stacktrace) {
    List<Map<String, Object>> stackDetails = new ArrayList<>();

    LOGGER.info("🆕 NEW extractStackDetails method is running!"); // ADD THIS LINE
    try {
      // Parse stacktrace to get ALL frames first
      List<StackTraceElement> allElements = parseStacktraceForAllCode(stacktrace);

      // Filter to get only application code frames
      List<StackTraceElement> appCodeElements = new ArrayList<>();
      for (StackTraceElement element : allElements) {
        if (isApplicationCode(element.getClassName())) {
          appCodeElements.add(element);
        } else {
          System.out.println(
              "⏭️  Filtering out system class: "
                  + element.getClassName()
                  + "."
                  + element.getMethodName());
        }
      }

      System.out.println(
          "📊 Found "
              + appCodeElements.size()
              + " application code frames (filtered from "
              + allElements.size()
              + " total)");

      // Process only application code frames
      for (StackTraceElement element : appCodeElements) {
        Map<String, Object> stackDetail = createStackDetailEntry(element);
        if (!stackDetail.isEmpty()) {
          stackDetails.add(stackDetail);

          System.out.println(
              "📋 Stack detail: "
                  + element.getMethodName()
                  + " ("
                  + element.getFileName()
                  + ":"
                  + element.getLineNumber()
                  + ")");
        }
      }

    } catch (Exception e) {
      System.out.println("❌ Error extracting stack details: " + e.getMessage());
    }

    return stackDetails;
  }

  /** Create a single stack detail entry matching Python format */
  private Map<String, Object> createStackDetailEntry(StackTraceElement element) {
    Map<String, Object> stackDetail = new HashMap<>();

    try {
      // Basic frame information
      stackDetail.put("exception.function_name", element.getMethodName());
      stackDetail.put("exception.file", element.getFileName());
      stackDetail.put("exception.line", element.getLineNumber());
      stackDetail.put("exception.language", "java");
      stackDetail.put("exception.is_file_external", !isApplicationCode(element.getClassName()));

      System.out.println(
          "   📋 Creating stack detail for: "
              + element.getClassName()
              + "."
              + element.getMethodName());

      // Extract complete function body
      FunctionExtractionResult functionResult = extractCompleteFunction(element);

      if (functionResult.success
          && functionResult.functionBody != null
          && !functionResult.functionBody.trim().isEmpty()) {
        stackDetail.put("exception.start_line", functionResult.startLine);
        stackDetail.put("exception.end_line", functionResult.endLine);
        stackDetail.put("exception.function_body", functionResult.functionBody);

        System.out.println(
            "   ✅ Function body stored: "
                + functionResult.functionBody.split("\n").length
                + " lines");
        System.out.println(
            "   📝 First line preview: "
                + (functionResult.functionBody.split("\n").length > 0
                    ? functionResult.functionBody.split("\n")[0].trim()
                    : "empty"));
      } else {
        System.out.println("   ❌ Function extraction failed:");
        System.out.println("      Success: " + functionResult.success);
        System.out.println(
            "      Body empty: "
                + (functionResult.functionBody == null
                    || functionResult.functionBody.trim().isEmpty()));
        System.out.println("      Start line: " + functionResult.startLine);
        System.out.println("      End line: " + functionResult.endLine);

        // Add placeholder values for debugging
        stackDetail.put("exception.start_line", functionResult.startLine);
        stackDetail.put("exception.end_line", functionResult.endLine);
        stackDetail.put("exception.function_body", "// Function body extraction failed");
      }

    } catch (Exception e) {
      System.out.println("❌ Error creating stack detail entry: " + e.getMessage());
      e.printStackTrace();

      // Add basic info even if extraction fails
      stackDetail.put("exception.start_line", element.getLineNumber());
      stackDetail.put("exception.end_line", element.getLineNumber());
      stackDetail.put("exception.function_body", "// Error during extraction: " + e.getMessage());
    }

    return stackDetail;
  }

  /** Extract complete function body for a stack trace element */
  private FunctionExtractionResult extractCompleteFunction(StackTraceElement element) {
    FunctionExtractionResult result = new FunctionExtractionResult();

    try {
      System.out.println(
          "   🔍 Extracting function for: "
              + element.getClassName()
              + "."
              + element.getMethodName());
      System.out.println(
          "      File: " + element.getFileName() + ", Line: " + element.getLineNumber());

      if (element.getFileName() == null || element.getLineNumber() <= 0) {
        System.out.println("      ❌ Invalid file name or line number");
        return result;
      }

      // Read source file
      List<String> sourceLines = readSourceFile(element.getClassName(), element.getFileName());
      if (sourceLines.isEmpty()) {
        System.out.println("      ❌ Could not read source file");
        return result;
      }

      System.out.println("      ✅ Source file read: " + sourceLines.size() + " lines");

      // Check if this is application code (skip system classes)
      if (!isApplicationCode(element.getClassName())) {
        System.out.println("      ⏭️  Skipping system class: " + element.getClassName());
        return result;
      }

      // Find method boundaries
      MethodBoundaries boundaries =
          findMethodBoundaries(sourceLines, element.getLineNumber(), element.getMethodName());

      if (boundaries.found) {
        // Extract complete method
        StringBuilder functionBody = new StringBuilder();

        System.out.println(
            "      📏 Method boundaries found: " + boundaries.startLine + "-" + boundaries.endLine);

        for (int i = boundaries.startLine; i <= boundaries.endLine; i++) {
          if (i >= 1 && i <= sourceLines.size()) {
            String line = sourceLines.get(i - 1);
            functionBody.append(line).append("\n");
          }
        }

        String extractedBody = functionBody.toString().trim();

        result.success = true;
        result.startLine = boundaries.startLine;
        result.endLine = boundaries.endLine;
        result.functionBody = extractedBody;

        System.out.println("      ✅ Method extraction SUCCESS");
        System.out.println("      📝 Body length: " + extractedBody.length() + " chars");
        System.out.println("      📝 Lines extracted: " + extractedBody.split("\n").length);

        // Show first line preview
        String[] lines = extractedBody.split("\n");
        if (lines.length > 0) {
          System.out.println("      📝 First line: " + lines[0].trim());
        }

      } else {
        System.out.println("      ❌ Method boundaries not found, falling back to context");
        // Fallback to context lines
        result = extractContextLines(sourceLines, element.getLineNumber());
      }

    } catch (Exception e) {
      System.out.println("      ❌ Exception during extraction: " + e.getMessage());
      e.printStackTrace();
    }

    return result;
  }

  /** Find method start and end boundaries */
  private MethodBoundaries findMethodBoundaries(
      List<String> sourceLines, int errorLine, String methodName) {
    MethodBoundaries boundaries = new MethodBoundaries();

    try {
      // Find method declaration line (search backwards from error line)
      int methodStartLine = -1;

      for (int i = errorLine - 1; i >= 0; i--) {
        String line = sourceLines.get(i).trim();

        if (isMethodDeclaration(line, methodName)) {
          methodStartLine = i + 1; // Convert to 1-based
          break;
        }

        // Stop if we hit another method or class
        if (isAnotherMethodOrClassDeclaration(line, methodName)) {
          break;
        }
      }

      if (methodStartLine == -1) {
        return boundaries; // Not found
      }

      // Find method end by tracking braces
      int methodEndLine = findMethodEndLine(sourceLines, methodStartLine);

      if (methodEndLine != -1) {
        boundaries.found = true;
        boundaries.startLine = methodStartLine;
        boundaries.endLine = methodEndLine;
      }

    } catch (Exception e) {
      System.out.println("❌ Error finding method boundaries: " + e.getMessage());
    }

    return boundaries;
  }

  private boolean isMethodDeclaration(String line, String methodName) {
    // Remove comments
    int commentIndex = line.indexOf("//");
    if (commentIndex != -1) {
      line = line.substring(0, commentIndex).trim();
    }

    if (line.isEmpty()) return false;

    // Look for method pattern: [modifiers] [return_type] methodName(
    String methodPattern = "\\b" + methodName + "\\s*\\(";

    if (line.matches(".*" + methodPattern + ".*")) {
      // Ensure it's a method declaration with modifiers or return type
      return line.matches(
              ".*(public|private|protected|static|final|abstract|synchronized).*"
                  + methodPattern
                  + ".*")
          || line.matches(".*\\b\\w+\\s+" + methodPattern + ".*");
    }

    return false;
  }

  private boolean isAnotherMethodOrClassDeclaration(String line, String currentMethodName) {
    int commentIndex = line.indexOf("//");
    if (commentIndex != -1) {
      line = line.substring(0, commentIndex).trim();
    }

    if (line.isEmpty()) return false;

    // Check for class/interface/enum
    if (line.matches(".*\\b(class|interface|enum)\\s+\\w+.*")) {
      return true;
    }

    // Check for other method declarations
    if (line.matches(
        ".*(public|private|protected|static|final|abstract|synchronized).*\\w+\\s*\\(.*")) {
      return !line.contains(currentMethodName + "(");
    }

    return false;
  }

  private int findMethodEndLine(List<String> sourceLines, int methodStartLine) {
    int openBraces = 0;
    boolean foundFirstBrace = false;

    for (int i = methodStartLine - 1; i < sourceLines.size(); i++) {
      String line = sourceLines.get(i);

      for (char c : line.toCharArray()) {
        if (c == '{') {
          openBraces++;
          foundFirstBrace = true;
        } else if (c == '}') {
          openBraces--;

          if (foundFirstBrace && openBraces == 0) {
            return i + 1; // Convert to 1-based
          }
        }
      }
    }

    return -1;
  }

  private FunctionExtractionResult extractContextLines(List<String> sourceLines, int errorLine) {
    FunctionExtractionResult result = new FunctionExtractionResult();

    System.out.println("      🔄 Using context lines fallback around line " + errorLine);

    int contextSize = 5;
    int startLine = Math.max(1, errorLine - contextSize);
    int endLine = Math.min(sourceLines.size(), errorLine + contextSize);

    StringBuilder functionBody = new StringBuilder();
    for (int i = startLine; i <= endLine; i++) {
      if (i >= 1 && i <= sourceLines.size()) {
        functionBody.append(sourceLines.get(i - 1)).append("\n");
      }
    }

    String extractedContext = functionBody.toString().trim();

    result.success = true;
    result.startLine = startLine;
    result.endLine = endLine;
    result.functionBody = extractedContext;

    System.out.println(
        "      ✅ Context extraction: "
            + startLine
            + "-"
            + endLine
            + " ("
            + extractedContext.split("\n").length
            + " lines)");

    return result;
  }

  /** Convert stack details to JSON string */
  private String convertStackDetailsToJson(List<Map<String, Object>> stackDetails) {
    try {
      return objectMapper.writeValueAsString(stackDetails);
    } catch (Exception e) {
      System.out.println("❌ Error converting stack details to JSON: " + e.getMessage());
      return "[]";
    }
  }

  // Helper classes and methods (reusing from previous implementation)

  private static class FunctionExtractionResult {
    boolean success = false;
    int startLine = -1;
    int endLine = -1;
    String functionBody = "";
  }

  private static class MethodBoundaries {
    boolean found = false;
    int startLine = -1;
    int endLine = -1;
  }

  private boolean hasExceptions(SpanData spanData) {
    if (spanData.getStatus().getStatusCode() == StatusCode.ERROR) {
      return true;
    }

    for (EventData event : spanData.getEvents()) {
      if (isExceptionEvent(event)) {
        return true;
      }
    }

    return false;
  }

  private boolean isExceptionEvent(EventData event) {
    String eventName = event.getName().toLowerCase();
    return eventName.contains("exception")
        || eventName.contains("error")
        || event.getAttributes().get(AttributeKey.stringKey("exception.type")) != null;
  }

  // Source code reading methods (reused from previous implementation)

  // private List<StackTraceElement> parseStacktraceForAppCode(String stacktrace) {
  //   List<StackTraceElement> appElements = new ArrayList<>();

  //   String[] lines = stacktrace.split("\n");

  //   for (String line : lines) {
  //     line = line.trim();
  //     if (line.startsWith("at ") && isApplicationCodeLine(line)) {
  //       StackTraceElement element = parseStackTraceLine(line);
  //       if (element != null) {
  //         appElements.add(element);
  //       }
  //     }
  //   }

  //   return appElements;
  // }
  //

  /** Parse stacktrace to get ALL frames (not just app code) */
  private List<StackTraceElement> parseStacktraceForAllCode(String stacktrace) {
    List<StackTraceElement> allElements = new ArrayList<>();

    String[] lines = stacktrace.split("\n");

    for (String line : lines) {
      line = line.trim();
      if (line.startsWith("at ")) { // Get ALL frames, not filtering here
        StackTraceElement element = parseStackTraceLine(line);
        if (element != null) {
          allElements.add(element);
        }
      }
    }

    return allElements;
  }

  private StackTraceElement parseStackTraceLine(String line) {
    try {
      line = line.substring(3); // Remove "at "

      int parenIndex = line.lastIndexOf('(');
      if (parenIndex == -1) return null;

      String classAndMethod = line.substring(0, parenIndex);
      String fileAndLine = line.substring(parenIndex + 1, line.length() - 1);

      int lastDotIndex = classAndMethod.lastIndexOf('.');
      if (lastDotIndex == -1) return null;

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
      return null;
    }
  }

  private boolean isApplicationCodeLine(String stacktraceLine) {
    if (stacktraceLine == null) return false;

    String[] systemPackages = {
      "java.",
      "javax.",
      "jakarta",
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
      if (stacktraceLine.contains(systemPackage)) {
        return false;
      }
    }

    return true;
  }

  private boolean isApplicationCode(String className) {
    if (className == null) return false;

    String[] systemPackages = {
      "java.",
      "javax.",
      "jakarta",
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

  private List<String> readSourceFile(String className, String fileName) {
    try {
      String[] searchPaths = {"src/main/java", "src/java", "src"};

      for (String basePath : searchPaths) {
        Path sourceFile = findFileInDirectory(Paths.get(basePath), fileName);
        if (sourceFile != null && Files.exists(sourceFile)) {
          return Files.readAllLines(sourceFile);
        }
      }
    } catch (Exception e) {
      // Silent failure
    }

    return Collections.emptyList();
  }

  private Path findFileInDirectory(Path directory, String fileName) {
    try {
      return Files.walk(directory)
          .filter(path -> path.getFileName().toString().equals(fileName))
          .findFirst()
          .orElse(null);
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public CompletableResultCode flush() {
    return delegate.flush();
  }

  @Override
  public CompletableResultCode shutdown() {
    System.out.println("🔚 EnhancedExceptionSpanExporter shutdown");
    System.out.println("   Total spans processed: " + processedSpans);
    System.out.println("   Total spans enhanced: " + enhancedSpans);
    return delegate.shutdown();
  }

  /** Enhanced EventData implementation */
  private static class EnrichedEventData implements EventData {
    private final String name;
    private final long epochNanos;
    private final Attributes attributes;

    public EnrichedEventData(String name, long epochNanos, Attributes attributes) {
      this.name = name;
      this.epochNanos = epochNanos;
      this.attributes = attributes;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public Attributes getAttributes() {
      return attributes;
    }

    @Override
    public long getEpochNanos() {
      return epochNanos;
    }

    @Override
    public int getTotalAttributeCount() {
      return attributes.size();
    }
  }

  /** Enhanced SpanData implementation that wraps original span with enriched events */
  private static class EnrichedSpanData implements SpanData {
    private final SpanData originalSpan;
    private final List<EventData> enrichedEvents;

    public EnrichedSpanData(SpanData originalSpan, List<EventData> enrichedEvents) {
      this.originalSpan = originalSpan;
      this.enrichedEvents = enrichedEvents;
    }

    @Override
    public List<EventData> getEvents() {
      return enrichedEvents;
    }

    // Delegate all other methods to original span
    @Override
    public String getName() {
      return originalSpan.getName();
    }

    @Override
    public io.opentelemetry.api.trace.SpanKind getKind() {
      return originalSpan.getKind();
    }

    @Override
    public io.opentelemetry.api.trace.SpanContext getSpanContext() {
      return originalSpan.getSpanContext();
    }

    @Override
    public io.opentelemetry.api.trace.SpanContext getParentSpanContext() {
      return originalSpan.getParentSpanContext();
    }

    @Override
    public io.opentelemetry.sdk.resources.Resource getResource() {
      return originalSpan.getResource();
    }

    @Override
    public io.opentelemetry.sdk.common.InstrumentationScopeInfo getInstrumentationScopeInfo() {
      return originalSpan.getInstrumentationScopeInfo();
    }

    @Override
    public io.opentelemetry.sdk.common.InstrumentationLibraryInfo getInstrumentationLibraryInfo() {
      return originalSpan.getInstrumentationLibraryInfo();
    }

    @Override
    public Attributes getAttributes() {
      return originalSpan.getAttributes();
    }

    @Override
    public List<io.opentelemetry.sdk.trace.data.LinkData> getLinks() {
      return originalSpan.getLinks();
    }

    @Override
    public io.opentelemetry.sdk.trace.data.StatusData getStatus() {
      return originalSpan.getStatus();
    }

    @Override
    public long getStartEpochNanos() {
      return originalSpan.getStartEpochNanos();
    }

    @Override
    public long getEndEpochNanos() {
      return originalSpan.getEndEpochNanos();
    }

    @Override
    public boolean hasEnded() {
      return originalSpan.hasEnded();
    }

    @Override
    public int getTotalRecordedEvents() {
      return enrichedEvents.size();
    }

    @Override
    public int getTotalRecordedLinks() {
      return originalSpan.getTotalRecordedLinks();
    }

    @Override
    public int getTotalAttributeCount() {
      return originalSpan.getTotalAttributeCount();
    }
  }
}
