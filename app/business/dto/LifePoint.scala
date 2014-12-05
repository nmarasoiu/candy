package business.dto

import models.CandyMachineRequest

/**
 * Created by dnmaras on 12/5/14.
 */
case class LifePoint(req: CandyMachineRequest, answer: Answer.Value, finalState: State)
