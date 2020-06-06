//import akka.actor.typed.scaladsl.Behaviors
//import akka.actor.typed.scaladsl.LoggerOps
//import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
//import java.net.URLEncoder
//import java.nio.charset.StandardCharsets
//import akka.actor.typed.Terminated
//import akka.NotUsed
//
//object ChatRoom {
//  //#chatroom-behavior
//  //#chatroom-protocol
//  sealed trait RoomCommand
//  final case class GetSession(screenName: String, replyTo: ActorRef[SessionEvent]) extends RoomCommand
//  //#chatroom-protocol
//  //#chatroom-behavior
//  private final case class PublishSessionMessage(screenName: String, message: String) extends RoomCommand
//  //#chatroom-behavior
//  //#chatroom-protocol
//
//  sealed trait SessionEvent
//  final case class SessionGranted(handle: ActorRef[PostMessage]) extends SessionEvent
//  final case class SessionDenied(reason: String) extends SessionEvent
//  final case class MessagePosted(screenName: String, message: String) extends SessionEvent
//
//  trait SessionCommand
//  final case class PostMessage(message: String) extends SessionCommand
//  private final case class NotifyClient(message: MessagePosted) extends SessionCommand
//  //#chatroom-protocol
//  //#chatroom-behavior
//
//  def apply(): Behavior[RoomCommand] =
//    chatRoom(List.empty)
//
//  private def chatRoom(sessions: List[ActorRef[SessionCommand]]): Behavior[RoomCommand] =
//    Behaviors.receive { (context, message) =>
//      message match {
//        case GetSession(screenName, client) =>
//          // create a child actor for further interaction with the client
//          val ses = context.spawn(
//            session(context.self, screenName, client),
//            name = URLEncoder.encode(screenName, StandardCharsets.UTF_8.name))
//          client ! SessionGranted(ses)
//          chatRoom(ses :: sessions)
//        case PublishSessionMessage(screenName, message) =>
//          val notification = NotifyClient(MessagePosted(screenName, message))
//          sessions.foreach(_ ! notification)
//          Behaviors.same
//      }
//    }
//
//  private def session(
//                       room: ActorRef[PublishSessionMessage],
//                       screenName: String,
//                       client: ActorRef[SessionEvent]): Behavior[SessionCommand] =
//    Behaviors.receiveMessage {
//      case PostMessage(message) =>
//        // from client, publish to others via the room
//        room ! PublishSessionMessage(screenName, message)
//        Behaviors.same
//      case NotifyClient(message) =>
//        // published from the room
//        client ! message
//        Behaviors.same
//    }
//}
////#chatroom-behavior
//
////#chatroom-gabbler
//object Gabbler {
//  import ChatRoom._
//
//  def apply(): Behavior[SessionEvent] =
//    Behaviors.setup { context =>
//      Behaviors.receiveMessage {
//        //#chatroom-gabbler
//        // We document that the compiler warns about the missing handler for `SessionDenied`
//        case SessionDenied(reason) =>
//          context.log.info("cannot start chat room session: {}", reason)
//          Behaviors.stopped
//        //#chatroom-gabbler
//        case SessionGranted(handle) =>
//          handle ! PostMessage("Hello World!")
//          Behaviors.same
//        case MessagePosted(screenName, message) =>
//          context.log.info2("message has been posted by '{}': {}", screenName, message)
//          Behaviors.stopped
//      }
//    }
//}
////#chatroom-gabbler
//
////#chatroom-main
//object Main {
//  def apply(): Behavior[NotUsed] =
//    Behaviors.setup { context =>
//      val chatRoom = context.spawn(ChatRoom(), "chatroom")
//      val gabblerRef = context.spawn(Gabbler(), "gabbler")
//      context.watch(gabblerRef)
//      chatRoom ! ChatRoom.GetSession("ol’ Gabbler", gabblerRef)
//
//      Behaviors.receiveSignal {
//        case (_, Terminated(_)) =>
//          Behaviors.stopped
//      }
//    }
//
//  def main(args: Array[String]): Unit = {
//    ActorSystem(Main(), "ChatRoomDemo")
//  }
//
//}
////#chatroom-main