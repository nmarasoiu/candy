package business

import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue, TimeUnit}

import business.dto.{UserCoins, InProcessRequest, State, Answer}
import models.CandyMachineRequest
import org.joda.time.DateTime

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

  private def startConsuming() {
    Future {
      val incoming: Stream[InProcessRequest] = immutable.Stream
        .continually(ticketing.poll(1, TimeUnit.DAYS))
        .filter(inProcessReq => inProcessReq != null)

      val requests = incoming.map(in => in.req)

      Business.life(Conf.initialState, requests)
        .zip(incoming)
        .foreach({
        case (lifePoint, original) =>
          original.answerPromise.success(lifePoint.answer)
      })
    }
  }
}
