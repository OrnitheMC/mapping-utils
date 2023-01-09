package net.ornithemc.mappingutils.io.tiny.v1;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Path;

import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.Mappings.ClassMapping;
import net.ornithemc.mappingutils.io.Mappings.FieldMapping;
import net.ornithemc.mappingutils.io.Mappings.MethodMapping;
import net.ornithemc.mappingutils.io.tiny.TinyMappingsWriter;

public class TinyV1Writer extends TinyMappingsWriter {

	public static void write(Path path, Mappings mappings) throws Exception {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toFile()))) {
			write(writer, mappings);
		} catch (Exception e) {
			throw new IllegalStateException("error writing " + path.toString(), e);
		}
	}

	public static void write(BufferedWriter writer, Mappings mappings) throws Exception {
		new TinyV1Writer(writer, mappings).write();
	}

	private TinyV1Writer(BufferedWriter writer, Mappings mappings) {
		super(writer, mappings);
	}

	@Override
	protected void writeHeader() throws Exception {
		writer.write(TinyV1Format.VERSION);
		writer.write(TAB);
		writer.write(mappings.getSrcNamespace().toString());
		writer.write(TAB);
		writer.write(mappings.getDstNamespace().toString());
		writer.newLine();
	}

	@Override
	protected void writeMappings() throws Exception {
		for (ClassMapping c : mappings.getTopLevelClasses()) {
			writeClass(c);
		}
	}

	private void writeClass(ClassMapping c) throws Exception {
		writer.write(TinyV1Format.CLASS);
		writer.write(TAB);
		writer.write(c.src());
		writer.write(TAB);
		writer.write(c.get().isEmpty() ? c.src() : c.getComplete());
		writer.newLine();

		for (FieldMapping f : c.getFields()) {
			writeField(f);
		}
		for (MethodMapping m : c.getMethods()) {
			writeMethod(m);
		}
		for (ClassMapping cc : c.getClasses()) {
			writeClass(cc);
		}
	}

	private void writeField(FieldMapping f) throws Exception {
		writer.write(TinyV1Format.FIELD);
		writer.write(TAB);
		writer.write(f.getParent().src());
		writer.write(TAB);
		writer.write(f.getDesc());
		writer.write(TAB);
		writer.write(f.src());
		writer.write(TAB);
		writer.write(f.get().isEmpty() ? f.src() : f.get());
		writer.newLine();
	}

	private void writeMethod(MethodMapping m) throws Exception {
		writer.write(TinyV1Format.METHOD);
		writer.write(TAB);
		writer.write(m.getParent().src());
		writer.write(TAB);
		writer.write(m.getDesc());
		writer.write(TAB);
		writer.write(m.src());
		writer.write(TAB);
		writer.write(m.get().isEmpty() ? m.src() : m.get());
		writer.newLine();
	}
}
