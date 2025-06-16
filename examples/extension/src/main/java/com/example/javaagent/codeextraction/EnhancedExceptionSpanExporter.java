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
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
  private final boolean debugEnabled;

  public EnhancedExceptionSpanExporter(SpanExporter delegate) {
    this.delegate = delegate;
    this.debugEnabled = LOGGER.isLoggable(Level.FINE);

    if (debugEnabled) {
      LOGGER.fine("🚀 EnhancedExceptionSpanExporter initialized - Python-style format");
    } else {
      LOGGER.info("EnhancedExceptionSpanExporter initialized with exception enrichment");
    }
  }

  @Override
  public CompletableResultCode export(Collection<SpanData> spans) {
    if (debugEnabled) {
      LOGGER.fine("📦 EXPORTING " + spans.size() + " SPANS");
    }

    List<SpanData> enrichedSpans = new ArrayList<>();

    for (SpanData span : spans) {
      processedSpans++;

      if (hasExceptions(span)) {
        if (debugEnabled) {
          LOGGER.fine("🔥 FOUND EXCEPTION SPAN: " + span.getName());
        }

        SpanData enrichedSpan = enrichSpanWithStackDetails(span);
        if (enrichedSpan != span) {
          enhancedSpans++;
          if (debugEnabled) {
            LOGGER.fine("✅ SUCCESSFULLY ENRICHED SPAN #" + enhancedSpans);

            // Debug: Show what's actually being exported
            List<EventData> events = enrichedSpan.getEvents();
            for (EventData event : events) {
              if (event.getName().equals("exception")) {
                String stackDetails =
                    event.getAttributes().get(AttributeKey.stringKey("exception.stack_details"));
                if (stackDetails != null && !stackDetails.equals("[]")) {
                  LOGGER.fine("🎉 CONFIRMED: exception.stack_details present in exported span");
                  LOGGER.fine("📏 Stack details length: " + stackDetails.length() + " characters");

                  // Show a preview of the function body
                  if (stackDetails.contains("exception.function_body")) {
                    LOGGER.fine("📝 Contains function body: ✅");

                    // Extract and show first few characters of function body
                    int bodyStart = stackDetails.indexOf("\"exception.function_body\":\"") + 27;
                    if (bodyStart > 26 && bodyStart < stackDetails.length()) {
                      int bodyEnd =
                          Math.min(bodyStart + 100, stackDetails.indexOf("\"", bodyStart));
                      if (bodyEnd > bodyStart) {
                        String bodyPreview = stackDetails.substring(bodyStart, bodyEnd);
                        LOGGER.fine("📝 Function body preview: " + bodyPreview + "...");
                      }
                    }
                  }
                } else {
                  LOGGER.fine("❌ WARNING: exception.stack_details is empty or missing");
                }
                break;
              }
            }
          }

          enrichedSpans.add(enrichedSpan);
        } else {
          if (debugEnabled) {
            LOGGER.fine("❌ Failed to enrich span");
          }
          enrichedSpans.add(span);
        }
      } else {
        enrichedSpans.add(span);
      }
    }

    if (debugEnabled) {
      LOGGER.fine(
          "📊 EXPORT SUMMARY: " + processedSpans + " processed, " + enhancedSpans + " enhanced");
    }

    return delegate.export(enrichedSpans);
  }

  /** Enrich span by modifying exception events to include stack_details */
  private SpanData enrichSpanWithStackDetails(SpanData originalSpan) {
    try {
      if (debugEnabled) {
        LOGGER.fine("🔍 ENRICHING SPAN WITH STACK DETAILS: " + originalSpan.getName());
      }

      List<EventData> originalEvents = originalSpan.getEvents();
      List<EventData> enrichedEvents = new ArrayList<>();

      boolean eventEnhanced = false;

      for (EventData event : originalEvents) {
        if (isExceptionEvent(event)) {
          if (debugEnabled) {
            LOGGER.fine("🚨 Processing exception event: " + event.getName());
          }

          EventData enrichedEvent = createEnrichedExceptionEvent(event);
          if (enrichedEvent != null) {
            enrichedEvents.add(enrichedEvent);
            eventEnhanced = true;
            if (debugEnabled) {
              LOGGER.fine("✅ Exception event enhanced with stack details");
            }
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
      LOGGER.log(Level.WARNING, "Error enriching span: " + e.getMessage(), e);
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
        if (debugEnabled) {
          LOGGER.fine("❌ No stacktrace found in exception event");
        }
        return null;
      }

      // Extract stack details
      List<Map<String, Object>> stackDetails = extractStackDetails(exceptionStacktrace);

      if (stackDetails.isEmpty()) {
        if (debugEnabled) {
          LOGGER.fine("❌ No application code found in stacktrace");
        }
        return null;
      }

      if (debugEnabled) {
        LOGGER.fine("📝 Extracted " + stackDetails.size() + " stack detail entries");
      }

      // Convert stack details to JSON string
      String stackDetailsJson = convertStackDetailsToJson(stackDetails);

      // Debug: Show the final JSON being exported
      if (debugEnabled) {
        LOGGER.fine("🎯 FINAL JSON BEING EXPORTED:");
        LOGGER.fine("exception.stack_details = " + stackDetailsJson);
      }

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
      LOGGER.log(Level.WARNING, "Error creating enriched exception event: " + e.getMessage(), e);
      return null;
    }
  }

  /** Extract stack details for each application code frame */
  private List<Map<String, Object>> extractStackDetails(String stacktrace) {
    List<Map<String, Object>> stackDetails = new ArrayList<>();

    LOGGER.info("🆕 NEW extractStackDetails method is running!");
    try {
      // Parse stacktrace to get ALL frames first
      List<StackTraceElement> allElements = parseStacktraceForAllCode(stacktrace);

      // Filter to get only application code frames
      List<StackTraceElement> appCodeElements = new ArrayList<>();
      for (StackTraceElement element : allElements) {
        if (isApplicationCode(element.getClassName())) {
          appCodeElements.add(element);
        } else if (debugEnabled) {
          LOGGER.fine(
              "⏭️  Filtering out system class: "
                  + element.getClassName()
                  + "."
                  + element.getMethodName());
        }
      }

      if (debugEnabled) {
        LOGGER.fine(
            "📊 Found "
                + appCodeElements.size()
                + " application code frames (filtered from "
                + allElements.size()
                + " total)");
      }

      // Process only application code frames
      for (StackTraceElement element : appCodeElements) {
        Map<String, Object> stackDetail = createStackDetailEntry(element);
        if (!stackDetail.isEmpty()) {
          stackDetails.add(stackDetail);

          if (debugEnabled) {
            LOGGER.fine(
                "📋 Stack detail: "
                    + element.getMethodName()
                    + " ("
                    + element.getFileName()
                    + ":"
                    + element.getLineNumber()
                    + ")");
          }
        }
      }

    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Error extracting stack details: " + e.getMessage(), e);
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

      if (debugEnabled) {
        LOGGER.fine(
            "   📋 Creating stack detail for: "
                + element.getClassName()
                + "."
                + element.getMethodName());
      }

      // Extract complete function body
      FunctionExtractionResult functionResult = extractCompleteFunction(element);

      if (functionResult.success
          && functionResult.functionBody != null
          && !functionResult.functionBody.trim().isEmpty()) {
        stackDetail.put("exception.start_line", functionResult.startLine);
        stackDetail.put("exception.end_line", functionResult.endLine);
        stackDetail.put("exception.function_body", functionResult.functionBody);

        if (debugEnabled) {
          LOGGER.fine(
              "   ✅ Function body stored: "
                  + functionResult.functionBody.split("\n").length
                  + " lines");
          LOGGER.fine(
              "   📝 First line preview: "
                  + (functionResult.functionBody.split("\n").length > 0
                      ? functionResult.functionBody.split("\n")[0].trim()
                      : "empty"));
        }
      } else {
        if (debugEnabled) {
          LOGGER.fine("   ❌ Function extraction failed:");
          LOGGER.fine("      Success: " + functionResult.success);
          LOGGER.fine(
              "      Body empty: "
                  + (functionResult.functionBody == null
                      || functionResult.functionBody.trim().isEmpty()));
          LOGGER.fine("      Start line: " + functionResult.startLine);
          LOGGER.fine("      End line: " + functionResult.endLine);
        }

        // Add placeholder values for debugging
        stackDetail.put("exception.start_line", functionResult.startLine);
        stackDetail.put("exception.end_line", functionResult.endLine);
        stackDetail.put("exception.function_body", "// Function body extraction failed");
      }

    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Error creating stack detail entry: " + e.getMessage(), e);

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
      if (debugEnabled) {
        LOGGER.fine(
            "   🔍 Extracting function for: "
                + element.getClassName()
                + "."
                + element.getMethodName());
        LOGGER.fine("      File: " + element.getFileName() + ", Line: " + element.getLineNumber());
      }

      if (element.getFileName() == null || element.getLineNumber() <= 0) {
        if (debugEnabled) {
          LOGGER.fine("      ❌ Invalid file name or line number");
        }
        return result;
      }

      // Read source file
      List<String> sourceLines = readSourceFile(element.getClassName(), element.getFileName());
      if (sourceLines.isEmpty()) {
        if (debugEnabled) {
          LOGGER.fine("      ❌ Could not read source file");
        }
        return result;
      }

      if (debugEnabled) {
        LOGGER.fine("      ✅ Source file read: " + sourceLines.size() + " lines");
      }

      // Check if this is application code (skip system classes)
      if (!isApplicationCode(element.getClassName())) {
        if (debugEnabled) {
          LOGGER.fine("      ⏭️  Skipping system class: " + element.getClassName());
        }
        return result;
      }

      // Find method boundaries
      MethodBoundaries boundaries =
          findMethodBoundaries(sourceLines, element.getLineNumber(), element.getMethodName());

      if (boundaries.found) {
        // Extract complete method
        StringBuilder functionBody = new StringBuilder();

        if (debugEnabled) {
          LOGGER.fine(
              "      📏 Method boundaries found: "
                  + boundaries.startLine
                  + "-"
                  + boundaries.endLine);
        }

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

        if (debugEnabled) {
          LOGGER.fine("      ✅ Method extraction SUCCESS");
          LOGGER.fine("      📝 Body length: " + extractedBody.length() + " chars");
          LOGGER.fine("      📝 Lines extracted: " + extractedBody.split("\n").length);

          // Show first line preview
          String[] lines = extractedBody.split("\n");
          if (lines.length > 0) {
            LOGGER.fine("      📝 First line: " + lines[0].trim());
          }
        }

      } else {
        if (debugEnabled) {
          LOGGER.fine("      ❌ Method boundaries not found, falling back to context");
        }
        // Fallback to context lines
        result = extractContextLines(sourceLines, element.getLineNumber());
      }

    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Exception during function extraction: " + e.getMessage(), e);
    }

    return result;
  }

  private MethodBoundaries findMethodBoundaries(
      List<String> sourceLines, int errorLine, String methodName) {
    MethodBoundaries boundaries = new MethodBoundaries();

    try {
      // Validate input parameters
      if (sourceLines == null || sourceLines.isEmpty() || methodName == null) {
        if (debugEnabled) {
          LOGGER.fine(
              "❌ Invalid input: sourceLines="
                  + (sourceLines != null ? sourceLines.size() : "null")
                  + ", methodName="
                  + methodName);
        }
        return boundaries;
      }

      // Check if errorLine is within bounds
      if (errorLine <= 0 || errorLine > sourceLines.size()) {
        if (debugEnabled) {
          LOGGER.fine(
              "❌ Error line "
                  + errorLine
                  + " is out of bounds for file with "
                  + sourceLines.size()
                  + " lines");
        }
        // Try to search entire file for the method instead
        return findMethodInEntireFile(sourceLines, methodName);
      }

      if (debugEnabled) {
        LOGGER.fine(
            "🔍 Searching for method '"
                + methodName
                + "' around line "
                + errorLine
                + " in file with "
                + sourceLines.size()
                + " lines");
      }

      // Find method declaration line (search backwards from error line)
      int methodStartLine = -1;
      int searchStartLine = Math.min(errorLine - 1, sourceLines.size() - 1);

      for (int i = searchStartLine; i >= 0; i--) {
        String line = sourceLines.get(i).trim();

        if (isMethodDeclaration(line, methodName)) {
          methodStartLine = i + 1; // Convert to 1-based
          if (debugEnabled) {
            LOGGER.fine("✅ Found method declaration at line " + methodStartLine + ": " + line);
          }
          break;
        }

        // Stop if we hit another method or class
        if (isAnotherMethodOrClassDeclaration(line, methodName)) {
          if (debugEnabled) {
            LOGGER.fine("⏹️ Hit another method/class declaration, stopping search");
          }
          break;
        }
      }

      if (methodStartLine == -1) {
        if (debugEnabled) {
          LOGGER.fine("❌ Method declaration not found, trying entire file search");
        }
        return findMethodInEntireFile(sourceLines, methodName);
      }

      // Find method end by tracking braces
      int methodEndLine = findMethodEndLine(sourceLines, methodStartLine);

      if (methodEndLine != -1 && methodEndLine <= sourceLines.size()) {
        boundaries.found = true;
        boundaries.startLine = methodStartLine;
        boundaries.endLine = methodEndLine;

        if (debugEnabled) {
          LOGGER.fine("✅ Method boundaries found: " + methodStartLine + "-" + methodEndLine);
        }
      } else {
        if (debugEnabled) {
          LOGGER.fine("❌ Method end not found or out of bounds");
        }
      }

    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Error finding method boundaries: " + e.getMessage(), e);
    }

    return boundaries;
  }

  private MethodBoundaries findMethodInEntireFile(List<String> sourceLines, String methodName) {
    MethodBoundaries boundaries = new MethodBoundaries();

    try {
      if (debugEnabled) {
        LOGGER.fine("🔍 Searching entire file for method: " + methodName);
      }

      for (int i = 0; i < sourceLines.size(); i++) {
        String line = sourceLines.get(i).trim();

        if (isMethodDeclaration(line, methodName)) {
          int methodStartLine = i + 1; // Convert to 1-based
          int methodEndLine = findMethodEndLine(sourceLines, methodStartLine);

          if (methodEndLine != -1 && methodEndLine <= sourceLines.size()) {
            boundaries.found = true;
            boundaries.startLine = methodStartLine;
            boundaries.endLine = methodEndLine;

            if (debugEnabled) {
              LOGGER.fine(
                  "✅ Found method in entire file search: " + methodStartLine + "-" + methodEndLine);
            }
            break;
          }
        }
      }

    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Error in entire file search: " + e.getMessage(), e);
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

    try {
      // Convert to 0-based index and validate
      int startIndex = methodStartLine - 1;
      if (startIndex < 0 || startIndex >= sourceLines.size()) {
        if (debugEnabled) {
          LOGGER.fine(
              "❌ Invalid start index: "
                  + startIndex
                  + " for file with "
                  + sourceLines.size()
                  + " lines");
        }
        return -1;
      }

      for (int i = startIndex; i < sourceLines.size(); i++) {
        String line = sourceLines.get(i);

        for (char c : line.toCharArray()) {
          if (c == '{') {
            openBraces++;
            foundFirstBrace = true;
          } else if (c == '}') {
            openBraces--;

            if (foundFirstBrace && openBraces == 0) {
              int endLine = i + 1; // Convert to 1-based
              if (debugEnabled) {
                LOGGER.fine("✅ Found method end at line " + endLine);
              }
              return endLine;
            }
          }
        }
      }

      if (debugEnabled) {
        LOGGER.fine("❌ Method end not found - unclosed braces or reached end of file");
      }

    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Error finding method end: " + e.getMessage(), e);
    }

    return -1;
  }

  private FunctionExtractionResult extractContextLines(List<String> sourceLines, int errorLine) {
    FunctionExtractionResult result = new FunctionExtractionResult();

    if (debugEnabled) {
      LOGGER.fine("      🔄 Using context lines fallback around line " + errorLine);
    }

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

    if (debugEnabled) {
      LOGGER.fine(
          "      ✅ Context extraction: "
              + startLine
              + "-"
              + endLine
              + " ("
              + extractedContext.split("\n").length
              + " lines)");
    }

    return result;
  }

  /** Convert stack details to JSON string */
  private String convertStackDetailsToJson(List<Map<String, Object>> stackDetails) {
    try {
      return objectMapper.writeValueAsString(stackDetails);
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Error converting stack details to JSON: " + e.getMessage(), e);
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
      // "java.",
      // "javax.",
      // "jakarta",
      // "sun.",
      // "com.sun.",
      // "org.springframework.",
      // "org.apache.",
      // "io.opentelemetry.",
      // "org.slf4j.",
      // "ch.qos.logback.",
      // "org.junit."
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
      // "java.",
      // "javax.",
      // "jakarta",
      // "sun.",
      // "com.sun.",
      // "org.springframework.",
      // "org.apache.",
      // "io.opentelemetry.",
      // "org.slf4j.",
      // "ch.qos.logback.",
      // "org.junit."
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
      // 1. Try local project source paths (your app code)
      String[] localPaths = {"src/main/java", "src/java", "src"};
      for (String basePath : localPaths) {
        Path sourceFile = findFileInDirectory(Paths.get(basePath), fileName);
        if (sourceFile != null && Files.exists(sourceFile)) {
          if (debugEnabled) {
            LOGGER.fine("✅ Found source in local project: " + sourceFile);
          }
          return Files.readAllLines(sourceFile);
        }
      }

      // 2. Try to find Spring Framework sources (if available)
      if (className.startsWith("org.springframework.")) {
        List<String> springSource = tryFindSpringSource(className, fileName);
        if (!springSource.isEmpty()) {
          return springSource;
        }
      }

      // 3. Try to find JDK sources (if available)
      if (className.startsWith("java.") || className.startsWith("jdk.")) {
        List<String> jdkSource = tryFindJdkSource(className, fileName);
        if (!jdkSource.isEmpty()) {
          return jdkSource;
        }
      }

      // 4. Try Maven/Gradle dependencies with sources
      List<String> dependencySource = tryFindDependencySource(className, fileName);
      if (!dependencySource.isEmpty()) {
        return dependencySource;
      }

      if (debugEnabled) {
        LOGGER.fine("❌ No source found for: " + className + " (" + fileName + ")");
      }

    } catch (Exception e) {
      if (debugEnabled) {
        LOGGER.fine("❌ Error reading source for " + className + ": " + e.getMessage());
      }
    }

    return Collections.emptyList();
  }

  private List<String> tryFindJdkSource(String className, String fileName) {
    try {
      // Common JDK source locations
      String[] jdkSourcePaths = {
        System.getProperty("java.home") + "/lib/src.zip",
        System.getProperty("java.home") + "/../src.zip",
        "/usr/lib/jvm/java-*/lib/src.zip",
        "~/.sdkman/candidates/java/*/lib/src.zip"
      };

      for (String sourcePath : jdkSourcePaths) {
        Path sourceZip = Paths.get(sourcePath);
        if (Files.exists(sourceZip)) {
          return extractFromZip(sourceZip, className, fileName);
        }
      }
    } catch (Exception e) {
      // Silent fallback
    }
    return Collections.emptyList();
  }

  private List<String> tryFindSpringSource(String className, String fileName) {
    try {
      // Look for Spring sources in Maven repository
      String userHome = System.getProperty("user.home");
      String[] springSourcePaths = {
        userHome + "/.m2/repository/org/springframework",
        userHome + "/.gradle/caches/modules-2/files-2.1/org.springframework"
      };

      for (String basePath : springSourcePaths) {
        Path springPath = Paths.get(basePath);
        if (Files.exists(springPath)) {
          // Search for sources JAR files
          try {
            List<Path> sourceJars =
                Files.walk(springPath)
                    .filter(path -> path.toString().endsWith("-sources.jar"))
                    .collect(Collectors.toList());

            for (Path sourceJar : sourceJars) {
              List<String> source = extractFromZip(sourceJar, className, fileName);
              if (!source.isEmpty()) {
                return source;
              }
            }
          } catch (Exception e) {
            // Continue searching
          }
        }
      }
    } catch (Exception e) {
      // Silent fallback
    }
    return Collections.emptyList();
  }

  private List<String> tryFindDependencySource(String className, String fileName) {
    try {
      String userHome = System.getProperty("user.home");
      String[] dependencyPaths = {userHome + "/.m2/repository", userHome + "/.gradle/caches"};

      for (String basePath : dependencyPaths) {
        Path depPath = Paths.get(basePath);
        if (Files.exists(depPath)) {
          try {
            List<Path> sourceJars =
                Files.walk(depPath)
                    .filter(path -> path.toString().endsWith("-sources.jar"))
                    .limit(100) // Limit search for performance
                    .collect(Collectors.toList());

            for (Path sourceJar : sourceJars) {
              List<String> source = extractFromZip(sourceJar, className, fileName);
              if (!source.isEmpty()) {
                return source;
              }
            }
          } catch (Exception e) {
            // Continue searching
          }
        }
      }
    } catch (Exception e) {
      // Silent fallback
    }
    return Collections.emptyList();
  }

  private List<String> extractFromZip(Path zipFile, String className, String fileName) {
    try {
      String classPath = className.replace('.', '/') + ".java";

      try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
          if (entry.getName().endsWith(classPath) || entry.getName().endsWith(fileName)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = zis.read(buffer)) > 0) {
              baos.write(buffer, 0, len);
            }
            String content = new String(baos.toByteArray(), StandardCharsets.UTF_8);

            if (debugEnabled) {
              LOGGER.fine("✅ Found source in ZIP: " + zipFile + " -> " + entry.getName());
            }

            return Arrays.asList(content.split("\n"));
          }
        }
      }
    } catch (Exception e) {
      // Silent fallback
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
    if (debugEnabled) {
      LOGGER.fine("🔚 EnhancedExceptionSpanExporter shutdown");
      LOGGER.fine("   Total spans processed: " + processedSpans);
      LOGGER.fine("   Total spans enhanced: " + enhancedSpans);
    } else {
      LOGGER.info(
          "EnhancedExceptionSpanExporter shutdown - "
              + processedSpans
              + " spans processed, "
              + enhancedSpans
              + " enhanced");
    }
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
