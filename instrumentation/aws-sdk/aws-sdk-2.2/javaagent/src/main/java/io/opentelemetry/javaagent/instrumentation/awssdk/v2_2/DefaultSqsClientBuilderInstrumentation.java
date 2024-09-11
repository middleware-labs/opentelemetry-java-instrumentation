/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v2_2;

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.instrumentation.awssdk.v2_2.autoconfigure.AwsSdkSingletons;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import software.amazon.awssdk.services.sqs.SqsClient;

public class DefaultSqsClientBuilderInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("software.amazon.awssdk.services.sqs.DefaultSqsClientBuilder");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("buildClient"), this.getClass().getName() + "$BuildClientAdvice");
  }

  @SuppressWarnings("unused")
  public static class BuildClientAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void methodExit(@Advice.Return(readOnly = false) SqsClient sqsClient) {
      sqsClient = AwsSdkSingletons.telemetry().wrap(sqsClient);
    }
  }
}
