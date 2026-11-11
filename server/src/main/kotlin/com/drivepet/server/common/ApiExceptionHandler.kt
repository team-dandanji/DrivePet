package com.drivepet.server.common

import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class ApiExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun api(e: ApiException) = ResponseEntity.status(e.status).body(ApiResponse.fail(e.code, e.message))

    // 요청 값 오류는 원문(토큰·좌표 등)을 메시지에 넣지 않는다
    @ExceptionHandler(
        MethodArgumentNotValidException::class,
        HandlerMethodValidationException::class,
        HttpMessageNotReadableException::class,
        MissingServletRequestParameterException::class,
        MethodArgumentTypeMismatchException::class,
        ConstraintViolationException::class,
        HttpMediaTypeNotSupportedException::class,
    )
    fun validation(e: Exception) =
        ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.VALIDATION_FAILED, "요청 값이 올바르지 않습니다"))

    @ExceptionHandler(NoResourceFoundException::class)
    fun noResource(e: NoResourceFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail(ErrorCode.NOT_FOUND, "대상을 찾을 수 없습니다"))

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun method(e: HttpRequestMethodNotSupportedException) =
        ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiResponse.fail(ErrorCode.VALIDATION_FAILED, "지원하지 않는 메서드입니다"))

    @ExceptionHandler(Exception::class)
    fun unknown(e: Exception): ResponseEntity<ApiResponse<Nothing>> {
        log.error("처리하지 못한 오류: {}", e.javaClass.name, e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR, "서버 오류가 발생했습니다"))
    }
}
