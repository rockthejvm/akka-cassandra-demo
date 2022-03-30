package com.rockthejvm.akka.cassandra.services

import akka.NotUsed
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import akka.util.Timeout
import com.rockthejvm.akka.cassandra.services.PersistentBankAccount._

import scala.concurrent.duration._
import java.util.UUID
import scala.concurrent.ExecutionContext

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
        case updateCmd @ UpdateBalance(id, _, _, replyTo) =>
          state.accounts.get(id) match {
            case Some(bankAccount) =>
              Effect.reply(bankAccount)(updateCmd)
            case None =>
              Effect.reply(replyTo)(BankAccountBalanceUpdatedResponse(None))
          }
        case getCmd @ GetBankAccount(id, replyTo) =>
          state.accounts.get(id) match {
            case Some(bankAccount) =>
              Effect.reply(bankAccount)(getCmd)
            case None =>
              Effect.reply(replyTo)(GetBankAccountResponse(None))
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

object BankPlayground {
  val rootBehavior: Behavior[NotUsed] = Behaviors.setup { context =>
    val bank = context.spawn(Bank(), "bank")

    import akka.actor.typed.scaladsl.AskPattern._
    implicit val scheduler: Scheduler = context.system.scheduler
    implicit val timeout: Timeout = Timeout(2.seconds)
    implicit val ec: ExecutionContext = context.executionContext
    bank.ask(replyTo => CreateBankAccount("daniel", "USD", 10, replyTo)).flatMap {
      case BankAccountCreatedResponse(id) =>
        context.log.info(s"successfully created bank account $id")
        bank.ask(replyTo => GetBankAccount(id, replyTo))
    }.foreach {
      case GetBankAccountResponse(maybeAccount) =>
        context.log.info(s"Account details: $maybeAccount")
    }

    Behaviors.empty
  }

  val system = ActorSystem(rootBehavior, "BankDemo")
}
