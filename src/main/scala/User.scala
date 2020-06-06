import akka.actor.ActorRef

class User(val actorRef: ActorRef) {
  var name: String = _
}