package com.example.DunbarHorizon.account.domain.policy;

/**
 * 닉네임 규칙의 단일 진실 공급원.
 *
 * <p>이전에는 규칙이 웹 DTO의 {@code @Size} 두 곳에만 있었고, OAuth 가입은 그 DTO를
 * 거치지 않아 검증 없이 통과했다. 공급자가 준 이름이 {@code MAX_LENGTH}를 넘으면
 * 컬럼 길이에 걸려 가입 자체가 실패했다.
 */
public final class NicknamePolicy {

    public static final int MIN_LENGTH = 1;
    public static final int MAX_LENGTH = 20;
    public static final String LENGTH_MESSAGE = "닉네임은 1자 이상 20자 이하로 입력해주세요.";

    private NicknamePolicy() {
    }

    /**
     * 앞뒤 공백을 걷어내고 {@code MAX_LENGTH}에 맞춰 자른다.
     *
     * <p>거부가 아니라 잘라내기인 이유는 호출자가 둘로 나뉘기 때문이다. 로컬 가입은 사용자가
     * 직접 입력하므로 웹 계층에서 먼저 걸러 되돌려줄 수 있지만, OAuth는 공급자가 준 값이라
     * 사용자가 고칠 수 없다. 거기서 거부하면 이름이 긴 사람은 로그인할 방법이 없어진다.
     *
     * <p>{@code null}은 그대로 통과시킨다. 로컬 경로는 {@code @NotBlank}가 앞에서 잡고,
     * OAuth 경로에서 공급자가 이름을 주지 않는 경우는 별도 정책이 필요한 사안이다.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.length() <= MAX_LENGTH ? trimmed : trimmed.substring(0, MAX_LENGTH);
    }
}
