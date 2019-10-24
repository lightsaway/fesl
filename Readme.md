# Event Sourcing in functional way

## Prerequisites
Event sourcing consits of: 
* fesl.LogTable
* fesl.ViewTable
* fesl.FSM 
* CQRS 

# fesl.LogTable
* Logtable should insert event entry
* Logtable should be able to read all entries per aggregateID

# fesl.ViewTable
* Should be able to insert compacted views
* Should be able to read last compacted view

# fesl.FSM
Finite state machine can be represented in terms of 
fsm :: I -> E -> S
Where I is initial state, E is event , S is finite state

Implementation can be both done in terms of State Monad and Monoid. Right?

## StateMonad vs Monoid
####StateMonad
```scala
type State[S,A] = S => (S, A)
```
Having current state we need to implement functions that accepts event A or events H[A] and returns new state
```scala
import cats.data.StateT
trait fesl.FSM[F[_], E, A] {
  def one(e: A): StateT[F, E, A]
  def many[H[_] ](es: H[A]): StateT[F, E, H[A]]
}
```

###Monoid
On other hand if  event A can contain the state entity inside and we can generate event number zero we can implement fsm in terms of monoid
```scala
trait Monoid[A]{
  def empty: A 
  def combine(x: A, y: A)
}
```
Having monoid implementation we can go from initial event(empty) to application of next events using  `foldLeft` with initial empty which will equal to `combineAll`
```scala
def fsm[H[_]: Foldable,A](items : H[A])(implicit m: Monoid[A]) = items.foldLeft(m.empty)((b,a) => m.combine(b,a))
def fsmC[H[_]: Foldable,A](items : H[A])(implicit m: Monoid[A]) = items.combineAll
```