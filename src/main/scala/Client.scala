import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef, ActorSystem, Kill, Props}
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import Utils.Message

import scala.annotation.tailrec


class ClientActor(address: InetSocketAddress, actorSystem: ActorSystem) extends Actor {
  IO(Tcp)(actorSystem) ! Connect(address)

  var connection: ActorRef = _

  def receive: PartialFunction[Any, Unit] = {
    case CommandFailed(_: Tcp.Command) =>
      println("Failed to connect to " + address.toString)
      self ! Kill
      actorSystem.terminate()

    case Connected(_, _) =>
      println("Successfully connected to " + address)
      connection = sender()
      connection ! Register(self)

    case Received(data) =>
      println(data.decodeString("US-ASCII"))

    case message @ Message(_) =>
      connection ! Write(message.toSend)

    case _ =>
      println("Unknown message")
  }
}

object Client extends App {
  val Port = 18573

  val system = ActorSystem("Client")
  val clientConnection =
    system.actorOf(Props(new ClientActor(new InetSocketAddress("localhost", Port), system)))

  val bufferedReader = io.Source.stdin.bufferedReader()
  loop("")

  @tailrec
  def loop(message: String): Boolean = message match {
    case "~quit" =>
      system.terminate()
      false
    case _ =>
      val msg = bufferedReader.readLine()
      clientConnection ! Message(msg)
      loop(msg)
  }
}