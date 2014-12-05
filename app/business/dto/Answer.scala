package business.dto

/**
 * Created by dnmaras on 12/4/14.
 */
object Answer  extends Enumeration {
  val Success, OtherUserIsUsingTheMachine, StashedCoinsContainerFull, CoinQueueFull, NoMoreCandies, NoCoinsInTheQueue = Value

}
