package controllers

import java.util.concurrent.TimeUnit

import akka.actor.Props
import akka.pattern.Patterns.ask
import akka.util.Timeout
import business._
import business.dto.Answer
import models._
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._;
import business.dto.Operation

import scala.concurrent.Future

object Application extends Controller {
  val candyMachine = Akka.system.actorOf(Props(classOf[CandyMachineActor], Conf.initialCandies), name = "candyMachine")
  val userIdForm: Form[CandyMachineRequestDuplicate] = Form {
    mapping(
      "userId" -> nonEmptyText,
      "operation" -> nonEmptyText
    )(CandyMachineRequestDuplicate.apply)(CandyMachineRequestDuplicate.unapply)
  }

  def decode(s: String): Operation.Value = s match {
    case "candy" => Operation.Candy
    case "coin" => Operation.Coin
    case "refill" => Operation.Refill
  }

  def exec = Action.async { implicit request =>
    val duplicate: CandyMachineRequestDuplicate = userIdForm.bindFromRequest.get
    val req = new CandyMachineRequest(duplicate.userId, decode(duplicate.operation))
    execute(req)
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