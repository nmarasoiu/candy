package business

import org.joda.time.Seconds

/**
 * Created by dnmaras on 12/4/14.
 */
object Conf {
  val maxInactiveDuration = Seconds.seconds(15)

  val initialState: State = new State(10, 10, None)

  val maxQueuedCoins = 100
  val maxStashedCoins = 100
}
