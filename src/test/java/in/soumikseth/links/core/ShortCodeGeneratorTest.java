package in.soumikseth.links.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ShortCodeGeneratorTest {

    private final ShortCodeGenerator codec = new ShortCodeGenerator(42L);

    @Test
    void encodeDecodeRoundTrip() {
        for (long id = 0; id < 100_000; id++) {
            assertThat(codec.decode(codec.encode(id))).isEqualTo(id);
        }
    }

    @Test
    void codesAreMinimalLength() {
        // First 64 ids fit in one character, the next in two, and so on.
        assertThat(codec.encode(0)).hasSize(1);
        assertThat(codec.encode(63)).hasSize(1);
        assertThat(codec.encode(64)).hasSize(2);
        assertThat(codec.encode(64L * 64 - 1)).hasSize(2);
        assertThat(codec.encode(64L * 64)).hasSize(3);
    }

    @Test
    void codesAreUniqueAndDeterministic() {
        Set<String> seen = new HashSet<>();
        for (long id = 0; id < 10_000; id++) {
            String code = codec.encode(id);
            assertThat(seen.add(code)).as("code %s should be unique", code).isTrue();
        }
        assertThat(codec.encode(1234L)).isEqualTo(codec.encode(1234L));
    }

    @Test
    void rejectInvalidInput() {
        assertThatThrownBy(() -> codec.encode(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode("not-a-valid-*"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
