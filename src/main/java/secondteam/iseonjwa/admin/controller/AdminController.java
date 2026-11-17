package secondteam.iseonjwa.admin.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import secondteam.iseonjwa.global.common.ApiResponse;
import secondteam.iseonjwa.member.dto.LogInRequest;
import secondteam.iseonjwa.member.dto.LogInResponse;
import secondteam.iseonjwa.member.service.MemberService;

@Tag(name = "관리자")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final MemberService memberService;

    @PostMapping("/login")
    public ApiResponse<LogInResponse> logIn(@RequestBody @Valid LogInRequest request) {
        return ApiResponse.ok(memberService.adminLogin(request));
    }

    @GetMapping("/abc")
    public ApiResponse<String> hey() {
        return ApiResponse.ok("Hello");
    }
}