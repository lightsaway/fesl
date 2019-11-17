package fesl.core

import java.time.LocalDateTime
import java.util.UUID

import cats.implicits._
import fesl.core.dtos.{Account, Fill, Transaction, Withdraw}
import fesl.{core, skunk}
import fesl.core.dtos.{Account, Fill, Transaction, Withdraw}
import org.scalatest.{FunSuite, Matchers}

class StateTest extends FunSuite with Matchers {

  test("test state monad positive") {
    val aggId = UUID.randomUUID()
    val date  = LocalDateTime.now()
    val flow: List[Transaction] =
      List(
        Fill(UUID.randomUUID(), aggId, 10, date),
        Fill(UUID.randomUUID(), aggId, 20, date),
        Fill(UUID.randomUUID(), aggId, 5, date),
        Withdraw(UUID.randomUUID(), aggId, 11, date)
      )
    val res = Transaction.fsm.many(flow).run(Account(aggId, 0))
    res._1 shouldBe Account(aggId, 24)
    res._2 shouldBe flow
  }
}
