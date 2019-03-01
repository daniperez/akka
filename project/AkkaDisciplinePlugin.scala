/*
 * Copyright (C) 2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka

import sbt._
import Keys.{scalacOptions, _}
import sbt.plugins.JvmPlugin

/**
  * Initial tests found:
  * `akka-actor` 151 errors with `-Xfatal-warnings`, 6 without the flag
  */
object AkkaDisciplinePlugin extends AutoPlugin with ScalafixSupport {

  import scoverage.ScoverageKeys._
  import scalafix.sbt.ScalafixPlugin
 
  /** The nightly coverage job sets `-Dakka.coverage.job=true`
    * in order to aggregate specific modules vs all.
    */
  lazy val coverageJobEnabled: Boolean =
    sys.props.getOrElse("akka.coverage.job", "false").toBoolean

  override def trigger: PluginTrigger = allRequirements
  override def requires: Plugins = JvmPlugin && ScalafixPlugin
  override lazy val projectSettings = if (coverageJobEnabled) scoverageSettings else disciplineSettings

  /** If we need to add any further tuning around disabling,
    * this way we can add in one place vs in each module.
    * TODO fix remoteTests in job conf and add remote coverage back.
    * Exclude
    * - planned removals in 2.6 https://github.com/akka/akka/milestone/119
    * - docs?, protobuf, benchJmh
    */
  lazy val coverageExclude = Seq(
    test in Test := {},
    coverageEnabled := false)

  lazy val scalaFixSettings = Seq(
    Compile / scalacOptions += "-Yrangepos")

  lazy val scoverageSettings = Seq(
    coverageMinimum := 70,
    coverageFailOnMinimum := false,
    coverageOutputHTML := true,
    coverageHighlighting := {
      import sbt.librarymanagement.{ SemanticSelector, VersionNumber }
      !VersionNumber(scalaVersion.value).matchesSemVer(SemanticSelector("<=2.11.1"))
    }) ++ {
    if (coverageJobEnabled) Seq(
      logLevel in Test := Level.Error,
      logLevel in Compile := Level.Error)
    else Nil
  }

  lazy val disciplineSettings =
    scalaFixSettings ++
      scoverageSettings ++ Seq(
      Compile / scalacOptions ++= disciplineScalacOptions,
      Compile / scalacOptions --= undisciplineScalacOptions,
      Compile / console / scalacOptions --= Seq("-deprecation", "-Xfatal-warnings", "-Xlint", "-Ywarn-unused:imports"),
      Compile / scalacOptions --= (CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, 13)) =>
          Seq("-Ywarn-inaccessible", "-Ywarn-infer-any", "-Ywarn-nullary-override", "-Ywarn-nullary-unit", "-Ypartial-unification", "-Yno-adapted-args")
        case Some((2, 12)) =>
          Nil
        case Some((2, 11)) =>
          Seq("-Ywarn-extra-implicit", "-Ywarn-unused:_")
        case _             =>
          Nil
      }))

  /**
    * Remain visibly filtered for future code quality work and removing.
    */
  val undisciplineScalacOptions = Seq(
    "-Ywarn-value-discard",
    "-Ywarn-numeric-widen",
    "-Yno-adapted-args",
    "-Xfatal-warnings")

  /** Optimistic, this is desired over time.
    * -Xlint and -Ywarn-unused: are included in commonScalaOptions.
    * If eventually all modules use this plugin, we could migrate them here.
    */
  val disciplineScalacOptions = Seq(
    // start: must currently remove, version regardless
    "-Xfatal-warnings",
    "-Ywarn-value-discard",
    "-Yno-adapted-args",
    "-Ywarn-numeric-widen",
    // end
    "-Xfuture",
    "-Ywarn-dead-code",
    "-Ywarn-inaccessible",
    "-Ywarn-infer-any",
    "-Ywarn-nullary-override",
    "-Ywarn-nullary-unit",
    "-Ywarn-unused:_",
    "-Ypartial-unification",
    "-Ywarn-extra-implicit",
    "-Ywarn-numeric-widen")

}
