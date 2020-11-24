package com.eventcremental.events

import org.joda.time.Instant
import scala.collection.mutable

object Counter {
  private def currentTimeMillis: Long = Instant.now().getMillis

  /** 5 minutes in milliseconds. Upper bound on the time for which counts are retained before getting purged. */
  val MaxRetentionTimeMillis: Long = 1000 * 60 * 5
}

/**
  * Thread-safe fixed-duration event counter. Create statically per logical use case. Stores event counts with epoch
  * formatted timestamps (ms) in a mutable ArrayDeque providing O(1) append, head, removeHead, tail, removeTail
  * operations.
  *
  * Clients can perform two operations:
  * <ul>
  *  <li> Increment the event counter to signal an event at a point in time e.g. (timestamp=0, count=1). Events may
  *  occur at a rate which exceeds the clock precision. For this reason events occurring at the same time are
  *  aggregated into a single value. For example, two events occurring at t=0 would yield (timestamp=0, count=2).
  *  Each time the counter is incremented, an opportunistic check is performed to see if the oldest event record can be
  *  dropped. Incrementing the event counter is a constant time O(1) operation in all cases.
  *  </li>
  *  <li> Request the total number of events that happened over a specified amount of time (ms) until current time.
  *  Requesting the total number of events relative to a given timespan is an O(n) operation where n is the size of the
  *  underlying array. The size of the array is set to the retention time and remains constant throughout the counter
  *  lifecycle.
  *  </li>
  * </ul>
  * <p>
  * An example event counter:
  * <pre>
  * {{{
  * object SomeRecordedApplication {
  *   val requests = new Counter("Http Requests")
  * }
  *
  * class SomeRecordedApplication {
  *   import SomeRecordedApplication._
  *
  *   def processRequest(): Unit = {
  *     requests.inc()
  *     println(s"10 (ms) total: ${requests.total(10)}") // > 10 (ms) total: 1
  *     // your application code
  *   }
  * }
  * }}}
  *
  * @param name The name of the event counter.
  * @param retentionTimeMillis Amount of time event counts should be retained before getting lazily purged.
  * @throws IllegalArgumentException Retention time must be non-negative and cannot be greater than the upper bound.
  */
class Counter(
  name: String,
  retentionTimeMillis: Long = Counter.MaxRetentionTimeMillis
) {
  import Counter._

  require(retentionTimeMillis >= 0, "Retention time must be non-negative.")
  require(retentionTimeMillis <= MaxRetentionTimeMillis, s"Retention time cannot exceed $MaxRetentionTimeMillis (ms).")

  /** Initialize the size of underlying array to the retention time. */
  private val adq = new mutable.ArrayDeque[(Long, Int)](initialSize = retentionTimeMillis.toInt)

  /** Increment the event counter. */
  def inc(): Unit = this.synchronized(inc(currentTimeMillis))

  /**
    * Summation of event counts recorded in the given timespan (ms) relative to the current epoch time.
    *
    * @param timespanMillis Query timespan in milliseconds. Defaults to the retention timespan upper bound.
    * @return The total number of events recorded in the timespan.
    * @throws IllegalArgumentException Timespan must be non-negative and cannot be greater than the upper bound.
    */
  def total(timespanMillis: Long = retentionTimeMillis): Int =
    this.synchronized(total(currentTimeMillis, timespanMillis))

  /**
    * Timestamps are assumed to be monotonically increasing. Each time the counter is incremented:
    *  - Check if the incoming event can be aggregated with the most recent event (if timestamps match).
    *  - Check if the oldest event (head of queue) can be purged (if older than retention time).
    *
    * @param currentTime The current epoch time. Can only be specified in tests.
    * @return Maybe the oldest stale count dropped in the update.
    */
  private[events] final def inc(currentTime: Long): Option[(Long, Int)] = this.synchronized {
    adq.lastOption match {
      case Some((`currentTime`, count)) => adq(adq.length - 1) = (currentTime, count + 1)
      case _                            => adq.addOne((currentTime, 1))
    }

    val dropped = adq.headOption match {
      case Some((timestamp, _)) if (currentTime - timestamp) >= retentionTimeMillis => Some(adq.removeHead())
      case _                                                                        => None
    }

    dropped
  }

  /**
    * Traverse the underlying array summing the event counts recorded between the current time and timespan (ms).
    *
    * @param currentTime The current epoch time. Can only be specified in tests.
    * @param timespanMillis Query timespan.
    * @return The total number of events recorded in the timespan.
    */
  private[events] final def total(
    currentTime: Long,
    timespanMillis: Long
  ): Int = this.synchronized {
    require(timespanMillis >= 0, "Timespan must be non-negative.")
    require(timespanMillis <= MaxRetentionTimeMillis, s"Timespan cannot exceed $MaxRetentionTimeMillis (ms).")
    var total = 0
    adq.foreach { case (time, count) => if ((currentTime - time) < timespanMillis) total += count }
    total
  }

  /** Only used in tests. */
  private[events] final def counts: Seq[(Long, Int)] = this.synchronized(Seq.empty ++ adq)
}
