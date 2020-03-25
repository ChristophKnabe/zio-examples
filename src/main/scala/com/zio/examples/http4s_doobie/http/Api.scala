package com.zio.examples.http4s_doobie
package http

import persistence._
import io.circe.{Decoder, Encoder}
import org.http4s.{EntityDecoder, EntityEncoder, HttpRoutes, Request, Response}
import org.http4s.dsl.Http4sDsl
import zio._
import org.http4s.circe._
import zio.interop.catz._
import io.circe.generic.auto._
import zio.console.{Console, putStrLn}

final case class Api[R <: UserPersistence with Console](rootUri: String) {

  type UserTask[A] = RIO[R, A]

  implicit def circeJsonDecoder[A](implicit decoder: Decoder[A]): EntityDecoder[UserTask, A] = jsonOf[UserTask, A]

  implicit def circeJsonEncoder[A](implicit decoder: Encoder[A]): EntityEncoder[UserTask, A] = jsonEncoderOf[UserTask, A]

  val dsl: Http4sDsl[UserTask] = Http4sDsl[UserTask]

  import dsl._

  def route: HttpRoutes[UserTask] = {

    HttpRoutes.of[UserTask] {
      case request@GET -> Root / "1" =>
        val id = 1
        domainReporting(request) {
          getUser(id)
        }
      case request@GET -> Root / "2" =>
        val id = 2
        httpReporting(request) {
          Ok(getUser(id))
        }
      /* This way the centralized reporting of failures works, but the execution trace shows only lines in
      * service functions as `getUser` or `domainReporting`. It does not show the location in this branch. */
      case request@GET -> Root / IntVar(id) => domainReporting(request) {
        getUser(id)
      }
      case request@ POST -> Root => httpReporting(request) {
        request.decode[User] { user =>
          Created(createUser(user))
        }
      }
      case request@ DELETE -> Root / IntVar(id) => httpReporting(request) {
        (getUser(id) *> deleteUser(id)).foldM({
          case x: UserNotFound => NotFound(x.toString)
          case x => ZIO.fail(x) //Should not be necessary, if UserNotFound were not an exception.
        }, Ok(_))
      }
    }
  }

  /** Wraps the `domainEffect` into a failure reporting effect, which reports information about the request, and the ZIO execution trace
   * about the Throwable by which the `domainEffect` failed. */
  private def domainReporting(request: Request[UserTask])(domainEffect: RIO[UserPersistence, User]) = {
    domainEffect.foldCauseM(
      failure = cause => {
        val method = request.method
        val uri = request.uri
        val messageHead = s"Request $method $uri failed with:\n"
        putStrLn(failureTrace(messageHead, cause)) *>
          InternalServerError(cause.failures.mkString(messageHead, "\n", ""))
      },
      success = result => {
        Ok(result)
      }
    )
  }

  /** Wraps the `httpEffect` into a failure reporting effect, which reports information about the request, and the ZIO execution trace
   * about the Throwable by which the `httpEffect` failed. */
  private def httpReporting0(request: Request[UserTask])(httpEffect: UserTask[Response[UserTask]]) = {
    httpEffect.foldCauseM(
      failure = cause => {
        val method = request.method
        val uri = request.uri
        val messageHead = s"Request $method $uri failed with:\n"
        putStrLn(failureTrace(messageHead, cause)) *>
          InternalServerError(cause.failures.mkString(messageHead, "\n", ""))
      },
      success = result => RIO.succeed(result)
    )
  }

  /** Wraps the `httpEffect` into a failure reporting effect, which reports information about the request, and the ZIO execution trace
   * about the Throwable by which the `httpEffect` failed. */
  private def httpReporting(request: Request[UserTask])(httpEffect: UserTask[Response[UserTask]]) = {
    httpEffect.catchAllCause{cause =>
      val method = request.method
      val uri = request.uri
      val messageHead = s"v3 Request $method $uri failed with:\n"
      putStrLn(failureTrace(messageHead, cause)) *>
        InternalServerError(cause.failures.mkString(messageHead, "\n", ""))
    }
  }

  /** Converts the given ZIO failure `cause` to a detailed diagnostic message containing the given `messageHead`,
   * the exceptions stack traces, and the execution traces and plans. */
  def failureTrace(messageHead: String, cause: Cause[Throwable]): String = {
    val lineIntro = "\n  at "
    val failures = for {
      failure <- cause.failures
      stackTrace = failure.getStackTrace.mkString(failure + lineIntro, lineIntro, "")
    } yield stackTrace
    val traces = cause.traces.map(_.prettyPrint).mkString("\n", "\n", "\n")
    failures.mkString(messageHead, "\n", "\n") + traces
  }


}
