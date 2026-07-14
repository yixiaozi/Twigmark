import re
import html
import os
import shutil

def convert_tags_in_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    shutil.copy2(filepath, filepath + '.bak')

    def replace_tag(match):
        tag = match.group(1)
        return f'【{tag}】'

    text_pattern = r'"([^"]*?)#([^\s",]+)([^"]*?)"'

    def replace_in_text(match):
        prefix = match.group(1)
        tag = match.group(2)
        suffix = match.group(3)
        if 'TEXT=' in match.string[max(0, match.start()-10):match.start()] or 'text=' in match.string[max(0, match.start()-10):match.start()]:
            return f'"{prefix}【{tag}】{suffix}"'
        return match.group(0)

    new_content = re.sub(text_pattern, replace_in_text, content)

    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(new_content)

    changes = len(re.findall(text_pattern, content))
    return changes

def process_directory(root_dir):
    total_changes = 0
    modified_files = 0

    for dirpath, dirnames, filenames in os.walk(root_dir):
        for filename in filenames:
            if filename.endswith('.mm') and not filename.startswith('~') and '冲突副本' not in filename:
                filepath = os.path.join(dirpath, filename)
                changes = convert_tags_in_file(filepath)
                if changes > 0:
                    modified_files += 1
                    total_changes += changes
                    print(f"Modified: {filepath} ({changes} changes)")

    print(f"\nTotal modified files: {modified_files}")
    print(f"Total tag conversions: {total_changes}")

if __name__ == '__main__':
    process_directory(r'E:\yixiaozi')
