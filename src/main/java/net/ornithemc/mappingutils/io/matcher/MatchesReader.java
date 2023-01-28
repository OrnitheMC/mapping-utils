package net.ornithemc.mappingutils.io.matcher;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import net.ornithemc.mappingutils.io.matcher.InputFile.HashAlgorithm;
import net.ornithemc.mappingutils.io.matcher.Matches.ClassMatch;
import net.ornithemc.mappingutils.io.matcher.Matches.FieldMatch;
import net.ornithemc.mappingutils.io.matcher.Matches.MethodMatch;
import net.ornithemc.mappingutils.io.matcher.Matches.ParameterMatch;
import net.ornithemc.mappingutils.io.matcher.Matches.VariableMatch;

public class MatchesReader {

	public static Matches read(Path path) throws IOException {
		try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
			return read(reader);
		} catch (Exception e) {
			throw new IllegalStateException("error reading " + path.toString(), e);
		}
	}

	public static Matches read(BufferedReader reader) throws IOException {
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
	private VariableMatch v;

	private MatchesReader(BufferedReader reader) {
		this.reader = reader;
		this.matches = new Matches();
	}

	public Matches read() throws IOException {
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
			if (!line.startsWith("Matches saved")) {
				throw new IllegalStateException("invalid header!");
			}

			matches.header = line;

			return Stage.HEADER;
		} else if (stage != Stage.MATCHES && line.startsWith(TAB)) {
			if (line.startsWith(TAB + TAB)) {
				List<InputFile> files;

				switch (stage) {
				case INPUT_A:
					files = matches.getInput(MatchSide.A);
					break;
				case INPUT_B:
					files = matches.getInput(MatchSide.B);
					break;
				case SHARED_CLASSPATH:
					files = matches.getSharedClasspath();
					break;
				case CLASSPATH_A:
					files = matches.getClasspath(MatchSide.A);
					break;
				case CLASSPATH_B:
					files = matches.getClasspath(MatchSide.B);
					break;
				default:
					throw new IllegalStateException("cannot parse file for stage " + stage.name());
				}

				// v1: \t\t<name>
				// v2: \t\t<size>\t<sha256>\t<name>
				// v3: \t\t<size>\t<alg>\t<hash>\t<name>

				String name = null;
				long size = -1;
				HashAlgorithm alg = null;
				byte[] hash = null;

				if (args.length == 3) { // v1
					name = args[2];
				} else {
					size = Long.parseLong(args[2]);

					int hashIndex;

					if (args.length == 5) { // v2
						alg = HashAlgorithm.SHA256;
						hashIndex = 3;
					} else { // v3
						for (HashAlgorithm a : HashAlgorithm.values()) {
							if (args[3].equals(a.name())) {
								alg = a;
								break;
							}
						}
						if (alg == null) {
							throw new IllegalStateException("unknown hash algorithm " + args[3]);
						}

						hashIndex = 4;
					}

					hash = Base64.getDecoder().decode(args[hashIndex]);
					name = args[hashIndex + 1];
				}

				files.add(new InputFile(name, size, alg, hash));
			} else {
				switch (args[1]) {
				case Matches.INPUT_A:
					return Stage.INPUT_A;
				case Matches.INPUT_B:
					return Stage.INPUT_B;
				case Matches.SHARED_CLASSPATH:
					return Stage.SHARED_CLASSPATH;
				case Matches.CLASSPATH_A:
					return Stage.CLASSPATH_A;
				case Matches.CLASSPATH_B:
					return Stage.CLASSPATH_B;
				default:
					switch (args[1]) {
					case Matches.NON_OBF_CLASS_A:
						matches.setNonObfClassPattern(MatchSide.A, args[2]);
						break;
					case Matches.NON_OBF_CLASS_B:
						matches.setNonObfClassPattern(MatchSide.B, args[2]);
						break;
					case Matches.NON_OBF_MEMBER_A:
						matches.setNonObfMemberPattern(MatchSide.A, args[2]);
						break;
					case Matches.NON_OBF_MEMBER_B:
						matches.setNonObfMemberPattern(MatchSide.B, args[2]);
						break;
					default:
						throw new IllegalStateException("illegal content in header: " + line);
					}
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

			if (ac != 3) {
				throw new IllegalStateException("illegal number of arguments (" + ac + ") for match on line " + lineNumber + " - expected 3");
			}

			String target = args[indents];

			String a;
			String b;

			if (target.endsWith(Matches.UNMATCHABLE)) {
				switch (args[1 + indents]) {
				case "a":
					a = args[2 + indents];
					b = null;

					break;
				case "b":
					a = null;
					b = args[2 + indents];

					break;
				default:
					throw new IllegalStateException("unknown match side " + args[1]);
				}
			} else {
				a = args[1 + indents];
				b = args[2 + indents];
			}

			switch (target) {
			case Matches.CLASS:
			case Matches.CLASS_UNMATCHABLE:
				if (indents != Matches.CLASS_INDENTS) {
					throw new IllegalStateException("illegal number of indents (" + indents + ") for class match on line " + lineNumber + " - expected " + Matches.CLASS_INDENTS);
				}

				c = matches.addClass(a, b);
				f = null;
				m = null;
				p = null;
				v = null;

				break;
			case Matches.FIELD:
			case Matches.FIELD_UNMATCHABLE:
				if (indents != Matches.FIELD_INDENTS) {
					throw new IllegalStateException("illegal number of indents (" + indents + ") for field match on line " + lineNumber + " - expected " + Matches.FIELD_INDENTS);
				}
				if (c == null) {
					throw new IllegalStateException("cannot read field match on line " + lineNumber + " - not in a class?");
				}

				f = c.addField(a, b);
				m = null;
				p = null;
				v = null;

				break;
			case Matches.METHOD:
			case Matches.METHOD_UNMATCHABLE:
				if (indents != Matches.METHOD_INDENTS) {
					throw new IllegalStateException("illegal number of indents (" + indents + ") for method match on line " + lineNumber + " - expected " + Matches.METHOD_INDENTS);
				}
				if (c == null) {
					throw new IllegalStateException("cannot read method match on line " + lineNumber + " - not in a class?");
				}

				m = c.addMethod(a, b);
				f = null;
				p = null;
				v = null;

				break;
			case Matches.PARAMETER:
			case Matches.PARAMETER_UNMATCHABLE:
				if (indents != Matches.PARAMETER_INDENTS) {
					throw new IllegalStateException("illegal number of indents (" + indents + ") for parameter match on line " + lineNumber + " - expected " + Matches.PARAMETER_INDENTS);
				}
				if (m == null) {
					throw new IllegalStateException("cannot read paremeter match on line " + lineNumber + " - not in a method?");
				}

				f = null;
				p = m.addParameter(a, b);
				v = null;

				break;
			case Matches.VARIABLE:
			case Matches.VARIABLE_UNMATCHABLE:
				if (indents != Matches.VARIABLE_INDENTS) {
					throw new IllegalStateException("illegal number of indents (" + indents + ") for variable match on line " + lineNumber + " - expected " + Matches.VARIABLE_INDENTS);
				}
				if (m == null) {
					throw new IllegalStateException("cannot read variable match on line " + lineNumber + " - not in a method?");
				}

				f = null;
				p = null;
				v = m.addVariable(a, b);

				break;
			default:
				throw new IllegalStateException("unknown match target " + target + " on line " + lineNumber);
			}
		}

		return stage;
	}

	private enum Stage {
		START, HEADER, INPUT_A, INPUT_B, SHARED_CLASSPATH, CLASSPATH_A, CLASSPATH_B, MATCHES
	}
}
