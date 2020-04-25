# Changes

## Breaking changes

## v0.0.79
- [clj-jdbc.curl#9](https://github.com/borkdude/babashka.curl/issues/9):
  BREAKING! Functions in `babashka.curl` like `get`, `post`, etc. now always
  return a map with `:status`, `:body`, and `:headers`.

## v0.0.71
- #267 Change behavior of reader conditionals: the `:clj` branch is taken when
  it occurs before a `:bb` branch.

## v0.0.44 - 0.0.45
- #173: Rename `*in*` to `*input*` (in the `user` namespace). The reason for
  this is that it shadowed `clojure.core/*in*` when used unqualified.

## v0.0.43
- #160: Add support for `java.lang.ProcessBuilder`. See docs. This replaces the
  `conch` namespace.
