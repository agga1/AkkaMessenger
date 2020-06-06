import java.net.InetSocketAddress

import akka.actor.{Actor, ActorSystem, Props}
import akka.io.Tcp._
import akka.io.{IO, Tcp}

import scala.collection.mutable

import Utils.{Message, helpText}

/**
 * Actor which is communicating with clients.
 *
 * @param actorSystem current actor system.
 */
class ServerActor(actorSystem: ActorSystem) extends Actor {
  // properties of the network
  val port = 18573
  val server = "localhost"

  // all users which have connected with the server; key is the name of the user's actor
  val activeUsers = mutable.HashMap.empty[String, User]

  // all created chat rooms; key is a name of the room given by its creator
  val chatRooms = mutable.HashMap.empty[String, ChatRoom]

  // binding connection
  IO(Tcp)(actorSystem) ! Bind(self, new InetSocketAddress(server, port))

  /**
   * Receiving messages from network.
   */
  def receive: Receive = {
    // problem with communication
    case CommandFailed(_: Bind) =>
      println("Failed to start listening on " + Server + ":" + port)
      context stop self
      actorSystem.terminate()

    // prints message after successful binding
    case Bound(localAddress: InetSocketAddress) =>
      println("Started listening on " + localAddress)

    // registering new client and sending initial message to the new user
    case Connected(_, _) =>
      activeUsers += (sender.path.name -> new User(sender))

      sender ! Register(self)
      sendMessage(sender.path.name, Message("<SERVER>: To check possible commands enter: \\help"))

    // receiving messages from the users
    case Received(data) =>
      val message = Message(data)
      val clientActorName = sender.path.name

      // handling commands from users
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
      }
      // transferring messages between users
      else
        loggedSafe(clientActorName, {
            sendToAll(clientActorName, activeUsers(clientActorName).chatRoom,
              Message("<" + activeUsers(clientActorName).name + ">: " + message.text))
        })

    // handling user disconnection
    case PeerClosed => quit(sender.path.name)
  }

  /**
   * Handles "help" command. Sends message with all possible commands to the user.
   *
   * @param clientActorName name of the user's actor.
   */
  def help(clientActorName: String): Unit = sendMessage(clientActorName, Message(helpText))

  /**
   * Handles "login" command. Checks if the user have chosen appropriate name and informs them about result.
   * In case user has logged in successfully, they can use all the commands.
   *
   * @param clientActorName name of the user's actor.
   * @param desiredName name which user used to identify themselves.
   */
  def login(clientActorName: String, desiredName: String): Unit = {
    if (activeUsers.values.exists(_.name == desiredName))
      sendMessage(clientActorName, Message("There is already a user with this username!"))
    else if (activeUsers.keys.exists(_ == clientActorName) && activeUsers(clientActorName).name != null)
      sendMessage(clientActorName, Message("You are already logged in!"))
    else {
      activeUsers(clientActorName).name = desiredName
      sendMessage(clientActorName, Message("<SERVER>: Successfully logged as " + desiredName))
    }
  }

  /**
   * Handles "online" command. Writes message containing names of all users which are connected and logged in.
   *
   * @param clientActorName name of the user's actor.
   */
  def online(clientActorName: String): Unit = loggedSafe(clientActorName, {
      sendMessage(clientActorName, Message("<SERVER>: Currently active users: " + activeUsersAsString))
    })

  /**
   * Handles "create" command. Creates new chat room.
   *
   * @param clientActorName name of the user's actor.
   * @param roomName name of the room.
   */
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

  /**
   * Handles "connect" command. Changes user's room.
   *
   * @param clientActorName name of the user's actor.
   * @param chatRoom name of the user's desired room.
   */
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

  /**
   * Handles "leave" command. User is leaving current room. Theirs `chatRoom` property is set to `null`.
   *
   * @param clientActorName name of the user's actor.
   */
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

  /**
   * Handles user disconnection. Also leaves room if necessary.
   *
   * @param clientActorName name of the user's actor.
   */
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

  /**
   * When user is not logged in they can use just three commands: "help", "login" and "quit". This function wraps
   * handlers of other commands and in case the user is not logged in, they get message which reminds of logging in.
   * Otherwise handler is normally called.
   *
   * @param clientActorName name of the user's actor.
   * @param blockOfCode code which will be run in case the user is logged in.
   */
  def loggedSafe(clientActorName: String, blockOfCode: => Unit): Unit = {
    if (activeUsers(clientActorName).name != null)
      blockOfCode
    else
      sendMessage(clientActorName, Message("<SERVER>: Please log in by: \\login [name]!"))
  }

  /**
   * Sending message to the user.
   *
   * @param clientActorName name of the user's actor (the one to which the message is being sent).
   * @param message it will be sent to the user.
   */
  def sendMessage(clientActorName: String, message: Message): Unit = {
    val actorRef = activeUsers(clientActorName).actorRef
    actorRef ! Write(message.toSend)
  }

  /**
   * Sending message all users in the room. Generates proper message to the sender in case they are not in any room.
   *
   * @param messageSender name of the user's actor (the one which is sending message).
   * @param room name of the room in which the message will be sent.
   * @param message it will be sent to the user.
   */
  def sendToAll(messageSender: String, room: String, message: Message): Unit = {
    if (room == null && activeUsers.contains(messageSender))
      sendMessage(messageSender, Message("<SERVER>: You are not in any conversation!"))
    else if (room != null)
      chatRooms(room).users.foreach(user => sendMessage(user, message))
  }

  /**
   * Gets String containing names of all logged users and number of them.
   */
  def activeUsersAsString: String = {
    val loggedUsersNames = activeUsers.values.map(_.name).filter(name => name != null)

    if (loggedUsersNames.isEmpty)
      "nobody (0 users total)."
    else
      loggedUsersNames.reduce(_ + ", " + _) + " (" + loggedUsersNames.size + " users total)."
  }
}

/**
 * Called when server starts running. It creates actor system and actor responsible for communication by network.
 */
object Server extends App {
  val system = ActorSystem("Server")
  val server = system.actorOf(Props(new ServerActor(system)))
}