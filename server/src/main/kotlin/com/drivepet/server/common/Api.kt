package com.drivepet.server.common

import org.springframework.http.HttpStatus

enum class ErrorCode {
    AUTH_INVALID_TOKEN,
    AUTH_EXPIRED_TOKEN,
    VALIDATION_FAILED,
    UNSUPPORTED_SCHEMA_VERSION,
    NOT_FOUND,
    KAKAO_NOT_LINKED,
    KAKAO_QR_EXPIRED,
    IDEMPOTENCY_CONFLICT,
    EXTERNAL_API_FAILED,
    RATE_LIMITED,
    INTERNAL_ERROR,
}

data class ApiError(val code: ErrorCode, val message: String)

/** 모든 JSON 응답의 공통 봉투: { data, error } */
data class ApiResponse<T>(val data: T?, val error: ApiError?) {
    companion object {
        fun <T> ok(data: T) = ApiResponse(data, null)
        fun fail(code: ErrorCode, message: String) = ApiResponse<Nothing>(null, ApiError(code, message))
    }
}

class ApiException(val status: HttpStatus, val code: ErrorCode, override val message: String) : RuntimeException(message) {
    companion object {
        fun invalidToken() = ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_INVALID_TOKEN, "인증 정보가 올바르지 않습니다")
        fun validation(message: String) = ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, message)
        fun notFound(message: String = "대상을 찾을 수 없습니다") = ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, message)
    }
}
