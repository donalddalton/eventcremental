package com.eventcremental.events

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CounterSpec extends AnyFlatSpec with Matchers {

  behavior of "Incrementing"

  "a counter" should "increment" in {
    val counter = new Counter("foo")
    counter.inc(0) // increment counter at t=0
    counter.counts shouldBe Seq((0, 1))
  }

  "a counter" should "increment at two different times" in {
    val counter = new Counter("foo")
    counter.inc(0) // increment counter at t=0
    counter.inc(1) // increment counter at t=1
    counter.counts shouldBe Seq((0, 1), (1, 1))
  }

  "a counter" should "aggregate counts for the same timestamp when incrementing" in {
    // the counter may be incremented at a rate that exceeds clock precision
    val counter = new Counter("foo", 1)
    for (i <- 1 to 5) {
      counter.inc(0)
      counter.counts shouldBe Seq((0, i))
    }
  }

  "a counter" should "drop stale counts when incrementing" in {
    val counter = new Counter("foo", 2) // set the retention time to 2 ms
    counter.inc(0) // increment counter at t=0
    counter.inc(1) // increment counter at t=1
    counter.counts shouldBe Seq((0, 1), (1, 1))
    counter.inc(3) shouldBe Some((0, 1)) // increment counter at t=3, at t=3 the t=0 event is stale and gets dropped
    counter.counts shouldBe Seq((1, 1), (3, 1))
  }

  behavior of "Counting"

  "a counter" should "count zero total events when empty" in {
    new Counter("foo").total() shouldBe 0
  }

  "a counter" should "count the total number of events in a timespan" in {
    val counter = new Counter("foo", 2)
    counter.inc(0) // increment counter at t=0
    counter.inc(1) // increment counter at t=1
    counter.counts shouldBe Seq((0, 1), (1, 1))
    counter.total(1, 2) shouldBe 2 // set clock to t=1 and request total events from last 2 ms
  }

  "a counter" should "not include stale counts in the total" in {
    val counter = new Counter("foo", 4)
    val timestamps = Seq(0, 2, 3) // increment the counter at t=0,2,3
    timestamps.foreach(ts => counter.inc(ts))
    counter.counts shouldBe timestamps.map(ts => (ts, 1))
    counter.total(3, 2) shouldBe 2 // set clock to t=3 and request total events from last 2 ms
  }

  behavior of "Error Handling"

  "a counter" should "throw an error for negative retention times" in {
    val ex = the[IllegalArgumentException] thrownBy new Counter("foo", -1)
    ex.getMessage should include("Retention time must be non-negative.")
  }

  "a counter" should "throw an error for retention times greater than the upper limit" in {
    val ex = the[IllegalArgumentException] thrownBy new Counter("foo", Counter.MaxRetentionTimeMillis + 1)
    ex.getMessage should include(s"Retention time cannot exceed ${Counter.MaxRetentionTimeMillis}")
  }

  "a counter" should "throw an error for negative range queries" in {
    val counter = new Counter("foo", 2)
    val ex = the[IllegalArgumentException] thrownBy counter.total(-1)
    ex.getMessage should include("Timespan must be non-negative.")
  }

  "a counter" should "throw an error for range queries greater than the upper limit" in {
    val counter = new Counter("foo", 2)
    val ex = the[IllegalArgumentException] thrownBy counter.total(Counter.MaxRetentionTimeMillis + 1)
    ex.getMessage should include(s"Timespan cannot exceed ${Counter.MaxRetentionTimeMillis}")
  }
}
