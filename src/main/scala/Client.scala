import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef, ActorSystem, Kill, Props}
import akka.io.Tcp._
import akka.io.{IO, Tcp}

import scala.annotation.tailrec

import Utils.Message

/**
 * Actor which is communicating with server.
 *
 * @param address to connect.
 * @param actorSystem current actor system.
 */
class ClientActor(address: InetSocketAddress, actorSystem: ActorSystem) extends Actor {
  // connecting with the server
  IO(Tcp)(actorSystem) ! Connect(address)

  // handler to the connection
  var connection: ActorRef = _

  /**
   * Receiving messages from network or other actors in this process.
   */
  def receive: Receive = {
    // problem with communication
    case CommandFailed(_: Tcp.Command) =>
      println("Failed to connect to " + address.toString)
      self ! Kill
      actorSystem.terminate()

    // successful communication
    case Connected(_, _) =>
      println("Successfully connected to " + address)
      connection = sender()
      connection ! Register(self)

    // receiving data (as ByteString) from network
    case Received(data) =>
      val message = Message(data)
      println(message.text)

    // receiving messages from actors in this process and sending messages by network
    case message @ Message(_) =>
      if (message.isCommand && message.command.contains("quit")) {
        println("Closing client...")
        connection ! Close
        context.stop(self)
      } else
        connection ! Write(message.toSend)

    // helps in debugging
    case _ =>
      println("Unknown message")
  }
}

/**
 * Called when client start running. It makes actor system, creates actor responsible for communication by network
 * and handles commands and messages written by user.
 */
object Client extends App {
  // properties of the network
  val port = 18573
  val server = "localhost"

  // creating actor system and the actor responsible for connection with the server
  val system = ActorSystem("Client")
  val clientConnection = system.actorOf(Props(new ClientActor(new InetSocketAddress(server, port), system)))

  // starting reading messages written to terminal by the user
  val bufferedReader = io.Source.stdin.bufferedReader()
  loop

  /**
   * Infinite loop responsible for reading messages from the user and terminating process when user wants.
   */
  @tailrec
  def loop: Boolean = {
    val msg = Message(bufferedReader.readLine())
    clientConnection ! msg

    if (msg.command.isDefined && msg.command.get == "quit") {
      system.terminate()
      false
    }
    else
      loop
  }
}