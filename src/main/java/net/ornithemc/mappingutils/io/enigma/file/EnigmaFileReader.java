package net.ornithemc.mappingutils.io.enigma.file;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Stack;

import net.ornithemc.mappingutils.io.MappingTarget;
import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.Mappings.ClassMapping;
import net.ornithemc.mappingutils.io.Mappings.Mapping;
import net.ornithemc.mappingutils.io.Mappings.MethodMapping;

public class EnigmaFileReader {

	public static Mappings read(Path path) throws IOException {
		try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
			return read(reader);
		} catch (Exception e) {
			throw new IOException("error reading " + path.toString(), e);
		}
	}

	public static Mappings read(BufferedReader reader) throws IOException {
		return read(reader, new Mappings());
	}

	public static Mappings read(Path path, Mappings mappings) throws IOException {
		try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
			return read(reader, mappings);
		} catch (Exception e) {
			throw new IOException("error reading " + path.toString(), e);
		}
	}

	public static Mappings read(BufferedReader reader, Mappings mappings) throws IOException {
		return new EnigmaFileReader(reader, mappings).read();
	}

	private final BufferedReader reader;
	private final Mappings mappings;

	private EnigmaFileReader(BufferedReader reader, Mappings mappings) {
		this.reader = reader;
		this.mappings = mappings;
	}

	public Mappings read() throws IOException {
		Stack<Mapping> parents = new Stack<>();

		for (int lineNumber = 1; parents != null; lineNumber++) {
			parents = parseLine(reader.readLine(), lineNumber, parents);
		}

		return mappings;
	}

	private Stack<Mapping> parseLine(String line, int lineNumber, Stack<Mapping> parents) {
		if (line == null) {
			return null;
		}

		int indents = countIndents(line);

		while (indents < parents.size()) {
			parents.pop();
		}

		line = line.substring(indents);
		line = stripComment(line);

		Mapping parent = parents.isEmpty() ? null : parents.peek();
		Mapping mapping = parseLine(line, lineNumber, parent);

		if (mapping != null) {
			parents.push(mapping);
		}

		return parents;
	}

	private int countIndents(String s) {
		int indents = 0;

		for (; indents < s.length(); indents++) {
			if (s.charAt(indents) != '\t') {
				break;
			}
		}

		return indents;
	}

	private String stripComment(String line) {
		// javadoc entries cannot have comments
		if (line.startsWith(EnigmaFileFormat.COMMENT)) {
			return line;
		}

		int i = line.indexOf('#');

		if (i < 0) {
			return line;
		}

		return line.substring(0, i);
	}

	private Mapping parseLine(String line, int lineNumber, Mapping parent) {
		String[] args = line.split("\\s");

		String src;
		String dst;
		String desc;
		String mod; // ignored

		switch (args[0]) {
		case EnigmaFileFormat.CLASS:
			if (args.length < 2 || args.length > 4) {
				throw new IllegalStateException("illegal number of arguments (" + args.length + ") for class mapping on line " + lineNumber + " - expected 2-4");
			}
			if (parent != null && parent.target() != MappingTarget.CLASS) {
				throw new IllegalStateException("invalid hierarchy on line " + lineNumber + ": a class cannot be the child of " + parent.target());
			}

			src = args[1];
			
			if (args.length == 2) {
				dst = "";
			} else if (args.length == 3) {
				if (isModifier(args[2])) {
					dst = "";
					mod = args[2];
				} else {
					dst = args[2];
					mod = "";
				}
			} else {
				dst = args[2];
				mod = args[3];
			}

			if (parent == null) {
				return mappings.addClass(src, dst);
			} else {
				return ((ClassMapping)parent).addClass(parent.src() + "$" + src, dst);
			}
		case EnigmaFileFormat.FIELD:
			if (args.length < 3 || args.length > 5) {
				throw new IllegalStateException("illegal number of arguments (" + args.length + ") for field mapping on line " + lineNumber + " - expected 3-5");
			}
			if (parent == null || parent.target() != MappingTarget.CLASS) {
				throw new IllegalStateException("invalid hierarchy on line " + lineNumber + ": a field must be the child of a class");
			}

			src = args[1];

			if (args.length == 3) {
				desc = args[2];
				dst = "";
				mod = "";
			} else if (args.length == 4) {
				if (isModifier(args[3])) {
					desc = args[2];
					dst = "";
					mod = args[3];
				} else {
					desc = args[3];
					dst = args[2];
					mod = "";
				}
			} else {
				desc = args[3];
				dst = args[2];
				mod = args[4];
			}

			return ((ClassMapping)parent).addField(src, dst, desc);
		case EnigmaFileFormat.METHOD:
			if (args.length < 3 || args.length > 5) {
				throw new IllegalStateException("illegal number of arguments (" + args.length + ") for method mapping on line " + lineNumber + " - expected 3-5");
			}
			if (parent == null || parent.target() != MappingTarget.CLASS) {
				throw new IllegalStateException("invalid hierarchy on line " + lineNumber + ": a method must be the child of a class");
			}

			src = args[1];

			if (args.length == 3) {
				desc = args[2];
				dst = "";
				mod = "";
			} else if (args.length == 4) {
				if (isModifier(args[3])) {
					desc = args[2];
					dst = "";
					mod = args[3];
				} else {
					desc = args[3];
					dst = args[2];
					mod = "";
				}
			} else {
				desc = args[3];
				dst = args[2];
				mod = args[4];
			}

			return ((ClassMapping)parent).addMethod(src, dst, desc);
		case EnigmaFileFormat.PARAMETER:
			if (args.length != 3) {
				throw new IllegalStateException("illegal number of arguments (" + args.length + ") for parameter mapping on line " + lineNumber + " - expected 3");
			}
			if (parent == null || parent.target() != MappingTarget.METHOD) {
				throw new IllegalStateException("invalid hierarchy on line " + lineNumber + ": a parameter must be the child of a method");
			}

			String rawIndex = args[1];
			dst = args[2];

			int index = Integer.parseInt(rawIndex);

			if (index < 0) {
				throw new IllegalStateException("illegal parameter index " + index + " on line " + lineNumber + " - cannot be negative!");
			}

			return ((MethodMapping)parent).addParameter("", dst, index);
		case EnigmaFileFormat.COMMENT:
			if (parent == null) {
				throw new IllegalStateException("invalid hierarchy on line " + lineNumber + ": javadocs must have a parent");
			}

			String javadoc = parent.getJavadoc();

			if (!javadoc.isEmpty()) {
				javadoc += "\\n";
			}

			javadoc += String.join(" ", Arrays.copyOfRange(args, 1, args.length));

			parent.setJavadoc(javadoc);

			return null;
		default:
			throw new IllegalStateException("unknown mapping target " + args[0] + " on line " + lineNumber);
		}
	}

	private boolean isModifier(String s) {
		return s.startsWith(EnigmaFileFormat.MODIFIER);
	}
}
