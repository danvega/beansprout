# Start a new blog

This guide makes a new Spring Boot app that is nothing but a blog. If you already have an app,
skip to step 2.

You need JDK 25 or newer.

## 1. Create the app

Go to [start.spring.io](https://start.spring.io) and pick:

- Project: Maven
- Spring Boot: 4.1
- Java: 25
- Dependencies: Spring Web

Download it and unzip it.

## 2. Add Beansprout and pick a theme

Beansprout comes in two parts. The starter is the blog. The theme is how it looks.

First import the BOM, so every Beansprout part gets the same version. In your `pom.xml`, add
this after `</properties>`. If you already have a `<dependencyManagement>` section, add the
`<dependency>` inside it instead.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>dev.danvega</groupId>
            <artifactId>beansprout-bom</artifactId>
            <version>0.4.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Then add the starter and one theme to `<dependencies>`, with no version:

```xml
<dependency>
    <groupId>dev.danvega</groupId>
    <artifactId>beansprout-spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>dev.danvega</groupId>
    <artifactId>beansprout-theme-linocut</artifactId>
</dependency>
```

Pick the theme you like. Each one is free.

| Theme | Add this artifact | Look |
| --- | --- | --- |
| Longhand | `beansprout-theme-longhand` | One writer, a long post with a picture. Light and dark. |
| Dateline | `beansprout-theme-dateline` | An engineering blog dressed as a status board. Dark. |
| Linocut | `beansprout-theme-linocut` | Bold and playful, black ink on warm paper with one red. |
| Marginalia | `beansprout-theme-marginalia` | A link blog kept like a notebook. Short entries. |
| Small Machines | `beansprout-theme-small-machines` | A lot of personality. The site is dressed as an old desktop. |
| Plainsong | `beansprout-theme-plainsong` | A plain list of posts in black and white, no pictures. Light and dark. |
| Onionskin | `beansprout-theme-onionskin` | Every post typed like a memo, in monospace. Light and dark. |
| Calling Card | `beansprout-theme-calling-card` | You first: your name in poster-sized type, your photo, your topics. Light and dark. |

To change your mind later, swap the artifact and restart. Keep one theme in your `pom.xml`. If
you want several on hand, name the one to use in `application.yaml` with `beansprout.theme`,
such as `beansprout.theme: dateline`. With two themes and no name, the app stops at startup
and tells you which themes it found.

With Gradle:

```kotlin
implementation(platform("dev.danvega:beansprout-bom:0.4.0"))
implementation("dev.danvega:beansprout-spring-boot-starter")
implementation("dev.danvega:beansprout-theme-linocut")
```

## 3. Name your blog

In `src/main/resources/application.yaml`:

```yaml
server:
  port: ${PORT:8080}       # most hosts, Railway included, tell the app its port this way

beansprout:
  title: My Blog
  description: Notes on Java and Spring.
  author: Your Name
```

The README lists every setting.

## 4. Write a post

Create `src/main/resources/posts/hello.md`:

```markdown
---
title: Hello, world
date: 2026-11-16
tags: [java, spring boot]
---
My first post.
```

Any `.md` file under `posts/` is a post, in any subfolder. The file name becomes the address:
`hello.md` is served at `/blog/hello`.

## 5. Run it

```bash
./mvnw spring-boot:run
```

Open `http://localhost:8080/blog`.

## Writing posts

| You want | Add to the frontmatter |
| --- | --- |
| A different address | `slug: my-address` |
| A line under the title, and on the card | `summary: One sentence.` |
| A picture on the card and the post | `picture: /images/hello.png`, or a file in the post's folder, and `picture-alt: What it shows` |
| To say you changed it later | `updated: 2026-12-02` |
| A line under the picture | `caption: Taken in Ljubljana.` |
| To keep it hidden | `draft: true` |
| A page only people with the link find | `unlisted: true` |
| To publish it later | a date in the future, like `date: 2026-12-01T09:00` |

No `summary`? Write the post's opening, then put `<!-- truncate -->` on its own line: the text before
the comment is the summary. Without the comment, the first paragraph is.

## Posts with pictures

A post can be a folder instead of a file. Put `index.md` in a folder, and put the post's pictures
next to it:

```
posts/
  hello.md                  /blog/hello
  welcome/                  /blog/welcome
    index.md
    cover.png
    shots/editor.png
```

The folder's name is the address, unless the post sets `slug`. Link a picture by its path in the
folder, with no `/` in front:

```markdown
---
title: Welcome
date: 2026-11-17
picture: cover.png
picture-alt: The new editor
---
![The editor](shots/editor.png)
```

Beansprout serves `cover.png` at `/blog/welcome/cover.png`. It serves PNG, JPEG, GIF, WebP and
AVIF files, and only while the post is published. Other Markdown files in the folder are not
posts, so notes can live there too. Delete the folder and the post and its pictures go together.

If your app has its own route under a post, such as `/blog/{slug}/og.png` for a share card, that
route wins over a file with the same name in the folder.

Plain `.md` files keep working, so mix the two however you like. Start with files, and use a
folder when a post needs pictures. This needs Beansprout 0.2.0 or newer.

You can also put pictures in `src/main/resources/static/images/`. Spring Boot serves that folder,
so `static/images/hello.png` is `/images/hello.png`. Link those with a `/` in front.

A post's picture is also its preview when someone shares the link. For that, use a PNG or JPEG
of 1200 by 630 pixels. Social sites do not show SVG previews.

## Code

Code fences are highlighted on the server, so readers download no JavaScript. Name the language
after the backticks:

````markdown
```java
record Bean(String name) {}
```
````

Java, Kotlin, XML, HTML, YAML, properties, JSON, SQL, shell, JavaScript and TypeScript are
highlighted, and short names like `yml`, `sh` and `ts` work too. Any other language is shown as
plain code.

The colors are GitHub's, light or dark to match the reader's system. They come from
`/beansprout/code.css`, which any theme can link. To change a color, set its custom property,
such as `--bs-code-keyword`, after that file. Highlighting uses Prism's class names, so a Prism
theme works too. To turn it off, set `beansprout.code.highlight: false`.

## Light and dark

Longhand, Plainsong, Onionskin and Calling Card follow the reader's system: a light page by day,
a dark one at night, with no JavaScript and nothing to set. Dateline is always dark. Linocut, Marginalia and Small Machines
are always light. Every theme keeps its text readable, at a contrast of at least 4.5 to 1.

## What search engines see

Every page gets a description, a canonical link and Open Graph tags, so a shared link shows a
title, a summary and a picture. Each post also gets structured data (`BlogPosting`) for Google.
You do not have to do anything, but three things make it better:

- Write a `summary`. It is the description in search results.
- Set `updated` when you change a post. Search engines trust a date you set, not a file's age.
- Set `beansprout.site-url` in production, such as `https://blog.example.com`. Links in the
  tags, the feed and the sitemap use it.

Beansprout also serves a robots.txt, and lets crawlers skip pages that have not changed. To keep
AI training crawlers out of the blog, see [Search engines and AI crawlers](search-engines.md).

To put your photo above the blog's name, add `photo: /images/me.png` under `beansprout:`.

## Your own HTML on every page

Analytics, a newsletter form and comments each need a few lines of HTML. Put them in files under
`src/main/resources/beansprout/`, and every theme adds them for you:

| File | Where it goes |
| --- | --- |
| `head.html` | At the end of every page's `<head>`, such as an analytics script |
| `after-post.html` | After the body of every post, such as a signup form or comments |
| `footer.html` | In every page's footer |

Leave out any file you don't need. Each file is your own HTML, so Beansprout writes it as it is,
without escaping it. Save each one as UTF-8. The files are read once, when the app starts, so a
change needs a restart. The static export writes them into every page too.

## See edits without a restart

Posts are read once at startup. While you write, read them straight from the folder on disk
and re-read them on every request:

```yaml
beansprout:
  posts:
    location: file:src/main/resources/posts/
    reload: true
```

Keep this for development only. In production, leave both settings out.

## Virtual threads

Beansprout works with virtual threads. To run your app's requests on them, add this to
`application.yaml`:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

It is a setting for the whole app, so Beansprout leaves it to you. Blog pages do no slow I/O.
Posts are read once and kept in memory, unless `reload` is on for development. Virtual threads
help most on pages that wait for a database or another service.

## Next

[Deploy it](deploy.md).
