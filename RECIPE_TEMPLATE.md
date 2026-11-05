# Recipe README template

Every `recipes/<name>/README.md` uses these headings in this order. Sections marked *optional* may be left out; all others are required. Replace each `<placeholder>` and delete the italic guidance lines.

## Voice

- The cooking theme lives in the headings and the `Serves` line. The text below them is plain and technical: no food words for technical things.
- Good: "**Keep each engine named `default`.** The web client calls `/configuration` without an engine name." Bad: "Season your engines carefully or the soufflé collapses."
- English, second person, present tense, short sentences. State facts, not hype.
- No badges, emoji, Contributing or License sections. The repository root covers those.

## Template

```markdown
# <name>

<The problem in one or two sentences, in the words someone would search for.> <What this recipe does about it, in one sentence.>

**Serves:** <who this is for, in one line>

![<what the screenshot shows>](images/<file>.png)

## Ingredients

| Component | Version |
| --- | --- |
| <engine> | <x.y.z> |
| Java | <java> |

*Tools to install, external systems and hosts entries. Say where versions are pinned.*

## How it works

*An architecture sketch or numbered flow, the modules or folders and what each one does, and links to the files that matter.*

## Cook it

*The shortest path from clone to seeing it work: commands, URLs to open, demo logins.*

## Taste test

*What the automated proof (`./e2e.sh` or the `build` from `recipe.yaml`) checks and how to run it.*

## Take it home

*What to copy into your own project, what to change, and a configuration reference.*

## Kitchen notes

*What bites you when you copy it. One bullet per gotcha: bold claim, then cause and fix. Name what is demo-only.*

## Variations

*Optional. Approaches this recipe did not take, and when they are the better choice.*
```

The H1 equals `name` in `recipe.yaml`, and the intro expands its `problem`. The engine and Java rows of Ingredients match `engine` and `java`.
