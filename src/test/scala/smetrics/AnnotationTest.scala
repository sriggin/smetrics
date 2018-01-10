package smetrics

import com.codahale.metrics.Timer
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.Timer.Context
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.{ reset, verify, when }
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{ FlatSpec, Matchers }
import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, Future }
import scala.concurrent.ExecutionContext.Implicits.global

class AnnotationTest extends FlatSpec with BeforeAndAfterEach with Matchers with MockitoSugar {

  val metrics = mock[MetricRegistry]
  val timer = mock[Timer]
  val context = mock[Context]

  var lastTime: Long = _
  when(metrics.timer(anyString)).thenReturn(timer)

  override def beforeEach(): Unit = {
    super.beforeEach()
    lastTime = 0
    reset(timer)
    when(timer.time()).thenAnswer { _ =>
      new Context {
        val start = System.nanoTime
        override def stop: Long = {
          lastTime = (System.nanoTime - start) / 1000000 // convert nanos to millis
          lastTime
        }
      }
    }
    ()
  }

  // annotation test methods
  @timed def fooBar: Int = {
    Thread.sleep(10)
    3
  }

  @timedAsync def fooAsync: Future[Int] = {
    Thread.sleep(10)
    Future {
      Thread.sleep(10)
      4
    }
  }

  "@Timed" should "construct a timer of the expected name and record an appropriate time for the method call" in {

    fooBar shouldEqual 3
    assert(lastTime >= 10, "recorded metric time must be >= 10ms")
    verify(metrics).timer(getClass.getName + ".fooBar.time")
  }

  "@TimedAsync" should "construct a timer of the expected name and record an appropriate time for the execution of the method call and completion of returned future" in {
    Await.result(fooAsync, Duration.Inf) shouldEqual 4
    assert(lastTime >= 20, "recorded metric time must be >= 20ms")
    verify(metrics).timer(getClass.getName + ".fooAsync.async.time")
  }
}
