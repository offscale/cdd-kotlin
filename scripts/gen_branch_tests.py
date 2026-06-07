import re

with open("src/commonMain/kotlin/org/cdd/mcp/McpModels.kt", "r") as f:
    kt_code = f.read()

models = re.findall(r'data class ([a-zA-Z0-9_]+)\(', kt_code)

out_test = """package org.cdd.mcp

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class McpModelsBranchCoverageTest {
"""

for m in models:
    # A generic reflection-based instantiation or just using the ones from McpModelsCoverageTest
    pass
