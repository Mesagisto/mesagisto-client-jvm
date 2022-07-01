package org.meowcat.mesagisto.client.data

sealed class Either<out A, out B> {
  internal abstract val isRight: Boolean
  internal abstract val isLeft: Boolean
  fun isLeft(): Boolean = isLeft
  fun isRight(): Boolean = isRight
  inline fun tapLeft(f: (A) -> Unit): Either<A, B> =
    when (this) {
      is Left -> {
        f(this.value)
        this
      }
      is Right -> this
    }
  inline fun tap(f: (B) -> Unit): Either<A, B> =
    when (this) {
      is Left -> this
      is Right -> {
        f(this.value)
        this
      }
    }

  inline fun findOrNull(predicate: (B) -> Boolean): B? =
    when (this) {
      is Right -> if (predicate(this.value)) this.value else null
      is Left -> null
    }

  data class Left<out A> constructor(val value: A) : Either<A, Nothing>() {
    override val isLeft = true
    override val isRight = false
    override fun toString(): String = "Either.Left($value)"

    companion object {
      internal val leftUnit: Either<Unit, Nothing> = Left(Unit)
    }
  }

  data class Right<out B> constructor(val value: B) : Either<Nothing, B>() {
    override val isLeft = false
    override val isRight = true
    override fun toString(): String = "Either.Right($value)"

    companion object {
      internal val unit: Either<Nothing, Unit> = Right(Unit)
    }
  }
}

fun <A> A.left(): Either<A, Nothing> = Either.Left(this)

fun <A> A.right(): Either<Nothing, A> = Either.Right(this)
