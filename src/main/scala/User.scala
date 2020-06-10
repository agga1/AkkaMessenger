import akka.actor.ActorRef

/**
 * Class containing handler to the connection with the user (`actorRef`), name using which user identifies themselves
 * (`name`) and name of the room in which the user is (`_chatRoom`).
 *
 * @param actorRef handler to the connection with the user.
 */
class User(val actorRef: ActorRef) {
  // name using which the user identified themselves
  var name: String = _

  // current room in which the user is
  private var _chatRoom: String = _

  private var _pair: String = _

  /**
   * Changing current room of the user. In case the parameter is equal to `null`, then it means that user is not in any
   * room.
   *
   * @param newRoom user is changing room to this new one.
   */
  def changeRoom(newRoom: String): Unit =
    _chatRoom = newRoom

  def pairWith(friend: String): Unit = {
    _pair = friend
  }

  /**
   * Getters
   */
  def chatRoom: String = _chatRoom
  def pair: String = _pair
}