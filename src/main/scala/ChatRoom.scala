class ChatRoom(val admin: String) {
  val users = scala.collection.mutable.HashSet.empty[String]

  def addUser(name: String): Unit = users.add(name)

  def removeUser(name: String): Unit = users.remove(name)

  def getAllUsers: Array[String] = users.toArray
}