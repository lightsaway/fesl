package fesl

import cats.effect.IO
import com.dimafeng.testcontainers.{ForAllTestContainer, PostgreSQLContainer}
import skunk.Session
import natchez.Trace.Implicits.noop
abstract class PostgresBaseTest extends org.scalatest.FunSuite with ForAllTestContainer {
  implicit val contextShift =
    IO.contextShift(scala.concurrent.ExecutionContext.global)
  val db                 = "eventsourcing"
  override val container = PostgreSQLContainer(databaseName = db, password = "")

  def init = {
    Session
      .single[IO](host = "localhost",
                  port = container.jdbcUrl.split("/")(2).split(":")(1).toInt,
                  user = container.username,
                  database = db,
                  debug = true)
      .allocated
      .unsafeRunSync()
      ._1
  }
}
