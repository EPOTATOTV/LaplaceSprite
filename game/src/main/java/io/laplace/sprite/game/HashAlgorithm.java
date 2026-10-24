package io.laplace.sprite.game;

/**
 * 下载校验算法。
 */
public enum HashAlgorithm {
    SHA1("SHA-1"),
    SHA256("SHA-256");

    private final String messageDigestName;

    HashAlgorithm(String messageDigestName) {
        this.messageDigestName = messageDigestName;
    }

    public String messageDigestName() {
        return messageDigestName;
    }
}
