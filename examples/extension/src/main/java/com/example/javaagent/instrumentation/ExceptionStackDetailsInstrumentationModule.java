/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent.instrumentation;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;
import com.example.instrumentation.ExceptionStackDetailsInstrumentation;

/**
 * This instrumentation module hooks into Span.recordException() calls to add 
 * detailed stack trace information including function bodies for internal code.
 */
@AutoService(InstrumentationModule.class)
public final class ExceptionStackDetailsInstrumentationModule extends InstrumentationModule {
  public ExceptionStackDetailsInstrumentationModule() {
    super("exception-stack-details");
  }

  /*
  We want this instrumentation to be applied with high priority to ensure
  it captures all exception recording events.
   */
  @Override
  public int order() {
    return Integer.MIN_VALUE; // Very high priority
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // Apply to all class loaders that have OpenTelemetry API
    return net.bytebuddy.matcher.ElementMatchers.any();
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ExceptionStackDetailsInstrumentation());
  }
} 