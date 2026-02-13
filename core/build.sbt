// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

lazy val DAO = project in file("dao")
lazy val Config = project in file("config")
lazy val Auth = (project in file("auth"))
  .dependsOn(DAO, Config)
lazy val ConfigService = (project in file("config-service"))
  .dependsOn(Auth, Config)
  .settings(
    dependencyOverrides ++= Seq(
      // override it as io.dropwizard 4 require 2.16.1 or higher
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.17.0"
    )
  )
lazy val WorkflowCore = (project in file("workflow-core"))
  .dependsOn(DAO, Config)
  .configs(Test)
  .dependsOn(DAO % "test->test") // test scope dependency
lazy val ComputingUnitManagingService = (project in file("computing-unit-managing-service"))
  .dependsOn(WorkflowCore, Auth, Config)
  .settings(
    dependencyOverrides ++= Seq(
      // override it as io.dropwizard 4 require 2.16.1 or higher
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.17.0"
    )
  )
lazy val FileService = (project in file("file-service"))
  .dependsOn(WorkflowCore, Auth, Config)
  .settings(
    dependencyOverrides ++= Seq(
      // override it as io.dropwizard 4 require 2.16.1 or higher
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.16.1",
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.16.1",
      "org.glassfish.jersey.core" % "jersey-common" % "3.0.12"
    )
  )

lazy val WorkflowOperator = (project in file("workflow-operator")).dependsOn(WorkflowCore)
lazy val WorkflowCompilingService = (project in file("workflow-compiling-service"))
  .dependsOn(WorkflowOperator, Config)
  .settings(
    dependencyOverrides ++= Seq(
      // override it as io.dropwizard 4 require 2.16.1 or higher
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.16.1",
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.16.1",
      "org.glassfish.jersey.core" % "jersey-common" % "3.0.12"
    )
  )

lazy val WorkflowExecutionService = (project in file("amber"))
  .dependsOn(WorkflowOperator, Auth, Config)
  .settings(
    dependencyOverrides ++= Seq(
      "com.fasterxml.jackson.core" % "jackson-core" % "2.15.1",
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.15.1",
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.15.1",
      "org.slf4j" % "slf4j-api" % "1.7.26",
      "org.eclipse.jetty" % "jetty-server" % "9.4.20.v20190813",
      "org.eclipse.jetty" % "jetty-servlet" % "9.4.20.v20190813",
      "org.eclipse.jetty" % "jetty-http" % "9.4.20.v20190813"
    ),
    libraryDependencies ++= Seq(
      "com.squareup.okhttp3" % "okhttp" % "4.10.0" force () // Force usage of OkHttp 4.10.0
    )
  )
  .configs(Test)
  .dependsOn(DAO % "test->test", Auth % "test->test") // test scope dependency

lazy val SqlService = (project in file("sql-service"))
  .settings(
    name := "sql-service",
    version := "1.0.0",
    scalaVersion := "2.13.12",

    // Specify the main class
    Compile / mainClass := Some("edu.uci.ics.texera.sqlservice.CalciteApplication"),

    // Java 11 target
    javacOptions ++= Seq("--release", "11", "-encoding", "UTF-8"),
    scalacOptions ++= Seq("-target:jvm-11"),

    // Dependencies
    libraryDependencies ++= Seq(
      // Dropwizard 2.x (Java 11 compatible)
      "io.dropwizard" % "dropwizard-core" % "2.1.1",
      "io.dropwizard" % "dropwizard-configuration" % "2.1.1",

      // Apache Calcite
      "org.apache.calcite" % "calcite-core" % "1.38.0",

      // JSON / Jackson
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.16.1",
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.16.1",

      // JAX-RS API (needed for Dropwizard)
      "javax.ws.rs" % "javax.ws.rs-api" % "2.1.1",

      // Logging (ensure compatible version)
      "ch.qos.logback" % "logback-classic" % "1.2.11",
      "ch.qos.logback" % "logback-core" % "1.2.11"
    ),

    // Override dependencies to avoid conflicts
    dependencyOverrides ++= Seq(
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.16.1",
      "com.fasterxml.jackson.core" % "jackson-core" % "2.16.1",
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.16.1",
      "ch.qos.logback" % "logback-classic" % "1.2.11",
      "ch.qos.logback" % "logback-core" % "1.2.11"
    )
  )
  .dependsOn(Config) // SqlService depends on Config project





// root project definition
lazy val CoreProject = (project in file("."))
  .aggregate(
    DAO,
    Config,
    ConfigService,
    Auth,
    WorkflowCore,
    ComputingUnitManagingService,
    FileService,
    WorkflowOperator,
    WorkflowCompilingService,
    WorkflowExecutionService,
    SqlService
  )
  .settings(
    name := "core",
    version := "1.0.0",
    organization := "edu.uci.ics",
    scalaVersion := "2.13.12",
    publishMavenStyle := true
  )
