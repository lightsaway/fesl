package fesl

import cats.effect.IO
import com.dimafeng.testcontainers.{ForAllTestContainer, PostgreSQLContainer}
abstract class PostgresBaseTest extends org.scalatest.FunSuite with ForAllTestContainer {
  implicit val contextShift =
    IO.contextShift(scala.concurrent.ExecutionContext.global)
  val db                 = "eventsourcing"
  override val container = PostgreSQLContainer(databaseName = db, password = "")

}
