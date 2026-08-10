package org.freeplane.view.swing.features.keylog;

/**
 * Maps Windows virtual-key codes to DocearReminder-style key names.
 */
final class KeyVkNames {
	private KeyVkNames() {
	}

	static String nameOf(final int vk) {
		if (vk >= 0x41 && vk <= 0x5A) {
			return String.valueOf((char) vk);
		}
		if (vk >= 0x30 && vk <= 0x39) {
			return "D" + (vk - 0x30);
		}
		if (vk >= 0x60 && vk <= 0x69) {
			return "NumPad" + (vk - 0x60);
		}
		if (vk >= 0x70 && vk <= 0x87) {
			return "F" + (vk - 0x6F);
		}
		switch (vk) {
			case 0x08:
				return "Back";
			case 0x09:
				return "Tab";
			case 0x0D:
				return "Return";
			case 0x10:
				return "ShiftKey";
			case 0x11:
				return "ControlKey";
			case 0x12:
				return "Menu";
			case 0x13:
				return "Pause";
			case 0x14:
				return "CapsLock";
			case 0x1B:
				return "Escape";
			case 0x20:
				return "Space";
			case 0x21:
				return "PageUp";
			case 0x22:
				return "Next";
			case 0x23:
				return "End";
			case 0x24:
				return "Home";
			case 0x25:
				return "Left";
			case 0x26:
				return "Up";
			case 0x27:
				return "Right";
			case 0x28:
				return "Down";
			case 0x2C:
				return "PrintScreen";
			case 0x2D:
				return "Insert";
			case 0x2E:
				return "Delete";
			case 0x5B:
				return "LWin";
			case 0x5C:
				return "RWin";
			case 0x5D:
				return "Apps";
			case 0x6A:
				return "Multiply";
			case 0x6B:
				return "Add";
			case 0x6C:
				return "Separator";
			case 0x6D:
				return "Subtract";
			case 0x6E:
				return "Decimal";
			case 0x6F:
				return "Divide";
			case 0x90:
				return "NumLock";
			case 0x91:
				return "Scroll";
			case 0xA0:
				return "LShiftKey";
			case 0xA1:
				return "RShiftKey";
			case 0xA2:
				return "LControlKey";
			case 0xA3:
				return "RControlKey";
			case 0xA4:
				return "LMenu";
			case 0xA5:
				return "RMenu";
			case 0xBA:
				return "Oem1";
			case 0xBB:
				return "Oemplus";
			case 0xBC:
				return "Oemcomma";
			case 0xBD:
				return "OemMinus";
			case 0xBE:
				return "OemPeriod";
			case 0xBF:
				return "Oem2";
			case 0xC0:
				return "Oem3";
			case 0xDB:
				return "Oem4";
			case 0xDC:
				return "Oem5";
			case 0xDD:
				return "Oem6";
			case 0xDE:
				return "Oem7";
			case 0xDF:
				return "Oem8";
			default:
				return "Vk" + Integer.toHexString(vk).toUpperCase();
		}
	}
}
