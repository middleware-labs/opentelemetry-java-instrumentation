/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.dispatch.ExecutionContexts
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.javadsl.model.HttpHeader
import org.apache.pekko.http.scaladsl.settings.ClientConnectionSettings
import org.apache.pekko.http.scaladsl.model.headers.RawHeader
import org.apache.pekko.http.scaladsl.settings.ConnectionPoolSettings
import org.apache.pekko.stream.ActorMaterializer
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.http.{
  AbstractHttpClientTest,
  HttpClientInstrumentationExtension,
  HttpClientResult,
  HttpClientTestOptions,
  SingleConnection
}

import java.net.URI
import java.util
import java.util.concurrent.Executor
import org.junit.jupiter.api.extension.RegisterExtension

import java.util.function.BiFunction
import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

class PekkoHttpClientInstrumentationTest
    extends AbstractHttpClientTest[HttpRequest] {

  @RegisterExtension val extension: InstrumentationExtension =
    HttpClientInstrumentationExtension.forAgent()

  val system: ActorSystem = ActorSystem.create()
  implicit val materializer: ActorMaterializer =
    ActorMaterializer.create(system)

  override def buildRequest(
      method: String,
      uri: URI,
      h: util.Map[String, String]
  ): HttpRequest =
    HttpRequest(HttpMethods.getForKey(method).get)
      .withUri(uri.toString)
      .addHeaders(
        h.entrySet()
          .asScala
          .map(e => RawHeader(e.getKey, e.getValue): HttpHeader)
          .asJava
      )

  override def sendRequest(
      request: HttpRequest,
      method: String,
      uri: URI,
      headers: util.Map[String, String]
  ): Int = {
    val settings = ConnectionPoolSettings(system)
      .withConnectionSettings(
        ClientConnectionSettings(system)
          .withConnectingTimeout(
            FiniteDuration(
              AbstractHttpClientTest.CONNECTION_TIMEOUT.toMillis,
              MILLISECONDS
            )
          )
          .withIdleTimeout(
            FiniteDuration(
              AbstractHttpClientTest.READ_TIMEOUT.toMillis,
              MILLISECONDS
            )
          )
      )
    val response = Await.result(
      Http.get(system).singleRequest(request, settings = settings),
      10 seconds
    )
    response.discardEntityBytes(materializer)
    response.status.intValue()
  }

  override def sendRequestWithCallback(
      request: HttpRequest,
      method: String,
      uri: URI,
      headers: util.Map[String, String],
      requestResult: HttpClientResult
  ): Unit = {
    implicit val ec: ExecutionContext =
      ExecutionContexts.fromExecutor(new Executor {
        override def execute(command: Runnable): Unit = command.run()
      })
    Http
      .get(system)
      .singleRequest(request)
      .onComplete {
        case Success(response: HttpResponse) => {
          response.discardEntityBytes(materializer)
          requestResult.complete(response.status.intValue())
        }
        case Failure(error) => requestResult.complete(error)
      }
  }

  override protected def configure(
      options: HttpClientTestOptions.Builder
  ): Unit = {
    options.disableTestRedirects()
    options.disableTestNonStandardHttpMethod()
    // singleConnection test would require instrumentation to support requests made through pools
    // (newHostConnectionPool, superPool, etc), which is currently not supported.
    options.setSingleConnectionFactory(
      new BiFunction[String, Integer, SingleConnection] {
        override def apply(t: String, u: Integer): SingleConnection = null
      }
    )
  }
}
