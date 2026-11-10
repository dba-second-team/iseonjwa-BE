package secondteam.iseonjwa.global.common;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResponse<T> {
    private final T data;

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data);
    }
}
