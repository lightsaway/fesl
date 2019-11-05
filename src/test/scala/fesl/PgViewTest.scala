package fesl

import java.time.LocalDateTime
import java.util.UUID

import cats.effect.IO
import skunk.Session
import natchez.Trace.Implicits.noop
import org.scalatest.matchers.should.Matchers

class PgViewTest extends PostgresBaseTest with Matchers {
  // failing due to some bugs in skunk, made a PR

  ignore("insert one") {

    implicit val session = Session
      .single[IO](host = "localhost",
                  port = container.jdbcUrl.split("/")(2).split(":")(1).toInt,
                  user = container.username,
                  database = db,
                  debug = true)
      .allocated
      .unsafeRunSync()
      ._1

    val test = for {
      _ <- PgView.createTable[IO].attempt
      account   = Account(UUID.randomUUID(), 0, isActive = true, 0)
      accountAg = AccountAggregate(UUID.randomUUID(), account, LocalDateTime.now())
      view      = new PgView[IO, AccountAggregate]()
      in  <- view.insert(accountAg)
      out <- view.select(accountAg.id)
      _ = in shouldBe accountAg
      _ = out shouldBe accountAg
    } yield ()
    test.unsafeRunSync()
  }

  test("insert many") {}

}
