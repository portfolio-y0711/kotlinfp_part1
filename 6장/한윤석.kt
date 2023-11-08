// ex1
fun nonNegativeInt(rng: RNG): Pair<Int, RNG> {
  var r: RNG = rng
  while (true) {
      val (value, nextRng) = r.nextInt()
      if (value == Int.MIN_VALUE) {
          r = nextRng
          continue
      }

      return Math.abs(value) to nextRng
  }
}

// ex2
fun double(rng: RNG): Pair<Double, RNG> {
  val (i, rng2) = nonNegativeInt(rng)
  return (i / (Int.MAX_VALUE.toDouble() + 1)) to rng2
}

// ex3
fun intDouble(rng: RNG): Pair<Pair<Int, Double>, RNG> {
  val (i, rng2) = rng.nextInt()
  val (d, rng3) = double(rng2)
  return (i to d) to rng3
}

fun doubleInt(rng: RNG): Pair<Pair<Double, Int>, RNG> {
  val (id, rng2) = intDouble(rng)
  val (i, d) = id
  return (d to i) to rng2
}

fun double3(rng: RNG): Pair<Triple<Double, Double, Double>, RNG> {
  val (d1, rng2) = double(rng)
  val (d2, rng3) = double(rng2)
  val (d3, rng4) = double(rng3)
  return Triple(d1, d2, d3) to rng4
}

// ex4
fun ints(count: Int, rng: RNG): Pair<List<Int>, RNG> {
  if (count <= 0) {
      return Nil to rng
  }

  val (value, rng2) = rng.nextInt()
  val (xs, nextRng) = ints(count - 1, rng2)
  return Cons(value, xs) to nextRng
}

// ex5
typealias Rand<A> = (RNG) -> Pair<A, RNG>

fun <A, B> map(s: Rand<A>, f: (A) -> B): Rand<B> = { rng ->
  val (a, rng2) = s(rng)
  f(a) to rng2
}

fun doubleR(): Rand<Double> =
    map(::nonNegativeInt) { it / (Int.MAX_VALUE.toDouble() + 1) }

// ex6
fun <A, B, C> map2(
  ra: Rand<A>,
  rb: Rand<B>,
  f: (A, B) -> C
): Rand<C> { rng -> 
  val (a, rng2) = ra(rng)
  val (b, rng3) = rb(rng2)
  f(a, b) to rng3
}

// ex7
fun <A> sequence(fs: List<Rand<A>>): Rand<List<A>> = { rng ->
  when (fs) {
      is Nil -> unit(List.empty<A>())(rng)
      is Cons -> {
          val (a, nrng) = fs.head(rng)
          val (xa, frng) = sequence(fs.tail)(nrng)
          Cons(a, xa) to frng
      }
  }
}

fun <A> sequence2(fs: List<Rand<A>>): Rand<List<A>> =
    foldRight(fs, unit(List.empty()), { f, acc ->
        map2(f, acc, { h, t -> Cons(h, t) })
    })

// ex8
fun <A, B> flatMap(f: Rand<A>, g: (A) -> Rand<B>): Rand<B> = { rng ->
        val (a, rng2) = f(rng)
        g(a)(rng2)
    }

fun nonNegativeIntLessThan(n: Int): Rand<Int> =
    flatMap(::nonNegativeInt) {
      val mod = it % n
      if (it + (n - 1) - mod >= 0) unit(mod)
      else nonNegativeIntLessThan(n)
    }

// ex9
fun <A, B> mapF(ra: Rand<A>, f: (A) -> B): Rand<B> =
  flatMap(ra) { unit(f(it)) }

  fun <A, B, C> map2F(
    ra: Rand<A>,
    rb: Rand<B>,
    f: (A, B) -> C
): Rand<C> =
    flatMap(ra) { a ->
        map(rb) { b ->
            f(a, b)
        }
    }

// ex10
data class State<S, out A>(val run: (S) -> Pair<A, S>) {

  companion object {
      fun <S, A> unit(a: A): State<S, A> =
          State { s: S -> a to s }

      fun <S, A, B, C> map2(
          ra: State<S, A>,
          rb: State<S, B>,
          f: (A, B) -> C
      ): State<S, C> =
          ra.flatMap { a ->
              rb.map { b ->
                  f(a, b)
              }
          }

      fun <S, A> sequence(fs: List<State<S, A>>): State<S, List<A>> =
          foldRight(fs, unit(List.empty<A>()),
              { f, acc ->
                  map2(f, acc) { h, t -> Cons(h, t) }
              }
          )
  }

  fun <B> map(f: (A) -> B): State<S, B> =
      flatMap { a -> unit<S, B>(f(a)) }

  fun <B> flatMap(f: (A) -> State<S, B>): State<S, B> =
      State { s: S ->
          val (a: A, s2: S) = this.run(s)
          f(a).run(s2)
      }
}

// ex11
sealed class Input

object Coin : Input()
object Turn : Input()

data class Machine(
    val locked: Boolean,
    val candies: Int,
    val coins: Int
)

val update: (Input) -> (Machine) -> Machine =
    { i: Input ->
        { s: Machine ->
            when (i) {
                is Coin ->
                    if (!s.locked || s.candies == 0) s
                    else Machine(false, s.candies, s.coins + 1)
                is Turn ->
                    if (s.locked || s.candies == 0) s
                    else Machine(true, s.candies - 1, s.coins)
            }
        }
    }

fun simulateMachine(
    inputs: List<Input>
): State<Machine, Tuple2<Int, Int>> =
    State.fx(Id.monad()) {
        inputs
            .map(update)
            .map(StateApi::modify)
            .stateSequential()
            .bind()
        val s = StateApi.get<Machine>().bind()
        Tuple2(s.candies, s.coins)
    }