package controllers

import java.util.concurrent.BlockingQueue
import business._
import models._
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._

object Application extends Controller {
  private val ticketing: BlockingQueue[InProcessRequest] = StreamPoller.createBlockingQueueAndStartPolling

  val userIdForm: Form[CandyMachineRequest] = Form {
    mapping(
      "userId" -> nonEmptyText,
      "operation" -> nonEmptyText
    )(CandyMachineRequest.apply)(CandyMachineRequest.unapply)
  }

  def exec = Action.async { implicit request =>
    val inFlightRequest = InProcessRequest(userIdForm.bindFromRequest.get)
    ticketing.add(inFlightRequest)
    inFlightRequest.answerPromise.future
      .map {
      case Answer.Success =>
        Ok("done")
      case Answer.NoCoinsInTheQueue =>
        BadRequest("You need to introduce a coin first!")
      case Answer.CoinQueueFull =>
        BadRequest("You cannot introduce too many coins! Please extract candies first!")
      case Answer.NoMoreCandies =>
        ServiceUnavailable("There are no more candies at this moment in the machine! Pls come back!")
      case Answer.StashedCoinsContainerFull =>
        ServiceUnavailable("There is a need for maintenance on the machine, please come back later!")
      case Answer.OtherUserIsUsingTheMachine =>
        ServiceUnavailable("There is another user still using the machine! Pls come back!")
    }
  }

}