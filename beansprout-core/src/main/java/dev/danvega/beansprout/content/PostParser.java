package dev.danvega.beansprout.content;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.danvega.beansprout.code.CodeHighlighter;
import dev.danvega.beansprout.code.PrismHighlighter;
import org.jspecify.annotations.Nullable;

/** Turns one {@link MarkdownFile} into a {@link Post}, or says why it cannot. */
public final class PostParser {

	static final int SUMMARY_LIMIT = 200;

	static final int WORDS_PER_MINUTE = 200;

	/** A Jekyll name: a date, a dash, then the rest, like {@code 2026-03-14-hello}. */
	private static final Pattern DATED_NAME = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})-(.+)$");

	private final ZoneId zone;

	private final MarkdownRenderer markdown;

	/** The context path and the blog's path, like {@code /app/blog}, for images in a bundle's body. */
	private final String address;

	/** The blog's path, like {@code /blog}, for a bundle's picture. Pages add the context path. */
	private final String base;

	/** The app's context path, like {@code /app}, or empty, for links in a post's body. */
	private final String root;

	/**
	 * Creates a parser for one blog that highlights code with the built-in highlighter.
	 *
	 * @param zone the blog's time zone: a date with no time starts at midnight here
	 */
	public PostParser(ZoneId zone) {
		this(zone, new PrismHighlighter());
	}

	/**
	 * Creates a parser for one blog.
	 *
	 * @param zone the blog's time zone: a date with no time starts at midnight here
	 * @param highlighter highlights code fences, or {@link CodeHighlighter#NONE} for none
	 */
	public PostParser(ZoneId zone, CodeHighlighter highlighter) {
		this(zone, highlighter, "", "/blog");
	}

	/**
	 * Creates a parser for one blog that knows where the blog is served, so a post that is a folder
	 * can link the images next to it.
	 *
	 * @param zone the blog's time zone: a date with no time starts at midnight here
	 * @param highlighter highlights code fences, or {@link CodeHighlighter#NONE} for none
	 * @param root the app's context path: empty, or like {@code /app}
	 * @param base the blog's path in the app, like {@code /blog}
	 */
	public PostParser(ZoneId zone, CodeHighlighter highlighter, String root, String base) {
		this.zone = zone;
		this.markdown = new MarkdownRenderer(highlighter);
		this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
		this.root = root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
		this.address = this.root + this.base;
	}

	/**
	 * Reads one file.
	 *
	 * @throws InvalidPostException when the frontmatter is missing or broken, or has no title or
	 *     date the parser can read
	 */
	public Post parse(MarkdownFile file) {
		return parse(file, (folder, path) -> false);
	}

	/**
	 * Reads one file, checking a bundle's relative picture against the files next to it.
	 *
	 * @param exists says whether a file is really in a post's folder, given the folder and the path
	 * @throws InvalidPostException when the frontmatter is missing or broken, has no title or date
	 *     the parser can read, or names a picture that is not there
	 */
	public Post parse(MarkdownFile file, BiPredicate<String, String> exists) {
		return parse(file, exists, (folder, path) -> null);
	}

	/**
	 * Reads one file from where it came from: a bundle's relative picture is checked against the
	 * files next to it, and the size of each picture in its folder is read from the file's header.
	 *
	 * @throws InvalidPostException when the frontmatter is missing or broken, has no title or date
	 *     the parser can read, or names a picture that is not there
	 */
	public Post parse(MarkdownFile file, PostSource source) {
		return parse(file, source::contains, (folder, path) -> size(source, folder, path));
	}

	/** A picture's size, or null when it is not an image a bundle serves, or it cannot be read. */
	private static @Nullable ImageSize size(PostSource source, String folder, String path) {
		if (Bundles.imageType(path) == null) {
			return null;
		}
		try (InputStream in = source.open(folder, path)) {
			return in == null ? null : ImageSize.read(in);
		}
		catch (IOException | UncheckedIOException ex) {
			return null;
		}
	}

	private Post parse(MarkdownFile file, BiPredicate<String, String> exists,
			BiFunction<String, String, @Nullable ImageSize> size) {
		Frontmatter frontmatter = Frontmatter.parse(file.text());

		String title = frontmatter.text("title");
		if (title == null || title.isBlank()) {
			throw new InvalidPostException("it has no title. Add a line like "
					+ (frontmatter.toml() ? "title = \"My first post\"" : "title: My first post") + " to its frontmatter.");
		}

		// A Jekyll post's name starts with its date, like 2026-03-14-hello.md. The date is the
		// post's date when the frontmatter has none, and it is never part of the slug.
		String folder = Bundles.folder(file.path());
		String named = folder != null ? folder.substring(folder.lastIndexOf('/') + 1) : fileName(file.path());
		Matcher dated = DATED_NAME.matcher(named);
		boolean jekyll = dated.matches();

		String dateText = frontmatter.text("date");
		ZonedDateTime date;
		if (dateText != null) {
			date = date("date", dateText);
		}
		else if (jekyll) {
			date = nameDate(dated.group(1), frontmatter.toml());
		}
		else {
			throw new InvalidPostException("it has no date. Add a line like "
					+ (frontmatter.toml() ? "date = 2026-11-16" : "date: 2026-11-16") + " to its frontmatter.");
		}
		// lastmod is checked whenever it is there. updated wins when both are.
		String updatedText = frontmatter.text("updated");
		String lastmodText = frontmatter.text("lastmod");
		ZonedDateTime lastmod = lastmodText != null ? date("lastmod", lastmodText) : null;
		ZonedDateTime modified = updatedText != null ? date("updated", updatedText) : lastmod != null ? lastmod : date;
		boolean draft = flag("draft", frontmatter.raw("draft"));
		boolean unlisted = flag("unlisted", frontmatter.raw("unlisted"));

		// A slug: line that is there but empty gives an empty slug, which Posts skips. Only a
		// missing slug: line falls back to the file name.
		Object chosen = frontmatter.raw("slug");
		String slug = Slugs.slugify(chosen == null ? (jekyll ? dated.group(2) : named) : chosen instanceof String text ? text : "");

		// Jekyll's image: is the picture only as a single value. A list or a nested block is ignored.
		String pictureKey = "picture";
		String image = orEmpty(frontmatter.text("picture"));
		if (frontmatter.raw("picture") == null && frontmatter.raw("image") instanceof String single) {
			pictureKey = "image";
			image = single.strip();
		}
		String inFolder = folder != null && !image.isEmpty() ? Bundles.relative(image) : null;
		ImageSize imageSize = null;
		if (folder != null && inFolder != null) {
			if (!exists.test(folder, inFolder)) {
				throw new InvalidPostException("its " + pictureKey + " '" + image + "' is not in its folder " + folder
						+ ". Put the file there, or fix the name.");
			}
			image = base + "/" + slug + "/" + Bundles.encode(inFolder);
			imageSize = size.apply(folder, inFolder);
		}
		else if (!image.isEmpty() && !Pictures.valid(image)) {
			throw new InvalidPostException("its " + pictureKey + " '" + image
					+ "' is not a path starting with / or an http or https address.");
		}

		String images = address + "/" + slug + "/";
		String bundle = folder;
		MarkdownRenderer.Rendered rendered = markdown.render(frontmatter.body(), root, bundle == null ? null : images,
				!image.isEmpty(), path -> bundle == null ? null : size.apply(bundle, path));
		String canonical = frontmatter.raw("canonical") == null && frontmatter.raw("canonical_url") != null
				? canonical("canonical_url", frontmatter.text("canonical_url"))
				: canonical("canonical", frontmatter.text("canonical"));
		String summary = frontmatter.text("summary");
		if (summary == null) {
			summary = frontmatter.text("description");
		}
		String lede = summary != null ? summary.strip() : "";

		// With no written summary, the text before a <!-- truncate --> comment stands in, and with
		// no comment, the first paragraph does.
		String inferred = rendered.beforeTruncate();
		if (inferred.isEmpty()) {
			inferred = rendered.firstParagraph();
		}

		// Old paths for the post. A dated name that gave the slug keeps its old address too.
		List<String> aliases = new ArrayList<>();
		for (String key : List.of("aliases", "redirect_from")) {
			frontmatter.list(key).stream().map(String::strip).filter(alias -> !alias.isEmpty()).forEach(aliases::add);
		}
		if (jekyll && chosen == null) {
			String old = Slugs.slugify(named);
			if (!old.isEmpty() && !old.equals(slug)) {
				aliases.add(base + "/" + old);
			}
		}

		// A name only. A list of - items or a nested block, like jekyll-seo-tag's, is ignored.
		String author = frontmatter.raw("author") instanceof String name ? name.strip() : "";

		return new Post(slug, title.strip(), date, tags(frontmatter.list("tags")),
				lede.isEmpty() ? cut(inferred) : lede, rendered.html(),
				rendered.headings(), Math.max(1, (rendered.words() + WORDS_PER_MINUTE - 1) / WORDS_PER_MINUTE), draft,
				file.path(), image, orEmpty(frontmatter.text("picture-alt")), orEmpty(frontmatter.text("caption")), lede,
				modified, canonical, aliases, author, imageSize == null ? 0 : imageSize.width(),
				imageSize == null ? 0 : imageSize.height(), unlisted);
	}

	/**
	 * Empty for none. Otherwise an absolute http or https address with a host. The host is read as
	 * written, so a name with letters outside ASCII, like {@code bücher.de}, is kept.
	 */
	private static String canonical(String key, @Nullable String text) {
		if (text == null) {
			return "";
		}
		String value = text.strip();
		try {
			URI uri = new URI(value);
			String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
			String authority = uri.getRawAuthority();
			if ((scheme.equals("http") || scheme.equals("https")) && authority != null && !host(authority).isBlank()) {
				return value;
			}
		}
		catch (URISyntaxException ignored) {
			// not an address at all; the message below says what to write
		}
		throw new InvalidPostException("its " + key + " '" + value
				+ "' is not an absolute http or https address. Write it like https://dev.to/you/the-post.");
	}

	/** The date a Jekyll name starts with, like {@code 2026-03-14}, at the start of that day. */
	private ZonedDateTime nameDate(String text, boolean toml) {
		try {
			return LocalDate.parse(text).atStartOfDay(zone);
		}
		catch (DateTimeParseException ex) {
			throw new InvalidPostException("its name starts with " + text + ", which is not a day on the calendar. Fix the name, or add a line like "
					+ (toml ? "date = 2026-11-16" : "date: 2026-11-16") + " to its frontmatter.");
		}
	}

	private ZonedDateTime date(String key, String text) {
		String value = text.strip();
		try {
			return OffsetDateTime.parse(value).atZoneSameInstant(zone);
		}
		catch (DateTimeParseException ignored) {
			// not a date with an offset; try the next shape
		}
		try {
			return LocalDateTime.parse(value.replace(' ', 'T')).atZone(zone);
		}
		catch (DateTimeParseException ignored) {
			// not a date and time; try a plain date
		}
		try {
			return LocalDate.parse(value).atStartOfDay(zone);
		}
		catch (DateTimeParseException ex) {
			throw new InvalidPostException("its " + key + " '" + value + "' is not a date. Write it like 2026-11-16 or 2026-11-16T09:30.");
		}
	}

	/** Missing means false. Otherwise only the words true and false are read. */
	private static boolean flag(String key, @Nullable Object value) {
		if (value == null || "false".equals(value)) {
			return false;
		}
		if ("true".equals(value)) {
			return true;
		}
		String shown = value instanceof List<?> list && list.isEmpty() ? "" : String.valueOf(value);
		throw new InvalidPostException("its " + key + " value '" + shown + "' is not true or false.");
	}

	private static List<String> tags(List<String> written) {
		Set<String> seen = new LinkedHashSet<>();
		List<String> tags = new ArrayList<>();
		for (String tag : written) {
			String name = tag.strip();
			String slug = Slugs.slugify(name);
			if (!slug.isEmpty() && seen.add(slug)) {
				tags.add(name);
			}
		}
		return tags;
	}

	private static String orEmpty(@Nullable String value) {
		return value == null ? "" : value.strip();
	}

	/** The last part of a path, without {@code .md}. */
	private static String fileName(String path) {
		String name = path.substring(path.lastIndexOf('/') + 1);
		return name.endsWith(".md") ? name.substring(0, name.length() - 3) : name;
	}

	/** The host in an address's authority: without a user before {@code @} or a port after {@code :}. */
	private static String host(String authority) {
		String host = authority.substring(authority.lastIndexOf('@') + 1);
		if (host.startsWith("[")) {
			int close = host.indexOf(']');
			return close < 0 ? "" : host.substring(0, close + 1);
		}
		int colon = host.indexOf(':');
		return colon < 0 ? host : host.substring(0, colon);
	}

	/** At most {@value #SUMMARY_LIMIT} characters, cut at a word, with an ellipsis when cut. */
	static String cut(String text) {
		if (text.length() <= SUMMARY_LIMIT) {
			return text;
		}
		int end = text.lastIndexOf(' ', SUMMARY_LIMIT - 1);
		if (end <= 0) {
			end = SUMMARY_LIMIT - 1;
			if (Character.isHighSurrogate(text.charAt(end - 1))) {
				end--;
			}
		}
		String start = text.substring(0, end);
		return start.stripTrailing() + "…";
	}

}
