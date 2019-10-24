import org.scalafmt.sbt.ScalafmtPlugin.autoImport.scalafmtOnCompile
import sbt.Keys.{javacOptions, libraryDependencies, parallelExecution, resolvers, scalaVersion, scalacOptions}
import sbt.{Def, Resolver, inThisBuild}

object Settings {
  val default: Seq[Def.SettingsDefinition] = Seq(
    scalacOptions ++= ScalacOptions.default ,
    resolvers += Resolver.sonatypeRepo("snapshots"),
    inThisBuild(
      List(
        scalaVersion := "2.13.1",
        scalafmtOnCompile := true,
        javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),
        parallelExecution := false
      )),
    scalafmtOnCompile := true
  )
}
