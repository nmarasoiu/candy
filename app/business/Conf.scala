package business

import org.joda.time.{Duration, Seconds}

/**
 * Created by dnmaras on 12/4/14.
 */
object Conf {
  val maxInactiveDuration = new Duration(10000)
  val initialCandies = 100
}
