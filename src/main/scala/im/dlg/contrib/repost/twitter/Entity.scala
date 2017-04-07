package im.dlg.contrib.repost.twitter

import twitter4j.Status

sealed trait Entity
case class TwitterStatus(status: Status) extends Entity
