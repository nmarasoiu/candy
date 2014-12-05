package business.dto

import models.CandyMachineRequest

import scala.concurrent.Promise

/**
 * Created by dnmaras on 12/4/14.
 */
case class InProcessRequest(req: CandyMachineRequest, answerPromise: Promise[Answer.Value] = Promise[Answer.Value]())
