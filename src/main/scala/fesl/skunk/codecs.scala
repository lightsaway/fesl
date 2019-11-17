package fesl.skunk

import java.util.UUID

import skunk.Codec
import skunk.data.Type
import cats.implicits._
import io.circe.syntax._

import scala.util.Try

object codecs {
  import io.circe.parser
  val uuid: Codec[UUID] =
    Codec.simple(_.toString, x => Try(UUID.fromString(x)).toEither.leftMap(_.getMessage), Type.uuid)

  private def decocde[A](a: String)(implicit encoder: io.circe.Encoder[A],
                                    decoder: io.circe.Decoder[A]) =
    parser.decode[A](a).leftMap(_.getMessage())

  def json[A](implicit encoder: io.circe.Encoder[A], decoder: io.circe.Decoder[A]): Codec[A] =
    Codec.simple(_.asJson.noSpaces, x => decocde[A](x), Type.json)

}
