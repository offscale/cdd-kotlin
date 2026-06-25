import xml.etree.ElementTree as ET
import sys

tree = ET.parse('build/reports/kover/report.xml')
root = tree.getroot()
total_missed_lines = 0

for package in root.findall('package'):
    pkg_name = package.get('name')
    if pkg_name.startswith('openapi') or pkg_name.startswith('psi'):
        for clazz in package.findall('class'):
            for method in clazz.findall('method'):
                for counter in method.findall('counter'):
                    if counter.get('type') == 'LINE':
                        missed = int(counter.get('missed'))
                        if missed > 0:
                            total_missed_lines += missed
                            print(f"{clazz.get('name')}.{method.get('name')} - Missed: {missed}")

print(f"\nTotal missed lines in openapi and psi: {total_missed_lines}")
