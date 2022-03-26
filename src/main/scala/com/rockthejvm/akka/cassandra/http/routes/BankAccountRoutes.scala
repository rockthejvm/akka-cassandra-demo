package com.rockthejvm.akka.cassandra.http.routes

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import cats.data.Validated.{Invalid, Valid}
import cats.implicits._
import com.rockthejvm.akka.cassandra.http.routes.BankAccountRoutes.{BankAccountBalanceUpdateRequest, BankAccountCreationRequest, FailureResponse}
import com.rockthejvm.akka.cassandra.services.PersistentBankAccount._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

import java.time.Duration
import scala.concurrent.{ExecutionContextExecutor, Future}

object BankAccountRoutes {
  final case class BankAccountCreationRequest(
      user: String,
      currency: String,
      balance: Double
  ) {
    def toCmd(replyTo: ActorRef[BankAccountCreatedResponse]): Command =
      CreateBankAccount(
        user,
        currency,
        balance,
        replyTo
      )
  }
  object BankAccountCreationRequest {
    implicit val BankAccountCreationRequestValidable: Validable[BankAccountCreationRequest] =
      (toVal: BankAccountCreationRequest) =>
        (
          validateRequired(toVal.user, "user"),
          validateRequired(toVal.currency, "currency"),
          validateMinimum(toVal.balance, 0, "balance")
        ).mapN(BankAccountCreationRequest.apply)
  }

  final case class BankAccountBalanceUpdateRequest(currency: String, amount: Double) {
    def toCmd(id: String, replyTo: ActorRef[BankAccountBalanceUpdatedResponse]): Command =
      UpdateBalance(
        id,
        currency,
        amount,
        replyTo
      )
  }

  object BankAccountBalanceUpdateRequest {
    implicit val BankAccountBalanceUpdateRequestValidable
        : Validable[BankAccountBalanceUpdateRequest] =
      (toVal: BankAccountBalanceUpdateRequest) =>
        (
          validateRequired(toVal.currency, "currency"),
          toVal.amount.validNel
        ).mapN(BankAccountBalanceUpdateRequest.apply)
  }

  final case class FailureResponse(error: String)
}

class BankAccountRoutes(bank: ActorRef[Command])(implicit val system: ActorSystem[_]) {

  implicit val ec: ExecutionContextExecutor = system.executionContext

  private implicit val timeout: Timeout = Timeout.create(Duration.ofSeconds(5))

  def findBankAccount(id: String): Future[GetBankAccountResponse] = {
    bank.ask(replyTo => GetBankAccount(id, replyTo))
  }

  def createBankAccount(request: BankAccountCreationRequest): Future[BankAccountCreatedResponse] =
    bank.ask(replyTo => request.toCmd(replyTo))

  def updateBalance(id: String, request: BankAccountBalanceUpdateRequest): Future[BankAccountBalanceUpdatedResponse] =
    bank.ask(replyTo => request.toCmd(id, replyTo))

  val bankAccountRoutes: Route =
    pathPrefix("bank-accounts") {
      concat(
        pathEnd {
          concat(post {
            entity(as[BankAccountCreationRequest]) { unvalidatedRequest =>
              validateEntity(unvalidatedRequest) match {
                case Valid(_) =>
                  onSuccess(createBankAccount(unvalidatedRequest)) { response =>
                    respondWithHeader(Location(s"/bank-accounts/${response.id}")) {
                      complete(StatusCodes.Created)
                    }
                  }
                case Invalid(failure) =>
                  complete(StatusCodes.BadRequest, FailureResponse(failure.toList.map(_.errorMessage).mkString(", ")))
              }
            }
          })
        },
        path(Segment) { id =>
          concat(
            get {
              rejectEmptyResponse {
                onSuccess(findBankAccount(id)) { response =>
                  response.maybeBankAccount match {
                    case Some(bankAccount) => complete(bankAccount)
                    case None =>
                      complete(StatusCodes.NotFound, FailureResponse(s"Bank account with id $id not found"))
                  }
                }
              }
            },
            put {
              entity(as[BankAccountBalanceUpdateRequest]) { unvalidatedRequest =>
                validateEntity(unvalidatedRequest) match {
                  case Valid(_) =>
                    onSuccess(updateBalance(id, unvalidatedRequest)) { response =>
                      response.maybeBankAccount match {
                        case Some(bankAccount) => complete(bankAccount)
                        case None =>
                          complete(StatusCodes.NotFound, FailureResponse(s"Bank account with id $id not found"))
                      }
                    }
                  case Invalid(failure) =>
                    complete(StatusCodes.BadRequest, FailureResponse(failure.toList.map(_.errorMessage).mkString(", ")))
                }
              }
            }
          )
        }
      )
    }

}
