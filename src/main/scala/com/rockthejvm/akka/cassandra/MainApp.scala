package com.rockthejvm.akka.cassandra

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import com.rockthejvm.akka.cassandra.http.routes.BankAccountRoutes

import scala.util.{Failure, Success}

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
  //#start-http-server
  def main(args: Array[String]): Unit = {
    val rootBehavior = Behaviors.setup[Nothing] { context =>
      val bankActor = context.spawn(Bank(), "BankActor")
      context.watch(bankActor)

      val routes = new BankAccountRoutes(bankActor)(context.system)
      startHttpServer(routes.bankAccountRoutes)(context.system)

      Behaviors.empty
    }
    val system = ActorSystem[Nothing](rootBehavior, "AkkaCassandraDemoServer")
  }
}
