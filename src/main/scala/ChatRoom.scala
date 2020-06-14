/**
 * Class represents a chat room. It contains information which users are inside.
 *
 * @param admin user which has created the room (as name of the actor).
 */
class ChatRoom(val admin: String, val name: String) {
  // contains all users (names of their actors)
  private val _users = scala.collection.mutable.HashSet.empty[String]

  /**
   * Adding user to the room
   *
   * @param name name of the actor of the user which is being added.
   */
  def addUser(name: String): Unit = _users.add(name)

  /**
   * Removing user from the room
   *
   * @param name name of the actor of the user which is being removed.
   */
  def removeUser(name: String): Unit = _users.remove(name)

  /**
   * Returns `Array` containing all users in the room (as array of names of their actors).
   */
  def users: Array[String] = _users.toArray
}

class SecureChatRoom(override val admin: String, override val name: String, val password: String) extends ChatRoom(admin, name){
}