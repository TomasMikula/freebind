package freebind

import scala.language.higherKinds
import scalaz.{BindRec, ~>}

case class ReaderT[F[_], R, A] private(unwrap: FreeBind[λ[α => R => F[α]], A]) extends AnyVal {
  type F0[X] = R => F[X]

  def flatMap[C](f: A => ReaderT[F, R, C])(implicit F: BindRec[F]): ReaderT[F, R, C] =
    ReaderT(unwrap.flatMap(b => f(b).unwrap))

  def apply(a: R)(implicit F: BindRec[F]): F[A] =
    unwrap.foldMap(λ[F0 ~> F](f => f(a)))

}