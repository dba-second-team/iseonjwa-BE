package secondteam.iseonjwa.admin.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import secondteam.iseonjwa.admin.dto.PerformanceCreateRequest;
import secondteam.iseonjwa.admin.dto.PerformanceCreateResponse;
import secondteam.iseonjwa.global.common.ApiResponse;
import secondteam.iseonjwa.global.error.BusinessException;
import secondteam.iseonjwa.global.error.ErrorCode;
import secondteam.iseonjwa.global.error.ErrorResponse;
import secondteam.iseonjwa.performance.service.PerformanceRegistrationService;

@Tag(name = "관리자")
@RestController
@RequestMapping("/api/admin/performances")
@RequiredArgsConstructor
public class AdminPerformanceController {
    private final PerformanceRegistrationService performanceRegistrationService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PerformanceCreateResponse> register(
            @RequestHeader("X-Member-Id") long adminId,
            @Valid @RequestPart("performance") PerformanceCreateRequest request,
            @RequestPart(value = "poster", required = false) MultipartFile poster
    ) {
        if (poster != null && !poster.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        return ApiResponse.ok(performanceRegistrationService.register(adminId, request));
    }

    @ExceptionHandler({MissingServletRequestPartException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> handleInvalidPart() {
        return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_REQUEST));
    }
}
