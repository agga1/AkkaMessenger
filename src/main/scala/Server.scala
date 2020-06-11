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
  // map of custom username to the user's Actor
  val actorsByName = mutable.HashMap.empty[String, String]
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
          case Some("rooms") => rooms(clientActorName)
          case Some("create") => createChatRoom(clientActorName, message.text)
          case Some("join") => join(clientActorName, message.text)
          case Some("leave") => leave(clientActorName)
          case Some("where") => checkWhere(clientActorName)
          case Some("pair") => pair(clientActorName, message.text)
          case _ => sendMessage(clientActorName, Message("<SERVER>: Unknown command!"))
        }
      }
      // transferring messages between users
      else passMessage(clientActorName, message)

    // handling user disconnection
    case PeerClosed => quit(sender.path.name)
  }

  def passMessage(clientActorName: String, message: Message): Unit = loggedSafe(clientActorName, {
    val msg = Message("<" + activeUsers(clientActorName).name + ">: " + message.text)
    if(activeUsers(clientActorName).chatRoom != null){
      sendToAll(clientActorName, activeUsers(clientActorName).chatRoom, msg)
    }
    else if(activeUsers(clientActorName).pair != null){
      sendMessage(activeUsers(clientActorName).pair, msg)
    }
  })

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
      actorsByName += (desiredName -> clientActorName)
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
   * Handles "rooms" command. Writes message containing names of all rooms which have been created and number of them.
   *
   * @param clientActorName name of the user's actor.
   */
  def rooms(clientActorName: String): Unit = loggedSafe(clientActorName, {
    sendMessage(clientActorName, Message("<SERVER>: Currently available rooms: " + availableRoomsAsString))
  })

  def checkWhere(clientActorName: String): Unit = loggedSafe(clientActorName, {
    if(!activeUsers(clientActorName).occupied()){
      sendMessage(clientActorName, Message("<SERVER>: You are not in any conversation!"))
    }else{
      val room: String = activeUsers(clientActorName).chatRoom
      val pair: String = activeUsers(clientActorName).pair
      if(room != null){
        sendMessage(clientActorName, Message("<SERVER>: You are currently in the room <" + room + ">!"))
      }else{
        sendMessage(clientActorName, Message("<SERVER>: You are currently in the private conversation" +
          " with <"+ activeUsers(pair).name +">."))
      }
    }
  })

  /**
   * Handles "create" command. Creates new chat room.
   *
   * @param clientActorName name of the user's actor.
   * @param roomName name of the room.
   */
  def createChatRoom(clientActorName: String, roomName: String): Unit = loggedSafe(clientActorName, {
      if (chatRooms.contains(roomName))
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
  def join(clientActorName: String, chatRoom: String): Unit = loggedSafe(clientActorName, {
      if (chatRooms.contains(chatRoom)) {
        if (activeUsers(clientActorName).occupied())
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
  def leave(clientActorName: String, quitting: Boolean = false): Unit = loggedSafe(clientActorName, {
      if(!activeUsers(clientActorName).occupied()){
        sendMessage(clientActorName, Message("<SERVER>: You are not in any conversation!"))
      }
      else{
        val room: String = activeUsers(clientActorName).chatRoom
        val pair: String = activeUsers(clientActorName).pair

        if (room != null) {
          chatRooms(room).removeUser(clientActorName)
          activeUsers(clientActorName).changeRoom(null)

          sendToAll(clientActorName, room,
            Message("<SERVER>: User <" + activeUsers(clientActorName).name + "> has left the chat room! "))

          if(!quitting)
            sendMessage(clientActorName, Message("<SERVER>: You have left the room <" + room + ">! "))
        }
        else if(pair != null) {
          activeUsers(clientActorName).unpair()
          if(!quitting)
            sendMessage(clientActorName, Message("<SERVER>: You have left the chat with " +
            "<" + activeUsers(pair).name + ">"))

          activeUsers(pair).unpair()
          sendMessage(pair, Message("<SERVER>: user <" + activeUsers(clientActorName).name + "> has disconnected. "))
        }
      }
  })

  def pair(clientActorName: String, friend: String): Unit = loggedSafe(clientActorName, {
    if(actorsByName.contains(friend)) {
      val otherActor = actorsByName(friend)

      if(clientActorName != otherActor) {
        // check if other user is available
        if (activeUsers(otherActor).occupied()) {
          sendMessage(clientActorName, Message("<SERVER>: User that you're trying to PM is currently occupied." +
            "\n We will send him notification about your request."))
          sendMessage(otherActor, Message("<SERVER>: user <" + activeUsers(clientActorName).name + ">" +
            " wants to PM you.\n Leave your current conversation to be available."))
        }
        else {
          // automatically leave current conversation
          if (activeUsers(clientActorName).occupied()) {
            leave(clientActorName)
          }
          // connect users
          activeUsers(clientActorName).pairWith(otherActor)
          activeUsers(otherActor).pairWith(clientActorName)
          sendMessage(clientActorName, Message("<SERVER>: Successfully paired with <" + friend + ">!"))
          sendMessage(otherActor, Message("<SERVER>: You are paired with <" + activeUsers(clientActorName).name + "> !"))
        }
      }
      else
        sendMessage(clientActorName, Message("<SERVER>: Cannot connect with yourself!"))
    }
    else {
      sendMessage(clientActorName, Message("<SERVER>: No user with name <" + friend + ">!"))
    }
  })

  /**
   * Handles user disconnection. Also leaves any conversations if necessary.
   *
   * @param clientActorName name of the user's actor.
   */
  def quit(clientActorName: String): Unit = {
    if (activeUsers.contains(clientActorName)) {
      if(activeUsers(clientActorName).occupied())
        leave(clientActorName, quitting=true)

      actorsByName -= activeUsers(clientActorName).name
      activeUsers -= clientActorName
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
   * Sending message to all other users in the room. Generates proper message to the sender in case they are not
   * in any room.
   *
   * @param clientActorName name of the sender's actor (the one which is sending message).
   * @param room name of the room in which the message will be sent.
   * @param message it will be sent to the user.
   */
  def sendToAll(clientActorName: String, room: String, message: Message): Unit = {
    if (room == null && activeUsers.contains(clientActorName))
      sendMessage(clientActorName, Message("<SERVER>: You are not in any conversation!"))
    else if (room != null)
      chatRooms(room).users.filter(user => user != clientActorName).foreach(user => sendMessage(user, message))
  }

  /**
   * Gets String containing names of all logged users and number of them.
   */
  def activeUsersAsString: String = {
    val loggedUsersNames = actorsByName.keys
    if (loggedUsersNames.isEmpty)
      "nobody (0 users total)."
    else
      loggedUsersNames.reduce(_ + ", " + _) + " (" + loggedUsersNames.size + " users total)."
  }

  /**
   * Gets String containing names of all available rooms and number of them.
   */
  def availableRoomsAsString: String = {
    val rooms = chatRooms.keys
    if (rooms.isEmpty)
      "There are no available rooms."
    else
      rooms.reduce(_ + ", " + _) + " (" + rooms.size + " rooms total)."
  }
}

/**
 * Called when server starts running. It creates actor system and actor responsible for communication by network.
 */
object Server extends App {
  val system = ActorSystem("Server")
  val server = system.actorOf(Props(new ServerActor(system)))
}