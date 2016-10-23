FreeBind
========

If we take away `point` from the `Free` monad, what's left is not pointless.

Intro
-----

When I saw this representation of the free monad

```scala
sealed trait Free[F[_], A]
case class Point[F[_], A](a: A) extends Free[F, A]
case class Suspend[F[_], A](fa: F[Free[F, A]]) extends Free[F, A]
```

it sure felt like cheating. Where's the `bind` operation?? It turns out that when `F[_]` is a `Functor`, you can indeed implement lawful monad operations for `Free[F, A]`. However, this representation has some problems:

 - _Quadratic complexity:_ it takes _O(n<sup>2</sup>)_ to perform _n_ successive binds.
 - _Stack-safety issues._ (In languages where stack-safety is an issue, that is.)
 - _Functor constraint:_ to implement the monad operations, `F` needs to be a functor.

Now let's consider a more straightforward representation. We will need to be able to:

 - pretend that `F` is a monad, by wrapping up `F[A]` as `Free[F, A]`;
 - represent a pure value (just like before);
 - pretend we can do `bind`s (a.k.a. `flatMap`s).

```scala
sealed trait Free[F[_], A]
case class LiftF[F[_], A](fa: F[A]) extends Free[F, A]
case class Point[F[_], A](a: A) extends Free[F, A]
case class FlatMap[F[_], Z, A](fz: Free[F, Z], f: Z => Free[F, A]) extends Free[F, A]
```

With this representation, we have eliminated the quadratic complexity, stack-safety issues and the functor constraint all at once, all while taking the straightforward approach. This is basically the current representation in both [scalaz](https://github.com/scalaz/scalaz/blob/be687093f605d81671290e5ffcd023c657d01f7b/core/src/main/scala/scalaz/Free.scala#L52-L59) and [cats](https://github.com/typelevel/cats/blob/bb927c7baa3ebd80dc2d42719f8558a5b1e16e56/free/src/main/scala/cats/free/Free.scala#L150-L159).

Another nice property of this representation is that it is still useful even when we take away the `Point` constructor.

Meet `FreeBind`
---------------

```scala
sealed trait FreeBind[F[_], A]
case class LiftF[F[_], A](fa: F[A]) extends FreeBind[F, A]
case class FlatMap[F[_], Z, A](fz: FreeBind[F, Z], f: Z => FreeBind[F, A]) extends FreeBind[F, A]
```

`FreeBind` retains a lot of nice properties of `Free`.

### Trampoline

`Free[Id, A]` can be used as a trampoline (stack-safe evaluator), but so can `FreeBind[Id, A]`.

```scala
type Trampoline[A] = FreeBind[Id, A]
```

The representation of a trampoline via `Free` just has one case too many.

See [`Trampoline.scala`](https://github.com/TomasMikula/freebind/blob/master/src/main/scala/freebind/Trampoline.scala) for a more complete implementation.

### Monad transformer

`Free` is a monad transformer that turns a strict monad into a lazy one. But so is `FreeBind`:

```scala
class FreeBindMonadTrans extends MonadTrans[FreeBind] {
  def liftM[F[_], A](ga: F[A])(implicit F: Monad[F]): FreeBind[F, A] = LiftF(ga)

  implicit def apply[F[_]](implicit F: Monad[F]): Monad[FreeBind[F, ?]] =
    new Monad[FreeBind[F, ?]] {
      def point[A](a: => A): FreeBind[F, A] = LiftF(F.point(a))
      def bind[A, B](fa: FreeBind[F, A])(f: A => FreeBind[F, B]): FreeBind[F, B] = FlatMap(fa, f)
    }
}
```

`Free` is just a little too redundant when `F` is already a monad itself, because it has two representations of `point`:

```scala
def point[A](a: A): Free[F, A] = Point(a)
def point[A](a: A): Free[F, A] = LiftF(Monad[F].point(a))
```

#### Examples

##### Stack-safe `State` monad

```scala
type StateF[S, A] = S => (S, A)
type State[S, A] = FreeBind[StateF[S, ?], A]
```

##### Stack-safe `StateT` monad transformer

```scala
type StateTF[F[_], S, A] = S => F[(S, A)]
type StateT[F[_], S, A] = FreeBind[StateTF[F, S, ?], A]
```

See [`StateT.scala`](https://github.com/TomasMikula/freebind/blob/master/src/main/scala/freebind/StateT.scala) for how this `StateT` representation can be run in a stack-safe fashion.

##### Stack-safe `Reader` monad

```scala
type Reader[R, A] = FreeBind[R => ?, A]
```

##### Stack-safe `ReaderT` monad transformer

```scala
type ReaderTF[F[_], R, A] = R => F[A]
type ReaderT[F[_], R, A] = FreeBind[ReaderTF[F, R, ?], A]
```

See [`ReaderT.scala`](https://github.com/TomasMikula/freebind/blob/master/src/main/scala/freebind/ReaderT.scala) for how this `ReaderT` representation can be run in a stack-safe fashion.

### `Free`

Yes, `FreeBind` can be used to implement `Free` itself.

Let me use `F :+: G` as a shorthand for `λ[A => Either[F[A], G[A]]]`.

```scala
type Free[F[_], A] = FreeBind[Id :+: F, A]
```

See [`Free.scala`](https://github.com/TomasMikula/freebind/blob/master/src/main/scala/freebind/Free.scala) for a more complete implementation.

### `FreeT`

`FreeBind` can even be used to implement the free monad transformer `FreeT`.

```scala
type FreeT[F[_], M[_], A] = FreeBind[M :+: F, A]
```

See [`FreeT.scala`](https://github.com/TomasMikula/freebind/blob/master/src/main/scala/freebind/FreeT.scala) for a more complete implementation.
