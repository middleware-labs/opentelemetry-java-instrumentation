/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.server.reactornetty;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import io.netty.channel.ChannelHandlerContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.netty.v4_1.internal.AttributeKeys;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Deque;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

// used since reactor-netty-0.8
public class HttpTrafficHandlerInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("reactor.netty.http.server.HttpTrafficHandler");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("run").and(takesNoArguments()),
        HttpTrafficHandlerInstrumentation.class.getName() + "$RunAdvice");
  }

  @SuppressWarnings("unused")
  public static class RunAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope onEnter(
        @Advice.FieldValue("ctx") ChannelHandlerContext channelHandlerContext) {
      // set context to the first unprocessed request
      Deque<Context> contexts =
          channelHandlerContext.channel().attr(AttributeKeys.SERVER_CONTEXT).get();
      Context context = contexts != null ? contexts.peekFirst() : null;
      if (context != null) {
        return context.makeCurrent();
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Enter Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
