package secondteam.iseonjwa.queue.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import secondteam.iseonjwa.global.common.ApiResponse;
import secondteam.iseonjwa.global.error.ErrorCode;
import secondteam.iseonjwa.global.error.ErrorResponse;
import secondteam.iseonjwa.queue.dto.WaitingQueueRegisterRequest;
import secondteam.iseonjwa.queue.dto.WaitingQueueRegisterResponse;
import secondteam.iseonjwa.queue.service.WaitingQueueService;

@Tag(name = "대기열")
@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
public class QueueController {

    private final WaitingQueueService waitingQueueService;

    @PostMapping("/waiting")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WaitingQueueRegisterResponse> registerWaiting(
            @RequestHeader("X-Member-Id") Long memberId,
            @RequestBody @Valid WaitingQueueRegisterRequest request) {
        return ApiResponse.ok(waitingQueueService.register(memberId, request));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable() {
        ErrorCode code = ErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code));
    }
}
