package com.example.DunbarHorizon.account.domain.policy;

import java.util.Locale;

/**
 * 이메일 동일성 규칙의 단일 진실 공급원.
 *
 * <p>이 설계에서 증명된 이메일은 곧 계정의 신원이다. 그런데 규칙이 코드 어디에도 없었고,
 * {@code Kim@x.com}과 {@code kim@x.com}이 같은 계정을 가리키는 것은 **MySQL 기본 콜레이션이
 * 대소문자를 구분하지 않기 때문**이었다. 즉 신원 판단이 코드가 아니라 DB 설정에 얹혀 있었고,
 * 그 의존은 어디에도 적혀 있지 않았다.
 *
 * <p>적용 지점은 둘뿐이다 — 쓰기는 {@code User.createActive}, 읽기는
 * {@code UserRepositoryAdapter.findByEmail}. 두 곳이 모든 경로의 길목이라
 * 호출자마다 정규화를 반복할 필요가 없다.
 *
 * <p><b>공급자별 정규화는 하지 않는다.</b> gmail의 {@code .} 무시나 {@code +} 별칭 같은 규칙은
 * 공급자마다 다르고, 틀리면 서로 다른 사람의 계정이 하나로 합쳐진다. 되돌릴 수 없는 종류의
 * 실수라 하지 않는 쪽이 안전하다.
 */
public final class EmailPolicy {

    private EmailPolicy() {
    }

    /**
     * 앞뒤 공백을 걷어내고 소문자로 맞춘다.
     *
     * <p>{@code Locale.ROOT}를 명시하는 이유는 터키어 로케일에서 {@code I}가 점 없는
     * {@code ı}로 내려가기 때문이다. 서버 로케일에 따라 신원이 갈리면 안 된다.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
