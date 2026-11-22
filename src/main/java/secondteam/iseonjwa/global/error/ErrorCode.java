package secondteam.iseonjwa.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400 Bad Request
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    PRICE_NOT_SET(HttpStatus.BAD_REQUEST, "구역별 가격이 설정되지 않았습니다."),

    // 401 Unauthorized
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "로그인에 실패했습니다."),

    // 403 Forbidden
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    // 404 Not Found
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."),
    PERFORMANCE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 공연입니다."),
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 회차입니다."),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 예매입니다."),
    QUEUE_ENTRY_NOT_FOUND(HttpStatus.NOT_FOUND, "대기열 또는 예매 작업열에 진입한 상태가 아닙니다."),

    // 409 Conflict
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    SEAT_UNAVAILABLE(HttpStatus.CONFLICT, "이미 선점되었거나 예매된 좌석입니다."),
    HOLD_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "회차당 예매 가능한 매수를 초과했습니다."),
    HOLD_EXPIRED(HttpStatus.CONFLICT, "좌석 선점 시간이 만료되었거나 선점한 좌석이 아닙니다."),
    ALREADY_CANCELED(HttpStatus.CONFLICT, "이미 취소된 예매입니다."),
    CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "공연 당일에는 예매를 취소할 수 없습니다."),
    POSTER_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "포스터 파일은 5MB 이하로 등록해 주세요."),
    POSTER_STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "포스터 저장소를 사용할 수 없습니다."),
    REFUND_NOT_PENDING(HttpStatus.CONFLICT, "환불 대기 중인 예매만 처리할 수 있습니다."),
    INVALID_REFUND_AMOUNT(HttpStatus.CONFLICT, "저장된 취소 수수료로 환불 금액을 계산할 수 없습니다."),
    SCHEDULE_CONFLICT(HttpStatus.CONFLICT, "같은 공연장에 시간이 겹치는 회차가 있습니다."),
    ALREADY_IN_QUEUE(HttpStatus.CONFLICT, "이미 대기열 또는 예매 작업열에 진입한 상태입니다."),
    CANNOT_ENTER_ACTIVE_QUEUE(HttpStatus.CONFLICT, "아직 입장 순서가 아니거나 예매 작업열 정원이 초과되었습니다.")
    ;

    private final HttpStatus status;
    private final String message;
}