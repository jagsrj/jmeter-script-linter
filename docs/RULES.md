# Rule catalogue

All rules can be switched off, re-graded or re-parameterized in your team rules file
(`RULE_ID.enabled`, `RULE_ID.severity`, `RULE_ID.<param>`). The full default file with every parameter is
[`jmx-lint-default.properties`](../src/main/resources/io/github/jagsrj/jmxlint/jmx-lint-default.properties).

| Rule | Category | Default severity | Checks that |
|---|---|---|---|
| `NAMING_TEST_PLAN` | Naming conventions | WARN | Test Plan name follows the naming convention |
| `NAMING_THREAD_GROUP` | Naming conventions | WARN | Thread Group names follow TGnn_BusinessFlow |
| `NAMING_TRANSACTION` | Naming conventions | WARN | Transaction Controller names follow Tnn_Action |
| `NAMING_SAMPLER` | Naming conventions | WARN | Sampler names are descriptive and contain only safe characters |
| `NAMING_DEFAULT_NAME` | Naming conventions | WARN | Elements are renamed from their JMeter default name |
| `NAMING_RECORDED_NAME` | Naming conventions | WARN | Samplers do not keep auto-recorded names (URL paths, '-123' suffixes) |
| `NAMING_VARIABLE` | Naming conventions | WARN | Variable names (extractors, CSV, User Defined Variables) follow the convention |
| `NAMING_DUPLICATE` | Naming conventions | WARN | Sampler and transaction names are unique (duplicates merge in reports) |
| `NAMING_UNSAFE_CHARS` | Naming conventions | WARN | Names have no characters that break CSV/JTL/HTML reports or leading/trailing spaces |
| `MEM_FUNCTIONAL_MODE` | Memory & resource usage | ERROR | Test Plan 'Functional Test Mode' is off (it stores every response) |
| `MEM_VIEW_RESULTS_TREE` | Memory & resource usage | ERROR | No enabled View Results Tree listener |
| `MEM_GUI_LISTENER` | Memory & resource usage | WARN | No in-memory GUI listeners (Summary/Aggregate/Graph/Table ...) in load scripts |
| `MEM_SAVE_RESPONSE_DATA` | Memory & resource usage | ERROR | Listeners do not save response / sampler data to results |
| `MEM_RESULT_SAVER` | Memory & resource usage | WARN | No 'Save Responses to a file' listener in load scripts |
| `MEM_DEBUG_ELEMENT` | Memory & resource usage | WARN | No enabled Debug Sampler / Debug PostProcessor |
| `MEM_BEANSHELL` | Memory & resource usage | WARN | No BeanShell elements or __BeanShell functions (use JSR223 + Groovy) |
| `MEM_JSR223_LANGUAGE` | Memory & resource usage | WARN | JSR223 elements use Groovy |
| `MEM_JSR223_NO_CACHE` | Memory & resource usage | WARN | Inline JSR223 scripts have 'Cache compiled script' enabled |
| `MEM_JSR223_INLINE_VARS` | Memory & resource usage | ERROR | JSR223 scripts do not use ${...} inside the script body |
| `MEM_SCRIPT_THREADS` | Memory & resource usage | WARN | Scripts do not start their own threads, executors or timers |
| `MEM_SCRIPT_UNCLOSED_RESOURCE` | Memory & resource usage | WARN | Scripts close files, readers, sockets and DB connections |
| `MEM_SCRIPT_GROWING_STATE` | Memory & resource usage | INFO | Scripts do not keep growing shared state (props, static collections) |
| `MEM_EMBEDDED_NO_CACHE` | Memory & resource usage | WARN | Embedded-resource download is paired with an HTTP Cache Manager |
| `MEM_EXTRACT_ALL_MATCHES` | Memory & resource usage | INFO | Extractors do not store all matches (-1) unless needed |
| `BP_INFINITE_LOOP` | Best practices | ERROR | Thread Groups with infinite loops have a duration (scheduler) set |
| `BP_RAMP_UP` | Best practices | WARN | Thread Groups with more than one thread have a ramp-up period |
| `BP_PARAMETERIZE_LOAD` | Best practices | INFO | Thread count, ramp-up and duration are parameterized with ${__P()} |
| `BP_THINK_TIME` | Best practices | WARN | Thread Groups include think time (timers or Flow Control Action pauses) |
| `BP_EMPTY_CONTAINER` | Best practices | WARN | Thread Groups and controllers contain at least one enabled sampler |
| `BP_MISSING_ASSERTION` | Best practices | WARN | Every sampler is validated by an assertion in scope |
| `BP_TRANSACTION_INCLUDE_TIMERS` | Best practices | WARN | Transaction times exclude timers and pre/post processors |
| `BP_SAMPLER_OUTSIDE_TRANSACTION` | Best practices | INFO | Samplers are grouped under Transaction Controllers |
| `BP_EXTRACTOR_DEFAULT` | Best practices | INFO | Extractors define a default value (e.g. NOT_FOUND) |
| `BP_IF_CONTROLLER_EXPRESSION` | Best practices | WARN | If Controllers use 'Interpret condition as variable expression' |
| `BP_CSV_ABSOLUTE_PATH` | Best practices | WARN | CSV Data Set files use relative or parameterized paths |
| `BP_DISABLED_ELEMENTS` | Best practices | INFO | No disabled elements left in the committed script |
| `BP_TEST_PLAN_DESCRIPTION` | Best practices | INFO | Test Plan has a description in Comments |
| `BP_HARDCODED_HOST` | Best practices | WARN | HTTP samplers do not hard-code the server name / URL |
| `BP_HTTP_DEFAULTS` | Best practices | INFO | Plans with HTTP samplers use HTTP Request Defaults |
| `BP_COOKIE_MANAGER` | Best practices | WARN | Thread Groups with HTTP samplers have an HTTP Cookie Manager |
| `BP_HTTP_TIMEOUTS` | Best practices | WARN | HTTP requests have connect and response timeouts |
| `BP_KEEPALIVE` | Best practices | WARN | HTTP samplers use KeepAlive |
| `BP_HTTP_IMPLEMENTATION` | Best practices | INFO | HTTP samplers use the HttpClient4 implementation |
| `BP_UNCORRELATED_VALUE` | Best practices | WARN | Dynamic values (session ids, CSRF tokens, view state) are correlated, not hard-coded |
| `SEC_HARDCODED_SECRET` | Security | ERROR | No hard-coded passwords, API keys or Authorization headers |

## Why the memory rules matter

| Rule | What leaks / grows |
|---|---|
| `MEM_JSR223_INLINE_VARS` | JMeter substitutes `${var}` into the script text *before* Groovy compiles it, so every distinct value produces a new script, a new compiled class and a new cache entry. Over a long soak test this fills Metaspace and the script cache until the load generator slows down or dies with `OutOfMemoryError: Metaspace`. |
| `MEM_JSR223_NO_CACHE` / `MEM_BEANSHELL` / `MEM_JSR223_LANGUAGE` | Scripts are re-parsed/re-compiled on every execution: CPU and short-lived garbage per iteration, Metaspace pressure. |
| `MEM_VIEW_RESULTS_TREE`, `MEM_GUI_LISTENER`, `MEM_SAVE_RESPONSE_DATA`, `MEM_FUNCTIONAL_MODE` | Results (and full response bodies) are accumulated in the JMeter heap for the whole run. |
| `MEM_SCRIPT_THREADS` | Threads, executors and `java.util.Timer`s started from a script outlive the sampler and pile up per iteration. |
| `MEM_SCRIPT_UNCLOSED_RESOURCE` | File handles, sockets and JDBC connections opened per iteration without `close()` exhaust file descriptors / pools. |
| `MEM_SCRIPT_GROWING_STATE` | `props` and static collections live for the whole test; per-iteration keys grow without bound. |
| `MEM_EXTRACT_ALL_MATCHES` | Match No. `-1` creates `var_1 ... var_N` variables for every match in every thread. |

The script rules use regular expressions over the script text, so they are heuristics: tune the `pattern` /
`mitigationPattern` parameters, or add `lint-ignore: RULE_ID` to an element's Comments when a finding is a
deliberate exception.
