package net.ornithemc.mappingutils.io.diff.tiny.v1;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Path;

import net.ornithemc.mappingutils.io.diff.DiffSide;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.ClassDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.FieldDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.MethodDiff;
import net.ornithemc.mappingutils.io.diff.tiny.TinyDiffWriter;

public class TinyV1DiffWriter extends TinyDiffWriter<TinyV1Diff> {

	public static void write(Path path, TinyV1Diff diff) throws Exception {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toFile()))) {
			write(writer, diff);
		}
	}

	public static void write(BufferedWriter writer, TinyV1Diff diff) throws Exception {
		new TinyV1DiffWriter(writer, diff).write();
	}

	private TinyV1DiffWriter(BufferedWriter writer, TinyV1Diff diff) {
		super(writer, diff);
	}

	@Override
	protected void writeHeader() throws Exception {
		TinyV1DiffHeader header = diff.getHeader();

		writer.write(header.getTinyVersion());
		writer.newLine();
	}

	@Override
	protected void writeDiffs() throws Exception {
		for (ClassDiff c : diff.getClasses()) {
			writeClass(c);
		}
	}

	private void writeClass(ClassDiff c) throws Exception {
		writer.write(TinyV1Diff.CLASS);
		writer.write(TAB);
		writer.write(c.src());
		writer.write(TAB);
		writer.write(c.get(DiffSide.A));
		writer.write(TAB);
		writer.write(c.get(DiffSide.B));
		writer.newLine();

		for (FieldDiff f : c.getFields()) {
			writeField(f);
		}
		for (MethodDiff m : c.getMethods()) {
			writeMethod(m);
		}
	}

	private void writeField(FieldDiff f) throws Exception {
		writer.write(TinyV1Diff.FIELD);
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

	private void writeMethod(MethodDiff m) throws Exception {
		writer.write(TinyV1Diff.METHOD);
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
