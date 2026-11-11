package secondteam.iseonjwa.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400 Bad Request
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    PRICE_NOT_SET(HttpStatus.BAD_REQUEST, "등급별 가격이 설정되지 않았습니다."),

    // 401 Unauthorized
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "로그인에 실패했습니다."),

    // 403 Forbidden
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    // 404 Not Found
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."),
    PERFORMANCE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 공연입니다."),
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 회차입니다."),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 예매입니다."),

    // 409 Conflict
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    SEAT_UNAVAILABLE(HttpStatus.CONFLICT, "이미 선점되었거나 예매된 좌석입니다."),
    HOLD_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "회차당 예매 가능한 매수를 초과했습니다."),
    HOLD_EXPIRED(HttpStatus.CONFLICT, "좌석 선점 시간이 만료되었거나 선점한 좌석이 아닙니다."),
    ALREADY_CANCELED(HttpStatus.CONFLICT, "이미 취소된 예매입니다."),
    CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "공연 당일에는 예매를 취소할 수 없습니다."),
    SCHEDULE_CONFLICT(HttpStatus.CONFLICT, "같은 공연장에 시간이 겹치는 회차가 있습니다.")
    ;

    private final HttpStatus status;
    private final String message;
}