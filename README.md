# Eventcremental

Small library which helps track the number of events that happened during a specified window of time.

### Commands
- Test `$ sbt test`
- Lint `$ sbt scalafmtAll`
- Doc `$ sbt doc`

### Example
```scala
object SomeRecordedApplication {
  val requests = new EventRecorder("Http Requests")
}

class SomeRecordedApplication {
  def processRequest(): Unit = {
    requests.record()
  }
}
```
