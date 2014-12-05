package business

import org.joda.time.DateTime

/**
 * Created by dnmaras on 12/4/14.
 */
case class UserCoins(id: String, queuedCoins: Int, lastActivity: DateTime)
