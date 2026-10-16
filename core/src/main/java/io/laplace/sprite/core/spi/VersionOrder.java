package io.laplace.sprite.core.spi;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * 版本比较：支持 {@code 1.21.11}、{@code 0.19.5}、{@code 21.11} 等点分数字段，
 * 字段数不同时缺位按 0 处理（14.7 版本矩阵）。
 */
public final class VersionOrder {

    private VersionOrder() {
    }

    public static int compare(String left, String right) {
        List<BigInteger> a = parts(left);
        List<BigInteger> b = parts(right);
        int n = Math.max(a.size(), b.size());
        for (int i = 0; i < n; i++) {
            BigInteger x = i < a.size() ? a.get(i) : BigInteger.ZERO;
            BigInteger y = i < b.size() ? b.get(i) : BigInteger.ZERO;
            int c = x.compareTo(y);
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    private static List<BigInteger> parts(String version) {
        List<BigInteger> out = new ArrayList<>();
        if (version == null) {
            return out;
        }
        for (String token : version.trim().split("[.\\-+_]")) {
            String digits = token.replaceAll("[^0-9].*$", "");
            if (digits.isEmpty()) {
                continue;
            }
            out.add(new BigInteger(digits));
        }
        return out;
    }
}
