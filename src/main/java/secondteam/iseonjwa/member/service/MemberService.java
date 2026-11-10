package secondteam.iseonjwa.member.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import secondteam.iseonjwa.global.error.BusinessException;
import secondteam.iseonjwa.global.error.ErrorCode;
import secondteam.iseonjwa.member.dto.SignUpRequest;
import secondteam.iseonjwa.member.dto.SignUpResponse;
import secondteam.iseonjwa.member.entity.Member;
import secondteam.iseonjwa.member.repository.MemberRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SignUpResponse signUp(SignUpRequest request) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }
        Member member = Member.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .name(request.name())
                .phone(request.phone())
                .build();
        return SignUpResponse.from(memberRepository.save(member));
    }
}
