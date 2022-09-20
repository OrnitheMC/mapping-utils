package net.ornithemc.mappingutils.io.diff.tiny.v2;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Path;

import net.ornithemc.mappingutils.io.diff.DiffSide;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.ClassDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.Diff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.FieldDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.JavadocDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.MethodDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.ParameterDiff;
import net.ornithemc.mappingutils.io.diff.tiny.TinyDiffWriter;

public class TinyV2DiffWriter extends TinyDiffWriter<TinyV2Diff> {

	public static void write(Path path, TinyV2Diff diff) throws Exception {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toFile()))) {
			write(writer, diff);
		}
	}

	public static void write(BufferedWriter writer, TinyV2Diff diff) throws Exception {
		new TinyV2DiffWriter(writer, diff).write();
	}

	private TinyV2DiffWriter(BufferedWriter writer, TinyV2Diff diff) {
		super(writer, diff);
	}

	@Override
	protected void writeHeader() throws Exception {
		TinyV2DiffHeader header = diff.getHeader();

		writer.write(header.getFormat());
		writer.write(TAB);
		writer.write(header.getTinyVersion());
		writer.write(TAB);
		writer.write(header.getMinorVersion());
		writer.newLine();
	}

	@Override
	protected void writeDiffs() throws Exception {
		for (ClassDiff c : diff.getClasses()) {
			writeClass(c);
		}
	}

	private void writeClass(ClassDiff c) throws Exception {
		indent(TinyV2Diff.CLASS_INDENTS);

		writer.write(TinyV2Diff.CLASS);
		writer.write(TAB);
		writer.write(c.src());
		if (c.isDiff()) {
			writer.write(TAB);
			writer.write(c.get(DiffSide.A));
			writer.write(TAB);
			writer.write(c.get(DiffSide.B));
		}
		writer.newLine();

		writeJavadoc(c);

		for (FieldDiff f : c.getFields()) {
			writeField(f);
		}
		for (MethodDiff m : c.getMethods()) {
			writeMethod(m);
		}
	}

	private void writeField(FieldDiff f) throws Exception {
		indent(TinyV2Diff.FIELD_INDENTS);

		writer.write(TinyV2Diff.FIELD);
		writer.write(TAB);
		writer.write(f.getDesc());
		writer.write(TAB);
		writer.write(f.src());
		if (f.isDiff()) {
			writer.write(TAB);
			writer.write(f.get(DiffSide.A));
			writer.write(TAB);
			writer.write(f.get(DiffSide.B));
		}
		writer.newLine();

		writeJavadoc(f);
	}

	private void writeMethod(MethodDiff m) throws Exception {
		indent(TinyV2Diff.METHOD_INDENTS);

		writer.write(TinyV2Diff.METHOD);
		writer.write(TAB);
		writer.write(m.getDesc());
		writer.write(TAB);
		writer.write(m.src());
		if (m.isDiff()) {
			writer.write(TAB);
			writer.write(m.get(DiffSide.A));
			writer.write(TAB);
			writer.write(m.get(DiffSide.B));
		}
		writer.newLine();

		writeJavadoc(m);

		for (ParameterDiff p : m.getParameters()) {
			writeParameter(p);
		}
	}

	private void writeParameter(ParameterDiff p) throws Exception {
		indent(TinyV2Diff.PARAMETER_INDENTS);

		writer.write(TinyV2Diff.PARAMETER);
		writer.write(TAB);
		writer.write(Integer.toString(p.getIndex()));
		writer.write(TAB);
		writer.write(p.src());
		if (p.isDiff()) {
			writer.write(TAB);
			writer.write(p.get(DiffSide.A));
			writer.write(TAB);
			writer.write(p.get(DiffSide.B));
		}
		writer.newLine();

		writeJavadoc(p);
	}

	private void writeJavadoc(Diff<?> d) throws Exception {
		JavadocDiff javadoc = d.getJavadoc();

		if (javadoc.isDiff()) {
			indents++;

			indent();
			writer.write(TinyV2Diff.COMMENT);
			writer.write(TAB);
			writer.write(javadoc.get(DiffSide.A));
			writer.write(TAB);
			writer.write(javadoc.get(DiffSide.B));
			writer.newLine();

			indents--;
		}
	}

	private void indent(int indents) throws Exception {
		this.indents = indents;
		indent();
	}

	private void indent() throws Exception {
		for (int i = 0; i < indents; i++) {
			writer.write(TAB);
		}
	}
}
