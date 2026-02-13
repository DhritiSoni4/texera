name := "sql-service"
version := "1.0.0"
scalaVersion := "2.13.12"

// Dropwizard + Calcite SQL service
libraryDependencies ++= Seq(
  "io.dropwizard" % "dropwizard-core" % "2.1.1",
  "io.dropwizard" % "dropwizard-configuration" % "2.1.1",
  "org.apache.calcite" % "calcite-core" % "1.38.0",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.16.1",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.16.1",
  "javax.ws.rs" % "javax.ws.rs-api" % "2.1.1",

  // Logback fixed version
  "ch.qos.logback" % "logback-classic" % "1.2.11",
  "ch.qos.logback" % "logback-core" % "1.2.11",
  "org.slf4j" % "slf4j-api" % "1.7.36"
)

// Override any transitive Logback/SLF4J brought in by Dropwizard
dependencyOverrides ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.2.11",
  "ch.qos.logback" % "logback-core" % "1.2.11",
  "org.slf4j" % "slf4j-api" % "1.7.36"
)


// ✅ Ensure correct entry point for Dropwizard application
Compile / mainClass := Some("edu.uci.ics.texera.sqlservice.CalciteApplication")
