# Railroad quality Gradle plugin

A standalone plugin containing Railroad's syntax-aware Java formatter, Spotless
profiles for Java/CSS/Groovy/Gradle, and searchable public API Javadoc coverage.
It has no dependency on the Railroad application and lives in its own repository,
including its Gradle wrapper and LICENSE.

## Use from Railroad Maven

In your other project's `settings.gradle`:

```groovy
pluginManagement {
    repositories {
        maven { url = uri('https://maven.railroadide.dev/releases') }
        gradlePluginPortal()
    }
}
```

In its `build.gradle`:

```groovy
plugins {
    id 'dev.railroadide.quality' version '1.0.0'
}
repositories {
    mavenCentral() // Spotless downloads its formatter engines here.
}
```

For Kotlin DSL, use `maven { url = uri("https://maven.railroadide.dev/releases") }`
in `pluginManagement.repositories` and `id("dev.railroadide.quality") version "1.0.0"` in `plugins`.
Apply the plugin in each Java subproject that needs these tools.

## Tasks

| Task | Behavior |
| --- | --- |
| `format` | Applies semantic rules, then Spotless formatting |
| `formatCheck` | Checks both without changing sources |
| `semanticFormat` / `semanticFormatCheck` | Runs only syntax-aware Java rules |
| `javadocCoverage` | Generates `build/reports/javadoc-coverage/index.html` and `violations.txt` |
| `javadocCoverageCheck` | Generates the report and fails on incomplete public API documentation |

Java targets come from all Java source sets, including custom source sets.
Coverage defaults to main Java sources and parses without compiling or resolving
application dependencies. It counts public types, constructors, methods and
constants; descriptions, parameter/type parameter tags, record component tags,
and return tags must be nonempty. Annotated overrides are excluded. The HTML
embeds its assets and can be opened offline.

Formatting covers the entire project by default, including projects without Git.
To use Railroad's changed-file behavior:

```groovy
railroadQuality {
    formatAll = false
    ratchetFrom = 'HEAD' // Or a CI merge base
}
```

`-PformatAll` always overrides ratcheting; `-PformatRatchetFrom=<revision>`
sets the default revision when ratcheting is enabled.

## Configuration

```groovy
railroadQuality {
    toolJavaVersion = 25
    languageVersion = 17
    checkJavadoc = true // Attach strict coverage to check (default: false)
    // Replace, rather than append to, default source selections:
    javaSources.setFrom(sourceSets.main.allJava, sourceSets.test.allJava)
    coverageSources.setFrom(sourceSets.main.allJava)
    reportDirectory = layout.buildDirectory.dir('reports/api-docs')
}
```

`languageVersion` defaults to the Java toolchain language version, or Java
`sourceCompatibility` when no toolchain is configured. The tool JDK must be at
least 21 and support the chosen language release. It defaults to 25, independently
of the application's compilation JDK. Semantic transformations respect the source
release: `var` requires Java 10; unnamed variables require Java 22.

Semantic rules include terminal control-flow brace removal, braces around
nonterminal bodies, exact non-collection constructor locals rewritten to `var`, safe shortening
of qualified type names, and explicit visibility checks. `format` reports missing
access modifiers without changing visibility; `formatCheck` fails on them.
CSS uses Prettier and needs Node.js/npm; Gradle templates outside `src` are excluded.
The plugin adds no dependencies to application configurations.

`var` is allowed only for `new Foo(...)` and class-qualified members such as
`Foo.create()` or `Foo.VALUE`. Lists, sets, maps, queues and other collection
types (including subclasses, iterables and iterators) always require an explicit
type, even when initialized by a constructor or static factory. `format` replaces
disallowed `var` with the compiler-inferred type, preserving generic arguments
and adding imports. `formatCheck` rejects these declarations without writing.
If dependencies are unresolved or an inferred type cannot be expressed in Java,
the formatter reports the declaration for manual correction instead of guessing.

Collection locals and private fields prefer their interfaces: `ArrayList` becomes
`List`, `HashSet` becomes `Set`, and `HashMap` becomes `Map`. Sorted, concurrent,
and queue collections use interfaces such as `NavigableMap`, `ConcurrentMap`,
and `Deque`. Generic arguments and constructors are preserved. Concrete types
remain when implementation-specific methods, overloads, assignments, or return
types require them. Public API types and record components are left unchanged.

Redundant constructor type arguments are replaced with `<>` when javac confirms
the same inferred arguments, for example `ArrayList<TypeCoverage> classes = new
ArrayList<>();`. Explicit arguments are preserved in `var` initializers and when
removing them could change inference, annotations, or comments.

Completed blocks are separated from following code by a blank line. Consecutive
closing braces stay on adjacent lines without a blank line between them.
The same separation applies after `break`, `continue`, `return`, `throw`, and
`yield` statements when more code or another switch case follows, including
labeled jumps. It does not add a blank line before a closing brace or between
an unbraced body and its `else` or do-`while` continuation.
Continuation clauses (`else`, `catch`, `finally`, and do-`while`) and closing
punctuation such as `});` stay attached to their constructs.
Javadoc comments use the multiline `/**`, ` * description`, ` */` form, even
when the description fits on one line, with indentation matching the declaration.

Requires Gradle 9.1+ and a Gradle daemon running JDK 21+. Building this plugin
requires JDK 25. Gradle configuration cache is not currently supported.

## Build, test and publish

From this directory:

```sh
./gradlew test validatePlugins
./gradlew publish
```

Publishing requires `RAILROAD_MAVEN_USERNAME` and `RAILROAD_MAVEN_PASSWORD`.
The plugin marker and implementation (including sources) are published together
to `https://maven.railroadide.dev/releases`. This plugin has not been published to
the Gradle Plugin Portal.

For local development, run `./gradlew publishToMavenLocal` and use:

```groovy
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}
```

Then use `id 'dev.railroadide.quality' version '1.0.0'` in the consumer build.

The extracted code retains Railroad's GPL-3.0-or-later license; see LICENSE.
