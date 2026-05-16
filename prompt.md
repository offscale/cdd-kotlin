# Local Test Fixes Required

The integration tests for this toolchain currently fail when running natively against the Stoplight Prism mock server (`./local-test.sh only-test` with `ONLY_TEST=cdd-kotlin`).

## Instructions
1. Run the test locally for this specific language using: 
   `cd .. && ONLY_TEST=cdd-kotlin ./local-test.sh only-test`
2. Observe the output. The failure is typically caused by one of the following:
   * **401 Unauthorized:** The generated SDK tests are not injecting the required `api_key` or OAuth headers that the Swagger Petstore schema demands. Prism enforces security constraints strictly.
   * **400 / 422 Unprocessable Entity:** The generated SDK is sending incorrectly formatted JSON or invalid parameters for the mock server.
   * **404 Not Found / Configuration Issues:** The SDK might be querying the wrong base URL or missing the `/v2` base path prefix.
   * **Coverage Missing:** The generated tests might not be covering all endpoints. Check if the toolchain's block in `local-test.sh` suppresses test exit codes with `|| true`. If so, remove them, fix the tests so they actually pass, and ensure every endpoint has a generated test.
3. Update the generator templates/AST extractors in this repository to resolve the issue.
4. Verify your changes by ensuring `cd .. && ONLY_TEST=cdd-kotlin ./local-test.sh only-test` passes successfully (including the coverage validation and chaos saboteur audits).

---
## Official Swagger Java Server Native Logs Update
A recent test run against the official JVM swagger-petstore backend yielded new failure patterns. Please address these specifically:

The Kotlin SDK fails the Coverage Validation stage. The generated tests are missing coverage for multiple endpoints (e.g. `POST /v2/store/order`, `PUT /v2/pet`). Ensure the generator templates output a test file for every defined route.
