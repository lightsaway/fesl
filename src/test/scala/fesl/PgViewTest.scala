package fesl

import java.time.LocalDateTime
import java.util.UUID

import cats.effect.IO
import skunk.Session
import org.scalatest.matchers.should.Matchers

class PgViewTest extends PostgresBaseTest with Matchers {

  test("insert and read from view") {

    implicit val session: Session[IO] = init

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

  test("insert and read from log") {
    import cats.implicits._
    implicit val session: Session[IO] = init
    val date                          = LocalDateTime.now()

    val test = for {
      _ <- PgLog.createTable[IO]
      id = UUID.randomUUID()
      events = List(CreateAccount(UUID.randomUUID(), id, 0, date),
                    Fill(UUID.randomUUID(), id, 2, date),
                    Block(UUID.randomUUID(), id, date))
      log = new PgLog[IO, Transaction]()
      _   <- events.traverse_(log.insert(_))
      out <- log.select(id).compile.toList
      _ = out shouldBe events
    } yield ()
    test.unsafeRunSync()
  }

}
