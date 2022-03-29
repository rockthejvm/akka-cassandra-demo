package com.rockthejvm.akka.cassandra

import akka.actor.typed.{ActorRef, ActorSystem, Scheduler}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import com.rockthejvm.akka.cassandra.services.{Bank, PersistentBankAccount}

import scala.util.{Failure, Success}
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import com.rockthejvm.akka.cassandra.http.routes.BankAccountRoutes

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object MainApp {
  private def startHttpServer(routes: Route)(implicit system: ActorSystem[_]): Unit = {

    import system.executionContext

    val futureBinding = Http().newServerAt("localhost", 8080).bind(routes)
    futureBinding.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info("Server online at http://{}:{}/", address.getHostString, address.getPort)
      case Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
  }

  def main(args: Array[String]): Unit = {
    trait RootCommand
    case class RetrieveBankActor(replyTo: ActorRef[ActorRef[PersistentBankAccount.Command]]) extends RootCommand

    val rootBehavior = Behaviors.setup[RootCommand] { context =>
      val bankActor = context.spawn(Bank(), "BankActor")
      context.watch(bankActor)

      Behaviors.receiveMessage {
        case RetrieveBankActor(replyTo) => replyTo ! bankActor
        Behaviors.same
      }
    }

    implicit val system: ActorSystem[RootCommand] = ActorSystem(rootBehavior, "AkkaCassandraDemoServer")
    implicit val timeout: Timeout = Timeout(2.seconds)
    implicit val ec: ExecutionContext = system.executionContext

    system.ask(replyTo => RetrieveBankActor(replyTo)).foreach { bankActor =>
      val routes = new BankAccountRoutes(bankActor)
      startHttpServer(routes.bankAccountRoutes)
    }
  }
}
