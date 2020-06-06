import akka.util.ByteString


object Utils {
  val commandSymbol = "\\"

  case class Message(message: Any) {
    def messageText: String = message match {
        case messageString: String =>
          messageString
        case messageByteString: ByteString =>
          messageByteString.decodeString("US-ASCII")
        case _ =>
          "invalid message"
      }

    val isCommand: Boolean = messageText.startsWith(commandSymbol)

    def command: Option[String] = {
      if (isCommand) {
        val splitText = messageText.split(" ")
        val commandWithSymbol = splitText(0)
        val actualCommand = commandWithSymbol.substring(1, commandWithSymbol.length())
        Some(actualCommand)
      }
      else
        None
    }

    def text: String = {
      if (isCommand) {
        val splitText = messageText.split(" ")

        if (splitText.length >= 2)
          splitText.tail.reduce((a, b) => a + " " + b)
        else
          ""
      }
      else
        messageText
    }

    def toSend: ByteString = message match {
      case messageByteString: ByteString =>
        messageByteString
      case messageString: String =>
        ByteString(messageString)
      case _ =>
        ByteString("invalid message")
    }
  }

  val helpText: String =
    """<SERVER>: Commands:
    Log in: \login [name]
    Check online users: \online
    Create a chat room: \create
    Connect to the chat room: \connect [room name]
    Log out: \quit"""
}
