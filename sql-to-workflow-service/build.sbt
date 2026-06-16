name := "sql-to-workflow-service"

version := "1.0.0"

scalaVersion := "2.13.18"
Compile / javacOptions --= Seq("-Werror")
// Dropwizard + Calcite SQL service
libraryDependencies ++= Seq(
  "io.dropwizard" % "dropwizard-core" % "2.1.1",
  "io.dropwizard" % "dropwizard-configuration" % "2.1.1",

  // Apache Calcite
  "org.apache.calcite" % "calcite-core" % "1.38.0",

  // PostgreSQL JDBC (required for PGobject)
  "org.postgresql" % "postgresql" % "42.7.3",

  // Jackson
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.16.1",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.16.1",

  // JAX-RS
  "javax.ws.rs" % "javax.ws.rs-api" % "2.1.1",

  // Logging
  "ch.qos.logback" % "logback-classic" % "1.2.11",
  "ch.qos.logback" % "logback-core" % "1.2.11",
  "org.slf4j" % "slf4j-api" % "1.7.36",

  // ScalaTest
  "org.scalatest" %% "scalatest" % "3.2.18" % Test
)

// Override any transitive Logback/SLF4J brought in by Dropwizard
dependencyOverrides ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.2.11",
  "ch.qos.logback" % "logback-core" % "1.2.11",
  "org.slf4j" % "slf4j-api" % "1.7.36"
)

// Ensure correct entry point for Dropwizard application
Compile / mainClass := Some("org.apache.texera.sqlservice.CalciteApplication")

// Enable tests
Test / fork := true