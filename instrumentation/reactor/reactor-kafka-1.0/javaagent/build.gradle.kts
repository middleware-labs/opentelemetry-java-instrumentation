plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.projectreactor.kafka")
    module.set("reactor-kafka")
    versions.set("[1.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly(project(":muzzle"))

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  bootstrap(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:bootstrap"))

  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common:library"))
  implementation(project(":instrumentation:reactor:reactor-3.1:library"))

  // using 1.3.0 to be able to implement several new KafkaReceiver methods added in 1.3.3
  // @NoMuzzle is used to ensure that this does not break muzzle checks
  compileOnly("io.projectreactor.kafka:reactor-kafka:1.3.3")

  testInstrumentation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:javaagent"))
  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))

  testImplementation(project(":instrumentation:reactor:reactor-kafka-1.0:testing"))

  testLibrary("io.projectreactor.kafka:reactor-kafka:1.0.0.RELEASE")

  latestDepTestLibrary("io.projectreactor:reactor-core:3.4.+")
}

val testLatestDeps = findProperty("testLatestDeps") as Boolean

testing {
  suites {
    val testV1_3_3 by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":instrumentation:reactor:reactor-kafka-1.0:testing"))

        if (testLatestDeps) {
          implementation("io.projectreactor.kafka:reactor-kafka:+")
          implementation("io.projectreactor:reactor-core:3.4.+")
        } else {
          implementation("io.projectreactor.kafka:reactor-kafka:1.3.3")
        }
      }

      targets {
        all {
          testTask.configure {
            systemProperty("hasConsumerGroupAndId", true)
          }
        }
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)

    jvmArgs("-Dotel.instrumentation.kafka.experimental-span-attributes=true")
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
  }

  test {
    systemProperty("hasConsumerGroupAndId", testLatestDeps)
  }

  check {
    dependsOn(testing.suites)
  }
}
