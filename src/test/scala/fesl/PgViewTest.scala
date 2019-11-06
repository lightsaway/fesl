package fesl

import java.time.LocalDateTime
import java.util.UUID

import cats.effect.IO
import fesl.codecs.decocde
import skunk.Session
import natchez.Trace.Implicits.noop
import org.scalatest.matchers.should.Matchers

class PgViewTest extends PostgresBaseTest with Matchers {

  test("insert one") {

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
      _ <- PgView.createTable[IO]
      accountAg = AccountAggregate(UUID.randomUUID(),
                                   Account(UUID.randomUUID(), 0, isActive = true, 0),
                                   LocalDateTime.now())
      view = new PgView[IO, AccountAggregate]()
      in  <- view.insert(accountAg)
      out <- view.select(accountAg.id)
      _ = {
        in shouldBe accountAg
        out.get shouldBe accountAg
      }
      updateAg = accountAg.copy(state = accountAg.state.copy(ballance = 100000),
                                updated_on = LocalDateTime.now())
      inUpdated  <- view.insert(updateAg)
      outUpdated <- view.select(accountAg.id)
      _ = {
        inUpdated shouldBe updateAg
        outUpdated.get shouldBe updateAg
      }
    } yield ()
    test.unsafeRunSync()
  }

}
