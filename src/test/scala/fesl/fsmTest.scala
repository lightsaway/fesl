package fesl

import java.util.UUID

import cats.implicits._
import org.scalatest.{FunSuite, Matchers}

class StateTest extends FunSuite with Matchers {

  test("test state monad positive") {
    val aggId = UUID.randomUUID()
    val flow: List[Transaction] =
      List(Fill(aggId, 10), Fill(aggId, 20), Fill(aggId, 5), Withdraw(aggId, 11))
    val res = Transaction.fsm.many(flow).run(Account(aggId, 0)).value
    res._1 shouldBe Account(aggId, 24)
    res._2 shouldBe flow
  }
}
