import xml.etree.ElementTree as ET

tree = ET.parse('build/reports/kover/report.xml')
root = tree.getroot()

for package in root.findall('package'):
    for clazz in package.findall('class'):
        for method in clazz.findall('method'):
            for counter in method.findall('counter'):
                if counter.get('type') == 'LINE':
                    missed = int(counter.get('missed'))
                    if missed > 0:
                        print(f"{clazz.get('name')}.{method.get('name')}")
