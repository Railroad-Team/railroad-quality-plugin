package dev.railroadide.railroad.formatter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DeclarationListWrappingTest {
    @Test
    public void onlyJoinsTopLevelDeclarationListSeparators() {
        String source = """
            class Example<T extends Bound,
                U> implements A<Map<T,
                U>>,
                @Label(values = {"a",
                    "b"}) B,
                C {
                void run(List<? extends Bound> items,
                    int permits) {
                    call(Example.class, permits,
                        items);
                }
            }
            """.replace("\n", "\r\n");
        String expected = source.replace(">>,\r\n    @Label", ">>, @Label")
            .replace("B,\r\n    C", "B, C");
        assertEquals(expected, DeclarationListWrapping.prepare(source));
    }

    @Test
    public void preservesCommentsAndFormatterDirectivesInLists() {
        String source = """
            interface Example extends A, // Keep the comment with A.
                B,
                /* @formatter:off */ C,
                D, /* @formatter:on */
                E,
                F {}
            """;
        assertEquals(source.replace("E,\n    F", "E, F"), DeclarationListWrapping.prepare(source));
    }
}
