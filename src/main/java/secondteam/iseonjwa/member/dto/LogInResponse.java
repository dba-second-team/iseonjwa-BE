package secondteam.iseonjwa.member.dto;

import secondteam.iseonjwa.member.entity.Member;
import secondteam.iseonjwa.member.entity.Role;

public record LogInResponse(
    Long memberId,
    Role role,
    String name

) {
    public static LogInResponse from(Member member) {
        return new LogInResponse(member.getMemberId(), member.getRole(), member.getName());
    }
}
