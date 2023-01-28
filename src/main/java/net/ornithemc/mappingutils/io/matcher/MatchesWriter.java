package net.ornithemc.mappingutils.io.matcher;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import net.ornithemc.mappingutils.io.matcher.Matches.ClassMatch;
import net.ornithemc.mappingutils.io.matcher.Matches.FieldMatch;
import net.ornithemc.mappingutils.io.matcher.Matches.Match;
import net.ornithemc.mappingutils.io.matcher.Matches.MethodMatch;
import net.ornithemc.mappingutils.io.matcher.Matches.ParameterMatch;
import net.ornithemc.mappingutils.io.matcher.Matches.VariableMatch;

public class MatchesWriter {

	public static void write(Path path, Matches matches) throws Exception {
		write(path, matches, MatchSide.A);
	}

	public static void write(Path path, Matches matches, MatchSide srcSide) throws Exception {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toFile()))) {
			write(writer, matches, srcSide);
		} catch (Exception e) {
			throw new IllegalStateException("error writing " + path.toString(), e);
		}
	}

	public static void write(BufferedWriter writer, Matches matches) throws Exception {
		write(writer, matches, MatchSide.A);
	}

	public static void write(BufferedWriter writer, Matches matches, MatchSide srcSide) throws Exception {
		new MatchesWriter(writer, matches, srcSide).write();
	}

	private static final String TAB = "\t";

	private final BufferedWriter writer;
	private final Matches matches;
	private final MatchSide srcSide;

	private MatchesWriter(BufferedWriter writer, Matches matches, MatchSide srcSide) {
		this.writer = writer;
		this.matches = matches;
		this.srcSide = srcSide;
	}

	public void write() throws Exception {
		writeHeader();
		writeMatches();
	}

	private void writeHeader() throws Exception {
		writer.write(matches.header);
		writer.newLine();

		writeInputFiles(Matches.INPUT_A, matches.getInput(srcSide));
		writeInputFiles(Matches.INPUT_B, matches.getInput(srcSide.opposite()));
		writeInputFiles(Matches.SHARED_CLASSPATH, matches.getSharedClasspath());
		writeInputFiles(Matches.CLASSPATH_A, matches.getClasspath(srcSide));
		writeInputFiles(Matches.CLASSPATH_B, matches.getClasspath(srcSide.opposite()));

		writeObfuscationPattern(Matches.NON_OBF_CLASS_A, matches.getNonObfClassPattern(srcSide));
		writeObfuscationPattern(Matches.NON_OBF_CLASS_B, matches.getNonObfClassPattern(srcSide.opposite()));
		writeObfuscationPattern(Matches.NON_OBF_MEMBER_A, matches.getNonObfMemberPattern(srcSide));
		writeObfuscationPattern(Matches.NON_OBF_MEMBER_B, matches.getNonObfMemberPattern(srcSide.opposite()));
	}

	private void writeInputFiles(String type, List<InputFile> files) throws Exception {
		writer.write(TAB);
		writer.write(type);
		writer.newLine();

		for (InputFile file : files) {
			writer.write(TAB + TAB);

			if (file.size == -1) { // v1
				writer.write(file.name);
			} else if (file.alg == null) { // v2
				writer.write(Long.toString(file.size));
				writer.write(TAB);
				writer.write(Base64.getEncoder().encodeToString(file.hash));
				writer.write(TAB);
				writer.write(file.name);
			} else { // v3
				writer.write(Long.toString(file.size));
				writer.write(TAB);
				writer.write(file.alg.name());
				writer.write(TAB);
				writer.write(Base64.getEncoder().encodeToString(file.hash));
				writer.write(TAB);
				writer.write(file.name);
			}

			writer.newLine();
		}
	}

	private void writeObfuscationPattern(String type, String pattern) throws Exception {
		if (pattern != null && !pattern.isEmpty()) {
			writer.write(TAB);
			writer.write(type);
			writer.write(TAB);
			writer.write(pattern);
			writer.newLine();
		}
	}

	private void writeMatches() throws Exception {
		for (ClassMatch c : matches.getClasses()) {
			writeClass(c);
		}
	}

	private void writeMatch(String type, Match<?> match) throws Exception {
		if (match.matched()) {
			writer.write(type);
			writer.write(TAB);
			writer.write(match.get(srcSide));
			writer.write(TAB);
			writer.write(match.get(srcSide.opposite()));
			writer.newLine();
		} else {
			String a = match.get(srcSide);
			String b = match.get(srcSide.opposite());

			writer.write(type);
			writer.write(Matches.UNMATCHABLE);
			writer.write(TAB);
			writer.write(a == null ? "b" : "a");
			writer.write(TAB);
			writer.write(a == null ? b : a);
			writer.newLine();
		}
	}

	private void writeClass(ClassMatch c) throws Exception {
		indent(Matches.CLASS_INDENTS);
		writeMatch(Matches.CLASS, c);

		for (MethodMatch m : c.getMethods()) {
			writeMethod(m);
		}
		for (FieldMatch f : c.getFields()) {
			writeField(f);
		}
	}

	private void writeMethod(MethodMatch m) throws Exception {
		indent(Matches.METHOD_INDENTS);
		writeMatch(Matches.METHOD, m);

		for (ParameterMatch p : m.getParameters()) {
			writeParameter(p);
		}
		for (VariableMatch v : m.getVariables()) {
			writeVariable(v);
		}
	}

	private void writeField(FieldMatch f) throws Exception {
		indent(Matches.FIELD_INDENTS);
		writeMatch(Matches.FIELD, f);
	}

	private void writeParameter(ParameterMatch p) throws Exception {
		indent(Matches.PARAMETER_INDENTS);
		writeMatch(Matches.PARAMETER, p);
	}

	private void writeVariable(VariableMatch v) throws Exception {
		indent(Matches.VARIABLE_INDENTS);
		writeMatch(Matches.VARIABLE, v);
	}

	private void indent(int indents) throws Exception {
		for (int i = 0; i < indents; i++) {
			writer.write(TAB);
		}
	}
}
