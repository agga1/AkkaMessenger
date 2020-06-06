import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.io.Tcp._
import akka.io.{IO, Tcp}

import scala.collection.mutable

import Utils.{Message, helpText}


class ServerActor(actorSystem: ActorSystem) extends Actor {
  val port = 18573
  val server = "localhost"

  val activeUsers = mutable.HashMap.empty[String, User]
  val chatRooms = mutable.HashMap.empty[String, ChatRoom]

  IO(Tcp)(actorSystem) ! Bind(self, new InetSocketAddress(server, port))

  def receive: Receive = {
    case CommandFailed(_: Bind) =>
      println("Failed to start listening on " + Server + ":" + port)
      context stop self
      actorSystem.terminate()

    case Bound(localAddress: InetSocketAddress) =>
      println("Started listening on " + localAddress)

    case Connected(_, _) =>
      activeUsers += (sender.path.name -> new User(sender))

      sender ! Register(self)
      sendMessage(sender.path.name, Message("<SERVER>: To check possible commands enter: \\help"))

    case Received(data) =>
      val message = Message(data)
      val clientActorName = sender.path.name

      if (message.isCommand) {
        message.command match {
          case Some("help") => help(clientActorName)
          case Some("login") => login(clientActorName, message.text)
          case Some("online") => online(clientActorName)
          case Some("create") => createChatRoom(clientActorName, message.text)
          case Some("connect") => connect(clientActorName, message.text)
          case Some("leave") => leave(clientActorName)
          case _ => sendMessage(clientActorName, Message("<SERVER>: Unknown command!"))
        }
      } else
        loggedSafe(clientActorName, {
            sendToAll(clientActorName, activeUsers(clientActorName).chatRoom,
              Message("<" + activeUsers(clientActorName).name + ">: " + message.text))
        })

    case PeerClosed => quit(sender.path.name)
  }

  def help(clientActorName: String): Unit = sendMessage(clientActorName, Message(helpText))

  def login(clientActorName: String, desiredName: String): Unit = {
    if (activeUsers.values.exists(_.name == desiredName)) {
      sendMessage(clientActorName, Message("There is already a user with this username!"))
    } else if (activeUsers.keys.exists(_ == clientActorName) && activeUsers(clientActorName).name != null) {
      sendMessage(clientActorName, Message("You are already logged in!"))
    } else {
      activeUsers(clientActorName).name = desiredName
      sendMessage(clientActorName, Message("<SERVER>: Successfully logged as " + desiredName))
    }
  }

  def online(clientActorName: String): Unit = loggedSafe(clientActorName, {
      sendMessage(clientActorName, Message("<SERVER>: Currently active users: " + activeUsersAsString))
    })

  def createChatRoom(clientActorName: String, roomName: String): Unit = loggedSafe(clientActorName, {
      if (activeUsers(clientActorName).name == null)
        sendMessage(clientActorName, Message("<SERVER>: Log in before creating a room!"))
      else if (chatRooms.contains(roomName))
        sendMessage(clientActorName, Message("<SERVER>: Room <" + roomName + "> already exists!"))
      else {
        val newRoom = new ChatRoom(clientActorName)
        chatRooms += (roomName -> newRoom)

        sendMessage(clientActorName, Message("<SERVER>: Successfully created room <" + roomName + ">!"))
      }
    })

  def connect(clientActorName: String, chatRoom: String): Unit = loggedSafe(clientActorName, {
      if (chatRooms.contains(chatRoom)) {
        if (activeUsers(clientActorName).chatRoom != null)
          leave(clientActorName)

        chatRooms(chatRoom).addUser(clientActorName)
        activeUsers(clientActorName).changeRoom(chatRoom)
        sendMessage(clientActorName, Message("<SERVER>: You entered the room <" + chatRoom + ">!"))
      }
      else
        sendMessage(clientActorName, Message("<SERVER>: There is no room with that name!"))
    })

  def leave(clientActorName: String): Unit = loggedSafe(clientActorName, {
      val room: String = activeUsers(clientActorName).chatRoom

      if (room != null) {
        chatRooms(room).removeUser(clientActorName)

        activeUsers(clientActorName).changeRoom(null)

        sendToAll(clientActorName, room,
          Message("<SERVER>: User <" + activeUsers(clientActorName).name + "> has left the chat room!"))
        sendMessage(clientActorName, Message("<SERVER>: You have left the room <" + room + ">!"))
      }
      else
        sendMessage(clientActorName, Message("<SERVER>: You are not in any room!"))
    })

  def quit(clientActorName: String): Unit = {
    if (activeUsers.contains(clientActorName)) {
      val quittingUser: User = activeUsers(clientActorName)
      val lastRoom: String = quittingUser.chatRoom

      if (lastRoom != null)
        chatRooms(lastRoom).removeUser(clientActorName)
      activeUsers -= clientActorName

      sendToAll(clientActorName, lastRoom,
        Message("<SERVER>: User <" + quittingUser.name + "> has left the chat room!"))
    }
  }

  def loggedSafe(clientActorName: String, blockOfCode: => Unit): Unit = {
    if (activeUsers(clientActorName).name != null)
      blockOfCode
    else
      sendMessage(clientActorName, Message("<SERVER>: Please log in by: \\login [name]!"))
  }

  def sendMessage(clientActorName: String, message: Message): Unit = {
    val actorRef = getActorRef(clientActorName)
    actorRef ! Write(message.toSend)
  }

  def sendToAll(messageSender: String, room: String, message: Message): Unit = {
    if (room == null && activeUsers.contains(messageSender))
      sendMessage(messageSender, Message("<SERVER>: You are not in any conversation!"))
    else if (room != null)
      chatRooms(room).users.foreach(user => sendMessage(user, message))
  }

  def getActorRef(actorName: String): ActorRef = activeUsers(actorName).actorRef

  def getName(actorName: String): String = activeUsers(actorName).name

  def activeUsersAsString: String = {
    if (!activeUsers.exists(item => item._2.name != null)) "nobody (0 users total)."
    else activeUsers.values.map(_.name).reduce(_ + ", " + _) +
      " (" + activeUsers.size + " users total)."
  }
}


object Server extends App {
  val system = ActorSystem("Server")
  val server = system.actorOf(Props(new ServerActor(system)))
}