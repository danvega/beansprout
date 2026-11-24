package dev.danvega.beansprout.content;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class TruncateSummaryTests {

    private static final ZoneId AMSTERDAM = ZoneId.of("Europe/Amsterdam");

    private final PostParser parser = new PostParser(AMSTERDAM);

    private Post parse(String path, String text) {
        return parser.parse(new MarkdownFile(path, text));
    }

    @Test
    @DisplayName("POSTS-19 without a summary or description, the text before a <!-- truncate --> comment is used")
    void summaryFromTruncate() {
        Post marked = parse("a.md", """
				---
				title: A
				date: 2026-11-16
				---
				First *paragraph*.

				Second paragraph.

				<!-- truncate -->

				Everything after.
				""");
        Post inline = parse("b.md", """
				---
				title: B
				date: 2026-11-16
				---
				The lede <!-- truncate --> and the rest of the sentence.
				""");
        Post written = parse("c.md", """
        		---
        		title: C
        		date: 2026-11-16
        		summary: Chosen.
        		---
        		Before.
        		
        		<!-- truncate -->
        		After.
        		""");
        Post described = parse("d.md", """
        		---
        		title: D
        		date: 2026-11-16
        		description: Chosen too.
        		---
        		Before.
        		
        		<!-- truncate -->
        		
        		After.
        		""");
        Post coded = parse("e.md", """
				---
				title: E
				date: 2026-11-16
				---
				Real first paragraph.

				```markdown
				<!-- truncate -->
				```
				""");
        Post longOne = parse("f.md",
                "---\ntitle: F\ndate: 2026-11-16\n---\n" + "word ".repeat(80).strip()
                        + "\n\n<!-- truncate -->\n\nAfter.\n");

        assertThat(marked.summary()).isEqualTo("First paragraph. Second paragraph.");
        assertThat(inline.summary()).isEqualTo("The lede");
        assertThat(written.summary()).isEqualTo("Chosen.");
        assertThat(described.summary()).isEqualTo("Chosen too.");
        assertThat(coded.summary()).isEqualTo("Real first paragraph.");
        assertThat(longOne.summary()).hasSizeLessThanOrEqualTo(200).endsWith("word…");
    }

}
