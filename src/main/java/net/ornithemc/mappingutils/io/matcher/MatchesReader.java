package net.ornithemc.mappingutils.io.matcher;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Path;

import net.ornithemc.mappingutils.io.matcher.Matches.ClassMatch;
import net.ornithemc.mappingutils.io.matcher.Matches.FieldMatch;
import net.ornithemc.mappingutils.io.matcher.Matches.MethodMatch;
import net.ornithemc.mappingutils.io.matcher.Matches.ParameterMatch;

public class MatchesReader {

	public static Matches read(Path path) throws Exception {
		try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
			return read(reader);
		} catch (Exception e) {
			throw new IllegalStateException("error reading " + path.toString(), e);
		}
	}

	public static Matches read(BufferedReader reader) throws Exception {
		return new MatchesReader(reader).read();
	}

	private static final String TAB = "\t";

	private final BufferedReader reader;
	private final Matches matches;

	private Stage stage;

	private ClassMatch c;
	private FieldMatch f;
	private MethodMatch m;
	private ParameterMatch p;

	private MatchesReader(BufferedReader reader) {
		this.reader = reader;
		this.matches = new Matches();
	}

	public Matches read() throws Exception {
		stage = Stage.START;

		for (int lineNumber = 1; stage != null; lineNumber++) {
			try {
				stage = parseLine(reader.readLine(), lineNumber);
			} catch (Exception e) {
				System.err.println("error on line " + lineNumber);
				throw e;
			}
		}

		return matches;
	}

	private Stage parseLine(String line, int lineNumber) {
		if (line == null) {
			return null;
		}

		String[] args = line.split(TAB);

		if (stage == Stage.START) {
			return Stage.HEADER;
		} else if (stage != Stage.MATCHES && line.startsWith(TAB)) {
			if (line.startsWith(TAB + TAB)) {
				switch (stage) {
				case INPUT_SRC:
				case INPUT_DST:
				case SHARED_CLASSPATH:
				case CLASSPATH_SRC:
				case CLASSPATH_DST:
					break;
				default:
					throw new IllegalStateException("cannot parse file for stage " + stage.name());
				}
			} else {
				switch (args[1]) {
				case Matches.INPUT_A:
					return Stage.INPUT_SRC;
				case Matches.INPUT_B:
					return Stage.INPUT_DST;
				case Matches.SHARED_CLASSPATH:
					return Stage.SHARED_CLASSPATH;
				case Matches.CLASSPATH_A:
					return Stage.CLASSPATH_SRC;
				case Matches.CLASSPATH_B:
					return Stage.CLASSPATH_DST;
				case Matches.NON_OBF_CLASS_A:
				case Matches.NON_OBF_CLASS_B:
				case Matches.NON_OBF_MEMBER_A:
				case Matches.NON_OBF_MEMBER_B:
					break;
				default:
					throw new IllegalStateException("illegal content in header: " + line);
				}
			}
		} else {
			if (stage != Stage.MATCHES) {
				stage = Stage.MATCHES;
			}

			int indents = 0;

			for (; indents < args.length; indents++) {
				if (!args[indents].isEmpty()) {
					break;
				}
			}

			int ac = args.length - indents;

			String a;
			String b;

			switch (args[indents]) {
			case Matches.CLASS:
				if (indents != Matches.CLASS_INDENTS) {
					throw new IllegalStateException("illegal number of indents (" + indents + ") for class match on line " + lineNumber + " - expected " + Matches.CLASS_INDENTS);
				}
				if (ac != 3) {
					throw new IllegalStateException("illegal number of arguments (" + ac + ") for class match on line " + lineNumber + " - expected 3");
				}

				a = args[1 + indents];
				b = args[2 + indents];

				c = matches.addClass(a, b);
				f = null;
				m = null;
				p = null;
			case Matches.CLASS_UNMATCHABLE:
				break;
			case Matches.FIELD:
				if (indents != Matches.FIELD_INDENTS) {
					throw new IllegalStateException("illegal number of indents (" + indents + ") for field match on line " + lineNumber + " - expected " + Matches.FIELD_INDENTS);
				}
				if (ac != 3) {
					throw new IllegalStateException("illegal number of arguments (" + ac + ") for field match on line " + lineNumber + " - expected 3");
				}
				if (c == null) {
					throw new IllegalStateException("cannot read field match on line " + lineNumber + " - not in a class?");
				}

				a = args[1 + indents];
				b = args[2 + indents];

				f = c.addField(a, b);
				m = null;
				p = null;
			case Matches.FIELD_UNMATCHABLE:
				break;
			case Matches.METHOD:
				if (indents != Matches.METHOD_INDENTS) {
					throw new IllegalStateException("illegal number of indents (" + indents + ") for method match on line " + lineNumber + " - expected " + Matches.METHOD_INDENTS);
				}
				if (ac != 3) {
					throw new IllegalStateException("illegal number of arguments (" + ac + ") for method match on line " + lineNumber + " - expected 3");
				}
				if (c == null) {
					throw new IllegalStateException("cannot read method match on line " + lineNumber + " - not in a class?");
				}

				a = args[1 + indents];
				b = args[2 + indents];

				m = c.addMethod(a, b);
				f = null;
				p = null;
			case Matches.METHOD_UNMATCHABLE:
				break;
			case Matches.PARAMETER:
				if (indents != Matches.PARAMETER_INDENTS) {
					throw new IllegalStateException("illegal number of indents (" + indents + ") for parameter match on line " + lineNumber + " - expected " + Matches.PARAMETER_INDENTS);
				}
				if (ac != 3) {
					throw new IllegalStateException("illegal number of arguments (" + ac + ") for parameter match on line " + lineNumber + " - expected 3");
				}
				if (m == null) {
					throw new IllegalStateException("cannot read paremter match on line " + lineNumber + " - not in a method?");
				}

				String rawIndexA = args[1 + indents];
				String rawIndexB = args[2 + indents];

				int indexA = Integer.parseInt(rawIndexA);
				int indexB = Integer.parseInt(rawIndexB);

				if (indexA < 0) {
					throw new IllegalStateException("illegal parameter index " + indexA + " on line " + lineNumber + " - cannot be negative!");
				}
				if (indexB < 0) {
					throw new IllegalStateException("illegal parameter index " + indexB + " on line " + lineNumber + " - cannot be negative!");
				}

				p = m.addParameter(indexA, indexB);
				f = null;
			case Matches.PARAMETER_UNMATCHABLE:
			case Matches.VARIABLE:
			case Matches.VARIABLE_UNMATCHABLE:
				break;
			default:
				throw new IllegalStateException("unknown match target " + args[indents] + " on line " + lineNumber);
			}
		}

		return stage;
	}

	private enum Stage {
		START, HEADER, INPUT_SRC, INPUT_DST, SHARED_CLASSPATH, CLASSPATH_SRC, CLASSPATH_DST, MATCHES
	}
}
