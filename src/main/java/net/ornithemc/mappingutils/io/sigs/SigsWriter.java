package net.ornithemc.mappingutils.io.sigs;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

import net.ornithemc.mappingutils.io.sigs.SignatureMappings.ClassMapping;
import net.ornithemc.mappingutils.io.sigs.SignatureMappings.MemberMapping;

public class SigsWriter {

	public static void write(Path path, SignatureMappings sigs) throws IOException {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toFile()))) {
			write(writer, sigs);
		} catch (Exception e) {
			throw new IllegalStateException("error writing " + path.toString(), e);
		}
	}

	public static void write(BufferedWriter writer, SignatureMappings sigs) throws IOException {
		new SigsWriter(writer, sigs).write();
	}

	private static final String TAB = "\t";

	private final BufferedWriter writer;
	private final SignatureMappings sigs;

	private SigsWriter(BufferedWriter writer, SignatureMappings sigs) {
		this.writer = writer;
		this.sigs = sigs;
	}

	public void write() throws IOException {
		for (ClassMapping c : sigs.getClasses()) {
			writeClass(c);
		}
	}

	private void writeClass(ClassMapping c) throws IOException {
		writer.write(c.getName());
		if (c.getMode() == SignatureMode.REMOVE) {
			writer.write(TAB);
			writer.write(".");
		} else if (c.getMode() == SignatureMode.MODIFY) {
			writer.write(TAB);
			writer.write(c.getSignature());
		}
		writer.newLine();

		for (MemberMapping m : c.getMembers()) {
			writeMember(m);
		}
	}

	private void writeMember(MemberMapping m) throws IOException {
		writer.write(TAB);
		writer.write(m.getName());
		writer.write(TAB);
		writer.write(m.getDesc());
		if (m.getMode() == SignatureMode.REMOVE) {
			writer.write(TAB);
			writer.write(".");
		} else if (m.getMode() == SignatureMode.MODIFY) {
			writer.write(TAB);
			writer.write(m.getSignature());
		}
		writer.newLine();
	}
}
