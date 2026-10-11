"""Check that reproduction never silently discards a reader's concurrent edits."""
from pathlib import Path
import importlib.util,tempfile,unittest
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('driver',Path(__file__).with_name('investigate-builds.py'))
driver=importlib.util.module_from_spec(spec);spec.loader.exec_module(driver)

class RestorationTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name);self.target=self.root/'target.kt';self.reader=self.root/'attempt.kt'
        self.target.write_text('original');self.reader.write_text('reader attempt')
        self.patches=[patch.object(driver,'ROOT',self.root),patch.object(driver,'source_files',lambda:[self.target,self.reader]),patch.object(driver,'environment',lambda:{})]
        for p in self.patches:p.start();self.addCleanup(p.stop)
        self.suite=driver.Suite('test')
    def test_restores_owned_edit_but_keeps_unrelated_reader_attempt(self):
        driver.edit('target.kt','original','experiment');self.reader.write_text('new reader answer')
        self.suite.restore()
        self.assertEqual('original',self.target.read_text());self.assertEqual('new reader answer',self.reader.read_text())
    def test_preserves_conflicting_edit_in_experiment_file(self):
        driver.edit('target.kt','original','experiment');self.target.write_text('reader repair')
        with self.assertRaisesRegex(RuntimeError,'Preserved concurrent'):self.suite.restore()
        self.assertEqual('reader repair',self.target.read_text())
    def test_refuses_to_edit_a_target_already_changed_by_reader(self):
        self.target.write_text('original plus reader change')
        with self.assertRaisesRegex(RuntimeError,'Preserved concurrent'):driver.edit('target.kt','original','experiment')
        self.assertEqual('original plus reader change',self.target.read_text())

if __name__=='__main__':unittest.main()
