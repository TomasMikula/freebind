package freebind

import scala.language.higherKinds
import scalaz.{BindRec, ~>}

final class StateT[F[_], S, A] private(val unwrap: FreeBind[λ[X => S => F[(S, X)]], A]) extends AnyVal {
  private type AppPair[X] = (S, S => F[(S, X)])
  private type Result[X] = F[(S, X)]

  private def app: AppPair ~> Result = λ[AppPair ~> Result](x => x._2(x._1))

  def run1(s: S)(implicit F: BindRec[F]): F[(S, A)] = {
    unwrap.foldRunM(s, app)
  }

  def run2(s: S)(implicit F: BindRec[F]): F[(S, A)] =
    unwrap.runStateM[F, S](s)
}