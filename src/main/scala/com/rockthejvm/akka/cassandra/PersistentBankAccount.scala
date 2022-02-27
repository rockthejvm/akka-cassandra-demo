package com.rockthejvm.akka.cassandra

import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import com.rockthejvm.akka.cassandra.Bank.BankAccountCreatedResponse

object PersistentBankAccount {

  // Commands
  sealed trait Command
  final case class CreateBankAccount(
      user: String,
      currency: String,
      initialBalance: Double,
      replyTo: ActorRef[BankAccountCreatedResponse] // TODO Change this type
  ) extends Command

  // Events
  sealed trait Event
  final case class BankAccountCreated(bankAccount: BankAccount) extends Event

  // State
  final case class State(bankAccount: BankAccount)
  object State {
    def empty(id: String): State = State(BankAccount(id, "", "",  0.0))
  }

  // Domain object
  final case class BankAccount(
      id: String,
      user: String,
      currency: String,
      balance: Double
  ) // Don't use Double for money in production!

  val commandHandler: (State, Command) => Effect[Event, State] = { (state, command) =>
    command match {
      case CreateBankAccount(user, currency, initialBalance, replyTo) =>
        // TODO Add some validation logic
        val id = state.bankAccount.id
        Effect
          .persist(BankAccountCreated(BankAccount(id, user, currency, initialBalance)))
          .thenReply(replyTo)(_ => BankAccountCreatedResponse(id))
    }
  }

  val eventHandler: (State, Event) => State = { (state, event) =>
    event match {
      case BankAccountCreated(bankAccount) =>
        state.copy(bankAccount = bankAccount)
    }
  }

  def apply(id: String): Behavior[Command] = EventSourcedBehavior[Command, Event, State](
    persistenceId = PersistenceId.ofUniqueId(id),
    emptyState = State.empty(id),
    commandHandler = commandHandler,
    eventHandler = eventHandler
  )
}
