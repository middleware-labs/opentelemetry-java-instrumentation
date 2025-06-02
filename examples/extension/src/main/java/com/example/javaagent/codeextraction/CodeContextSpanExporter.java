/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent.codeextraction;

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
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

/**
 * SpanExporter that enriches exception spans with source code context before export. This works
 * because we create new SpanData with additional events/attributes.
 */
public class CodeContextSpanExporter implements SpanExporter {
  private static final Logger LOGGER = Logger.getLogger(CodeContextSpanExporter.class.getName());

  private final SpanExporter delegate;
  private static int processedSpans = 0;
  private static int enhancedSpans = 0;

  public CodeContextSpanExporter(SpanExporter delegate) {
    this.delegate = delegate;
    System.out.println(
        "🚀 CodeContextSpanExporter initialized with delegate: "
            + delegate.getClass().getSimpleName());
  }

  @Override
  public CompletableResultCode export(Collection<SpanData> spans) {
    System.out.println("📦 EXPORTING " + spans.size() + " SPANS");

    // Create enriched spans
    List<SpanData> enrichedSpans = new ArrayList<>();

    for (SpanData span : spans) {
      processedSpans++;

      if (hasExceptions(span)) {
        System.out.println("🔥 FOUND EXCEPTION SPAN: " + span.getName());

        SpanData enrichedSpan = enrichSpanWithCodeContext(span);
        if (enrichedSpan != span) { // Check if enrichment occurred
          enhancedSpans++;
          System.out.println("✅ SUCCESSFULLY ENRICHED SPAN #" + enhancedSpans);
          enrichedSpans.add(enrichedSpan);
        } else {
          System.out.println("❌ Failed to enrich span");
          enrichedSpans.add(span);
        }
      } else {
        // Non-exception span, pass through unchanged
        enrichedSpans.add(span);
      }
    }

    System.out.println(
        "📊 EXPORT SUMMARY: " + processedSpans + " processed, " + enhancedSpans + " enhanced");

    // Export the enriched spans to the delegate
    return delegate.export(enrichedSpans);
  }

  /** Enrich span with code context by creating a new SpanData with additional events */
  private SpanData enrichSpanWithCodeContext(SpanData originalSpan) {
    try {
      System.out.println("🔍 ENRICHING SPAN: " + originalSpan.getName());

      // Find exception events
      List<EventData> originalEvents = originalSpan.getEvents();
      List<EventData> enrichedEvents = new ArrayList<>(originalEvents);

      for (EventData event : originalEvents) {
        if (isExceptionEvent(event)) {
          System.out.println("🚨 Processing exception event: " + event.getName());

          // Extract code context
          Map<String, Object> codeContext = extractCodeContextFromExceptionEvent(event);

          if (!codeContext.isEmpty()) {
            // Create code context event
            EventData codeContextEvent = createCodeContextEvent(codeContext);
            enrichedEvents.add(codeContextEvent);

            System.out.println(
                "✅ Added code context event with " + codeContext.size() + " attributes");

            // Also add as span attributes
            SpanData spanWithAttributes = addCodeContextAttributes(originalSpan, codeContext);

            // Create new SpanData with enriched events
            return createEnrichedSpanData(spanWithAttributes, enrichedEvents);
          }
        }
      }

      return originalSpan; // No enrichment needed

    } catch (Exception e) {
      System.out.println("❌ Error enriching span: " + e.getMessage());
      e.printStackTrace();
      return originalSpan;
    }
  }

  /** Extract code context from exception event */
  private Map<String, Object> extractCodeContextFromExceptionEvent(EventData exceptionEvent) {
    Map<String, Object> context = new HashMap<>();

    try {
      String stacktrace =
          exceptionEvent.getAttributes().get(AttributeKey.stringKey("exception.stacktrace"));

      if (stacktrace == null) {
        System.out.println("❌ No stacktrace in exception event");
        return context;
      }

      // Parse stacktrace for application code
      List<StackTraceElement> appElements = parseStacktraceForAppCode(stacktrace);

      if (appElements.isEmpty()) {
        System.out.println("❌ No application code found in stacktrace");
        return context;
      }

      StackTraceElement primaryElement = appElements.get(0);
      System.out.println(
          "🎯 Primary element: "
              + primaryElement.getClassName()
              + "."
              + primaryElement.getMethodName());

      // Extract source code
      Map<Integer, String> sourceCode = extractMethodCode(primaryElement);

      if (sourceCode.isEmpty()) {
        System.out.println("❌ Could not extract source code");
        return context;
      }

      System.out.println("📝 Extracted " + sourceCode.size() + " lines of source code");

      // Build context
      context.put("function_name", primaryElement.getMethodName());
      context.put("file_name", primaryElement.getFileName());
      context.put("line_number", primaryElement.getLineNumber());
      context.put("class_name", primaryElement.getClassName());
      context.put("function_body", formatSourceCode(sourceCode));
      context.put("extraction_timestamp", Instant.now().toString());
      context.put("extraction_method", "span_exporter");

      return context;

    } catch (Exception e) {
      System.out.println("❌ Error extracting code context: " + e.getMessage());
      return context;
    }
  }

  /** Create a code context event */
  private EventData createCodeContextEvent(Map<String, Object> codeContext) {
    AttributesBuilder attributes = Attributes.builder();

    for (Map.Entry<String, Object> entry : codeContext.entrySet()) {
      String key = "code." + entry.getKey();
      Object value = entry.getValue();

      if (value instanceof String) {
        attributes.put(key, (String) value);
      } else if (value instanceof Integer) {
        attributes.put(key, (Integer) value);
      } else if (value instanceof Long) {
        attributes.put(key, (Long) value);
      } else {
        attributes.put(key, value.toString());
      }
    }

    // Create event data (this is a simplified implementation)
    // In practice, you'd need to implement EventData interface
    return new SimpleEventData("code.context.extracted", Instant.now(), attributes.build());
  }

  /** Add code context as span attributes */
  private SpanData addCodeContextAttributes(
      SpanData originalSpan, Map<String, Object> codeContext) {
    AttributesBuilder newAttributes = originalSpan.getAttributes().toBuilder();

    for (Map.Entry<String, Object> entry : codeContext.entrySet()) {
      String key = "code." + entry.getKey();
      Object value = entry.getValue();

      if (value instanceof String) {
        newAttributes.put(key, (String) value);
      } else if (value instanceof Integer) {
        newAttributes.put(key, (Integer) value);
      } else if (value instanceof Long) {
        newAttributes.put(key, (Long) value);
      } else {
        newAttributes.put(key, value.toString());
      }
    }

    // Create new SpanData with enriched attributes
    return createSpanDataWithNewAttributes(originalSpan, newAttributes.build());
  }

  /** Create enriched SpanData with new events */
  private SpanData createEnrichedSpanData(SpanData originalSpan, List<EventData> enrichedEvents) {
    System.out.println("📋 Creating enriched span data with " + enrichedEvents.size() + " events");
    System.out.println("   Original events: " + originalSpan.getEvents().size());
    System.out.println(
        "   New events: " + (enrichedEvents.size() - originalSpan.getEvents().size()));

    // Create new SpanData with enriched events
    return new EnrichedSpanData(originalSpan, enrichedEvents);
  }

  /** Create new SpanData with different attributes */
  private SpanData createSpanDataWithNewAttributes(
      SpanData originalSpan, Attributes newAttributes) {
    System.out.println("📋 Creating new SpanData with enriched attributes:");
    System.out.println("   Original attributes: " + originalSpan.getAttributes().size());
    System.out.println("   New attributes: " + newAttributes.size());

    // Create new SpanData with enriched attributes
    return new EnrichedSpanData(originalSpan, newAttributes);
  }

  // Helper methods for source code extraction (reused from previous implementations)

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

  private String formatSourceCode(Map<Integer, String> sourceCode) {
    StringBuilder builder = new StringBuilder();
    for (Map.Entry<Integer, String> entry : sourceCode.entrySet()) {
      builder.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
    }
    return builder.toString().trim();
  }

  @Override
  public CompletableResultCode flush() {
    return delegate.flush();
  }

  @Override
  public CompletableResultCode shutdown() {
    System.out.println("🔚 CodeContextSpanExporter shutdown");
    System.out.println("   Total spans processed: " + processedSpans);
    System.out.println("   Total spans enhanced: " + enhancedSpans);
    return delegate.shutdown();
  }

  /** Simple EventData implementation for demonstration */
  private static class SimpleEventData implements EventData {
    private final String name;
    private final Instant timestamp;
    private final Attributes attributes;

    public SimpleEventData(String name, Instant timestamp, Attributes attributes) {
      this.name = name;
      this.timestamp = timestamp;
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
      return timestamp.getEpochSecond() * 1_000_000_000L + timestamp.getNano();
    }

    @Override
    public int getTotalAttributeCount() {
      return attributes.size();
    }
  }

  /** SpanData implementation that wraps original span with enriched events/attributes */
  private static class EnrichedSpanData implements SpanData {
    private final SpanData originalSpan;
    private final List<EventData> enrichedEvents;
    private final Attributes enrichedAttributes;

    // Constructor for enriched events
    public EnrichedSpanData(SpanData originalSpan, List<EventData> enrichedEvents) {
      this.originalSpan = originalSpan;
      this.enrichedEvents = enrichedEvents;
      this.enrichedAttributes = originalSpan.getAttributes();
    }

    // Constructor for enriched attributes
    public EnrichedSpanData(SpanData originalSpan, Attributes enrichedAttributes) {
      this.originalSpan = originalSpan;
      this.enrichedEvents = originalSpan.getEvents();
      this.enrichedAttributes = enrichedAttributes;
    }

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
      return enrichedAttributes; // Return enriched attributes
    }

    @Override
    public List<EventData> getEvents() {
      return enrichedEvents; // Return enriched events
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
      return enrichedAttributes.size();
    }
  }
}
