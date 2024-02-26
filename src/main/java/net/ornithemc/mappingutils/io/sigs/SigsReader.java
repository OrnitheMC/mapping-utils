package net.ornithemc.mappingutils.io.sigs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

import net.ornithemc.mappingutils.io.sigs.SignatureMappings.ClassMapping;

public class SigsReader {

	public static SignatureMappings read(Path path) throws IOException {
		try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
			return read(reader);
		} catch (Exception e) {
			throw new IllegalStateException("error reading " + path.toString(), e);
		}
	}

	public static SignatureMappings read(BufferedReader reader) throws IOException {
		return new SigsReader(reader).read();
	}

	private static final String TAB = "\t";

	private final BufferedReader reader;
	private final SignatureMappings sigs;

	private SigsReader(BufferedReader reader) {
		this.reader = reader;
		this.sigs = new SignatureMappings();
	}

	public SignatureMappings read() throws IOException {
		boolean inClass = false;
		ClassMapping classMapping = null;

		String line;
		int lineNumber = 0;
		while ((line = reader.readLine()) != null) {
			lineNumber++;

			if (line.isEmpty()) {
				continue;
			}

			if (line.startsWith(TAB)) {
				if (!inClass) {
					throw new IllegalArgumentException("Unexpected indent on line \"" + line + "\"");
				}
				String[] parts = line.split(TAB, -1);
				if (parts.length < 3) {
					throw new IllegalArgumentException("Member info must have at least name and desc");
				}
				String name = unescape(parts[1]);
				if (name.isBlank()) {
					throw new IllegalArgumentException("Member name \"" + name + "\" cannot be blank");
				}
				String desc = unescape(parts[2]);
				if (desc.isBlank()) {
					throw new IllegalArgumentException("Member descriptor \"" + desc + "\" cannot be blank");
				}
				SignatureMode signatureMode;
				String newSignature = "";
				if (parts.length == 3) {
					signatureMode = SignatureMode.KEEP;
				} else if (parts.length == 4) {
					signatureMode = parts[3].equals(".") ? SignatureMode.REMOVE : SignatureMode.MODIFY;
					newSignature = unescape(parts[3]);
				} else {
					throw new IllegalArgumentException("Trailing parts on line " + lineNumber + ": "
							+ Arrays.stream(parts).skip(3).collect(Collectors.joining(TAB)));
				}
				if (classMapping != null) {
					classMapping.addMember(name, desc, signatureMode, newSignature);
				}
				continue;
			}
			String[] parts = line.split(TAB, -1);
			String className = unescape(parts[0]);
			if (className.isBlank()) {
				throw new IllegalArgumentException("Class name \"" + className + "\" cannot be blank");
			}
			SignatureMode signatureMode;
			String newSignature = "";
			if (parts.length == 1) {
				signatureMode = SignatureMode.KEEP;
			} else if (parts.length == 2) {
				signatureMode = parts[1].equals(".") ? SignatureMode.REMOVE : SignatureMode.MODIFY;
				newSignature = unescape(parts[1]);
			} else {
				throw new IllegalArgumentException("Trailing parts on line " + lineNumber + ": "
						+ Arrays.stream(parts).skip(2).collect(Collectors.joining(TAB)));
			}
			inClass = true;
			classMapping = sigs.addClass(className, signatureMode, newSignature);
		}

		return this.sigs;
	}

	public static String unescape(String escaped) {
		int i = escaped.indexOf('\\');
		if (i == -1) {
			return escaped;
		}
		final StringBuilder result = new StringBuilder(escaped.length());
		int lastI = 0;
		while (i != -1) {
			result.append(escaped, lastI, i);
			if (i + 1 >= escaped.length()) {
				throw new IllegalArgumentException("Trailing \\ in string \"" + escaped + "\"");
			}
			final char c = escaped.charAt(i + 1);
			int skip = 1;
			switch (c) {
			case '"':
			case '\'':
			case '\\':
				result.append(c);
				break;
			case '0':
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7': {
				final char c1 = i + 2 < escaped.length() ? escaped.charAt(i + 2) : '\0';
				final char c2 = i + 3 < escaped.length() ? escaped.charAt(i + 3) : '\0';
				final int digit0 = Character.digit(c, 8);
				final int digit1 = Character.digit(c1, 8);
				final int digit2 = Character.digit(c2, 8);
				if (digit1 == -1) {
					result.append((char) digit0);
				} else if (digit2 == -1 || digit0 > 3) {
					result.append((char) (digit0 << 3 | digit1));
					skip = 2;
				} else {
					result.append((char) (digit0 << 6 | digit1 << 3 | digit2));
					skip = 3;
				}
				break;
			}
			case 'u': {
				if (i + 5 >= escaped.length()) {
					throw new IllegalArgumentException("\\u escape overflows in string \"" + escaped + "\"");
				}
				result.append((char) Integer.parseInt(escaped, i + 2, i + 6, 16));
				skip = 5;
				break;
			}
			case 'b':
				result.append('\b');
				break;
			case 'f':
				result.append('\f');
				break;
			case 'n':
				result.append('\n');
				break;
			case 'r':
				result.append('\r');
				break;
			case 's':
				result.append(' ');
				break;
			case 't':
				result.append('\t');
				break;
			default:
				throw new IllegalArgumentException("Unknown escape \\" + c + " in string \"" + escaped + "\"");
			}
			lastI = i + skip + 1;
			i = escaped.indexOf('\\', lastI);
		}
		result.append(escaped, lastI, escaped.length());
		return result.toString();
	}
}
