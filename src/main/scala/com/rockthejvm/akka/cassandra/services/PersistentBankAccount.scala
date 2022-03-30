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
      replyTo: ActorRef[Response]
  ) extends Command

  // withdraw OR deposit
  final case class UpdateBalance(
      id: String,
      currency: String,
      amount: Double, // can be negative
      replyTo: ActorRef[Response]
  ) extends Command

  final case class GetBankAccount(
      id: String,
      replyTo: ActorRef[Response]
  ) extends Command

  // Events
  sealed trait Event
  final case class BankAccountCreated(bankAccount: BankAccount) extends Event
  final case class BalanceUpdated(newBalance: Double)           extends Event

  // State
  // Domain object
  final case class BankAccount(
      id: String,
      user: String,
      currency: String,
      balance: Double
  ) // Don't use Double for money in production!

  object BankAccount {
    def empty(id: String): BankAccount = BankAccount(id, "", "", 0.0)
  }

  // Responses
  sealed trait Response
  final case class BankAccountCreatedResponse(id: String) extends Response
  final case class BankAccountBalanceUpdatedResponse(maybeBankAccount: Option[BankAccount]) extends Response
  final case class GetBankAccountResponse(maybeBankAccount: Option[BankAccount]) extends Response


  val commandHandler: (BankAccount, Command) => Effect[Event, BankAccount] = { (state, command) =>
    command match {
      case CreateBankAccount(user, currency, initialBalance, replyTo) =>
        val id = state.id
        Effect
          .persist(BankAccountCreated(BankAccount(id, user, currency, initialBalance)))
          .thenReply(replyTo)(_ => BankAccountCreatedResponse(id))
      case UpdateBalance(_, _, amount, replyTo) =>
        val newBalance = state.balance + amount
        Effect
          .persist(BalanceUpdated(newBalance))
          .thenReply(replyTo)(newState => BankAccountBalanceUpdatedResponse(Some(newState)))
      case GetBankAccount(_, replyTo) =>
        Effect.reply(replyTo)(GetBankAccountResponse(Some(state)))
    }
  }

  val eventHandler: (BankAccount, Event) => BankAccount = { (state, event) =>
    event match {
      case BankAccountCreated(bankAccount) => bankAccount
      case BalanceUpdated(amount) =>
        state.copy(balance = state.balance + amount)
    }
  }

  def apply(id: String): Behavior[Command] =
    EventSourcedBehavior[Command, Event, BankAccount](
      persistenceId = PersistenceId.ofUniqueId(id),
      emptyState = BankAccount.empty(id),
      commandHandler = commandHandler,
      eventHandler = eventHandler
    )
}
