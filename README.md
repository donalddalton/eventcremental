# Eventcremental

Small library which helps track the number of events that happened during a specified window of time.

### Commands
- Test `$ sbt test`
- Lint `$ sbt scalafmtAll`
- Doc `$ sbt doc`

### Example
```scala
object SomeRecordedApplication {
  val requests = new Counter("Http Requests")
}

class SomeRecordedApplication {
  import SomeRecordedApplication._

  def processRequest(): Unit = {
    requests.inc()
    println(s"10 (ms) total: ${requests.total(10)}") // > 10 (ms) total: 1
    // your application code
  }
}
```
