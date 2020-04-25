<!-- [![CircleCI](https://circleci.com/gh/borkdude/clj-jdbc/tree/master.svg?style=shield)](https://circleci.com/gh/borkdude/clj-jdbc/tree/master)
[![project chat](https://img.shields.io/badge/slack-join_chat-brightgreen.svg)](https://app.slack.com/client/T03RZGPFR/CLX41ASCS)
[![Financial Contributors on Open Collective](https://opencollective.com/clj-jdbc/all/badge.svg?label=financial+contributors)](https://opencollective.com/clj-jdbc) [![Clojars Project](https://img.shields.io/clojars/v/borkdude/clj-jdbc.svg)](https://clojars.org/borkdude/clj-jdbc) -->

## Introduction

Hypothetical command line tool around Clojure database interaction via JDBC.

Supported databases:

 - PostgresQL
 - HSQLDB

Example:

`jdbc.clj`:

``` clojure
(require '[next.jdbc :as jdbc])

(defn query []
  (let [db {:dbtype "hsqldb"
            :dbname "mem:testdb"}]
    (jdbc/execute!
     db
     ["create table foo ( x int )"])
    (jdbc/execute!
     db
     ["insert into foo values ( 6 )"])
    (jdbc/execute!
     db
     ["select * from foo"])))

(println (query))
```

``` shell
$ clj-jdbc jdbc.clj
[{:FOO/X 6}]
```

## Installation

Currently there is one SNAPSHOT release available for macOS [here](https://github.com/borkdude/clj-jdbc/releases/tag/0.0.1-SNAPSHOT). The other way of installing this is building yourself via `script/compile`.

## Usage

``` shellsession
Usage: clj-jdbc [--verbose]
          [ ( --classpath | -cp ) <cp> ]
          [ ( --main | -m ) <main-namespace> | -e <expression> | -f <file> |
            --repl | --socket-repl [<host>:]<port> | --nrepl-server [<host>:]<port> ]
          [ arg* ]

Options:

  --help, -h or -?    Print this help text.
  --version           Print the current version of clj-jdbc.
  --verbose           Print entire stacktrace in case of exception.

  -e, --eval <expr>   Evaluate an expression.
  -f, --file <path>   Evaluate a file.
  -cp, --classpath    Classpath to use.
  -m, --main <ns>     Call the -main function from namespace with args.
  --repl              Start REPL. Use rlwrap for history.
  --socket-repl       Start socket REPL. Specify port (e.g. 1666) or host and port separated by colon (e.g. 127.0.0.1:1666).
  --nrepl-server      Start nREPL server. Specify port (e.g. 1667) or host and port separated by colon (e.g. 127.0.0.1:1667).
  --                  Stop parsing args and pass everything after -- to *command-line-args*

If neither -e, -f, or --socket-repl are specified, then the first argument that is not parsed as a option is treated as a file if it exists, or as an expression otherwise.
Everything after that is bound to *command-line-args*.
```

The `clojure.core` functions are accessible without a namespace alias.

The following namespaces are required by default and available through the
pre-defined aliases in the `user` namespace. You may use `require` + `:as`
and/or `:refer` on these namespaces. If not all vars are available, they are
enumerated explicitly.

- `clojure.string` aliased as `str`
- `clojure.set` aliased as `set`
- `clojure.edn` aliased as `edn`:
  - `read-string`
- `clojure.java.shell` aliased as `shell`
- `clojure.java.io` aliased as `io`:
  - `as-relative-path`, `as-url`, `copy`, `delete-file`, `file`, `input-stream`,
    `make-parents`, `output-stream`, `reader`, `resource`, `writer`
- `clojure.main`: `repl`
- [`clojure.core.async`](https://clojure.github.io/core.async/) aliased as
  `async`.
- `clojure.stacktrace`
- `clojure.test`
- `clojure.pprint`: `pprint` (currently backed by [fipp](https://github.com/brandonbloom/fipp)'s  `fipp.edn/pprint`)
- [`next.jdbc`](https://github.com/seancorfield/next-jdbc) aliased as `jdbc`

A selection of java classes are available, see `clj-jdbc/impl/classes.clj`.

Clj-jdbc supports `import`: `(import clojure.lang.ExceptionInfo)`.

Clj-jdbc supports a subset of the `ns` form where you may use `:require` and `:import`:

## License

Copyright © 2019-2020 Michiel Borkent

Distributed under the EPL License. See LICENSE.

This project contains code from:
- Clojure, which is licensed under the same EPL License.

