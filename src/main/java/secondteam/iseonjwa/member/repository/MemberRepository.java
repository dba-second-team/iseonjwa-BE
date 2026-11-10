package secondteam.iseonjwa.member.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import secondteam.iseonjwa.member.entity.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {
    boolean existsByEmail(String email);
}
