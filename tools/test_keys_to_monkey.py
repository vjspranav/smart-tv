import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import keys_to_monkey as k


class Script(unittest.TestCase):
    def test_basic(self):
        s = k.script(['KEYCODE_DPAD_RIGHT 250'])
        self.assertIn('DispatchKey(0,0,0,22,0,0,0,0)', s)
        self.assertIn('DispatchKey(0,0,1,22,0,0,0,0)', s)
        self.assertIn('UserWait(250)', s)
        self.assertTrue(s.startswith('type= raw events\ncount= '))
        self.assertIn('start data >>', s)

    def test_skips_comments_blank(self):
        s = k.script(['# warm-up', '', 'KEYCODE_DPAD_LEFT 100', '   '])
        self.assertEqual(s.count('DispatchKey(0,0,0,'), 1)
        self.assertIn(',21,', s)

    def test_default_delay(self):
        self.assertIn('UserWait(250)', k.script(['KEYCODE_HOME']))

    def test_empty_raises(self):
        with self.assertRaises(ValueError):
            k.script(['# only a comment', ''])

    def test_unknown_key_raises(self):
        with self.assertRaises(ValueError):
            k.script(['KEYCODE_NOPE 100'])


if __name__ == '__main__':
    unittest.main()
