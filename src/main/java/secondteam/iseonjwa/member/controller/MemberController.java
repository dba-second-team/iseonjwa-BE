package secondteam.iseonjwa.member.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import secondteam.iseonjwa.global.common.ApiResponse;
import secondteam.iseonjwa.member.dto.EmailCheckResponse;
import secondteam.iseonjwa.member.dto.LogInRequest;
import secondteam.iseonjwa.member.dto.LogInResponse;
import secondteam.iseonjwa.member.dto.SignUpRequest;
import secondteam.iseonjwa.member.dto.SignUpResponse;
import secondteam.iseonjwa.member.service.MemberService;

@Tag(name = "회원")
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {
    private final MemberService memberService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignUpResponse> signUp(@RequestBody @Valid SignUpRequest request) {
        return ApiResponse.ok(memberService.signUp(request));
    }

    @PostMapping("/login")
    public ApiResponse<LogInResponse> logIn(@RequestBody @Valid LogInRequest request) {
        return ApiResponse.ok(memberService.logIn(request));
    }

    @GetMapping("/email-check")
    public ApiResponse<EmailCheckResponse> checkEmail(
            @RequestParam @NotBlank @Email @Size(max = 255) String email) {
        return ApiResponse.ok(memberService.checkEmail(email));
    }
}
