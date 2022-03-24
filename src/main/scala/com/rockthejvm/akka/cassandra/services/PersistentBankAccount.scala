package com.rockthejvm.akka.cassandra.services

import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

object PersistentBankAccount {

  // Commands
  sealed trait Command
  final case class CreateBankAccount(
      user: String,
      currency: String,
      initialBalance: Double,
      replyTo: ActorRef[BankAccountCreatedResponse] // TODO Change this type
  ) extends Command
  final case class UpdateBalance(
      id: String,
      currency: String,
      amount: Double,
      replyTo: ActorRef[BankAccountBalanceUpdatedResponse] // TODO Change this type
  ) extends Command
  final case class GetBankAccount(
      id: String,
      replyTo: ActorRef[GetBankAccountResponse]
  ) extends Command

  // Events
  sealed trait Event
  final case class BankAccountCreated(bankAccount: BankAccount) extends Event
  final case class BalanceUpdated(newBalance: Double)           extends Event

  // State
  final case class State(bankAccount: BankAccount)
  object State {
    def empty(id: String): State = State(BankAccount(id, "", "", 0.0))
  }

  // Responses
  sealed trait Response
  final case class BankAccountCreatedResponse(id: String) extends Response
  final case class BankAccountBalanceUpdatedResponse(newBalance: Double)
      extends Response // TODO Maybe, we can return the whole updated object?
  final case class GetBankAccountResponse(maybeBankAccount: Option[BankAccount]) extends Response

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
        val id = state.bankAccount.id
        Effect
          .persist(BankAccountCreated(BankAccount(id, user, currency, initialBalance)))
          .thenReply(replyTo)(_ => BankAccountCreatedResponse(id))
      case UpdateBalance(_, currency, amount, replyTo) =>
        // TODO We have also to change the currency
        val newBalance = state.bankAccount.balance + amount
        Effect
          .persist(BalanceUpdated(newBalance))
          .thenReply(replyTo)(_ => BankAccountBalanceUpdatedResponse(newBalance))
      case GetBankAccount(_, replyTo) =>
        Effect.reply(replyTo)(GetBankAccountResponse(Some(state.bankAccount)))
    }
  }

  val eventHandler: (State, Event) => State = { (state, event) =>
    event match {
      case BankAccountCreated(bankAccount) =>
        state.copy(bankAccount = bankAccount)
      case BalanceUpdated(newBalance) =>
        state.copy(bankAccount = state.bankAccount.copy(balance = newBalance))
    }
  }

  def apply(id: String): Behavior[Command] =
    EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId(id),
      emptyState = State.empty(id),
      commandHandler = commandHandler,
      eventHandler = eventHandler
    )
}
