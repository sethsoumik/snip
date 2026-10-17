package in.soumikseth.links.core;

import java.util.Arrays;
import java.util.Random;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Turns a numeric id into a short, hard-to-guess code.
 *
 * The idea: use the full 64-char base64url alphabet so each character carries 6
 * bits (shorter than base62), encode the id in base-64 with no padding so the
 * length tracks magnitude, and shuffle the alphabet with a secret seed so
 * sequential ids don't look sequential. It's a pure integer-to-string bijection,
 * so there are no collisions to handle.
 */
@Component
public class ShortCodeGenerator {

    /** Ordered base64url alphabet before the seed-based shuffle. */
    private static final char[] BASE64URL =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray();

    private final char[] alphabet;
    private final int[] charIndex;
    private final int base;

    public ShortCodeGenerator(@Value("${app.short-code.seed:42}") long seed) {
        this.alphabet = shuffledAlphabet(seed);
        this.base = this.alphabet.length;
        this.charIndex = buildIndex(this.alphabet);
    }

    /**
     * Encodes a non-negative integer into the shortest possible short code.
     *
     * @param id the monotonic id to encode
     * @return minimal-length code string
     */
    public String encode(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("id must be non-negative");
        }
        if (id == 0) {
            return String.valueOf(alphabet[0]);
        }
        StringBuilder sb = new StringBuilder();
        long n = id;
        while (n > 0) {
            sb.append(alphabet[(int) (n % base)]);
            n /= base;
        }
        return sb.reverse().toString();
    }

    /**
     * Decodes a short code back to its integer id.
     *
     * @param code the short code
     * @return the original id
     * @throws IllegalArgumentException if the code contains an invalid character
     */
    public long decode(String code) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("code must not be empty");
        }
        long value = 0;
        for (int i = 0; i < code.length(); i++) {
            int digit = indexOf(code.charAt(i));
            if (digit < 0) {
                throw new IllegalArgumentException("invalid character in code: " + code.charAt(i));
            }
            value = value * base + digit;
            if (value < 0) {
                throw new IllegalArgumentException("code too large");
            }
        }
        return value;
    }

    private int indexOf(char c) {
        return c < charIndex.length ? charIndex[c] : -1;
    }

    private static char[] shuffledAlphabet(long seed) {
        char[] chars = Arrays.copyOf(BASE64URL, BASE64URL.length);
        Random random = new Random(seed);
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return chars;
    }

    private static int[] buildIndex(char[] alphabet) {
        int[] index = new int[128]; // all base64url chars are ASCII
        Arrays.fill(index, -1);
        for (int i = 0; i < alphabet.length; i++) {
            index[alphabet[i]] = i;
        }
        return index;
    }
}
