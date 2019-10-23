import cats.Eval
import cats.data.StateT
import cats.implicits._
import org.scalatest.{FunSuite, Matchers, matchers}
import state.FSM

sealed trait Transaction {
  val money: Int
}
case class Fill(money: Int) extends Transaction
case class Withdraw(money: Int) extends Transaction
case class Account(ballance: Int)


class StateTest extends FunSuite with Matchers {

  val atm = new FSM[Eval, Account, Transaction] {
    override def one(e: Transaction) = StateT[Eval, Account, Transaction] { account =>
      e match {
        case Fill(x) => Eval.now(account.copy(ballance = account.ballance + x)).tupleRight(e)
        case Withdraw(x) => Eval.now(account.copy(ballance = account.ballance - x)).tupleRight(e)
      }
    }
  }

  test("test state monad positive") {
    val flow: List[Transaction] = List(Fill(10), Fill(20), Fill(5), Withdraw(11))
    val res = atm.many(flow).run(Account(0)).value
    res._1 shouldBe Account(24)
    res._2 shouldBe flow
  }
}
