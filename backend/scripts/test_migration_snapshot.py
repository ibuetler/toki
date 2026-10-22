"""Regression for committed WAL data in extracted migration backups."""
import ast
from pathlib import Path
import sqlite3
import tempfile
import time
import unittest


class SnapshotTests(unittest.TestCase):
    def test_offline_backup_includes_committed_wal(self):
        # Load only the helper: importing the SSH worker would execute deployment.
        tree = ast.parse(Path(__file__).with_name('birthday-migration-remote.py').read_text())
        function = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == 'snapshot')
        namespace = {'Path': Path, 'sqlite3': sqlite3, 'time': time}
        exec(compile(ast.Module(body=[function], type_ignores=[]), 'snapshot', 'exec'), namespace)
        with tempfile.TemporaryDirectory() as directory:
            source, target = Path(directory) / 'wal.db', Path(directory) / 'restored.db'
            connection = sqlite3.connect(source)
            try:
                connection.execute('PRAGMA journal_mode=WAL')
                connection.execute('CREATE TABLE example (value TEXT)')
                connection.execute("INSERT INTO example VALUES ('committed-in-wal')")
                connection.commit()
                self.assertGreater(Path(str(source) + '-wal').stat().st_size, 0)
                namespace['snapshot'](source, target, offline=True)
                with sqlite3.connect(target) as restored:
                    self.assertEqual(('committed-in-wal',), restored.execute('SELECT value FROM example').fetchone())
            finally:
                connection.close()


if __name__ == '__main__':
    unittest.main()
