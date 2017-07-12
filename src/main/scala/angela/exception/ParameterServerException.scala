package angela.exception

/**
  * Created by tuoyu on 12/07/2017.
  */

class ParameterServerException(message: String) extends Exception

case class NotEnoughParameterServerException(message: String) extends ParameterServerException(message)
case class NoParameterServerException(message: String) extends ParameterServerException(message)
case class PushMessageException(message: String) extends ParameterServerException(message)
case class PullMessageException(message: String) extends ParameterServerException(message)