package net.ornithemc.mappingutils.io.enigma.dir;

import java.nio.file.Files;
import java.nio.file.Path;

import net.ornithemc.mappingutils.FileUtils;
import net.ornithemc.mappingutils.io.Format;
import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.Mappings.ClassMapping;
import net.ornithemc.mappingutils.io.enigma.file.EnigmaFileWriter;

public class EnigmaDirWriter {

	public static void write(Path dir, Mappings mappings) throws Exception {
		new EnigmaDirWriter(dir, mappings).write();
	}

	private final Path dir;
	private final Mappings mappings;

	private EnigmaDirWriter(Path dir, Mappings mappings) {
		mappings.validate();

		this.dir = dir;
		this.mappings = mappings;
	}

	public void write() throws Exception {
		for (ClassMapping cm : mappings.getTopLevelClasses()) {
			Path cp = dir.resolve(cm.getComplete() + Format.ENIGMA_FILE.mappingsExtension());

			FileUtils.delete(cp.toFile());
			Files.createDirectories(cp);
			Files.deleteIfExists(cp);

			EnigmaFileWriter.write(cp, cm);
		}
	}
}
