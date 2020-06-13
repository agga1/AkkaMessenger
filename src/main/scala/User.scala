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

  // current actorsName of the paired friend
  private var _pair: String = _

  /**
   * Changing current room of the user.
   * If user is not in any room, the parameter is equal to `null`.
   *
   * @param newRoom user is changing room to this new one.
   */
  def changeRoom(newRoom: String): Unit =
    _chatRoom = newRoom

  /**
   * Pair with other user.
   * @param friend other user's Actor name
   */
  def pairWith(friend: String): Unit = {
    _pair = friend
  }
  def unpair(): Unit = { _pair = null }

  /**
   * Checks whether user is currently in any conversation
   */
  def occupied(): Boolean = {
    _pair != null || _chatRoom != null
  }
  /**
   * Getters
   */
  def chatRoom: String = _chatRoom
  def pair: String = _pair
}