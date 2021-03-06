package business

import business.dto.{Answer, LifePoint, State, UserCoins}
import models.CandyMachineRequest
import org.joda.time.DateTime

/**
 * Created by dnmaras on 12/5/14.
 */
object Business {
  type Life = Stream[LifePoint]

  def life(initialState: State, requests: Stream[CandyMachineRequest]): Life = {
    val req = requests.head
    val (answer, newState) = nextState(initialState, req)
    val lifePoint = LifePoint(req, answer, newState)
    lifePoint #:: life(newState, requests.tail)
  }

  /**
   * Implements the business logic:
   *
   * On any operation attempt, if there is a coin introduced by another user, the current user is refused.
   *
   * When the user tries to introduce a coin:
   * - if the machine is out of candies, the coin is refused
   * - if the coin queue is full, the coin is refused
   * - if the stashed coin container is full, the coin is refused
   *
   * When the user tries to extract a candy:
   * - if there is no coin in the queue, he is refused
   * - if there is a coin but not his own, the op is refused (this is implied by the general rule above)
   * - if the machine is out of candies, the op is refused (should not reach this point ever)
   *
   * Refill..to be continued
   *
   * @param state
   * @param req
   * @return
   */
  def nextState(state: State, req: CandyMachineRequest): (Answer.Value, State) = {
    val id = req.userId
    def isTooOld(userSession: UserCoins): Boolean = {
      userSession.lastActivity.compareTo(new DateTime().minus(Conf.maxInactiveDuration)) < 0
    }
    val userCoins = state.userSession
      .flatMap(userSession => if (isTooOld(userSession) || userSession.queuedCoins <= 0) None else Some(userSession))
      .getOrElse(new UserCoins(id, 0, null))
    def refuse(answer: Answer.Value) = (answer, state)

    if (userCoins.id != id) {
      refuse(Answer.OtherUserIsUsingTheMachine)
    } else if (state.availableCandies == 0) {
      refuse(Answer.NoMoreCandies)
    }
    else {
      val queuedCoins = userCoins.queuedCoins
      //in real life I would make operation a enum (via case Object or Enumeration)
      req.operation match {
        case "coin" => {
          if (state.stashedCoins == Conf.maxStashedCoins)
            refuse(Answer.StashedCoinsContainerFull)
          else if (queuedCoins == Conf.maxQueuedCoins)
            refuse(Answer.CoinQueueFull)
          else
            (Answer.Success, new State(state.stashedCoins, state.availableCandies, Some(new UserCoins(id, queuedCoins + 1, new DateTime()))))
        }
        case "candy" => {
          if (queuedCoins > 0) {
            val newUserCoinsOption = if (queuedCoins == 1) None else Some(new UserCoins(id, queuedCoins - 1, new DateTime()))
            (Answer.Success, new State(state.stashedCoins + 1, state.availableCandies - 1, newUserCoinsOption))
          } else {
            refuse(Answer.NoCoinsInTheQueue)
          }
        }
      }
    }
  }

}
