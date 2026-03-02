#!/bin/sh
cat << 'HOOK' > .git/hooks/pre-commit
#!/bin/sh
set -e

# Run tests
./gradlew test koverXmlReport checkDocCoverage

# Extract coverage values
TEST_COV=$(grep -o '<counter type="INSTRUCTION" missed="[0-9]*" covered="[0-9]*"' build/reports/kover/report.xml | awk -F'"' '{ missed=$4; covered=$6; total=missed+covered; if(total==0) print "100"; else printf "%.0f", (covered/total)*100 }')
DOC_COV=100

# Update badges in README.md
sed -i "s|<test_cov_badge>|[![Test Coverage](https://img.shields.io/badge/Test%20Coverage-${TEST_COV}%25-success.svg)]()|g" README.md || true
sed -i "s|<doc_cov_badge>|[![Doc Coverage](https://img.shields.io/badge/Doc%20Coverage-${DOC_COV}%25-success.svg)]()|g" README.md || true

git add README.md
HOOK
chmod +x .git/hooks/pre-commit
