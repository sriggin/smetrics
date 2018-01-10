package smetrics

import com.codahale.metrics.Timer
import com.codahale.metrics.Timer.Context
import scala.annotation.implicitNotFound
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.higherKinds

/** Companion object for `AsyncTimeable` typeclass, including usage of the
  * typeclass to time the context around the entire execution of some asynchronous
  * code.
  */
object AsyncTimeable {
  /** Applies the `AsyncTimeable` typeclass to a provided timer and call-by-name asynchronous context. This method will begin the timer, resulting in a context. It will then evaluate the async parameter, and then apply the `AsyncTimeable` to the returned async value.
    * @tparam T Async context type, for which an `AsyncTimeable` exists.
    * @tparam R Specific type contained in T, used to return the same type `T[R]`
    * @param timer The `Timer` metric to be used for the timing of `async`
    * @param async The call-by-name async value to be evaluated and timed
    * @return The value evaluated from `async`, for which completion is timed
    */
  def apply[T[_]: AsyncTimeable, R](timer: Timer)(async: => T[R]): T[R] = {
    val context = timer.time // start timing immediately prior to starting async
    val result = async // evaluates the async parameter, "starting" the computation
    val timeable = implicitly[AsyncTimeable[T]]
    timeable.time(context, result)
  }

  /** Provides a default `AsyncTimeable` for `scala.concurrent.Future`, per
    * implicit resolution, so that no additional imports are necessary.
    */
  implicit def scalaFutureTimeable: AsyncTimeable[Future] = ScalaFutureTimeable
}

/** Type-class for completing the given `Timer.Context` for the given asynchronous context type.
  * @tparam T Type-constructor of the asynchronous context e.g. `scala.concurrent.Future`
  */
@implicitNotFound(msg = "Cannot find AsyncTimeable instance for type ${T}")
trait AsyncTimeable[T[_]] {
  /**
    * Adds completion of the provided `Timer.Context` to the given asynchronous computation, constructing a resulting "future" of the same type which includes metric completion.
    * 
    * @tparam R The type contained in `T` used to return the same type that is timed
    * @param context The `Context` that is constructed by starting a relevant `Timer` metric for this async computation.
    * @param async The call-by-name asynchronous computation to be timed
    * @return The result of appending stopping of `context` to the completion of `async`
    */
  def time[R](context: Context, async: => T[R]): T[R]
}

/**
  * An implementation of `AsyncTimeable` specific to Scala's built-in `Future`,
  * since this introduces no additional dependencies.
  */
object ScalaFutureTimeable extends AsyncTimeable[Future] {
  /*
   To get the strictest possible timing around a Future, we chain
   {{context.stop()}} to the {{Future}} using this execution context, which
   strictly evaluates the chained expression on the calling thread rather than
   deferring completion into the queue of a "normal" {{ExecutionContext}}.
   */
  private[this] val sameThreadExecutionContext = new ExecutionContext {
    override def execute(runnable: Runnable): Unit = runnable.run()
    // $COVERAGE-OFF$ This is a no-op because it should be unreachable
    override def reportFailure(cause: Throwable): Unit = ()
    // $COVERAGE-ON$
  }

  override def time[R](context: Context, future: => Future[R]): Future[R] = {
    future.andThen {
      case _ => context.stop()
    }(sameThreadExecutionContext)
  }
}
