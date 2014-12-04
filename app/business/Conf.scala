package business

/**
 * Created by dnmaras on 12/4/14.
 */
object Conf {
  val initialState: State = new State(10, 10, None)

  val maxQueuedCoins = 100
  val maxStashedCoins = 100
}
