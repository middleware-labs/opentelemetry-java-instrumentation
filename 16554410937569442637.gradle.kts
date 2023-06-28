import java.time.Duration

plugins {
  id("idea")

  id("com.github.ben-manes.versions")
  id("io.github.gradle-nexus.publish-plugin")
  id("otel.spotless-conventions")
}

apply(from = "version.gradle.kts")

nexusPublishing {
  packageGroup.set("io.opentelemetry")

  repositories {
    sonatype {
      username.set(System.getenv("SONATYPE_USER"))
      password.set(System.getenv("SONATYPE_KEY"))
    }
  }

  connectTimeout.set(Duration.ofMinutes(5))
  clientTimeout.set(Duration.ofMinutes(5))

  transitionCheckOptions {
    // We have many artifacts so Maven Central takes a long time on its compliance checks. This sets
    // the timeout for waiting for the repository to close to a comfortable 50 minutes.
    maxRetries.set(300)
    delayBetween.set(Duration.ofSeconds(10))
  }
}

description = "OpenTelemetry instrumentations for Java"

if (project.findProperty("skipTests") as String? == "true") {
  subprojects {
    tasks.withType<Test>().configureEach {
      enabled = false
    }
  }
}

tasks {
  val listTestsInPartition by registering {
    group = "Help"
    description = "List test tasks in given partition"

    // total of 4 partitions (see modulo 4 below)
    var testPartition = (project.findProperty("testPartition") as String?)?.toInt()
    if (testPartition == null) {
      throw GradleException("Test partition must be specified")
    } else if (testPartition < 0 || testPartition >= 4) {
      throw GradleException("Invalid test partition")
    }

    val partitionTasks = ArrayList<Test>()
    var testPartitionCounter = 0
    subprojects {
      // relying on predictable ordering of subprojects
      // (see https://docs.gradle.org/current/dsl/org.gradle.api.Project.html#N14CB4)
      // since we are splitting these tasks across different github action jobs
      val enabled = testPartitionCounter++ % 4 == testPartition
      if (enabled) {
        tasks.withType<Test>().configureEach {
          partitionTasks.add(this)
        }
      }
    }

    doLast {
      File("test-tasks.txt").printWriter().use { writer ->
        partitionTasks.forEach { task ->
          var taskPath = task.project.path + ":" + task.name
          // smoke tests are run separately
          // :instrumentation:test runs all instrumentation tests
          if (taskPath != ":smoke-tests:test" && taskPath != ":instrumentation:test") {
            writer.println(taskPath)
          }
        }
      }
    }

    // disable all tasks to stop build
    subprojects {
      tasks.configureEach {
        enabled = false
      }
    }
  }
}

if (gradle.startParameter.taskNames.any { it.equals("listTestsInPartition") }) {
  // disable all tasks to stop build
  project.tasks.configureEach {
    if (this.name != "listTestsInPartition") {
      enabled = false
    }
  }
}

val quarkusDeployment by configurations.creating
dependencies {
quarkusDeployment("io.quarkus:quarkus-smallrye-context-propagation-deployment:3.1.2.Final")
quarkusDeployment("io.quarkus:quarkus-netty-deployment:3.1.2.Final")
quarkusDeployment("io.quarkus:quarkus-resteasy-deployment:3.1.2.Final")
quarkusDeployment("io.quarkus:quarkus-vertx-deployment:3.1.2.Final")
quarkusDeployment("io.quarkus:quarkus-vertx-http-deployment:3.1.2.Final")
quarkusDeployment("io.quarkus:quarkus-resteasy-server-common-deployment:3.1.2.Final")
quarkusDeployment("io.quarkus:quarkus-arc-deployment:3.1.2.Final")
quarkusDeployment("io.quarkus:quarkus-resteasy-common-deployment:3.1.2.Final")
quarkusDeployment("io.quarkus:quarkus-mutiny-deployment:3.1.2.Final")
quarkusDeployment("io.quarkus:quarkus-core-deployment:3.1.2.Final")
}
typealias PrintWriter = java.io.PrintWriter
typealias FileWriter = java.io.FileWriter
tasks.register("listQuarkusDependencies") {
    val writer = PrintWriter(FileWriter("/tmp/17606517227475443485.txt"))
    quarkusDeployment.incoming.artifacts.forEach {
        writer.println(it.id.componentIdentifier)
        writer.println(it.file)
    }
    val componentIds = quarkusDeployment.incoming.resolutionResult.allDependencies.map { (it as ResolvedDependencyResult).selected.id }
    val result = dependencies.createArtifactResolutionQuery()
        .forComponents(componentIds)
        .withArtifacts(JvmLibrary::class, SourcesArtifact::class)
        .execute()
    result.resolvedComponents.forEach { component ->
        val sources = component.getArtifacts(SourcesArtifact::class)
        sources.forEach { ar ->
            if (ar is ResolvedArtifactResult) {
                writer.println(ar.id.componentIdentifier)
                writer.println(ar.file)
            }
        }
    }
    writer.close()
}