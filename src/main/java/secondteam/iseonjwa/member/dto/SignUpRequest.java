package secondteam.iseonjwa.member.dto;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignUpRequest(

        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(min = 8, max = 20)
        String password,

        @NotBlank
        @Size(max = 100)
        String name,

        @Size(max = 20)
        @Pattern(regexp = "^01[016789]\\d{7,8}$")
        String phone
) {}
