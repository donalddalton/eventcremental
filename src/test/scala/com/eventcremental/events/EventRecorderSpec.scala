package com.eventcremental.events

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class EventRecorderSpec extends AnyFlatSpec with Matchers {

  behavior of "Event Recording"

  "recorder" should "record an event" in {
    val recorder = new EventRecorder("foo")
    recorder.record(Some(0))                          // record an event at t=0
    recorder.getEvents shouldBe Seq(0)
  }

  "recorder" should "record two events" in {
    val recorder = new EventRecorder("foo")
    recorder.record(Some(0))                          // record an event at t=0
    recorder.record(Some(1))                          // record an event at t=1
    recorder.getEvents shouldBe Seq(0, 1)
  }

  "recorder" should "record multiple events that occur at the same time" in  {
    // events may come in at a rate that exceeds the clock precision (millis)
    val recorder = new EventRecorder("foo", 2)
    recorder.record(Some(0))
    recorder.record(Some(0))
    recorder.record(Some(0))
    recorder.getEvents shouldBe Seq(0, 0, 0)
  }

  "recorder" should "drop stale events when recording new events" in {
    val recorder = new EventRecorder("foo", 2)
    recorder.record(Some(0))                   // record an event at t=0
    recorder.record(Some(1))                   // record an event at t=1
    recorder.getEvents shouldBe Seq(0, 1)
    recorder.record(Some(3)) shouldBe Some(0)  // at t=3 the t=0 event becomes stale so it should get dropped
    recorder.getEvents shouldBe Seq(1, 3)
  }

  behavior of "Event Counting"

  "recorder" should "return 0 when empty" in {
    new EventRecorder("foo").getCount() shouldBe 0
  }

  "recorder" should "count events" in {
    val recorder = new EventRecorder("foo", 5)
    recorder.record(Some(0))                          // record an event at t=0
    recorder.record(Some(1))                          // record an event at t=1
    recorder.getEvents shouldBe Seq(0, 1)
    recorder.getCount(Some(1), 2) shouldBe 2          // set clock to t=1 and request last 2 ms of events (1, 0)
  }

  "recorder" should "filter events by timespan" in {
    val recorder = new EventRecorder("foo", 5)
    recorder.record(Some(0))                          // record an event at t=0
    recorder.record(Some(1))                          // record an event at t=1
    recorder.record(Some(3))                          // record an event at t=3
    recorder.record(Some(4))                          // record an event at t=4
    recorder.record(Some(5))                          // record an event at t=5
    recorder.getEvents shouldBe Seq(0, 1, 3, 4, 5)
    recorder.getCount(Some(5), 2) shouldBe 3          // set clock to t=5 and request last 2 ms of events (5, 4, 3)
  }

  "recorder" should "not count stale events" in {
    val recorder = new EventRecorder("foo", 2)
    recorder.record(Some(0))                          // record an event at t=0
    recorder.record(Some(1))                          // record an event at t=1
    recorder.getEvents shouldBe Seq(0, 1)
    recorder.getCount(Some(100), 2) shouldBe 0        // set clock to t=100 and request last 2 ms of events
  }

  behavior of "Error Handling"

  "recorder" should "throw an error for negative retention times" in {
    val ex = the[IllegalArgumentException] thrownBy new EventRecorder("foo",-1)
    ex.getMessage should include("Retention time must be non-negative.")
  }

  "recorder" should "throw an error for negative range queries" in {
    val recorder = new EventRecorder("foo", 2)
    val ex = the[IllegalArgumentException] thrownBy recorder.getCount(-1)
    ex.getMessage should include("Timespan must be non-negative.")
  }
}