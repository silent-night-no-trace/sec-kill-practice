package com.style.seckill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonLibraryGuardTest {

    private static final List<String> FORBIDDEN_TOKENS = List.of(
            "import com.fasterxml.jackson",
            "ObjectMapper",
            "JsonNode",
            "Jackson2JsonMessageConverter");

    @Test
    void shouldNotReintroduceExplicitJacksonUsage() throws IOException {
        List<String> violations = new ArrayList<>(scanForForbiddenTokens(Path.of("src", "main", "java")));
        violations.addAll(scanForForbiddenTokens(Path.of("src", "test", "java")));

        assertThat(violations)
                .withFailMessage("Explicit Jackson usage is forbidden by project convention. Violations:%n%s",
                        String.join(System.lineSeparator(), violations))
                .isEmpty();
    }

    private List<String> scanForForbiddenTokens(Path root) throws IOException {
        if (!Files.exists(root)) {
            return List.of();
        }

        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("JsonLibraryGuardTest.java"))
                    .flatMap(this::findForbiddenTokens)
                    .toList();
        }
    }

    private Stream<String> findForbiddenTokens(Path path) {
        try {
            String source = Files.readString(path);
            return FORBIDDEN_TOKENS.stream()
                    .filter(source::contains)
                    .map(token -> path + " -> " + token);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to inspect source file: " + path, exception);
        }
    }
}
