package com.rockthejvm.akka.cassandra

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route

import scala.concurrent.Future
import akka.actor.typed.ActorSystem
import akka.util.Timeout
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import com.rockthejvm.akka.cassandra.Bank.GetBankAccountResponse
import com.rockthejvm.akka.cassandra.BankAccountRoutes.{BankAccountBalanceUpdateRequest, BankAccountCreationRequest}


object BankAccountRoutes {
  final case class BankAccountCreationRequest(user: String, currency: String, balance: Double)
  final case class BankAccountBalanceUpdateRequest(currency: String, balance: Double)
}

class BankAccountRoutes(/* TODO Provide the business layer */)(implicit val system: ActorSystem[_])  {

  private implicit val timeout =
    Timeout.create(system.settings.config.getDuration("my-app.routes.ask-timeout"))

  def findBankAccount(id: String): Future[GetBankAccountResponse] = ???
  def createBankAccount(bankAccount: BankAccountCreationRequest): Future[String] = ???
  def updateBalance(id: String, request: BankAccountBalanceUpdateRequest): Future[Double] = ???

  val bankAccountRoutes: Route =
    pathPrefix("bank-accounts") {
      concat(
        pathEnd {
          concat(
            post {
              entity(as[BankAccountCreationRequest]) { bankAccount =>
                onSuccess(createBankAccount(bankAccount)) { performed =>
                  // FIXME The returned id must be placed in the Location header
                  complete((StatusCodes.Created, performed))
                }
              }
            })
        },
        path(Segment) { id =>
          concat(
            get {
              rejectEmptyResponse {
                onSuccess(findBankAccount(id)) { response =>
                  complete(response.maybeBankAccount)
                }
              }
            },
            put {
              entity(as[BankAccountBalanceUpdateRequest]) { request =>
                onSuccess(updateBalance(id, request)) { performed =>
                  // FIXME: This is not the correct return type
                  complete((StatusCodes.OK, performed))
                }
              }
            }
          )
        })
    }

}
