# JMX Lint report

## `examples/bad-plan.jmx`

**7** errors, **42** warnings, **9** info

| Severity | Rule | Element | Issue | Fix |
|---|---|---|---|---|
| ERROR | MEM_FUNCTIONAL_MODE | Test Plan | Functional Test Mode is enabled: every request and response body is saved | Untick 'Functional Test Mode' on the Test Plan before running load. |
| ERROR | MEM_JSR223_INLINE_VARS | Test Plan / Thread Group / /login-12 / JSR223 PostProcessor | Script body contains ${auth.token}: each distinct value compiles a new class (Metaspace leak) and breaks caching | Pass values via the Parameters field or read them with vars.get("name") / props.get("name"). For Groovy GStrings use $name, not ${name}. |
| ERROR | MEM_SAVE_RESPONSE_DATA | Test Plan / View Results Tree | Listener saves full response data for every sample | Untick 'Save Response Data (XML)' in the listener's Configure dialog. |
| ERROR | MEM_VIEW_RESULTS_TREE | Test Plan / View Results Tree | View Results Tree keeps full request/response data in memory | Disable or delete it before load tests; use it only while debugging with 1 user. |
| ERROR | BP_INFINITE_LOOP | Test Plan / Thread Group | Loop Count is Infinite and no Duration is set: the test never stops on its own | Tick 'Specify Thread lifetime' and set Duration, e.g. ${__P(duration,3600)}. |
| ERROR | SEC_HARDCODED_SECRET | Test Plan / Thread Group / /login-12 | 'password' has a literal value | Parameterize secrets (CSV Data Set, ${__P(...)}, ${__env(...)}) and keep them out of git. |
| ERROR | SEC_HARDCODED_SECRET | Test Plan / Thread Group / Search, Results / HTTP Request / HTTP Header Manager | 'Authorization' has a literal value | Parameterize secrets (CSV Data Set, ${__P(...)}, ${__env(...)}) and keep them out of git. |
| WARN | NAMING_DEFAULT_NAME | Test Plan | Element still has its default name 'Test Plan' | Give it a meaningful business name so results and logs are readable. |
| WARN | NAMING_DEFAULT_NAME | Test Plan / Thread Group | Element still has its default name 'Thread Group' | Give it a meaningful business name so results and logs are readable. |
| WARN | NAMING_DEFAULT_NAME | Test Plan / Thread Group / /login-12 / JSR223 PostProcessor | Element still has its default name 'JSR223 PostProcessor' | Give it a meaningful business name so results and logs are readable. |
| WARN | NAMING_DEFAULT_NAME | Test Plan / Thread Group / /login-12 / Regular Expression Extractor | Element still has its default name 'Regular Expression Extractor' | Give it a meaningful business name so results and logs are readable. |
| WARN | NAMING_DEFAULT_NAME | Test Plan / Thread Group / BeanShell Sampler | Element still has its default name 'BeanShell Sampler' | Give it a meaningful business name so results and logs are readable. |
| WARN | NAMING_DEFAULT_NAME | Test Plan / Thread Group / Debug Sampler | Element still has its default name 'Debug Sampler' | Give it a meaningful business name so results and logs are readable. |
| WARN | NAMING_DEFAULT_NAME | Test Plan / Thread Group / If Controller | Element still has its default name 'If Controller' | Give it a meaningful business name so results and logs are readable. |
| WARN | NAMING_DEFAULT_NAME | Test Plan / Thread Group / Search, Results / HTTP Request | Element still has its default name 'HTTP Request' | Give it a meaningful business name so results and logs are readable. |
| WARN | NAMING_DEFAULT_NAME | Test Plan / Thread Group / Search, Results / HTTP Request | Element still has its default name 'HTTP Request' | Give it a meaningful business name so results and logs are readable. |
| WARN | NAMING_DUPLICATE | Test Plan / Thread Group / Search, Results / HTTP Request | Name 'HTTP Request' is also used by Test Plan / Thread Group / Search, Results / HTTP Request | Make names unique so response times are not merged in reports. |
| WARN | NAMING_RECORDED_NAME | Test Plan / Thread Group / /login-12 | Sampler name '/login-12' looks auto-recorded | Rename it to describe the business step, e.g. '02_POST_Login'. |
| WARN | NAMING_SAMPLER | Test Plan / Thread Group / /login-12 | Name '/login-12' does not match the convention ^[A-Za-z0-9][A-Za-z0-9 _.\-]*$ | Rename it following the convention, e.g. '01_GET_HomePage'. |
| WARN | NAMING_THREAD_GROUP | Test Plan / Thread Group | Name 'Thread Group' does not match the convention ^(TG\d{2}\|SETUP\|TEARDOWN)_[A-Za-z][A-Za-z0-9_]*$ | Rename it following the convention, e.g. 'TG01_AccountOpening'. |
| WARN | NAMING_TRANSACTION | Test Plan / Thread Group / Search, Results | Name 'Search, Results' does not match the convention ^(?:[A-Za-z0-9]+_)?T\d{2,3}_[A-Za-z][A-Za-z0-9_]*$ | Rename it following the convention, e.g. 'T01_LaunchHomePage'. |
| WARN | NAMING_UNSAFE_CHARS | Test Plan / Thread Group / Search, Results | Name 'Search, Results' contains the character ',' | Use only letters, digits, '_', '-', '.' and spaces. |
| WARN | NAMING_VARIABLE | Test Plan | Variable name 'base-url' does not match ^[A-Za-z_][A-Za-z0-9_]*$ | Use letters, digits and underscores only (e.g. camelCase or UPPER_SNAKE). |
| WARN | NAMING_VARIABLE | Test Plan / Thread Group / /login-12 / Regular Expression Extractor | Variable name 'auth.token' does not match ^[A-Za-z_][A-Za-z0-9_]*$ | Use letters, digits and underscores only (e.g. camelCase or UPPER_SNAKE). |
| WARN | NAMING_VARIABLE | Test Plan / Thread Group / Users CSV | Variable name 'user-name' does not match ^[A-Za-z_][A-Za-z0-9_]*$ | Use letters, digits and underscores only (e.g. camelCase or UPPER_SNAKE). |
| WARN | NAMING_VARIABLE | Test Plan / Thread Group / Users CSV | Variable name 'pass word' does not match ^[A-Za-z_][A-Za-z0-9_]*$ | Use letters, digits and underscores only (e.g. camelCase or UPPER_SNAKE). |
| WARN | MEM_BEANSHELL | Test Plan / Thread Group / BeanShell Sampler | BeanShell is interpreted on every call and scales poorly under load | Replace with the matching JSR223 element using Groovy (script compilation cached). |
| WARN | MEM_DEBUG_ELEMENT | Test Plan / Thread Group / Debug Sampler | DebugSampler dumps all variables/properties on every iteration | Disable debug elements before committing the script. |
| WARN | MEM_EMBEDDED_NO_CACHE | Test Plan / Thread Group / /login-12 | 'Retrieve All Embedded Resources' is on without an HTTP Cache Manager in scope | Add an HTTP Cache Manager so static resources are cached like a real browser. |
| WARN | MEM_GUI_LISTENER | Test Plan / Aggregate Report | GUI listener 'StatVisualizer' aggregates results in JMeter's heap | Run in CLI mode with -l results.jtl and build the HTML report (-e -o) or use a Backend Listener (InfluxDB/Grafana). |
| WARN | MEM_JSR223_NO_CACHE | Test Plan / Thread Group / /login-12 / JSR223 PostProcessor | Script is recompiled on every execution (cache disabled) | Tick 'Cache compiled script if available'. Recompiling under load burns CPU and fills Metaspace. |
| WARN | MEM_SCRIPT_THREADS | Test Plan / Thread Group / /login-12 / JSR223 PostProcessor | Script starts threads/executors that may outlive the sampler (found 'new Thread(') | Avoid custom threads; let JMeter's thread groups provide concurrency, or shut executors down. |
| WARN | MEM_SCRIPT_UNCLOSED_RESOURCE | Test Plan / Thread Group / /login-12 / JSR223 PostProcessor | Script opens a file/stream/socket/connection with no visible close() (found 'new FileWriter(') | Use try-with-resources or Groovy withWriter/withReader so handles are released every iteration. |
| WARN | BP_COOKIE_MANAGER | Test Plan / Thread Group | HTTP samplers run without an HTTP Cookie Manager in scope | Add an HTTP Cookie Manager (clear cookies each iteration) to handle sessions. |
| WARN | BP_CSV_ABSOLUTE_PATH | Test Plan / Thread Group / Users CSV | Absolute path 'C:\Users\jag\data\users.csv' will not exist on other machines / load agents | Use a path relative to the .jmx, or ${__P(dataDir,data)}/file.csv. |
| WARN | BP_EMPTY_CONTAINER | Test Plan / Thread Group / If Controller | IfController has no enabled samplers | Remove or disable it. |
| WARN | BP_HARDCODED_HOST | Test Plan / Thread Group / /login-12 | Hard-coded IP address '10.20.30.40' | Leave Server Name empty and set it once in HTTP Request Defaults as ${__P(host,...)}, so the script can target any environment. |
| WARN | BP_HARDCODED_HOST | Test Plan / Thread Group / Search, Results / HTTP Request | Hard-coded host 'uat.example.com' | Leave Server Name empty and set it once in HTTP Request Defaults as ${__P(host,...)}, so the script can target any environment. |
| WARN | BP_HARDCODED_HOST | Test Plan / Thread Group / Search, Results / HTTP Request | Hard-coded host 'uat.example.com' | Leave Server Name empty and set it once in HTTP Request Defaults as ${__P(host,...)}, so the script can target any environment. |
| WARN | BP_HTTP_TIMEOUTS | Test Plan / Thread Group | 3 HTTP request(s) have no connect/response timeout | Set Connect and Response timeouts in HTTP Request Defaults so hung calls cannot block virtual users forever. |
| WARN | BP_IF_CONTROLLER_EXPRESSION | Test Plan / Thread Group / If Controller | Condition is evaluated with JavaScript on every iteration (slow) | Tick 'Interpret Condition as Variable Expression' and use ${__groovy(...)} or ${__jexl3(...)}. |
| WARN | BP_KEEPALIVE | Test Plan / Thread Group / /login-12 | KeepAlive is off: a new TCP/TLS connection is opened for every request | Tick 'Use KeepAlive' to behave like a browser and avoid port exhaustion. |
| WARN | BP_MISSING_ASSERTION | Test Plan / Thread Group / /login-12 | No assertion in scope: a 200 OK error page would count as success | Add a Response Assertion / JSON Assertion that checks business content. |
| WARN | BP_MISSING_ASSERTION | Test Plan / Thread Group / Search, Results / HTTP Request | No assertion in scope: a 200 OK error page would count as success | Add a Response Assertion / JSON Assertion that checks business content. |
| WARN | BP_MISSING_ASSERTION | Test Plan / Thread Group / Search, Results / HTTP Request | No assertion in scope: a 200 OK error page would count as success | Add a Response Assertion / JSON Assertion that checks business content. |
| WARN | BP_RAMP_UP | Test Plan / Thread Group | 50 threads start at once (ramp-up 0) | Set a ramp-up so load builds gradually and the system can warm up. |
| WARN | BP_THINK_TIME | Test Plan / Thread Group | No timers: virtual users fire requests back-to-back | Add think time (e.g. Uniform/Gaussian Random Timer or a Flow Control Action) or a throughput timer to model real user pacing. |
| WARN | BP_TRANSACTION_INCLUDE_TIMERS | Test Plan / Thread Group / Search, Results | Transaction includes timer/processor time, inflating response times | Untick 'Include duration of timer and pre-post processors in generated sample'. |
| WARN | BP_UNCORRELATED_VALUE | Test Plan / Thread Group / /login-12 | 'JSESSIONID' is sent with a recorded literal value | Correlate it: extract the value from the previous response and use ${var}. |
| INFO | MEM_EXTRACT_ALL_MATCHES | Test Plan / Thread Group / /login-12 / Regular Expression Extractor | Match No. is -1: every match is stored as a separate variable | Use a specific match (1 or 0 for random) unless you loop over all values. |
| INFO | MEM_SCRIPT_GROWING_STATE | Test Plan / Thread Group / /login-12 / JSR223 PostProcessor | Script stores objects in props/vars/static collections (found 'props.put(') | Make sure keys are bounded (not per-iteration ids) and remove entries you no longer need; props live for the whole test. |
| INFO | BP_DISABLED_ELEMENTS | Test Plan / Thread Group / Think | Element is disabled | Delete dead elements to keep the script maintainable. |
| INFO | BP_EXTRACTOR_DEFAULT | Test Plan / Thread Group / /login-12 / Regular Expression Extractor | No default value: a failed extraction silently reuses the old value | Set Default Value to e.g. NOT_FOUND and assert on it. |
| INFO | BP_HTTP_DEFAULTS | Test Plan | No HTTP Request Defaults element | Add HTTP Request Defaults for protocol, host, port and timeouts. |
| INFO | BP_HTTP_IMPLEMENTATION | Test Plan / Thread Group / /login-12 | Uses the basic 'Java' HTTP implementation | Use HttpClient4 (default) for proper connection pooling, cookies and TLS. |
| INFO | BP_PARAMETERIZE_LOAD | Test Plan / Thread Group | Hard-coded threads=50, ramp-up=0 | Use ${__P(threads,1)} style properties so the same script runs smoke, load and stress tests from the command line / CI (-Jthreads=50). |
| INFO | BP_SAMPLER_OUTSIDE_TRANSACTION | Test Plan / Thread Group / /login-12 | Sampler is not inside a Transaction Controller | Wrap each business step in a Transaction Controller to report step-level SLAs. |
| INFO | BP_TEST_PLAN_DESCRIPTION | Test Plan | Test Plan has no description | Describe the application, scenario, owner and required properties (-J...) in Comments. |

