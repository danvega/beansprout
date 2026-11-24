package dev.danvega.beansprout.content;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import dev.danvega.beansprout.code.CodeHighlighter;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.text.TextContentRenderer;
import org.jspecify.annotations.Nullable;

/** CommonMark with GitHub tables. Gives the HTML, the {@code ##} headings, and plain text for summaries. */
final class MarkdownRenderer {

	private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create());

	/** An {@code <img>} tag written as raw HTML. */
	private static final Pattern RAW_IMG = Pattern.compile("(?i)<img(\\s|/|>|$)");

	/**
	 * An HTML comment, which shows nothing: the empty {@code <!-->} and {@code <!--->}, one closed by
	 * {@code -->} or {@code --!>}, or one running to the end.
	 */
	private static final Pattern COMMENT = Pattern.compile("(?s)<!--(?:>|->|.*?(?:--!?>|$))");

	/**
	 * The comment that ends a summary: {@code <!-- truncate -->}.
	 */
	private static final Pattern TRUNCATE = Pattern.compile("<!--\\s*truncate\\s*-->");

	private final Parser parser = Parser.builder().extensions(EXTENSIONS).build();

	private final CodeHighlighter highlighter;

	MarkdownRenderer(CodeHighlighter highlighter) {
		this.highlighter = highlighter;
	}

	private final TextContentRenderer text = TextContentRenderer.builder().extensions(EXTENSIONS).build();

	record Rendered(String html, List<Heading> headings, String firstParagraph, String beforeTruncate, int words) {
	}

	Rendered render(String markdown) {
		return render(markdown, "", null);
	}

	/**
	 * Renders a post's body.
	 *
	 * @param root the app's context path, like {@code /app}, or empty. A link or image that starts
	 *     with one {@code /} gets it in front, the way a {@code picture:} does.
	 * @param images for a post that is a folder, the address its images are served under, ending in
	 *     {@code /}. An image linked by a relative path, like {@code shots/a.png}, gets it in front.
	 *     Null for a post that is a single file, whose links stay as written.
	 */
	Rendered render(String markdown, String root, @Nullable String images) {
		return render(markdown, root, images, false, path -> null);
	}

	/**
	 * Renders a post's body, with loading hints on each Markdown image. Every one is decoded off the
	 * main thread and loaded lazily, except the body's first picture when the post has no lead
	 * picture: it may be the first thing a reader sees. An image written as raw HTML is left as it is.
	 *
	 * @param root the app's context path, like {@code /app}, or empty
	 * @param images for a post that is a folder, the address its images are served under, or null
	 * @param picture true when the post has a lead picture above its body
	 * @param size the size of a file in the post's folder, by its decoded path, or null when unknown
	 */
	Rendered render(String markdown, String root, @Nullable String images, boolean picture,
			Function<String, @Nullable ImageSize> size) {
		Node document = parser.parse(markdown);
		@Nullable Image eager = picture ? null : firstImage(document);

		IdentityHashMap<Node, String> ids = new IdentityHashMap<>();
		List<Heading> headings = new ArrayList<>();
		Map<String, Integer> used = new HashMap<>();
		Set<String> taken = new HashSet<>();
		document.accept(new AbstractVisitor() {
			@Override
			public void visit(org.commonmark.node.Heading heading) {
				if (heading.getLevel() == 2) {
					String label = plain(heading);
					String id = unique(Slugs.slugify(label).isEmpty() ? "section" : Slugs.slugify(label), used, taken);
					ids.put(heading, id);
					headings.add(new Heading(id, label));
				}
				visitChildren(heading);
			}
		});

		HtmlRenderer html = HtmlRenderer.builder()
			.extensions(EXTENSIONS)
			.nodeRendererFactory(context -> new FencedCodeRenderer(context, highlighter))
			.attributeProviderFactory(context -> (node, tagName, attributes) -> {
				String id = ids.get(node);
				if (id != null) {
					attributes.put("id", id);
				}
				if (node instanceof Image image && tagName.equals("img")) {
					String file = images == null ? null : Bundles.file(image.getDestination());
					ImageSize known = file == null ? null : size.apply(file);
					if (known != null) {
						attributes.put("width", Integer.toString(known.width()));
						attributes.put("height", Integer.toString(known.height()));
					}
					if (!node.equals(eager)) {
						attributes.put("loading", "lazy");
					}
					attributes.put("decoding", "async");
				}
			})
			.build();

		String body = html.render(document);
		// The context path first: a bundle's images get the bundle's address, which already holds it.
		body = withRoot(body, root);
		return new Rendered(images == null ? body : inFolder(body, images), headings, firstParagraph(document),
				beforeTruncate(document), countWords(text.render(document)));
	}

	/** The tags whose address gets the context path, each with the attribute that holds it. */
	private static final String[][] LINKS = { { "a", "href" }, { "link", "href" }, { "img", "src" },
			{ "source", "src" }, { "video", "src" }, { "audio", "src" } };

	/**
	 * Puts the context path in front of every link and image whose address starts with one
	 * {@code /}, in Markdown and in raw HTML alike. An address starting with {@code //} or
	 * {@code /\} points at another host, so it stays as written.
	 */
	private static String withRoot(String html, String root) {
		if (root.isEmpty()) {
			return html;
		}
		String out = html;
		for (String[] link : LINKS) {
			out = rewrite(out, link[0], link[1], value -> {
				String address = value.strip();
				boolean inApp = address.startsWith("/")
						&& (address.length() == 1 || (address.charAt(1) != '/' && address.charAt(1) != '\\'));
				return inApp ? root + address : null;
			}, true);
		}
		return out;
	}

	/**
	 * Puts a bundle's address in front of every image that points at a file in its folder, in
	 * Markdown images and in raw HTML alike.
	 */
	private static String inFolder(String html, String images) {
		return rewrite(html, "img", "src", value -> Bundles.inFolder(value) ? images + Bundles.tidy(value) : null, false);
	}

	/**
	 * Changes one attribute on every tag of one name. Each tag's attributes are read one by one, so
	 * a quoted value may hold {@code >} or the attribute's name. Code is escaped by then, so a tag
	 * shown in code is not touched.
	 *
	 * @param change gets the attribute's value as a browser reads it, and gives the new value, or
	 *     null to leave it as written
	 * @param keepUnread true to pass a value this cannot read to {@code change} as written, and
	 *     write the new value back the same way. False leaves such a value alone.
	 */
	private static String rewrite(String html, String tag, String attribute,
			java.util.function.Function<String, @Nullable String> change, boolean keepUnread) {
		StringBuilder out = new StringBuilder(html.length());
		int done = 0;
		int at = indexOfTag(html, 0, tag);
		while (at >= 0) {
			int p = at + 1 + tag.length();
			while (p < html.length() && html.charAt(p) != '>') {
				char c = html.charAt(p);
				if (Character.isWhitespace(c) || c == '/') {
					p++;
					continue;
				}
				int nameStart = p;
				while (p < html.length() && !Character.isWhitespace(html.charAt(p)) && "=>/".indexOf(html.charAt(p)) < 0) {
					p++;
				}
				String name = html.substring(nameStart, p);
				int q = skipSpace(html, p);
				if (q >= html.length() || html.charAt(q) != '=') {
					p = Math.max(p, nameStart + 1);
					continue;
				}
				int valueStart = skipSpace(html, q + 1);
				int valueEnd;
				int next;
				if (valueStart < html.length() && (html.charAt(valueStart) == '"' || html.charAt(valueStart) == '\'')) {
					int close = html.indexOf(html.charAt(valueStart), valueStart + 1);
					valueEnd = close < 0 ? html.length() : close;
					next = close < 0 ? html.length() : close + 1;
					valueStart++;
				}
				else {
					valueEnd = valueStart;
					while (valueEnd < html.length() && !Character.isWhitespace(html.charAt(valueEnd)) && html.charAt(valueEnd) != '>') {
						valueEnd++;
					}
					next = valueEnd;
				}
				String written = html.substring(valueStart, valueEnd);
				String value = unescape(written);
				// A value with an & this cannot read, like a bare & in a query, is changed as written.
				boolean asWritten = value == null && keepUnread;
				String read = value != null ? value : asWritten ? written : null;
				String changed = name.equalsIgnoreCase(attribute) && read != null ? change.apply(read) : null;
				if (changed != null) {
					out.append(html, done, nameStart)
						.append(attribute)
						.append("=\"")
						.append(asWritten ? changed.replace("\"", "&quot;")
								: changed.replace("&", "&amp;").replace("\"", "&quot;"))
						.append('"');
					done = next;
				}
				p = next;
			}
			at = indexOfTag(html, p, tag);
		}
		return out.append(html, done, html.length()).toString();
	}

	/**
	 * An attribute value with its character references decoded, the way a browser reads it, or
	 * null when it has an {@code &} this does not read for sure. Such a value is left as written.
	 */
	private static @Nullable String unescape(String value) {
		if (value.indexOf('&') < 0) {
			return value;
		}
		StringBuilder out = new StringBuilder();
		int i = 0;
		while (i < value.length()) {
			char c = value.charAt(i);
			if (c != '&') {
				out.append(c);
				i++;
				continue;
			}
			int semi = value.indexOf(';', i);
			if (semi < 0) {
				return null;
			}
			String character = character(value.substring(i + 1, semi));
			if (character == null) {
				return null;
			}
			out.append(character);
			i = semi + 1;
		}
		return out.toString();
	}

	/** One character reference without its {@code &} and {@code ;}, or null when it is not one this reads. */
	private static @Nullable String character(String reference) {
		try {
			if (reference.startsWith("#x") || reference.startsWith("#X")) {
				return Character.toString(Integer.parseInt(reference.substring(2), 16));
			}
			if (reference.startsWith("#")) {
				return Character.toString(Integer.parseInt(reference.substring(1)));
			}
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
		return switch (reference) {
			case "amp" -> "&";
			case "quot" -> "\"";
			case "apos" -> "'";
			case "lt" -> "<";
			case "gt" -> ">";
			case "period" -> ".";
			case "sol" -> "/";
			case "percnt" -> "%";
			default -> null;
		};
	}

	/** Where the next tag of this name starts, in any case, or -1. */
	private static int indexOfTag(String html, int from, String tag) {
		int after = tag.length() + 1;
		for (int i = html.indexOf('<', from); i >= 0 && i + after <= html.length(); i = html.indexOf('<', i + 1)) {
			if (html.regionMatches(true, i + 1, tag, 0, tag.length()) && (i + after == html.length()
					|| Character.isWhitespace(html.charAt(i + after)) || "/>".indexOf(html.charAt(i + after)) >= 0)) {
				return i;
			}
		}
		return -1;
	}

	private static int skipSpace(String html, int from) {
		int i = from;
		while (i < html.length() && Character.isWhitespace(html.charAt(i))) {
			i++;
		}
		return i;
	}

	/**
	 * The first Markdown image, when it is the first picture in the body, or null. An {@code <img>}
	 * written as raw HTML before it is the first picture instead.
	 */
	private static @Nullable Image firstImage(Node document) {
		class First extends AbstractVisitor {

			boolean seen;

			@Nullable Image image;

			@Override
			public void visit(Image node) {
				if (!seen) {
					seen = true;
					image = node;
				}
			}

			@Override
			public void visit(HtmlInline node) {
				seen |= rawImage(node.getLiteral());
			}

			@Override
			public void visit(HtmlBlock node) {
				seen |= rawImage(node.getLiteral());
			}

		}
		First first = new First();
		document.accept(first);
		return first.image;
	}

	/** True when raw HTML shows an {@code <img>}: one inside a comment does not count. */
	private static boolean rawImage(String html) {
		return RAW_IMG.matcher(COMMENT.matcher(html).replaceAll("")).find();
	}

	private String firstParagraph(Node document) {
		for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
			if (node instanceof Paragraph) {
				return plain(node).replaceAll("\\s+", " ").strip();
			}
		}
		return "";
	}

	/**
	 * The plain text before the first {@code <!-- truncate -->} comment, or empty when there is none.
	 * One inside code, or written as code, does not count.
	 */
	private static String beforeTruncate(Node document) {
		StringBuilder out = new StringBuilder();
		boolean truncated = false;
		for (Node node = document.getFirstChild(); node != null && !truncated; node = node.getNext()) {
			if (endsSummary(node)) {
				truncated = true;
			}
			else if (node instanceof Paragraph paragraph) {
				// The comment can sit inside a paragraph, between its words.
				for (Node inline = paragraph.getFirstChild(); inline != null; inline = inline.getNext()) {
					if (endsSummary(inline)) {
						truncated = true;
						break;
					}
					plainText(inline, out);
				}
				out.append(' ');
			}
			else {
				plainText(node, out);
				out.append(' ');
			}
		}
		return truncated ? out.toString().replaceAll("\\s+", " ").strip() : "";
	}

	/** True when raw HTML is the comment that ends a summary: {@code <!-- truncate -->}. */
	private static boolean endsSummary(Node node) {
		if (node instanceof HtmlBlock block) {
			return TRUNCATE.matcher(block.getLiteral()).find();
		}
		if (node instanceof HtmlInline inline) {
			return TRUNCATE.matcher(inline.getLiteral()).find();
		}
		return false;
	}

	/** The text a reader sees: literal text and inline code, with line breaks as spaces. */
	private static String plain(Node node) {
		StringBuilder out = new StringBuilder();
		plainText(node, out);
		return out.toString().strip();
	}

	private static void plainText(Node node, StringBuilder out) {
		node.accept(new AbstractVisitor() {
			@Override
			public void visit(Text text) {
				out.append(text.getLiteral());
			}

			@Override
			public void visit(Code code) {
				out.append(code.getLiteral());
			}

			@Override
			public void visit(SoftLineBreak softLineBreak) {
				out.append(' ');
			}

			@Override
			public void visit(HardLineBreak hardLineBreak) {
				out.append(' ');
			}
		});
	}

	private static String unique(String base, Map<String, Integer> used, Set<String> taken) {
		int seen = used.merge(base, 1, Integer::sum);
		String id = seen == 1 ? base : base + "-" + seen;
		while (!taken.add(id)) {
			seen = used.merge(base, 1, Integer::sum);
			id = base + "-" + seen;
		}
		return id;
	}

	private static int countWords(String text) {
		String trimmed = text.strip();
		return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
	}

}
