/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.incubator;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.incubator.metrics.ApplicationMeterFactory138Incubator;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class OpenTelemetryIncubatorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("application.io.opentelemetry.api.GlobalOpenTelemetry");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        none(), OpenTelemetryIncubatorInstrumentation.class.getName() + "$InitAdvice");
  }

  @SuppressWarnings({"ReturnValueIgnored", "unused"})
  public static class InitAdvice {
    @Advice.OnMethodEnter
    public static void init() {
      // the sole purpose of this advice is to ensure that ApplicationMeterFactory138Incubator
      // is recognized as helper class and injected into class loader
      ApplicationMeterFactory138Incubator.class.getName();
    }
  }
}
