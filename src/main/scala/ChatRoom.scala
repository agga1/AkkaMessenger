class ChatRoom(val admin: String) {
  private val _users = scala.collection.mutable.HashSet.empty[String]

  def addUser(name: String): Unit = _users.add(name)

  def removeUser(name: String): Unit = _users.remove(name)

  def users: Array[String] = _users.toArray
}