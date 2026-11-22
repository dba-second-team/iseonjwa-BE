package secondteam.iseonjwa.global.error;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 우리가 직접 만든 예외
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code));
    }

    // @Valid 검증 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        ErrorCode code = ErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadLimit() {
        ErrorCode code = ErrorCode.POSTER_TOO_LARGE;
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code));
    }

    // X-Member-Id 같은 필수 헤더 누락
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
        ErrorCode code = ErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code));
    }

    // 헤더/쿼리/경로 파라미터 타입 변환 실패 (예: 숫자 자리에 문자열 입력)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        ErrorCode code = ErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code));
    }

    // @RequestParam, @PathVariable 검증 실패
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException e) {
        ErrorCode code = ErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code));
    }

    // 필수 쿼리 파라미터 누락
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException e) {
        ErrorCode code = ErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code));
    }

}