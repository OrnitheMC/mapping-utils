package net.ornithemc.mappingutils.io.diff.tiny.v2;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

import net.ornithemc.mappingutils.io.diff.DiffSide;
import net.ornithemc.mappingutils.io.diff.MappingsDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.ClassDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.Diff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.FieldDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.JavadocDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.MethodDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.ParameterDiff;
import net.ornithemc.mappingutils.io.diff.tiny.TinyDiffWriter;

public class TinyV2DiffWriter extends TinyDiffWriter {

	public static void write(Path path, MappingsDiff diff) throws IOException {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toFile()))) {
			write(writer, diff);
		} catch (Exception e) {
			throw new IOException("error writing " + path.toString(), e);
		}
	}

	public static void write(BufferedWriter writer, MappingsDiff diff) throws IOException {
		new TinyV2DiffWriter(writer, diff).write();
	}

	private int indents;

	private TinyV2DiffWriter(BufferedWriter writer, MappingsDiff diff) {
		super(writer, diff);
	}

	@Override
	protected void writeHeader() throws IOException {
		writer.write(TinyV2Format.FORMAT);
		writer.write(TAB);
		writer.write(TinyV2Format.VERSION);
		writer.write(TAB);
		writer.write(TinyV2Format.MINOR_VERSION);
		writer.newLine();
	}

	@Override
	protected void writeDiffs() throws IOException {
		for (ClassDiff c : diff.getTopLevelClasses()) {
			writeClass(c);
		}
	}

	private void writeClass(ClassDiff c) throws IOException {
		indent(TinyV2Format.CLASS_INDENTS);

		writer.write(TinyV2Format.CLASS);
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
		for (ClassDiff cc : c.getClasses()) {
			writeClass(cc);
		}
	}

	private void writeField(FieldDiff f) throws IOException {
		indent(TinyV2Format.FIELD_INDENTS);

		writer.write(TinyV2Format.FIELD);
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

	private void writeMethod(MethodDiff m) throws IOException {
		indent(TinyV2Format.METHOD_INDENTS);

		writer.write(TinyV2Format.METHOD);
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

	private void writeParameter(ParameterDiff p) throws IOException {
		indent(TinyV2Format.PARAMETER_INDENTS);

		writer.write(TinyV2Format.PARAMETER);
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

	private void writeJavadoc(Diff d) throws IOException {
		JavadocDiff javadoc = d.getJavadoc();

		if (javadoc.isDiff()) {
			indents++;

			indent();
			writer.write(TinyV2Format.COMMENT);
			writer.write(TAB);
			writer.write(javadoc.get(DiffSide.A));
			writer.write(TAB);
			writer.write(javadoc.get(DiffSide.B));
			writer.newLine();

			indents--;
		}
	}

	private void indent(int indents) throws IOException {
		this.indents = indents;
		indent();
	}

	private void indent() throws IOException {
		for (int i = 0; i < indents; i++) {
			writer.write(TAB);
		}
	}
}
