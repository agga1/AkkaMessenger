import akka.actor.ActorRef

class User(val actorRef: ActorRef) {
  var name: String = _
  private var _chatRoom: String = _

  def changeRoom(newRoom: String): Unit =
    _chatRoom = newRoom

  def chatRoom: String = _chatRoom
}