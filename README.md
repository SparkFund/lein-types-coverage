# lein-types-coverage

runs the equivalent of `lein test` while tracking calls to core.typed annotated
functions wrapped with https://github.com/SparkFund/types-to-schema. Alerts you
if you have functinos that are annotated but never wrapped in any test
namespace, and any functions that are wrapped but never called in any test.

Usage is the same as `lein test`, for example:

`lein types-coverage`
`lein types-coverage :integration`
`lein types-coverage only.this.test.namespace`

## License

Copyright © 2015 

Distributed under the Apache License, Version 2.0.
