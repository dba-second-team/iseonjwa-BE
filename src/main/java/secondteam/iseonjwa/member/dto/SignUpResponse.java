package secondteam.iseonjwa.member.dto;

import secondteam.iseonjwa.member.entity.Member;

public record SignUpResponse(
        Long memberId,
        String email,
        String name
) {
    public static SignUpResponse from(Member member) {
        return new SignUpResponse(member.getMemberId(), member.getEmail(), member.getName());
    }
}