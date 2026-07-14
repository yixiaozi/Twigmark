import os
import shutil

def replace_brackets_in_file(file_path):
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        old_count = content.count('&#x3010;')
        if old_count == 0:
            old_count = content.count('【')
            if old_count == 0:
                return False, 0
        
        new_content = content.replace('&#x3010;', '[').replace('&#x3011;', ']')
        new_content = new_content.replace('【', '[').replace('】', ']')
        
        backup_path = file_path + '.bak'
        shutil.copy2(file_path, backup_path)
        
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(new_content)
        
        return True, old_count
    except Exception as e:
        print(f"  处理失败: {e}")
        return False, 0

def main():
    root_dir = r'E:\yixiaozi'
    
    if not os.path.exists(root_dir):
        print(f"目录不存在: {root_dir}")
        return
    
    total_files = 0
    changed_files = 0
    total_replacements = 0
    
    print(f"开始扫描目录: {root_dir}")
    print("=" * 60)
    
    for dirpath, dirnames, filenames in os.walk(root_dir):
        dirnames[:] = [d for d in dirnames if not d.startswith('.') and d != 'bin']
        
        for filename in filenames:
            if filename.lower().endswith('.mm') and not filename.startswith('~') and '冲突副本' not in filename:
                file_path = os.path.join(dirpath, filename)
                total_files += 1
                
                changed, count = replace_brackets_in_file(file_path)
                if changed:
                    changed_files += 1
                    total_replacements += count
                    print(f"  修改: {file_path} ({count}处替换)")
    
    print("=" * 60)
    print(f"扫描完成！")
    print(f"  总文件数: {total_files}")
    print(f"  修改文件数: {changed_files}")
    print(f"  替换总数: {total_replacements}")
    print(f"  备份文件: 已为每个修改的文件创建 .bak 备份")

if __name__ == '__main__':
    main()