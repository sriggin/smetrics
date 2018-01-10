package smetrics

import scala.reflect.macros.whitebox.Context
import scala.language.experimental.macros
import scala.annotation.StaticAnnotation
import scala.annotation.compileTimeOnly

/** Creates a new `Timer` metric and wraps the annotated method body with the
  * beginning/end of the timer.
  *
  * =Example=
  * {{{
  * @timed def foo = bar
  * }}}
  * 
  * =Is translated to:=
  * 
  * {{{
  * private[this] val fooTimer = metricsRegistry.timer(MetricsRegistry.name(getClass, "foo", "time"))
  * def foo = {
  *   val randomName = fooTimer.time
  *   try {
  *     bar
  *   } finally {
  *     randomName.stop()
  *   } 
  * }
  * }}}
  */
@compileTimeOnly("enable macro paradise to expand macro annotations")
class timed extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro MetricMacros.timedAnnotation
}

/** Creates a new `Timer` metric and wraps the annotated method body with the
  * metric. Completion of the returned asynchronous value completes the timer,
  * using the `AsyncTimeable` typeclass instance for the returned type.
  *
  * =Example=
  * {{{
  * @timedAsync def foo = Future(bar)
  * }}}
  * 
  * =Is translated to:=
  *   
  * {{{
  * private[this] val fooAsyncTimer = metricsRegistry.timer(MetricsRegistry.name(getClass, "foo", "async", "time"))
  * def foo = {
  *   timeAsync { // uses typeclass for timing returned Future, with call-by-name to ensure strict timing of lifetime of Future
  *     Future(bar)
  *   }
  * }
  * }}}
  */
@compileTimeOnly("enable macro paradise to expand macro annotations")
class timedAsync extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro MetricMacros.timedAsyncAnnotation
}

protected[smetrics] object MetricMacros {
  /*
   ```
   @timed def foo = bar
   ```
   Becomes
   ```
   private[this] val fooTimer = metricsRegistry.timer(MetricsRegistry.name(getClass, "foo", "time"))
   def foo = {
     val randomName = fooTimer.time
     try {
       bar
     } finally {
       randomName.stop()
     } 
   }
   ```
   */  
  def timedAnnotation(c: Context)(annottees: c.Tree*): c.Tree = {
    import c.universe._

    if(!annottees.head.isDef) {
      c.abort(annottees.head.pos, "@timed annotation must be used on a method.")
    }

    val (input: DefDef) :: rest = annottees.toList
    val TermName(defName)  = input.name
    val registry = TermName("metrics")
    val timerName = TermName(defName + "Timer")
    val timingName = TermName(c.freshName("timing"))
    /*
     Original RHS is wrapped in try/finally to ensure timing is captured.
     `finally` is terminated with a unit to prevent the compiler warning that `$timingName.stop()` would discard the resulting `Long`
     */
    val timedRhs = q"""{
      val $timingName = $timerName.time
        try {
          ${input.rhs}
        } finally {
          $timingName.stop()
          ()
        }
    }"""
    val annotatedDef = DefDef(input.mods, input.name, input.tparams, input.vparamss, input.tpt, timedRhs)
    q"""
      private[this] val $timerName = $registry.timer(com.codahale.metrics.MetricRegistry.name(getClass, $defName, "time"))
      $annotatedDef
      ..$rest
    """
  }

  /*
   ```
   @timedAsync def foo = Future(bar)
   ```

   Becomes

   ```
   private[this] val fooAsyncTimer = metricsRegistry.timer(MetricsRegistry.name(getClass, "foo", "async", "time"))
   def foo = {
     timeAsync { // uses typeclass for timing returned Future, with call-by-name to ensure strict timing of lifetime of Future
       Future(bar)
     }
   }
   ```
   */
  def timedAsyncAnnotation(c: Context)(annottees: c.Tree*): c.Tree = {
    import c.universe._

    if(!annottees.head.isDef) {
      c.abort(annottees.head.pos, "@timedAsync annotation must be used on a method.")
    }

    val (input: DefDef) :: rest = annottees.toList
    val TermName(defName)  = input.name
    val registry = TermName("metrics")
    val timerName = TermName(defName + "Timer")
    val timedRhs = q"""{
      smetrics.AsyncTimeable.apply($timerName) {
        ${input.rhs}
      }
    }"""
    val annotatedDef = DefDef(input.mods, input.name, input.tparams, input.vparamss, input.tpt, timedRhs)
    q"""
      private[this] val $timerName = $registry.timer(com.codahale.metrics.MetricRegistry.name(getClass, $defName, "async", "time"))
      $annotatedDef
      ..$rest
    """

  }
}
