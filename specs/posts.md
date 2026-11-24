# Spec: posts

Goals 1 and 4 in PRODUCT.md: posts come from Markdown, and drafts and scheduled posts stay hidden.

## Why

A blog needs posts before it needs pages. Today Beansprout reads nothing. This spec turns a folder
of Markdown files into posts that every page, feed and the MCP server can share.

## Requirements

| ID | Requirement |
| --- | --- |
| POSTS-1 | When a `.md` file has `title` and `date` frontmatter, the system shall publish it as a post. |
| POSTS-2 | The system shall render a post's body from Markdown to HTML, including GitHub-style tables. |
| POSTS-3 | The system shall take a post's slug from its `slug` frontmatter, or else from its file name without `.md`. |
| POSTS-4 | The system shall turn a slug into lowercase letters and digits joined by single dashes, with accents removed. |
| POSTS-5 | If a file has no frontmatter, no `title`, or a `date` it cannot read, then the system shall skip the file and log a warning that names it. |
| POSTS-6 | If two posts have the same slug, then the system shall keep the one whose path sorts first and log a warning that names both files. |
| POSTS-7 | If a slug is empty or is a route word (`page`, `tags`, `archive`), then the system shall skip the file and log a warning that names it. |
| POSTS-8 | The system shall read `tags` written as `[a, b]` or as one `- item` per line. |
| POSTS-9 | The system shall list published posts newest first. |
| POSTS-10 | When a post has no `summary`, the system shall use the text of its first paragraph, cut at a word to at most 200 characters. |
| POSTS-11 | The system shall give each post a reading time of its word count divided by 200, rounded up, and at least one minute. |
| POSTS-12 | The system shall give each `##` heading an id made from its text, and list those headings in order. |
| POSTS-13 | The system shall read posts from `beansprout.posts.location`, which defaults to `classpath:posts/`, including subfolders. |
| POSTS-14 | When `beansprout.posts.reload` is true, the system shall show an added, changed or removed file on the next request, with no restart. |
| POSTS-15 | When a `date` has no time, the system shall publish the post at the start of that day in `beansprout.time-zone`, which defaults to the server's zone. |
| POSTS-16 | If a post has `draft: true`, then the system shall not list it or find it by slug. |
| POSTS-17 | If a post's date is in the future, then the system shall hide it until that moment, with no restart and no reload. |
| POSTS-18 | If `draft` is set to anything but `true` or `false`, then the system shall skip the file and log a warning that names it. |
| POSTS-19 | When a post has no `summary` and no `description`, and its body holds a `<!-- truncate -->` comment, the system shall use the text before that comment, cut at a word to at most 200 characters, as its summary. |

## Frontmatter

```markdown
---
title: Hello, Beansprout
date: 2026-11-16            # or 2026-11-16T09:30, or 2026-11-16T09:30:00Z
tags: [spring, java]        # or one "- tag" per line
summary: One line for cards and feeds.
slug: hello                 # optional
draft: false                # optional
---
```

Values may be wrapped in single or double quotes. Unknown keys are ignored.

## Out of scope

- An `updated` date, authors, series, cover images. Longhand can show them, they come later.
- Reading posts from a database or over HTTP.
- Watching the folder for changes in the background. Reload checks on the next request.
- Full YAML. Only the shapes above are read.
