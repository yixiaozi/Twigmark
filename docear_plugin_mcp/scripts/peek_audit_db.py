import sqlite3
import sys

path = sys.argv[1] if len(sys.argv) > 1 else r"E:\yixiaozi\_data\_data\audit.db"
conn = sqlite3.connect(path)
tables = [r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table'")]
print("db:", path)
print("tables:", tables)
if "audit_event" in tables:
    print("audit_event rows:", conn.execute("SELECT COUNT(*) FROM audit_event").fetchone()[0])
conn.close()
