package background

import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue, TimeUnit}

import business._

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by dnmaras on 12/3/14.
 */
object StreamPoller {
  private val ticketing: BlockingQueue[InProcessRequest] = new LinkedBlockingQueue[InProcessRequest]()

  def createBlockingQueueAndStartPolling: BlockingQueue[InProcessRequest] = {
    startConsuming()
    ticketing
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
    val id = req.id
    val userCoins: UserCoins = state.userSession.getOrElse(new UserCoins(id, 0))
    def refuse(answer: Answer.Value) = (answer, state)

    if (userCoins.id != id) {
      refuse(Answer.OtherUserIsUsingTheMachine)
    } else if (state.availableCandies == 0) {
      refuse(Answer.NoMoreCandies)
    }
    else {
      req.op match {
        case Operation.InsertCoin => {
          if (state.stashedCoins == Conf.maxStashedCoins)
            refuse(Answer.StashedCoinsContainerFull)
          else if (userCoins.queuedCoins == Conf.maxQueuedCoins)
            refuse(Answer.CoinQueueFull)
          else
            (Answer.Success, new State(state.stashedCoins, state.availableCandies, Some(new UserCoins(id, userCoins.queuedCoins + 1))))
        }
        case Operation.ExtractCandy => {
          if (userCoins.queuedCoins > 0) {
            (Answer.Success, new State(state.stashedCoins + 1, state.availableCandies - 1, Some(new UserCoins(id, userCoins.queuedCoins - 1))))
          } else {
            refuse(Answer.NoCoinsInTheQueue)
          }
        }
      }
    }
  }

  case class LifePoint(req: CandyMachineRequest, answer: Answer.Value, finalState: State)

  private def life(initialState: State, requests: Stream[CandyMachineRequest]): Stream[LifePoint] = {
    val req = requests.head
    val (answer, newState) = nextState(initialState, req)
    val lifePoint = LifePoint(req, answer, newState)
    lifePoint #:: life(newState, requests.tail)
  }

  private def startConsuming(): Unit = {
    Future {
      val incoming: Stream[InProcessRequest] = immutable.Stream
        .continually(ticketing.poll(1, TimeUnit.DAYS))
        .filter(inProcessReq => inProcessReq != null)

      val requests = incoming.map(in => in.req)

      life(Conf.initialState, requests)
        .zip(incoming)
        .foreach({
        case (lifePoint, original) =>
          original.answerPromise.success(lifePoint.answer)
      })
    }
  }
}
