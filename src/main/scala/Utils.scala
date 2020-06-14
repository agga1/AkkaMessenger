import akka.util.ByteString

/**
 * Container for variables and abstraction shared by many source files.
 */
object Utils {
  // user is using this symbol to indicate that following word is a command (e.g. "\connect")
  val commandSymbol = "\\"

  /**
   * The purpose of this case class is that the sending of messages by TCP needs that messages to be of type ByteString.
   * On the other hand, everywhere else it is better to have String so the case class converts between types.
   * Additionally, it separates commands keywords and commands arguments in case the message is a command.
   *
   * @param message String or ByteString containing message (or command).
   */
  case class Message(message: Any) {

    /**
     * Returns full message of type String.
     */
    def messageText: String = message match {
      case messageString: String =>
        messageString
      case messageByteString: ByteString =>
        messageByteString.decodeString("US-ASCII")
      case _ =>
        "invalid message"
    }

    /**
     * Tells whether the message is a command or not.
     */
    val isCommand: Boolean = messageText.startsWith(commandSymbol)

    /**
     * Extracts command (without `commandSymbol`) from the message.
     * There is a type constructor Option because the message may not be a command.
     */
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

    /**
     * Gets text without command in case the message is a command. Otherwise full text.
     */
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

    /**
     * Returns full message of type ByteString. Useful when sending messages by network.
     */
    def toSend: ByteString = message match {
      case messageByteString: ByteString =>
        messageByteString
      case messageString: String =>
        ByteString(messageString)
      case _ =>
        ByteString("invalid message")
    }
  }

  // text which shows all possible commands
  val helpText: String =
    """<SERVER>: Commands:
    Display help text: \help
    Log in: \login [name]
    Check online users: \online
    Check available rooms: \rooms
    Create chat room: \create [room name]
      Create password-secured chat room: \create [room name] [password]
    Join the chat room: \join [room name]
    Start private chat with other user: \pair [name]
    Leave current chat: \leave
    Check current chat: \where
    Log out: \quit"""
}
