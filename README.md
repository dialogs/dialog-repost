Dialog repost
=============

Dialog Repost is a [Dialog Server](https://dlg.im) extension for posting [Twitter](https://twitter.com) tweets or RSS
feed items to Dialog channels.

Usage
-----

In your `build.sbt` file add following line:

```scala
libraryDependencies += "im.dlg" %% "dialog-repost" % "0.0.2-sdk_<YOUR-CURRENT-SDK-VERSION>"
```

Note that `<YOUR-CURRENT-SDK-VERSION>` should be free of `-SNAPSHOT` suffix even if you are
developing against an sdk snapshot.

Add following to your configuration file:

```hocon
modules.reposter.extension = "im.dlg.contrib.repost.RepostExtension"

services.poster {
  twitter {
    consumer-key = "<twitter-api-consumer-key>"
    consumer-secret = "<twitter-api-consumer-secret>"
    token = "<twitter-api-token>"
    token-secret = "<twitter-api-token-secret>"
    repost {
      # this section declares a mapping from channel-id (literal integer value)
      # to a twitter account denoted by "@<account-name>" or twitter hash-tag
      # tweets stream denoted by "#<hashtag>"
      # You can have as many such pairs as twitter rate limiting allows you
      <channel-id>: "@account1"
      <channel-id>: "@account2"
      <channel-id>: "#hashtag1"
      <channel-id>: "#hashtag2"
    }
  }
  
  rss {
    # describes an interval between RSS feed lookups
    interval = 15 seconds
    repost {
      # this section declares a mapping from channel-id (literal integer value)
      # to a RSS feed URL 
      <channel-id>: "http://rss.feed/url"
    }
  }
}
```

The extension will be started with the server.

License
-------

This software is available under the [Apache 2.0 License](https://www.apache.org/licenses/LICENSE-2.0.html)
