/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

public class ExceptionStackDetailsTest {

  public static void main(String[] args) {
    Tracer tracer = GlobalOpenTelemetry.getTracer("exception-stack-details-test");
    
    Span span = tracer.spanBuilder("test-span").startSpan();
    
    try {
      // Call a method that will throw an exception
      methodThatThrows();
    } catch (Exception e) {
      // Record the exception - this should trigger our instrumentation
      span.recordException(e);
    } finally {
      span.end();
    }
    
    System.out.println("Test completed - check telemetry output for exception.stack_details");
  }
  
  private static void methodThatThrows() {
    nestedMethod();
  }
  
  private static void nestedMethod() {
    throw new RuntimeException("Test exception for stack details");
  }
} 