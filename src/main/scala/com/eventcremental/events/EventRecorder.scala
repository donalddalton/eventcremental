package com.eventcremental.events

import org.joda.time.Instant
import scala.collection.mutable

object EventRecorder {
  private def currentTimeMillis: Long = Instant.now().getMillis

  /** 5 minutes in milliseconds. */
  val DefaultRetentionTimeMillis: Long = 1000 * 60 * 5
}

/**
  * Thread-safe fixed-duration event recorder. Create statically per logical use case. Stores events with epoch
  * formatted timestamps (ms) in a mutable ArrayDeque providing O(1) append, head, removeHead, tail, removeTail
  * operations.
  *
  * Clients can perform two operations:
  * <ul>
  *  <li> Signal that a single event happened. Each time a new event is recorded, an opportunistic check is performed to
  *  see if the oldest event can be dropped. This is a constant time operation (append + head + removeHead).
  *  </li>
  *  <li> Request the number of events that happened over a specified amount of time (ms) until current time. Requesting
  *  the total number of events relative to a given timespan is an O(n) operation where n is the size of the underlying
  *  (resizeable) array.
  *  </li>
  * </ul>
  * <p>
  * An example event recorder:
  * <pre>
  * {{{
  *  object SomeRecordedApplication {
  *    val requests = new EventRecorder("Http Requests")
  *  }
  *
  *  class SomeRecordedApplication {
  *    def processRequest(): Unit = {
  *      requests.record()
  *    }
  *  }
  * }}}
  * @param name The name of the event recorder.
  * @param retentionTimeMillis Amount of time events should be retained.
  * @throws IllegalArgumentException Retention time must be non-negative.
  */
class EventRecorder(
  name: String,
  retentionTimeMillis: Long = EventRecorder.DefaultRetentionTimeMillis
) {
  import EventRecorder._

  require(retentionTimeMillis >= 0, "Retention time must be non-negative.")

  /** Initialize the size of underlying array to the retention time. */
  private val adq = new mutable.ArrayDeque[Long](initialSize = retentionTimeMillis.toInt)

  /** Record an event. */
  def record(): Unit = record(Some(currentTimeMillis))

  /**
    * Returns the number of events recorded in a given timespan (ms) relative to the current epoch time.
    *
    * @param timespanMillis Query timespan in milliseconds.
    * @return The number of events recorded in the timespan.
    * @throws IllegalArgumentException Timespan must be non-negative.
    */
  def getCount(timespanMillis: Long = retentionTimeMillis): Int = getCount(Some(currentTimeMillis), timespanMillis)

  /**
    * Timestamps are assumed to be monotonically increasing. Each time a new event is recorded, check if the oldest
    * event (head of queue) can be purged.
    *
    * @param currentTime Optionally override the clock for tests.
    * @return The oldest event dropped in the update.
    */
  private[events] final def record(currentTime: Option[Long]) = this.synchronized {
    val now = currentTime.getOrElse(currentTimeMillis)
    val dropped = adq.headOption match {
      case Some(oldestEvent) if (now - oldestEvent) > retentionTimeMillis => Some(adq.removeHead())
      case _                                                              => None
    }
    adq.addOne(now)

    dropped
  }

  /**
    * Traverse the underlying array counting the number of events recorded between the current time and timespan.
    *
    * @param currentTime Optionally override the clock for tests.
    * @param timespanMillis Query timespan.
    * @return The number of events recorded in the timespan.
    */
  private[events] final def getCount(
    currentTime: Option[Long],
    timespanMillis: Long
  ): Int = this.synchronized {
    require(timespanMillis >= 0, "Timespan must be non-negative.")
    val now = currentTime.getOrElse(currentTimeMillis)
    adq.count(event => (now - event) <= timespanMillis)
  }

  /** Only used in tests. */
  private[events] final def getEvents: Seq[Long] = this.synchronized(adq.toSeq)
}
