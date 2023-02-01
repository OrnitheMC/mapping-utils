package net.ornithemc.mappingutils.io.diff.tiny.v1;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

import net.ornithemc.mappingutils.io.diff.DiffSide;
import net.ornithemc.mappingutils.io.diff.MappingsDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.ClassDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.FieldDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.MethodDiff;
import net.ornithemc.mappingutils.io.diff.tiny.TinyDiffWriter;

public class TinyV1DiffWriter extends TinyDiffWriter {

	public static void write(Path path, MappingsDiff diff) throws IOException {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toFile()))) {
			write(writer, diff);
		} catch (Exception e) {
			throw new IOException("error writing " + path.toString(), e);
		}
	}

	public static void write(BufferedWriter writer, MappingsDiff diff) throws IOException {
		new TinyV1DiffWriter(writer, diff).write();
	}

	private TinyV1DiffWriter(BufferedWriter writer, MappingsDiff diff) {
		super(writer, diff);
	}

	@Override
	protected void writeHeader() throws IOException {
		writer.write(TinyV1Format.VERSION);
		writer.newLine();
	}

	@Override
	protected void writeDiffs() throws IOException {
		for (ClassDiff c : diff.getTopLevelClasses()) {
			writeClass(c);
		}
	}

	private void writeClass(ClassDiff c) throws IOException {
		writer.write(TinyV1Format.CLASS);
		writer.write(TAB);
		writer.write(c.src());
		if (c.isDiff()) {
			writer.write(TAB);
			writer.write(c.get(DiffSide.A).isEmpty() ? c.src() : c.getComplete(DiffSide.A));
			writer.write(TAB);
			writer.write(c.get(DiffSide.B).isEmpty() ? c.src() : c.getComplete(DiffSide.B));
		}
		writer.newLine();

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
		if (!f.isDiff()) {
			return;
		}

		writer.write(TinyV1Format.FIELD);
		writer.write(TAB);
		writer.write(f.getParent().src());
		writer.write(TAB);
		writer.write(f.getDesc());
		writer.write(TAB);
		writer.write(f.src());
		writer.write(TAB);
		writer.write(f.get(DiffSide.A));
		writer.write(TAB);
		writer.write(f.get(DiffSide.B));
		writer.newLine();
	}

	private void writeMethod(MethodDiff m) throws IOException {
		if (!m.isDiff()) {
			return;
		}

		writer.write(TinyV1Format.METHOD);
		writer.write(TAB);
		writer.write(m.getParent().src());
		writer.write(TAB);
		writer.write(m.getDesc());
		writer.write(TAB);
		writer.write(m.src());
		writer.write(TAB);
		writer.write(m.get(DiffSide.A));
		writer.write(TAB);
		writer.write(m.get(DiffSide.B));
		writer.newLine();
	}
}
