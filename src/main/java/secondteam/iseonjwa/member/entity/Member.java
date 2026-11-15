package secondteam.iseonjwa.member.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import jakarta.validation.constraints.Pattern;

@Entity
@Table(name = "member", uniqueConstraints = @UniqueConstraint(name = "AK_MEMBER_EMAIL", columnNames = "email"))
public class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Pattern(
        regexp = "^[0-9]+$",
        message = "전화번호는 하이픈 없이 숫자만 입력해야 합니다."
    )
    @Column(name = "phone", length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    protected Member() {}

    public Long getMemberId() { return memberId; }
    public void setMemberId(Long value) { this.memberId = value; }

    public String getEmail() { return email; }
    public void setEmail(String value) { this.email = value; }

    public String getName() { return name; }
    public void setName(String value) { this.name = value; }

    public String getPhone() { return phone; }
    public void setPhone(String value) { this.phone = value; }

    public Role getRole() { return role; }
    public void setRole(Role value) { this.role = value; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }

    public String getPassword() { return password; }
    public void setPassword(String value) { this.password = value; }
}
