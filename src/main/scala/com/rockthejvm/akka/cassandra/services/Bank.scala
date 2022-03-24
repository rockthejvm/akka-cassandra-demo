package com.rockthejvm.akka.cassandra.services

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import com.rockthejvm.akka.cassandra.services.PersistentBankAccount._

import java.util.UUID

object Bank {

  // Events
  sealed trait Event
  case class BankAccountCreated(id: String) extends Event

  // State
  case class State(accounts: Map[String, ActorRef[Command]])

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId("bank"),
      emptyState = State(Map.empty),
      commandHandler = commandHandler(ctx),
      eventHandler = eventHandler(ctx)
    )
  }

  def commandHandler(ctx: ActorContext[Command]): (State, Command) => Effect[Event, State] = {
    (state, command) =>
      command match {
        case createCmd @ CreateBankAccount(_, _, _, _) =>
          val id             = UUID.randomUUID().toString
          val newBankAccount = ctx.spawn(PersistentBankAccount(id), id)
          Effect
            .persist(BankAccountCreated(id))
            .thenReply(newBankAccount)(_ => createCmd)
        case updateCmd @ UpdateBalance(id, _, _, _) =>
          state.accounts.get(id) match {
            case Some(bankAccount) =>
              Effect.none.thenReply(bankAccount)(_ => updateCmd)
            case None => ???
            // TODO: Reply with some error
          }
        case getCmd @ GetBankAccount(id, replyTo) =>
          state.accounts.get(id) match {
            case Some(bankAccount) =>
              Effect.none.thenReply(bankAccount)(_ => getCmd)
            case None =>
              Effect.none.thenReply(replyTo)(_ => GetBankAccountResponse(None))
          }
      }
  }

  def eventHandler(ctx: ActorContext[Command]): (State, Event) => State = { (state, event) =>
    event match {
      case BankAccountCreated(id) =>
        val bankAccount =
          ctx
            .child(id)
            .getOrElse(ctx.spawn(PersistentBankAccount(id), id))
            .asInstanceOf[ActorRef[Command]]
        state.copy(accounts = state.accounts + (id -> bankAccount))
    }
  }
}
