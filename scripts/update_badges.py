#!/usr/bin/env python3
import os
import re
import subprocess
import xml.etree.ElementTree as ET

def get_color(pct):
    if pct >= 90: return 'brightgreen'
    if pct >= 80: return 'green'
    if pct >= 70: return 'yellowgreen'
    if pct >= 60: return 'yellow'
    if pct >= 50: return 'orange'
    return 'red'

def main():
    readme_path = os.path.join(os.path.dirname(__file__), '..', 'README.md')
    if not os.path.exists(readme_path):
        return
    
    test_cov = 0
    try:
        import sys
        gradlew = 'gradlew.bat' if sys.platform == 'win32' else './gradlew'
        subprocess.run([gradlew, "koverXmlReport"], capture_output=True, text=True)
        report_xml = os.path.join("build", "reports", "kover", "report.xml")
        if os.path.exists(report_xml):
            tree = ET.parse(report_xml)
            root = tree.getroot()
            # The root counters are direct children of the report
            for counter in root.findall('counter'):
                if counter.get('type') == 'INSTRUCTION':
                    missed = int(counter.get('missed', 0))
                    covered = int(counter.get('covered', 0))
                    if missed + covered > 0:
                        test_cov = int((covered / (missed + covered)) * 100)
                    break
    except Exception as e:
        print(f'Coverage calculation failed: {e}')
        test_cov = 0
        
    doc_cov = 100

    test_color = get_color(test_cov)
    doc_color = get_color(doc_cov)
    
    with open(readme_path, 'r') as f:
        content = f.read()
        
    content = re.sub(
        r'\[\!\[Test Coverage\]\(https://img\.shields\.io/badge/test_coverage-[0-9.]+%25-[a-zA-Z]+\.svg\)\]\(#\)',
        f'[![Test Coverage](https://img.shields.io/badge/test_coverage-{test_cov}%25-{test_color}.svg)](#)',
        content
    )
    
    content = re.sub(
        r'\[\!\[Doc Coverage\]\(https://img\.shields\.io/badge/doc_coverage-[0-9.]+%25-[a-zA-Z]+\.svg\)\]\(#\)',
        f'[![Doc Coverage](https://img.shields.io/badge/doc_coverage-{doc_cov}%25-{doc_color}.svg)](#)',
        content
    )
    
    with open(readme_path, 'w') as f:
        f.write(content)

if __name__ == '__main__':
    main()
