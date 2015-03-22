package lila.video

import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.ws.WS
import play.api.Play.current

private[video] final class Youtube(
    url: String,
    apiKey: String,
    max: Int,
    api: VideoApi) {

  import Youtube._

  private implicit val readStatistics = Json.reads[Statistics]
  private implicit val readEntry = Json.reads[Entry]
  private implicit val readEntries: Reads[Seq[Entry]] =
    (__ \ "items").read(Reads seq readEntry)

  def updateAll: Funit = fetch flatMap { entries =>
    entries.map { entry =>
      api.video.setMetadata(entry.id, Metadata(
        views = ~parseIntOption(entry.statistics.viewCount),
        likes = ~parseIntOption(entry.statistics.likeCount) -
          ~parseIntOption(entry.statistics.dislikeCount))
      ).recover {
        case e: Exception => logerr(s"[video youtube] ${e.getMessage}")
      }
    }.sequenceFu.void
  }

  private def fetch: Fu[List[Entry]] = api.video.allIds flatMap { ids =>
    WS.url(url).withQueryString(
      "id" -> scala.util.Random.shuffle(ids).take(max).mkString(","),
      "part" -> "id,statistics",
      "key" -> apiKey
    ).get() flatMap {
        case res if res.status == 200 => readEntries reads res.json match {
          case JsError(err)          => fufail(err.toString)
          case JsSuccess(entries, _) => fuccess(entries.toList)
        }
        case res =>
          println(res.body)
          fufail(s"[video youtube] fetch ${res.status}")
      }
  }
}

object Youtube {

  def empty = Metadata(0, 0)

  case class Metadata(
    views: Int,
    likes: Int)

  private[video] case class Entry(
    id: String,
    statistics: Statistics)

  private[video] case class Statistics(
    viewCount: String,
    likeCount: String,
    dislikeCount: String)
}
