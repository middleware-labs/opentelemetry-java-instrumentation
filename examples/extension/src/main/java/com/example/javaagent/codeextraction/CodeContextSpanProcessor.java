/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent.codeextraction;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class CodeContextSpanProcessor implements SpanProcessor {
  private static final Logger LOGGER = Logger.getLogger(CodeContextSpanProcessor.class.getName());

  private final Map<String, Integer> spanEventCounts = new ConcurrentHashMap<>();
  private final Set<String> enhancedSpans = ConcurrentHashMap.newKeySet();

  private static int startCount = 0;
  private static int endCount = 0;
  private static int interceptCount = 0;

  @Override
  public void onStart(Context parentContext, ReadWriteSpan span) {
    startCount++;

    String spanId = span.getSpanContext().getSpanId();
    spanEventCounts.put(spanId, 0);

    if (startCount <= 3) {
      System.out.println("=== STEP 6: SPAN START #" + startCount + " ===");
      System.out.println("Span name: " + span.getName());
      System.out.println("Span ID: " + spanId);
      System.out.println("=======================================");
    }
  }

  @Override
  public boolean isStartRequired() {
    return true;
  }

  @Override
  public void onEnd(ReadableSpan span) {
    endCount++;
    String spanId = span.getSpanContext().getSpanId();

    System.out.println("=== STEP 6: SPAN END #" + endCount + " ===");
    System.out.println("Span name: " + span.getName());

    // IMMEDIATE check - before any processing
    SpanData initialSpanData = span.toSpanData();
    System.out.println("📊 INITIAL EVENT COUNT: " + initialSpanData.getEvents().size());

    // Process new exception events
    boolean enhanced = checkForNewExceptionEvents(span);

    // AFTER processing check
    SpanData finalSpanData = span.toSpanData();
    System.out.println("📊 FINAL EVENT COUNT: " + finalSpanData.getEvents().size());
    System.out.println("📊 ENHANCEMENT ATTEMPTED: " + (enhanced ? "✅" : "❌"));

    if (hasExceptions(finalSpanData)) {
      System.out.println("🚨 EXCEPTION SPAN ANALYSIS:");

      // List all events with details
      int eventIndex = 0;
      for (EventData event : finalSpanData.getEvents()) {
        eventIndex++;
        System.out.println("  Event #" + eventIndex + ": " + event.getName());
        System.out.println("    Attributes: " + event.getAttributes().size());

        if (event.getName().equals("code.context.extracted")) {
          System.out.println("    🎯 THIS IS OUR CODE CONTEXT EVENT!");
          displayCodeContextEvent(event);
        } else if (isExceptionEvent(event)) {
          System.out.println("    🚨 This is an exception event");
        }
      }

      // Final verdict
      boolean hasCodeContext = checkForCodeContextEvents(finalSpanData);
      System.out.println("📋 FINAL VERDICT - Code context added: " + (hasCodeContext ? "✅" : "❌"));
    }

    System.out.println("========================================");

    // Cleanup
    spanEventCounts.remove(spanId);
    enhancedSpans.remove(spanId);
  }

  @Override
  public boolean isEndRequired() {
    return true;
  }

  /** Check for new exception events and process them - return true if enhancement was attempted */
  private boolean checkForNewExceptionEvents(ReadableSpan span) {
    try {
      String spanId = span.getSpanContext().getSpanId();
      SpanData spanData = span.toSpanData();

      Integer lastKnownCount = spanEventCounts.get(spanId);
      if (lastKnownCount == null) {
        lastKnownCount = 0;
      }

      List<EventData> events = spanData.getEvents();
      int currentCount = events.size();

      System.out.println("📊 Event count check: " + lastKnownCount + " → " + currentCount);

      // Check if there are new events
      if (currentCount > lastKnownCount) {
        System.out.println("🔍 New events detected: " + (currentCount - lastKnownCount));

        // Process new events
        for (int i = lastKnownCount; i < currentCount; i++) {
          EventData event = events.get(i);

          if (isExceptionEvent(event) && !enhancedSpans.contains(spanId)) {
            System.out.println("🚨 NEW EXCEPTION EVENT DETECTED!");

            // TEST MULTIPLE STRATEGIES
            boolean success = testMultipleEnhancementStrategies(span, event);

            if (success) {
              enhancedSpans.add(spanId);
            }

            spanEventCounts.put(spanId, currentCount);
            return true; // Enhancement was attempted
          }
        }

        spanEventCounts.put(spanId, currentCount);
      }

      return false; // No enhancement attempted

    } catch (Exception e) {
      System.out.println("❌ Error checking for new events: " + e.getMessage());
      return false;
    }
  }

  /** Test multiple enhancement strategies to see what works */
  private boolean testMultipleEnhancementStrategies(ReadableSpan span, EventData exceptionEvent) {
    interceptCount++;

    System.out.println("🧪 TESTING MULTIPLE ENHANCEMENT STRATEGIES #" + interceptCount);

    // Extract code context first
    Map<Integer, String> sourceCode = extractCodeContextFromEvent(exceptionEvent);
    if (sourceCode.isEmpty()) {
      System.out.println("❌ Could not extract source code - aborting enhancement");
      return false;
    }

    StackTraceElement primaryElement = getPrimaryStackElement(exceptionEvent);
    if (primaryElement == null) {
      System.out.println("❌ Could not get primary stack element - aborting enhancement");
      return false;
    }

    System.out.println(
        "📋 Code context extracted: "
            + sourceCode.size()
            + " lines from "
            + primaryElement.getMethodName());

    boolean anySuccess = false;

    // STRATEGY 1: Try as ReadWriteSpan with addEvent
    if (span instanceof ReadWriteSpan) {
      System.out.println("🧪 STRATEGY 1: ReadWriteSpan.addEvent()");
      try {
        ReadWriteSpan writableSpan = (ReadWriteSpan) span;

        AttributesBuilder attributes =
            Attributes.builder()
                .put("code.function.name", primaryElement.getMethodName())
                .put("code.file.name", primaryElement.getFileName())
                .put("code.line.number", primaryElement.getLineNumber())
                .put("code.function.body", formatSourceCode(sourceCode))
                .put("code.strategy", "readwrite_addevent");

        writableSpan.addEvent("code.context.extracted", attributes.build());

        System.out.println("✅ STRATEGY 1 SUCCESS: addEvent() completed without exception");
        anySuccess = true;

        // Immediate verification
        SpanData verifyData = span.toSpanData();
        boolean found = checkForCodeContextEvents(verifyData);
        System.out.println("🔍 IMMEDIATE VERIFICATION: Code context found = " + found);

      } catch (Exception e) {
        System.out.println("❌ STRATEGY 1 FAILED: " + e.getMessage());
      }
    } else {
      System.out.println("❌ STRATEGY 1 SKIPPED: Span is not ReadWriteSpan");
    }

    // STRATEGY 2: Try setAttribute on ReadWriteSpan
    if (span instanceof ReadWriteSpan) {
      System.out.println("🧪 STRATEGY 2: ReadWriteSpan.setAttribute()");
      try {
        ReadWriteSpan writableSpan = (ReadWriteSpan) span;

        writableSpan.setAttribute("code.function.name", primaryElement.getMethodName());
        writableSpan.setAttribute("code.file.name", primaryElement.getFileName());
        writableSpan.setAttribute("code.line.number", primaryElement.getLineNumber());
        writableSpan.setAttribute("code.strategy", "readwrite_setattribute");

        System.out.println("✅ STRATEGY 2 SUCCESS: setAttribute() completed without exception");
        anySuccess = true;

      } catch (Exception e) {
        System.out.println("❌ STRATEGY 2 FAILED: " + e.getMessage());
      }
    }

    // STRATEGY 3: Try reflection-based approach
    System.out.println("🧪 STRATEGY 3: Reflection-based enhancement");
    try {
      // This is just a test - we'll log that we tried reflection
      System.out.println("📝 STRATEGY 3: Would use reflection to modify span internals");
      System.out.println("   (Not implemented in this test, but logged as attempted)");

    } catch (Exception e) {
      System.out.println("❌ STRATEGY 3 FAILED: " + e.getMessage());
    }

    return anySuccess;
  }

  /** Extract code context from exception event */
  private Map<Integer, String> extractCodeContextFromEvent(EventData exceptionEvent) {
    String exceptionStacktrace =
        exceptionEvent.getAttributes().get(AttributeKey.stringKey("exception.stacktrace"));

    if (exceptionStacktrace == null) {
      return Collections.emptyMap();
    }

    List<StackTraceElement> appCodeElements = parseStacktraceForAppCode(exceptionStacktrace);

    if (appCodeElements.isEmpty()) {
      return Collections.emptyMap();
    }

    StackTraceElement primaryElement = appCodeElements.get(0);
    return extractMethodCode(primaryElement);
  }

  /** Get primary stack trace element from exception event */
  private StackTraceElement getPrimaryStackElement(EventData exceptionEvent) {
    String exceptionStacktrace =
        exceptionEvent.getAttributes().get(AttributeKey.stringKey("exception.stacktrace"));

    if (exceptionStacktrace == null) {
      return null;
    }

    List<StackTraceElement> appCodeElements = parseStacktraceForAppCode(exceptionStacktrace);

    return appCodeElements.isEmpty() ? null : appCodeElements.get(0);
  }

  /** Format source code map as a string */
  private String formatSourceCode(Map<Integer, String> sourceCode) {
    StringBuilder builder = new StringBuilder();
    for (Map.Entry<Integer, String> entry : sourceCode.entrySet()) {
      builder.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
    }
    return builder.toString().trim();
  }

  /** Display code context event details */
  private void displayCodeContextEvent(EventData event) {
    String functionName = event.getAttributes().get(AttributeKey.stringKey("code.function.name"));
    String fileName = event.getAttributes().get(AttributeKey.stringKey("code.file.name"));
    Long lineNumber = event.getAttributes().get(AttributeKey.longKey("code.line.number"));
    String strategy = event.getAttributes().get(AttributeKey.stringKey("code.strategy"));

    System.out.println("      🎯 Function: " + functionName);
    System.out.println("      🎯 File: " + fileName + ":" + lineNumber);
    System.out.println("      🎯 Strategy: " + strategy);
  }

  // Helper methods (same as before)

  private boolean hasExceptions(SpanData spanData) {
    if (spanData.getStatus().getStatusCode() == io.opentelemetry.api.trace.StatusCode.ERROR) {
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

  private boolean checkForCodeContextEvents(SpanData spanData) {
    for (EventData event : spanData.getEvents()) {
      if (event.getName().equals("code.context.extracted")) {
        return true;
      }
    }
    return false;
  }

  // Source code extraction methods (reused from previous steps)

  private List<StackTraceElement> parseStacktraceForAppCode(String stacktrace) {
    List<StackTraceElement> appElements = new ArrayList<>();

    String[] lines = stacktrace.split("\n");

    for (String line : lines) {
      line = line.trim();
      if (line.startsWith("at ") && isApplicationCodeLine(line)) {
        StackTraceElement element = parseStackTraceLine(line);
        if (element != null) {
          appElements.add(element);
        }
      }
    }

    return appElements;
  }

  private StackTraceElement parseStackTraceLine(String line) {
    try {
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
      return null;
    }
  }

  private boolean isApplicationCodeLine(String stacktraceLine) {
    if (stacktraceLine == null) return false;

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
      if (stacktraceLine.contains(systemPackage)) {
        return false;
      }
    }

    return true;
  }

  private Map<Integer, String> extractMethodCode(StackTraceElement element) {
    if (element.getFileName() == null || element.getLineNumber() <= 0) {
      return Collections.emptyMap();
    }

    List<String> sourceLines = readSourceFile(element.getClassName(), element.getFileName());

    if (sourceLines.isEmpty()) {
      return Collections.emptyMap();
    }

    // Extract ±5 lines around the error
    Map<Integer, String> methodCode = new LinkedHashMap<>();
    int startLine = Math.max(1, element.getLineNumber() - 5);
    int endLine = Math.min(sourceLines.size(), element.getLineNumber() + 5);

    for (int i = startLine; i <= endLine; i++) {
      if (i >= 1 && i <= sourceLines.size()) {
        methodCode.put(i, sourceLines.get(i - 1));
      }
    }

    return methodCode;
  }

  private List<String> readSourceFile(String className, String fileName) {
    // Try project structure first
    List<String> lines = readFromProjectStructure(fileName);
    if (!lines.isEmpty()) {
      return lines;
    }

    return Collections.emptyList();
  }

  private List<String> readFromProjectStructure(String fileName) {
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
  public CompletableResultCode shutdown() {
    System.out.println("=== STEP 6: PROCESSOR SHUTDOWN ===");
    System.out.println("Total spans started: " + startCount);
    System.out.println("Total spans ended: " + endCount);
    System.out.println("Exception events processed: " + interceptCount);
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode forceFlush() {
    return CompletableResultCode.ofSuccess();
  }
}
