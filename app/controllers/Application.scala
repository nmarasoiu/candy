package controllers

import java.util.concurrent.TimeUnit

import akka.actor.Props
import akka.pattern.Patterns.ask
import akka.util.Timeout
import business._
import business.dto.{Answer, Operation}
import models._
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._

import scala.concurrent.Future

object Application extends Controller {
  val candyMachine = Akka.system.actorOf(Props(classOf[CandyMachineActor], Conf.initialCandies), name = "candyMachine")
  val userIdForm: Form[CandyMachineRequestDuplicate] = Form {
    mapping(
      "userId" -> nonEmptyText,
      "operation" -> nonEmptyText
    )(CandyMachineRequestDuplicate.apply)(CandyMachineRequestDuplicate.unapply)
  }

  def exec = Action.async { implicit request =>
    val input = userIdForm.bindFromRequest.get
    decode(input.operation)
      .map(op => new CandyMachineRequest(input.userId, op))
      .map(req => execute(req))
      .getOrElse(Future(BadRequest("Unrecognized operation")))
  }

  def decode(s: String): Option[Operation.Value] = s match {
    case "candy" => Option(Operation.Candy)
    case "coin" => Option(Operation.Coin)
    case "refill" => Option(Operation.Refill)
    case _ => None
  }

  def execute(req: CandyMachineRequest): Future[Result] = {
    ask(candyMachine, req, Timeout(1, TimeUnit.MINUTES))
      .map {
      case Answer.Success =>
        Ok("done")
      case Answer.NoCoinsInTheQueue =>
        BadRequest("You need to introduce a coin first!")
      case Answer.CannotInsertMoreThanOneCoin =>
        BadRequest("You cannot introduce too many coins! Please extract candies first!")
      case Answer.NoMoreCandies =>
        ServiceUnavailable("There are no more candies at this moment in the machine! Pls come back!")
      case Answer.OtherUserIsUsingTheMachine =>
        ServiceUnavailable("There is another user still using the machine! Pls come back!")
    }
  }
}