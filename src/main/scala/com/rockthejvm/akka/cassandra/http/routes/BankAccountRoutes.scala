package com.rockthejvm.akka.cassandra.http.routes

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive1, Route}
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

  def findBankAccount(id: String): Future[Response] = {
    bank.ask(replyTo => GetBankAccount(id, replyTo))
  }

  def createBankAccount(request: BankAccountCreationRequest): Future[Response] =
    bank.ask(replyTo => request.toCmd(replyTo))

  def updateBalance(id: String, request: BankAccountBalanceUpdateRequest): Future[Response] =
    bank.ask(replyTo => request.toCmd(id, replyTo))

  def validateRequest[R: Validable](request: R)(validRoute: Route): Route =
    validateEntity(request) match {
      case Valid(_) =>
        validRoute
      case Invalid(failure) =>
        complete(StatusCodes.BadRequest, FailureResponse(failure.toList.map(_.errorMessage).mkString(", ")))
    }

  /*
    optional: can compress

          entity(as[BankAccountCreationRequest]) { unvalidatedRequest =>
            validateRequest(unvalidatedRequest) {
              // something that uses the request
            }
          }

    into a single directive

          entityWithValidation[BankAccountCreationRequest] { validatedReq =>
            // same code
          }
   */
  def entityWithValidation[R: Validable](handler: R => Route): Route =
    entity(as[R]) { req =>
      validateRequest(req) {
        handler(req)
      }
    }

  /*
    POST / (bank account creation)
      201 created
      Location: /bank-accounts/UUID
      - or -
      400 bad request

    GET /UUID
      200 OK
      Bank account stats as JSON
      - or -
      404 not found

    PUT /UUID (bank account update)
      200 ok
      Bank account stats as JSON
      - or -
      400 bad request
      - or -
      404 not found
   */
  val bankAccountRoutes: Route =
    pathPrefix("bank-accounts") {
      pathEnd {
        post {
          entity(as[BankAccountCreationRequest]) { unvalidatedRequest =>
            validateRequest(unvalidatedRequest) {
              onSuccess(createBankAccount(unvalidatedRequest)) {
                case BankAccountCreatedResponse(id) =>
                  respondWithHeader(Location(s"/bank-accounts/$id")) {
                    complete(StatusCodes.Created)
                  }
              }
            }
          }
        }
      } ~
      path(Segment) { id =>
        get {
          rejectEmptyResponse {
            onSuccess(findBankAccount(id)) {
              case GetBankAccountResponse(Some(bankAccount)) => complete(bankAccount)
              case GetBankAccountResponse(None) =>
                complete(StatusCodes.NotFound, FailureResponse(s"Bank account with id $id not found"))
            }
          }
        } ~
        put {
          entity(as[BankAccountBalanceUpdateRequest]) { unvalidatedRequest =>
            validateRequest(unvalidatedRequest) {
              onSuccess(updateBalance(id, unvalidatedRequest)) {
                case BankAccountBalanceUpdatedResponse(Some(bankAccount)) => complete(bankAccount)
                case BankAccountBalanceUpdatedResponse(None) =>
                  complete(StatusCodes.NotFound, FailureResponse(s"Bank account with id $id not found"))
              }
            }
          }
        }
      }
    }
}
