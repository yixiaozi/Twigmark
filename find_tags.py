import re
import html

with open(r'E:\yixiaozi\02目标发展\03婚恋性福\1求偶\3相亲活动\相亲活动.mm', 'r', encoding='utf-8') as f:
    content = f.read()

matches = re.findall(r'<node[^>]*TEXT="([^"]*#[^"]*)"', content)
print(f"Found {len(matches)} nodes with # tags")
for match in matches[:30]:
    decoded = html.unescape(match)
    if '#' in decoded:
        print(f"  {decoded}")
