package com.drivepet.vehicle.model

/**
 * 차량 값 하나와 그 상태.
 * 값이 없을 때 0이나 false로 채우지 않고, 왜 없는지를 구분해 화면에 '미측정'으로 보여 준다.
 */
sealed interface Signal<out T> {
    /** 정상 값. [at]은 마지막으로 받은 시각(ms). */
    data class Ok<T>(val value: T, val at: Long) : Signal<T>

    /** 이 차(또는 에뮬레이터)가 지원하지 않음. 예: ERROR_NOT_SUPPORTED_PROPERTY */
    data object Unsupported : Signal<Nothing>

    /** 권한이 없음(미선언·미승인). */
    data object NoPermission : Signal<Nothing>

    /** 아직 값을 받지 못함. */
    data object Unknown : Signal<Nothing>
}

/** 정상 값이면 값을, 아니면 null. */
fun <T> Signal<T>.valueOrNull(): T? = (this as? Signal.Ok<T>)?.value

/** 정상 값이 있는지. */
val Signal<*>.isOk: Boolean get() = this is Signal.Ok<*>

/** 화면 표시용 짧은 설명. */
fun Signal<*>.label(): String = when (this) {
    is Signal.Ok<*> -> value.toString()
    Signal.Unsupported -> "미지원"
    Signal.NoPermission -> "권한 없음"
    Signal.Unknown -> "미측정"
}

/**
 * 값이 그대로면 기존 객체를 그대로 돌려준다.
 * 주기 조회로 같은 값이 반복 들어와도 상태가 '바뀐 것'으로 취급되지 않게(불필요한 화면 갱신 방지).
 */
fun <T> Signal<T>.updated(value: T, at: Long): Signal<T> =
    if (this is Signal.Ok<T> && this.value == value) this else Signal.Ok(value, at)
