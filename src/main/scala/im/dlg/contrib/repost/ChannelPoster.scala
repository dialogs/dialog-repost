package im.dlg.contrib.repost

import akka.actor.ActorSystem
import akka.stream._
import configs.Configs
import im.dlg.api.rpc.messaging._
import im.dlg.api.rpc.peers._
import im.dlg.server.db.DbExtension
import im.dlg.server.dialog.DialogExtension
import im.dlg.server.model.Group
import im.dlg.server.persist.GroupRepo
import im.dlg.server.sequence.SeqStateDate
import im.dlg.util.ThreadLocalSecureRandom

import scala.concurrent.{ExecutionContext, Future}
import configs.syntax._
import im.dlg.DialogRuntime
import im.dlg.util.log.AnyLogging

object ChannelPoster {
  trait Poster extends AnyLogging {
    implicit val system: ActorSystem
    protected val channelId: Int
    protected implicit val mat: Materializer = DialogRuntime(system).materializer

    protected implicit val ec: ExecutionContext = system.dispatcher

    private final lazy val dialogExt = DialogExtension(system)
    private final lazy val db = DbExtension(system).db
    private final val peer = ApiPeer(ApiPeerType.Group, channelId, None)
    private final val rng = ThreadLocalSecureRandom.current()

    protected def post(userId: Int, msg: ApiMessageContent): Future[SeqStateDate] =
      dialogExt
        .sendMessage(peer, userId, 0, None, rng.nextLong, msg, None, isFat = false, Set.empty, Set.empty, None)
        .recover {
          case t: Throwable ⇒
            log.error(t, "send message failed")
            throw t
        }
        .map(_._1)

    protected def startStream(key: String, info: Group): Unit

    final def start(key: String): Unit =
      db.run(GroupRepo.find(channelId)).foreach {
        case Some(info) ⇒
          startStream(key, info)
          log.debug("Started stream for `{}` to `{}`", key, info.id)
        case None ⇒
          throw new RuntimeException(s"Can't find channel with id = $channelId")
      }
  }

  trait Starter[ConfigType] {
    protected def name: String

    final def configPath: String = s"services.poster.$name"

    final def start(system: ActorSystem)(implicit ev: Configs[ConfigType]): Unit =
      if (system.settings.config.hasPath(configPath)) {
        val config = system.settings.config.get[ConfigType](configPath).valueOrThrow(_.configException)
        system.log.debug("Poster for {} config: {}", name, config)
        startWithConfig(config)(system)
      }

    def startWithConfig(config: ConfigType)(implicit system: ActorSystem): Unit
  }
}
