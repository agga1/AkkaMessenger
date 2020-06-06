import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.io.Tcp._
import akka.io.{IO, Tcp}

import scala.collection.mutable

import Utils.Message


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
      sendMessage(sender.path.name, Message("<SERVER>: Please log in using ~login [name]!"))

    case Received(data) =>
      val message = Message(data)
      val clientActorName = sender.path.name

      if (message.isCommand) {
        message.command match {
          case Some("login") => login(clientActorName, message.text)
          case Some("online") => online(clientActorName)
          case Some("create") => createChatRoom(clientActorName, message.text)
          case Some("connect") => connect(clientActorName, message.text)
          case Some("quit") => quit(clientActorName)
          case _ => sendMessage(clientActorName, Message("<SERVER>: Unknown command!"))
        }
      } else {
        if (!activeUsers.contains(clientActorName)) {
          sendMessage(clientActorName, Message("<SERVER>: Please login using ~login [name]!"))
        } else {
          sendToAll(clientActorName, message)
        }
      }
  }

  def login(clientActorName: String, desiredName: String): Unit = {
    if (activeUsers.values.exists(_.name == desiredName)) {
      sendMessage(clientActorName, Message("There is already an user with this username!"))
    } else if (activeUsers.keys.exists(_ == clientActorName) && activeUsers(clientActorName).name != null) {
      sendMessage(clientActorName, Message("You are already logged in!"))
    } else {
      activeUsers(clientActorName).name = desiredName
      sendMessage(clientActorName, Message("<SERVER>: Successfully logged as " + desiredName))
    }
  }

  def online(clientActorName: String): Unit =
    sendMessage(clientActorName, Message("<SERVER>: Currently active users: " + activeUsersNumber))

  def createChatRoom(clientActorName: String, roomName: String): Unit = {
    if (activeUsers(clientActorName).name == null)
      sendMessage(clientActorName, Message("<SERVER>: Log in before creating a room!"))
    else if (chatRooms.contains(roomName))
      sendMessage(clientActorName, Message("<SERVER>: Room <" + roomName + "> already exists!"))
    else {
      val newRoom = new ChatRoom(clientActorName)
      chatRooms += (roomName -> newRoom)

      sendMessage(clientActorName, Message("<SERVER>: Successfully created room <" + roomName + ">!"))
    }
  }


  def connect(clientActorName: String, chatRoom: String): Unit = {

  }

  def quit(clientActorName: String): Unit = {
    if (activeUsers.contains(clientActorName)) {
      val quittingUser = activeUsers(clientActorName)
      activeUsers -= clientActorName

      sendToAll("SERVER", Message("<" + quittingUser.name + "> has left the chatroom."))
    }
  }

  def sendMessage(clientActorName: String, message: Message): Unit = {
    val actorRef = getActorRef(clientActorName)
    actorRef ! Write(message.toSend)
  }

  def sendToAll(messageSender: String, message: Message): Unit =
    activeUsers.foreach(item => sendMessage(item._1, Message("<" + messageSender + ">: " + message.text)))

  def getActorRef(actorName: String): ActorRef = activeUsers(actorName).actorRef

  def getName(actorName: String): String = activeUsers(actorName).name

  def activeUsersNumber: String = {
    if (!activeUsers.exists(item => item._2.name != null)) "nobody (0 users total)."
    else activeUsers.values.map(_.name).reduce(_ + ", " + _) +
      " (" + activeUsers.size + " users total)."
  }
}


object Server extends App {
  val system = ActorSystem("Server")
  val server = system.actorOf(Props(new ServerActor(system)))
}