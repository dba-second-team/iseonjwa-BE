package secondteam.iseonjwa.global.error;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ErrorResponse {

    private final ErrorCode errorCode;
    private final HttpStatus status;
    private final String message;

    public ErrorResponse of(ErrorCode code) {
        return new ErrorResponse(errorCode, code.getStatus(), code.getMessage());
    }

}
