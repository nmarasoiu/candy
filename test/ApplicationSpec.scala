import java.util.UUID
import java.util.concurrent.CountDownLatch

import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._
import play.api.test.Helpers._
import play.api.test._

import scala.concurrent.Future

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends Specification {
  def get(userId: String, operation: String) = {
    val Some(result) = route(FakeRequest(GET, "/exec?userId=" + userId + "&operation=" + operation))
    result
  }

  "Application" should {

    "send 404 on a bad request" in new WithApplication {
      route(FakeRequest(GET, "/boum")) must beNone
    }

    "main testing" in new WithApplication() {
      val parallelismFactor: Int = 20
      val cdl = new CountDownLatch(parallelismFactor)
      for (iteration <- 1 to parallelismFactor) {
        val B: Int = BAD_REQUEST
        val O: Int = OK
        Future {
          val user = UUID.randomUUID().toString
          List(status(get(user, "candy")),
            status(get(user, "coin")),
            status(get(user, "coin")),
            status(get(user, "candy")),
            status(get(user, "coin")),
            status(get(user, "candy")),
            status(get(user, "candy")),
            status(get(user, "coin")),
            status(get(user, "coin")),
            status(get(user, "candy")),
            status(get(user, "candy")),
            status(get(user, "x")))
        }.foreach( (result:List[Int]) => {
          result must beEqualTo(List(B, O, B, O, O, O, B, O, B, O, B, B))
          cdl.countDown()
        } )
      }
      cdl.await()
    }

  }
}
