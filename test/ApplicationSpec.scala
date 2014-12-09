import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._
import play.api.test.Helpers._
import play.api.test._

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends Specification {
  def get(userId: Int, operation: String) = {
    val Some(result) = route(FakeRequest(GET, "/exec?userId=" + userId + "&operation=" + operation))
    result
  }

  "Application" should {

    "send 404 on a bad request" in new WithApplication {
      route(FakeRequest(GET, "/boum")) must beNone
    }

    "cannot start by asking candy" in new WithApplication() {
      status(get(23, "candy")) must beEqualTo(BAD_REQUEST)
      status(get(23,"coin")) must beEqualTo(OK)
      status(get(23,"coin")) must beEqualTo(BAD_REQUEST)
      status(get(231,"candy")) must beEqualTo(BAD_REQUEST)
      status(get(23,"candy")) must beEqualTo(OK)
      status(get(23,"candy")) must beEqualTo(BAD_REQUEST)
      status(get(23,"candy")) must beEqualTo(BAD_REQUEST)
      status(get(23,"candy")) must beEqualTo(BAD_REQUEST)
      status(get(23,"candy")) must beEqualTo(BAD_REQUEST)
      status(get(23,"candy")) must beEqualTo(BAD_REQUEST)
      status(get(23,"candy")) must beEqualTo(BAD_REQUEST)
      status(get(23,"x")) must beEqualTo(BAD_REQUEST)
    }

  }
}
