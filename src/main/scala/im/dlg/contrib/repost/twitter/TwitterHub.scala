package im.dlg.contrib.repost.twitter

import akka.actor._
import akka.stream.Supervision.Decider
import akka.stream.scaladsl._
import akka.stream._
import im.dlg.api.rpc.messaging._
import im.dlg.api.rpc.peers.{ApiPeer, ApiPeerType}
import im.dlg.server.dialog.DialogExtension
import im.dlg.util.ThreadLocalSecureRandom
import im.dlg.util.log.AnyLogging
import im.dlg.util.misc.CommonStringUtils
import twitter4j._
import twitter4j.auth._
import twitter4j.conf._
import twitter4j.util.function.Consumer

import scala.concurrent.duration._

sealed trait Key {
  val flow: Flow[Status, Status, _]
  val quotationRequired: Boolean
}

case class HashTagKey(rawTag: String) extends Key {
  private val tag: String = rawTag.dropWhile(_ == '#')
  override val flow: Flow[Status, Status, _] =
    Flow[Status].filter(_.getHashtagEntities.exists(_.getText.contains(tag)))
  override val quotationRequired: Boolean = true
}

case class AccountKey(account: String, userId: Long) extends Key {
  override val flow: Flow[Status, Status, _] =
    Flow[Status].filter(_.getUser.getId == userId)
  override val quotationRequired: Boolean = false
}

object Key extends CommonStringUtils {
  def resolve(raw: String, service: Twitter): Key =
    withTrimmed(raw) { trimmed ⇒
      if (trimmed.startsWith("#")) HashTagKey(trimmed)
      else {
        val account = trimmed.dropWhile(_ == "@")
        AccountKey(account, service.showUser(account).getId)
      }
    }
}

trait TwitterSupport extends AnyLogging {
  val config: TwitterConfig

  protected def getConf: Configuration =
    new ConfigurationBuilder()
      .setOAuthConsumerKey(config.consumerKey)
      .setOAuthConsumerSecret(config.consumerSecret)
      .setIncludeMyRetweetEnabled(false)
      .setUserStreamWithFollowingsEnabled(true)
      .setDebugEnabled(false)
      .setIncludeEntitiesEnabled(true)
      .setApplicationOnlyAuthEnabled(true)
      .setGZIPEnabled(true)
      .setMBeanEnabled(false)
      .setOAuthAuthenticationURL("https://api.twitter.com/oauth2/token")
      .setOAuthAuthorizationURL("https://api.twitter.com/oauth/authorize")
      .setOAuthAccessTokenURL("https://api.twitter.com/oauth/access_token")
      .build()

  protected def accessToken: AccessToken =
    new AccessToken(config.token, config.tokenSecret)

  private val streamFactory = new TwitterStreamFactory(getConf)

  protected def getTwitterStream: TwitterStream = {
    val stream = streamFactory.getInstance(accessToken)
    stream.onException(new Consumer[Exception] {
      override def accept(t: Exception): Unit =
        log.error(t, "TwitterStream general exception")
    })
    stream
  }

  private var _instance: Option[Twitter] = None

  protected def getInstance: Twitter = synchronized {
    _instance match {
      case Some(i) ⇒ i
      case None ⇒
        val i = new TwitterFactory(getConf).getInstance(accessToken)
        _instance = Some(i)
        i
    }
  }
}

final case class TwitterHub(config: TwitterConfig)(implicit val system: ActorSystem)
    extends TwitterSupport
    with AnyLogging {

  import cats.instances.tuple._
  import cats.syntax.bifunctor._
  import system.dispatcher

  private val loggingResumer: Decider = { e ⇒
    log.error(e, "Stream exception. Resuming")
    Supervision.Resume
  }

  private implicit val mat: Materializer = ActorMaterializer(
    ActorMaterializerSettings(system).withSupervisionStrategy(loggingResumer)
  )
  private val instance = getInstance

  private lazy val dialogExt = DialogExtension(system)
  private val rng = ThreadLocalSecureRandom.current

  private def postFlow =
    Flow[(ApiPeer, ApiTextMessage)].mapAsync(1) {
      case (peer, msg) ⇒
        dialogExt
          .sendMessage(peer, 0, 0, None, rng.nextLong, msg, None, isFat = false, Set.empty, Set.empty, None)
          .map(_ ⇒ ())
          .recover {
            case t: Throwable ⇒
              log.error(t, "send message failed")
              ()
          }
    }

  private def unfoldMedia(key: Key) =
    Flow[Status].flatMapConcat { status ⇒
      Source
        .fromIterator(() ⇒
            status.getMediaEntities.toIterator.map { media ⇒
            ApiTextMessage(media.getMediaURLHttps, IndexedSeq.empty, None, IndexedSeq.empty, IndexedSeq.empty)
        })
        .prepend(Source.single(
              ApiTextMessage(
                if (key.quotationRequired)
                  s"@${status.getUser.getName}:\n${status.getText.split('\n').map("> " + _).mkString("\n")}"
                else
                  status.getText,
                IndexedSeq.empty,
                None,
                IndexedSeq.empty,
                IndexedSeq.empty
              )))
    }

  private def tweetsSource(bufferSize: Int): Source[Status, ActorRef] =
    Source
      .actorRef[Entity](bufferSize, OverflowStrategy.dropTail)
      .collect {
        case TwitterStatus(status) ⇒ status
      }
      .filterNot(_.isRetweet)

  private def pair(peer: ApiPeer) = Flow[ApiTextMessage].map(m ⇒ peer → m)

  private def keysFlow(keys: List[(ApiPeer, Key)]) =
    Flow.fromGraph(GraphDSL.create() { implicit b ⇒
      import GraphDSL.Implicits._

      val bcast = b.add(Broadcast[Status](keys.length))
      val merge = b.add(Merge[(ApiPeer, ApiTextMessage)](keys.length))

      keys.zipWithIndex.foreach {
        case ((peer, key), index) ⇒
          bcast.out(index) ~> key.flow ~> unfoldMedia(key) ~> pair(peer) ~> merge.in(index)
      }

      FlowShape(bcast.in, merge.out)
    })

  // each hashtag should be consumed from it's own twitter stream
  private def startHashTagStream(actor: ActorRef, tag: HashTagKey) = {
    val hashTagStream = getTwitterStream
    hashTagStream.addListener(Listener(actor))
    hashTagStream.filter(new FilterQuery(tag.rawTag))
    log.debug("Started stream for key: `{}`", tag.rawTag)
    system.registerOnTermination { hashTagStream.shutdown() }
  }

  private val streamStartDelay = 15.seconds

  private def startHashTagsStreams(actor: ActorRef, hashTags: List[HashTagKey]): FiniteDuration =
    hashTags.foldLeft(streamStartDelay) { (accum, key) ⇒
      system.scheduler.scheduleOnce(accum)(startHashTagStream(actor, key))
      accum + streamStartDelay
    }

  private def startUsersStream(actor: ActorRef, users: List[AccountKey]): Unit = {
    val usersStream = getTwitterStream
    usersStream.addListener(Listener(actor))
    usersStream.filter(new FilterQuery(users.map(_.userId): _*))
    log.debug("Started stream for keys: `{}`", users.map(_.account).mkString(","))
    system.registerOnTermination { usersStream.shutdown() }
  }

  def start(): Unit = {
    val resolvedKeys =
      config.repost.toList.map(_.bimap(id ⇒ ApiPeer(ApiPeerType.Group, id.toInt, None), Key.resolve(_, instance)))
    val actor = tweetsSource(resolvedKeys.length * 256)
      .via(keysFlow(resolvedKeys))
      .via(postFlow)
      .named("TwitterStatusesStream")
      .toMat(Sink.ignore)(Keep.left)
      .run
    val (hashTags, users) = resolvedKeys
      .map(_._2)
      .partition {
        case _: HashTagKey ⇒ true
        case _ ⇒ false
      }
      .asInstanceOf[(List[HashTagKey], List[AccountKey])]

    // each twitter stream should be started with a delay, to avoid rate limiting denials
    system.scheduler.scheduleOnce(streamStartDelay) {
      if (users.nonEmpty)
        startUsersStream(actor, users)
      if (hashTags.nonEmpty)
        system.scheduler.scheduleOnce(streamStartDelay)(startHashTagsStreams(actor, hashTags))
    }
  }
}
