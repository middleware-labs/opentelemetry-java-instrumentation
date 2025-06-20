/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent.benchmarking;

import com.example.javaagent.codeextraction.EnhancedExceptionSpanExporter;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/** Utility class to benchmark the performance of exception enrichment */
public class ExceptionEnrichmentPerformanceTest {
  private static final Logger LOGGER =
      Logger.getLogger(ExceptionEnrichmentPerformanceTest.class.getName());

  public static void main(String[] args) {
    LOGGER.info("Starting Exception Enrichment Performance Test");

    // Test parameters
    int warmupRuns = 1;
    int testRuns = 1;
    int spansPerRun = 5;

    // Create test data
    List<SpanData> testSpans = createTestSpans(spansPerRun);

    // Create mock exporter
    MockSpanExporter mockExporter = new MockSpanExporter();

    // Test current exporter
    LOGGER.info("Testing EnhancedExceptionSpanExporter...");
    EnhancedExceptionSpanExporter exporter = new EnhancedExceptionSpanExporter(mockExporter);
    long totalTime = runBenchmark(exporter, testSpans, warmupRuns, testRuns);

    // Print results
    printResults(totalTime, testRuns, spansPerRun);

    // Print detailed benchmark stats
    PerformanceBenchmark.printStats();

    // Cleanup
    exporter.shutdown();
  }

  private static long runBenchmark(
      SpanExporter exporter, List<SpanData> testSpans, int warmupRuns, int testRuns) {
    LOGGER.info("Starting warmup runs: " + warmupRuns);

    // Warmup
    for (int i = 0; i < warmupRuns; i++) {
      exporter.export(testSpans);
    }

    LOGGER.info("Starting test runs: " + testRuns);

    // Reset benchmark counters after warmup
    PerformanceBenchmark.reset();

    // Actual test
    long startTime = System.nanoTime();
    for (int i = 0; i < testRuns; i++) {
      exporter.export(testSpans);

      if ((i + 1) % 10 == 0) {
        LOGGER.info("Completed " + (i + 1) + "/" + testRuns + " test runs");
      }
    }
    long endTime = System.nanoTime();

    return TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
  }

  private static void printResults(long totalTime, int testRuns, int spansPerRun) {
    LOGGER.info("=== PERFORMANCE TEST RESULTS ===");
    LOGGER.info("Test runs: " + testRuns);
    LOGGER.info("Spans per run: " + spansPerRun);
    LOGGER.info("Total spans processed: " + (testRuns * spansPerRun));
    LOGGER.info("");
    LOGGER.info("Total Test Time: " + totalTime + " ms");
    LOGGER.info("Average per run: " + (totalTime / testRuns) + " ms");
    LOGGER.info("Average per span: " + (totalTime / (testRuns * spansPerRun)) + " ms");
    LOGGER.info("================================");
  }

  private static List<SpanData> createTestSpans(int count) {
    List<SpanData> spans = new ArrayList<>();

    for (int i = 0; i < count; i++) {
      // Create spans with and without exceptions
      if (i % 2 == 0) {
        // Exception span
        spans.add(createExceptionSpan("test-exception-span-" + i));
      } else {
        // Normal span
        spans.add(createNormalSpan("test-normal-span-" + i));
      }
    }

    return spans;
  }

  private static SpanData createExceptionSpan(String spanName) {
    return new TestSpanData(spanName, true);
  }

  private static SpanData createNormalSpan(String spanName) {
    return new TestSpanData(spanName, false);
  }

  /** Mock SpanExporter for testing */
  private static class MockSpanExporter implements SpanExporter {
    private int exportCount = 0;

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
      exportCount++;
      // Simulate minimal work (no artificial delay)
      return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {
      return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
      return CompletableResultCode.ofSuccess();
    }

    public int getExportCount() {
      return exportCount;
    }
  }

  /** Test implementation of SpanData */
  private static class TestSpanData implements SpanData {
    private final String name;
    private final boolean hasException;
    private final List<EventData> events;

    public TestSpanData(String name, boolean hasException) {
      this.name = name;
      this.hasException = hasException;
      this.events = hasException ? createExceptionEvents() : Collections.emptyList();
    }

    private List<EventData> createExceptionEvents() {
      List<EventData> events = new ArrayList<>();

      // Create a realistic exception event
      Attributes exceptionAttributes =
          Attributes.builder()
              .put("exception.type", "java.lang.RuntimeException")
              .put("exception.message", "Test exception for performance testing")
              .put("exception.stacktrace", createRealisticStackTrace())
              .build();

      events.add(new TestEventData("exception", System.nanoTime(), exceptionAttributes));
      return events;
    }

    private String createRealisticStackTrace() {
      return "java.lang.RuntimeException: Test exception for performance testing\n"
          + "\tat com.example.service.UserService.processUser(UserService.java:42)\n"
          + "\tat com.example.controller.UserController.createUser(UserController.java:28)\n"
          + "\tat com.example.controller.UserController$$FastClassBySpringCGLIB$$1a2b3c4d.invoke(<generated>)\n"
          + "\tat org.springframework.cglib.proxy.MethodProxy.invoke(MethodProxy.java:218)\n"
          + "\tat org.springframework.aop.framework.CglibAopProxy$CglibMethodInvocation.invokeJoinpoint(CglibAopProxy.java:771)\n"
          + "\tat org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:163)\n"
          + "\tat org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:708)\n"
          + "\tat com.example.controller.UserController$$EnhancerBySpringCGLIB$$5e6f7g8h.createUser(<generated>)\n"
          + "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\n"
          + "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)\n"
          + "\tat java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\n"
          + "\tat java.base/java.lang.reflect.Method.invoke(Method.java:568)\n"
          + "\tat org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:205)\n"
          + "\tat org.springframework.web.method.support.InvocableHandlerMethod.invokeForRequest(InvocableHandlerMethod.java:150)\n"
          + "\tat org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:117)\n"
          + "\tat org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.invokeHandlerMethod(RequestMappingHandlerAdapter.java:895)\n"
          + "\tat org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.handleInternal(RequestMappingHandlerAdapter.java:808)\n"
          + "\tat org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter.handle(AbstractHandlerMethodAdapter.java:87)\n"
          + "\tat org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1067)\n"
          + "\tat org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:963)\n"
          + "\tat org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1006)";
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public SpanKind getKind() {
      return SpanKind.SERVER;
    }

    @Override
    public SpanContext getSpanContext() {
      return SpanContext.getInvalid();
    }

    @Override
    public SpanContext getParentSpanContext() {
      return SpanContext.getInvalid();
    }

    @Override
    public Resource getResource() {
      return Resource.getDefault();
    }

    @Override
    public InstrumentationScopeInfo getInstrumentationScopeInfo() {
      return InstrumentationScopeInfo.create("test-instrumentation");
    }

    @Override
    public io.opentelemetry.sdk.common.InstrumentationLibraryInfo getInstrumentationLibraryInfo() {
      return io.opentelemetry.sdk.common.InstrumentationLibraryInfo.create(
          "test-instrumentation", "1.0.0");
    }

    @Override
    public Attributes getAttributes() {
      return Attributes.empty();
    }

    @Override
    public List<LinkData> getLinks() {
      return Collections.emptyList();
    }

    @Override
    public List<EventData> getEvents() {
      return events;
    }

    @Override
    public StatusData getStatus() {
      return hasException ? StatusData.error() : StatusData.ok();
    }

    @Override
    public long getStartEpochNanos() {
      return System.nanoTime() - 1000000; // 1ms ago
    }

    @Override
    public long getEndEpochNanos() {
      return System.nanoTime();
    }

    @Override
    public boolean hasEnded() {
      return true;
    }

    @Override
    public int getTotalRecordedEvents() {
      return events.size();
    }

    @Override
    public int getTotalRecordedLinks() {
      return 0;
    }

    @Override
    public int getTotalAttributeCount() {
      return 0;
    }
  }

  /** Test implementation of EventData */
  private static class TestEventData implements EventData {
    private final String name;
    private final long epochNanos;
    private final Attributes attributes;

    public TestEventData(String name, long epochNanos, Attributes attributes) {
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
}
