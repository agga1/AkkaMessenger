import akka.util.ByteString

object Utils {
  val commandSymbol = "~"

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
        splitText.tail.reduce((a, b) => a + " " + b)
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
}
